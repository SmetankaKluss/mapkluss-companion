package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class CompanionSyncService {
    private final CompanionApiClient api;
    private final Path minecraftRunDir;
    private final InstalledArtifactIndex installedIndex;
    private final LitematicInstaller litematicInstaller;

    public CompanionSyncService(CompanionApiClient api, Path minecraftRunDir, InstalledArtifactIndex installedIndex, Path schematicDir) {
        this.api = api;
        this.minecraftRunDir = minecraftRunDir;
        this.installedIndex = installedIndex;
        this.litematicInstaller = new LitematicInstaller(schematicDir, installedIndex);
    }

    public static CompanionSyncService create(Path minecraftRunDir, CompanionConfig config) throws IOException {
        CompanionApiClient api = new CompanionApiClient(config);
        if (config.accessToken() != null && !config.accessToken().isBlank()) {
            api.setBearerToken(config.accessToken());
        }
        InstalledArtifactIndex index = InstalledArtifactIndex.load(LitematicaPaths.companionIndexPath(minecraftRunDir));
        return new CompanionSyncService(api, minecraftRunDir, index, LitematicaPaths.defaultSchematicDir(minecraftRunDir));
    }

    public InstalledArtifact installLitematic(String artId) throws IOException, InterruptedException {
        CompanionManifest manifest = api.manifest(artId);
        CompanionArtifact artifact = manifest.litematicArtifact()
            .orElseThrow(() -> new IOException("Art has no litematic artifact"));
        byte[] bytes = api.downloadArtifact(artifact);
        removeLitematicsForArt(artId);
        return litematicInstaller.install(manifest, artifact, bytes);
    }

    public List<InstalledArtifact> installLitematicTiles(String artId) throws IOException, InterruptedException {
        CompanionManifest manifest = api.manifest(artId);
        CompanionArtifact artifact = manifest.litematicTilesArtifact()
            .orElseThrow(() -> new IOException("Art has no split litematic artifact"));
        byte[] bytes = api.downloadArtifact(artifact);
        verifyArtifact(artifact, bytes);
        removeLitematicsForArt(artId);
        return litematicInstaller.installTilesZip(manifest, artifact, bytes);
    }

    public SyncInstalledResult refreshInstalledLitematics() {
        int checked = 0;
        int refreshed = 0;
        int removedMissing = 0;
        int failed = 0;
        Set<String> artIds = new HashSet<>();
        for (InstalledArtifact entry : installedIndex.entries()) {
            artIds.add(entry.artId());
        }

        for (String artId : artIds) {
            checked++;
            try {
                CompanionManifest manifest = api.manifest(artId);
                if (usesSplitLitematics(artId)) {
                    CompanionArtifact artifact = manifest.litematicTilesArtifact()
                        .orElseThrow(() -> new IOException("Art has no split litematic artifact"));
                    byte[] bytes = api.downloadArtifact(artifact);
                    verifyArtifact(artifact, bytes);
                    removeLitematicsForArt(artId);
                    litematicInstaller.installTilesZip(manifest, artifact, bytes);
                } else {
                    CompanionArtifact artifact = manifest.litematicArtifact()
                        .orElseThrow(() -> new IOException("Art has no litematic artifact"));
                    boolean alreadyCurrent = installedIndex.findSameArtifact(artId, artifact.id(), artifact.sha256()).isPresent();
                    byte[] bytes = api.downloadArtifact(artifact);
                    if (!alreadyCurrent) {
                        removeLitematicsForArt(artId);
                    }
                    litematicInstaller.install(manifest, artifact, bytes);
                }
                refreshed++;
            } catch (Exception e) {
                failed++;
            }
        }

        return new SyncInstalledResult(checked, refreshed, removedMissing, failed);
    }

    public boolean removeLitematic(String artId, String artifactId) throws IOException {
        return litematicInstaller.uninstall(artId, artifactId);
    }

    public boolean removeLitematicsForArt(String artId) throws IOException {
        boolean removed = false;
        for (InstalledArtifact entry : installedIndex.findByArt(artId)) {
            removed |= litematicInstaller.uninstall(entry.artId(), entry.artifactId());
        }
        return removed;
    }

    public CompanionManifest refreshManifest(String artId) throws IOException, InterruptedException {
        return api.manifest(artId);
    }

    public ArtRefreshResult refreshArt(String artId) throws IOException, InterruptedException {
        CompanionManifest manifest = api.manifest(artId);
        if (manifest.litematicArtifact().isEmpty()) {
            return new ArtRefreshResult(manifest, null, false);
        }

        boolean hadInstalledEntries = !installedIndex.findByArt(artId).isEmpty();
        if (!hadInstalledEntries) {
            return new ArtRefreshResult(manifest, null, false);
        }

        if (usesSplitLitematics(artId)) {
            CompanionArtifact artifact = manifest.litematicTilesArtifact()
                .orElseThrow(() -> new IOException("Art has no split litematic artifact"));
            byte[] bytes = api.downloadArtifact(artifact);
            verifyArtifact(artifact, bytes);
            removeLitematicsForArt(artId);
            List<InstalledArtifact> installed = litematicInstaller.installTilesZip(manifest, artifact, bytes);
            return new ArtRefreshResult(manifest, installed.isEmpty() ? null : installed.get(0), true);
        }

        CompanionArtifact artifact = manifest.litematicArtifact().orElseThrow();
        boolean alreadyCurrent = installedIndex.findSameArtifact(artId, artifact.id(), artifact.sha256()).isPresent();
        byte[] bytes = api.downloadArtifact(artifact);
        if (!alreadyCurrent) {
            removeLitematicsForArt(artId);
        }
        InstalledArtifact installed = litematicInstaller.install(manifest, artifact, bytes);
        return new ArtRefreshResult(manifest, installed, true);
    }

    public Path downloadArtifact(CompanionArtifact artifact) throws IOException, InterruptedException {
        byte[] bytes = api.downloadArtifact(artifact);
        verifyArtifact(artifact, bytes);
        Path target = downloadedArtifactPath(artifact);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return target;
    }

    public Path downloadsDir() {
        return minecraftRunDir
            .resolve("mapkluss")
            .resolve("downloads");
    }

    public MapDatImportResult importMapDat(String artId, Path worldDir) throws IOException, InterruptedException {
        CompanionManifest manifest = api.manifest(artId);
        CompanionArtifact artifact = manifest.mapDatArtifact()
            .orElseThrow(() -> new IOException("Art has no MAP.DAT artifact"));
        byte[] bytes = api.downloadArtifact(artifact);
        verifyArtifact(artifact, bytes);
        return MapDatImporter.importZip(worldDir, bytes);
    }

    private static void verifyArtifact(CompanionArtifact artifact, byte[] bytes) throws IOException {
        String actualSha = sha256(bytes);
        if (artifact.sha256() != null && !artifact.sha256().isBlank() && !actualSha.equalsIgnoreCase(artifact.sha256())) {
            throw new IOException("Downloaded artifact checksum mismatch: " + artifact.filename());
        }
    }

    private Path downloadedArtifactPath(CompanionArtifact artifact) {
        return downloadsDir()
            .resolve(SafeNames.slug(artifact.kind()))
            .resolve(SafeNames.downloadFilename(artifact.filename(), artifact.kind()));
    }

    public ScanUploadResponse uploadScan(MapScanDraft draft) throws IOException, InterruptedException {
        return api.uploadScan(draft);
    }

    public BuildSessionState tracker(String sessionId) throws IOException, InterruptedException {
        return api.tracker(sessionId);
    }

    public BuildSessionState trackerForArt(String artId) throws IOException, InterruptedException {
        return api.trackerForArt(artId);
    }

    public InstalledArtifactIndex installedIndex() {
        return installedIndex;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available", e);
        }
    }

    private boolean usesSplitLitematics(String artId) {
        return installedIndex.findByArt(artId).stream().anyMatch(entry -> entry.artifactId().contains("#"));
    }
}
