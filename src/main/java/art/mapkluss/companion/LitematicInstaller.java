package art.mapkluss.companion;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class LitematicInstaller {
    private static final int MAX_TILE_ENTRIES = 256;
    private static final int MAX_TILE_BYTES = 64 * 1024 * 1024;
    private static final long MAX_EXPANDED_ZIP_BYTES = 128L * 1024 * 1024;
    private final Path schematicDir;
    private final InstalledArtifactIndex index;

    public LitematicInstaller(Path schematicDir, InstalledArtifactIndex index) {
        this.schematicDir = schematicDir;
        this.index = index;
    }

    public InstalledArtifact install(CompanionManifest manifest, CompanionArtifact artifact, byte[] bytes) throws IOException {
        if (!artifact.isLitematic()) throw new IllegalArgumentException("Artifact is not a litematic: " + artifact.kind());
        String actualSha = sha256(bytes);
        if (!actualSha.equalsIgnoreCase(artifact.sha256())) {
            throw new IOException("Downloaded litematic checksum mismatch");
        }
        validateLitematic(bytes, artifact.filename());

        var existing = index.findSameArtifact(manifest.artId(), artifact.id(), artifact.sha256());
        if (existing.isPresent() && isManagedPath(Path.of(existing.get().path())) && Files.exists(Path.of(existing.get().path()))) {
            InstalledArtifact refreshed = refreshExistingInstall(manifest, artifact, existing.get());
            index.upsert(refreshed);
            index.save();
            return refreshed;
        }

        Files.createDirectories(schematicDir);
        Path target = chooseTargetPath(manifest, artifact);
        Files.write(target, bytes);
        InstalledArtifact installed = new InstalledArtifact(
            manifest.artId(),
            artifact.id(),
            artifact.sha256(),
            target.toAbsolutePath().toString(),
            target.getFileName().toString(),
            System.currentTimeMillis()
        );
        index.upsert(installed);
        index.save();
        return installed;
    }

    public List<InstalledArtifact> installTilesZip(CompanionManifest manifest, CompanionArtifact artifact, byte[] bytes) throws IOException {
        if (!artifact.isLitematicTilesZip()) throw new IllegalArgumentException("Artifact is not a litematic tile zip: " + artifact.kind());
        String actualSha = sha256(bytes);
        if (!actualSha.equalsIgnoreCase(artifact.sha256())) {
            throw new IOException("Downloaded litematic tile ZIP checksum mismatch");
        }

        Files.createDirectories(schematicDir);
        List<TilePayload> payloads = new ArrayList<>();
        long expandedBytes = 0L;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int indexInZip = 1;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (indexInZip > MAX_TILE_ENTRIES) throw new IOException("Litematic tile ZIP has too many files");
                String entryName = entry.getName();
                if (entryName == null || !entryName.toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")) continue;
                byte[] tileBytes = CompanionApiClient.readBounded(zip, MAX_TILE_BYTES);
                expandedBytes += tileBytes.length;
                if (expandedBytes > MAX_EXPANDED_ZIP_BYTES) {
                    throw new IOException("Litematic tile ZIP expands beyond the safe limit");
                }
                validateLitematic(tileBytes, entryName);
                String tileSha = sha256(tileBytes);
                String tileArtifactId = artifact.id() + "#" + indexInZip;
                payloads.add(new TilePayload(entryName, tileSha, tileArtifactId, tileBytes, indexInZip));
                indexInZip++;
            }
        }

        if (payloads.isEmpty()) {
            throw new IOException("Litematic tile ZIP has no .litematic files: " + artifact.filename());
        }

        List<InstalledArtifact> installed = new ArrayList<>();
        for (TilePayload payload : payloads) {
                Path target = chooseTileTargetPath(manifest, payload.entryName(), payload.sha256(), payload.indexInZip());
                AtomicFiles.write(target, payload.bytes(), false);
                InstalledArtifact tile = new InstalledArtifact(
                    manifest.artId(),
                    payload.artifactId(),
                    payload.sha256(),
                    target.toAbsolutePath().toString(),
                    target.getFileName().toString(),
                    System.currentTimeMillis()
                );
                index.upsert(tile);
                installed.add(tile);
        }
        index.save();
        return installed;
    }

    public boolean uninstall(String artId, String artifactId) throws IOException {
        boolean removedFile = false;
        for (InstalledArtifact entry : index.entries()) {
            if (entry.artId().equals(artId) && entry.artifactId().equals(artifactId)) {
                Path installedPath = Path.of(entry.path());
                if (isManagedPath(installedPath)) {
                    Files.deleteIfExists(installedPath);
                    removedFile = true;
                }
            }
        }
        boolean removedIndex = index.remove(artId, artifactId);
        if (removedIndex) index.save();
        return removedFile || removedIndex;
    }

    private Path chooseTargetPath(CompanionManifest manifest, CompanionArtifact artifact) throws IOException {
        Path canonical = schematicDir.resolve(canonicalLitematicFilename(manifest, artifact));
        if (!Files.exists(canonical)) return canonical;
        String existingSha = sha256(Files.readAllBytes(canonical));
        if (existingSha.equalsIgnoreCase(artifact.sha256())) return canonical;

        for (int suffix = 1; suffix <= 99; suffix++) {
            Path numbered = schematicDir.resolve(SafeNames.conflictFilename(
                manifest.title(),
                manifest.grid().wide(),
                manifest.grid().tall(),
                suffix
            ));
            if (!Files.exists(numbered)) return numbered;
            String numberedSha = sha256(Files.readAllBytes(numbered));
            if (numberedSha.equalsIgnoreCase(artifact.sha256())) return numbered;
        }

        String shortId = artifact.id().replace("-", "");
        if (shortId.length() > 8) shortId = shortId.substring(0, 8);
        return schematicDir.resolve(SafeNames.conflictFilename(
            manifest.title(),
            manifest.grid().wide(),
            manifest.grid().tall(),
            shortId
        ));
    }

    private String canonicalLitematicFilename(CompanionManifest manifest, CompanionArtifact artifact) {
        String fallback = SafeNames.litematicFilename(
            manifest.title(),
            manifest.grid().wide(),
            manifest.grid().tall()
        );
        String filename = SafeNames.downloadFilename(artifact.filename(), fallback);
        return filename.toLowerCase(java.util.Locale.ROOT).endsWith(".litematic") ? filename : fallback;
    }

    private Path chooseTileTargetPath(CompanionManifest manifest, String entryName, String sha256, int indexInZip) throws IOException {
        String fallback = SafeNames.tileLitematicFilename(manifest.title(), manifest.grid().wide(), manifest.grid().tall(), indexInZip);
        String filename = SafeNames.downloadFilename(entryName, fallback);
        if (!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")) filename = fallback;
        Path canonical = schematicDir.resolve(filename);
        if (!Files.exists(canonical)) return canonical;
        String existingSha = sha256(Files.readAllBytes(canonical));
        if (existingSha.equalsIgnoreCase(sha256)) return canonical;

        for (int suffix = 1; suffix <= 99; suffix++) {
            Path numbered = schematicDir.resolve(SafeNames.tileConflictFilename(
                manifest.title(),
                manifest.grid().wide(),
                manifest.grid().tall(),
                indexInZip,
                suffix
            ));
            if (!Files.exists(numbered)) return numbered;
            String numberedSha = sha256(Files.readAllBytes(numbered));
            if (numberedSha.equalsIgnoreCase(sha256)) return numbered;
        }

        return schematicDir.resolve(SafeNames.tileConflictFilename(
            manifest.title(),
            manifest.grid().wide(),
            manifest.grid().tall(),
            indexInZip,
            artifactSafeSuffix(sha256)
        ));
    }

    private static String artifactSafeSuffix(String sha256) {
        return sha256 == null || sha256.length() < 8 ? "tile" : sha256.substring(0, 8);
    }

    private InstalledArtifact refreshExistingInstall(CompanionManifest manifest, CompanionArtifact artifact, InstalledArtifact existing) throws IOException {
        Path current = Path.of(existing.path());
        if (!isManagedPath(current)) {
            throw new IOException("Refusing to move litematic outside schematic folder");
        }
        Path desired = chooseTargetPath(manifest, artifact);
        if (!current.toAbsolutePath().normalize().equals(desired.toAbsolutePath().normalize())) {
            if (!Files.exists(desired)) {
                Files.createDirectories(desired.getParent());
                Files.move(current, desired);
            } else {
                String desiredSha = sha256(Files.readAllBytes(desired));
                if (desiredSha.equalsIgnoreCase(artifact.sha256())) {
                    Files.deleteIfExists(current);
                } else {
                    desired = current;
                }
            }
        }
        return new InstalledArtifact(
            existing.artId(),
            existing.artifactId(),
            existing.sha256(),
            desired.toAbsolutePath().toString(),
            desired.getFileName().toString(),
            existing.installedAt()
        );
    }

    private boolean isManagedPath(Path path) {
        Path normalizedDir = schematicDir.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        return normalizedPath.startsWith(normalizedDir);
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private static void validateLitematic(byte[] bytes, String filename) throws IOException {
        if (bytes.length < 32) {
            throw new IOException("Downloaded litematic is too small: " + filename);
        }
        if ((bytes[0] & 0xFF) != 0x1F || (bytes[1] & 0xFF) != 0x8B) {
            throw new IOException("Downloaded litematic is not gzip-compressed: " + filename);
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            int rootTag = gzip.read();
            if (rootTag != 0x0A) {
                throw new IOException("Downloaded litematic is not NBT compound data: " + filename);
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains(filename)) throw e;
            throw new IOException("Downloaded litematic is invalid: " + filename, e);
        }
    }

    private record TilePayload(String entryName, String sha256, String artifactId, byte[] bytes, int indexInZip) {
    }
}
