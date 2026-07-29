package art.mapkluss.companion;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SuppressionBundleReader {
    private static final Gson GSON = new Gson();
    private static final int MAX_TILES = 100;
    private static final int MAX_ENTRIES = MAX_TILES * 2 + 4;
    private static final int MAX_LITEMATIC_NBT_BYTES = 32 * 1024 * 1024;

    private SuppressionBundleReader() { }

    public static SuppressionBundle read(Path path) throws IOException {
        SuppressionBundleCatalog catalog = readCatalog(path);
        if (catalog.tiles().size() != 1) {
            throw new IOException("Choose a map part from this multi-map Two-layer ZIP");
        }
        return catalog.tiles().getFirst().bundle();
    }

    public static SuppressionBundleCatalog readCatalog(Path path) throws IOException {
        byte[] bytes;
        try (InputStream input = Files.newInputStream(path)) {
            bytes = CompanionApiClient.readBounded(input, SuppressionPlanParser.MAX_BUNDLE_BYTES);
        }
        if (bytes.length < 1) {
            throw new IOException("Two-layer ZIP size is outside the safe limit");
        }
        return readCatalog(bytes, path.getFileName().toString());
    }

    static SuppressionBundle read(byte[] zipBytes, String sourceName) throws IOException {
        SuppressionBundleCatalog catalog = readCatalog(zipBytes, sourceName);
        if (catalog.tiles().size() != 1) {
            throw new IOException("Choose a map part from this multi-map Two-layer ZIP");
        }
        return catalog.tiles().getFirst().bundle();
    }

    static SuppressionBundleCatalog readCatalog(byte[] zipBytes, String sourceName) throws IOException {
        if (zipBytes == null || zipBytes.length < 1 || zipBytes.length > SuppressionPlanParser.MAX_BUNDLE_BYTES) {
            throw new IOException("Two-layer ZIP size is outside the safe limit");
        }
        Map<String, byte[]> files = unzip(zipBytes);
        byte[] manifestBytes = files.get("SHA256.json");
        if (manifestBytes == null) throw new IOException("Two-layer ZIP has no SHA256.json");
        BundleManifest manifest = parseManifest(manifestBytes);
        verifyManifestFiles(manifest, files);
        if (manifest.version == 1) {
            return readLegacy(files, sourceName, zipBytes);
        }
        if (manifest.version != 2) throw new IOException("Unsupported Two-layer bundle manifest version");
        return readMultiMap(manifest, files, sourceName, zipBytes);
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        int entries = 0;
        int totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++entries > MAX_ENTRIES) throw new IOException("Two-layer ZIP contains too many files");
                String name = safeZipEntry(entry.getName());
                int perFileLimit = name.toLowerCase(Locale.ROOT).endsWith(".litematic")
                    ? SuppressionPlanParser.MAX_LITEMATIC_BYTES
                    : SuppressionPlanParser.MAX_PLAN_BYTES;
                byte[] bytes = readBounded(zip, perFileLimit);
                totalBytes += bytes.length;
                if (totalBytes > SuppressionPlanParser.MAX_BUNDLE_EXPANDED_BYTES) {
                    throw new IOException("Two-layer ZIP expands beyond the safe limit");
                }
                if (files.put(name, bytes) != null) throw new IOException("Two-layer ZIP contains duplicate filenames");
            }
        }
        return files;
    }

    private static BundleManifest parseManifest(byte[] bytes) throws IOException {
        final BundleManifest manifest;
        try {
            manifest = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), BundleManifest.class);
        } catch (RuntimeException error) {
            throw new IOException("Two-layer SHA256.json is invalid", error);
        }
        if (manifest == null || !"mapkluss.suppression-bundle".equals(manifest.schema)
            || manifest.files == null || manifest.files.length < 2 || manifest.files.length > MAX_ENTRIES) {
            throw new IOException("Unsupported Two-layer bundle manifest");
        }
        return manifest;
    }

    private static void verifyManifestFiles(BundleManifest manifest, Map<String, byte[]> files) throws IOException {
        Set<String> covered = new HashSet<>();
        for (BundleFile file : manifest.files) {
            if (file == null) throw new IOException("Two-layer bundle manifest contains an empty file entry");
            String name = safeZipEntry(file.path);
            byte[] actual = files.get(name);
            if (actual == null || actual.length != file.sizeBytes || !safeSha(file.sha256)
                || !SuppressionHashes.sha256(actual).equals(file.sha256)) {
                throw new IOException("Two-layer bundle checksum mismatch: " + name);
            }
            if (!covered.add(name)) throw new IOException("Two-layer bundle manifest contains duplicate paths");
        }
        Set<String> expected = new HashSet<>(files.keySet());
        expected.remove("SHA256.json");
        if (!covered.equals(expected)) throw new IOException("Two-layer bundle manifest does not cover every payload");
    }

    private static SuppressionBundleCatalog readLegacy(
        Map<String, byte[]> files,
        String sourceName,
        byte[] zipBytes
    ) throws IOException {
        java.util.List<Map.Entry<String, byte[]>> planFiles = files.entrySet().stream()
            .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).endsWith("_suppression_plan.json"))
            .toList();
        if (planFiles.size() != 1) throw new IOException("Legacy Two-layer ZIP must contain exactly one suppression plan");
        Map.Entry<String, byte[]> planFile = planFiles.getFirst();
        SuppressionBundle bundle = bundleFromFiles(
            planFile.getKey(), planFile.getValue(),
            findLegacyLitematic(files, planFile.getValue()), files,
            sourceName, "local_zip"
        );
        return new SuppressionBundleCatalog(
            null, null, sourceName, "local_zip", SuppressionHashes.sha256(zipBytes),
            1, 1, java.util.List.of(new SuppressionBundleCatalog.Tile("tile_001", 1, 0, 0, bundle))
        );
    }

    private static String findLegacyLitematic(Map<String, byte[]> files, byte[] planBytes) throws IOException {
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(planBytes);
        String expectedName = parsed.plan().litematic().filename();
        if (!files.containsKey(expectedName)) throw new IOException("Two-layer ZIP has no matching Litematic");
        return expectedName;
    }

    private static SuppressionBundleCatalog readMultiMap(
        BundleManifest manifest,
        Map<String, byte[]> files,
        String sourceName,
        byte[] zipBytes
    ) throws IOException {
        if (manifest.grid == null || manifest.grid.wide < 1 || manifest.grid.wide > 10
            || manifest.grid.tall < 1 || manifest.grid.tall > 10
            || !"row_major_top_left".equals(manifest.tileOrder)
            || manifest.tiles == null || manifest.tiles.length != manifest.grid.wide * manifest.grid.tall
            || manifest.tiles.length < 2 || manifest.tiles.length > MAX_TILES) {
            throw new IOException("Invalid multi-map Two-layer grid");
        }
        if (manifest.title == null || manifest.title.isBlank() || manifest.title.length() > 180) {
            throw new IOException("Invalid multi-map Two-layer title");
        }

        java.util.List<SuppressionBundleCatalog.Tile> tiles = new java.util.ArrayList<>(manifest.tiles.length);
        Set<String> ids = new HashSet<>();
        Set<String> payloadPaths = new HashSet<>();
        String minecraftVersion = null;
        for (int offset = 0; offset < manifest.tiles.length; offset++) {
            BundleTile tile = manifest.tiles[offset];
            int expectedIndex = offset + 1;
            int expectedColumn = offset % manifest.grid.wide;
            int expectedRow = offset / manifest.grid.wide;
            if (tile == null || tile.id == null || !tile.id.matches("tile_[0-9]{3}")
                || !ids.add(tile.id) || tile.index != expectedIndex
                || tile.column != expectedColumn || tile.row != expectedRow
                || tile.plan == null || tile.litematic == null) {
                throw new IOException("Invalid or non-deterministic Two-layer tile order");
            }
            String planPath = verifyTileRef(tile.plan, files, ".json");
            String litematicPath = verifyTileRef(tile.litematic, files, ".litematic");
            if (!payloadPaths.add(planPath) || !payloadPaths.add(litematicPath)
                || !java.util.Objects.equals(Path.of(planPath).getParent(), Path.of(litematicPath).getParent())) {
                throw new IOException("Two-layer tile payload paths overlap or leave their tile folder");
            }
            SuppressionBundle bundle = bundleFromFiles(
                planPath, files.get(planPath), litematicPath, files,
                manifest.title + " · карта " + expectedIndex + "/" + manifest.tiles.length,
                "local_zip"
            );
            if (bundle.parsed().plan().version() != 3) throw new IOException("Multi-map Two-layer tiles must use plan version 3");
            String tileMinecraftVersion = bundle.parsed().plan().target().minecraftVersion();
            if (minecraftVersion == null) minecraftVersion = tileMinecraftVersion;
            if (!minecraftVersion.equals(tileMinecraftVersion)) throw new IOException("Two-layer tiles target different Minecraft versions");
            tiles.add(new SuppressionBundleCatalog.Tile(tile.id, tile.index, tile.column, tile.row, bundle));
        }
        return new SuppressionBundleCatalog(
            null, null, manifest.title, "local_zip", SuppressionHashes.sha256(zipBytes),
            manifest.grid.wide, manifest.grid.tall, tiles
        );
    }

    private static String verifyTileRef(BundleRef ref, Map<String, byte[]> files, String suffix) throws IOException {
        String path = safeZipEntry(ref.path);
        byte[] actual = files.get(path);
        if (actual == null || actual.length != ref.sizeBytes || !safeSha(ref.sha256)
            || !SuppressionHashes.sha256(actual).equals(ref.sha256)
            || !path.toLowerCase(Locale.ROOT).endsWith(suffix)) {
            throw new IOException("Invalid Two-layer tile reference: " + path);
        }
        return path;
    }

    private static SuppressionBundle bundleFromFiles(
        String planPath,
        byte[] planBytes,
        String litematicPath,
        Map<String, byte[]> files,
        String title,
        String source
    ) throws IOException {
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(planBytes);
        byte[] litematicBytes = files.get(litematicPath);
        if (litematicBytes == null) throw new IOException("Two-layer ZIP has no matching Litematic");
        validateLitematic(litematicBytes);
        String expectedName = parsed.plan().litematic().filename();
        if (!Path.of(litematicPath).getFileName().toString().equals(expectedName)) {
            throw new IOException("Two-layer plan references a different Litematic filename");
        }
        String planSha = SuppressionHashes.sha256(planBytes);
        String litematicSha = SuppressionHashes.sha256(litematicBytes);
        if (!litematicSha.equals(parsed.plan().litematic().sha256())) {
            throw new IOException("Two-layer Litematic checksum mismatch");
        }
        return new SuppressionBundle(
            parsed, planBytes, litematicBytes, planSha, litematicSha,
            null, null, title, source
        );
    }

    private static boolean safeSha(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private static String safeZipEntry(String raw) throws IOException {
        if (raw == null || raw.isBlank() || raw.length() > 240 || raw.contains("\\") || raw.startsWith("/")) {
            throw new IOException("Unsafe path in Two-layer ZIP");
        }
        for (String segment : raw.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IOException("Unsafe path in Two-layer ZIP");
            }
        }
        Path path = Path.of(raw).normalize();
        if (path.isAbsolute() || path.getNameCount() > 6 || path.startsWith("..")) {
            throw new IOException("Unsafe path in Two-layer ZIP");
        }
        return path.toString().replace('\\', '/');
    }

    private static byte[] readBounded(ZipInputStream zip, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(8192, limit));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = zip.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IOException("Two-layer ZIP entry exceeds the safe limit");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static void validateLitematic(byte[] bytes) throws IOException {
        if (bytes.length < 32 || bytes.length > SuppressionPlanParser.MAX_LITEMATIC_BYTES
            || (bytes[0] & 0xFF) != 0x1F || (bytes[1] & 0xFF) != 0x8B) {
            throw new IOException("Two-layer Litematic is invalid");
        }
        try (java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(bytes))) {
            if (gzip.read() != 10) throw new IOException("Two-layer Litematic has no root NBT compound");
            byte[] buffer = new byte[8192];
            int expanded = 1;
            int read;
            while ((read = gzip.read(buffer)) >= 0) {
                expanded += read;
                if (expanded > MAX_LITEMATIC_NBT_BYTES) {
                    throw new IOException("Two-layer Litematic expands beyond the safe limit");
                }
            }
        } catch (IOException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("Two-layer")) throw error;
            throw new IOException("Two-layer Litematic gzip payload is corrupt", error);
        }
    }

    private static final class BundleManifest {
        String schema;
        int version;
        String title;
        BundleGrid grid;
        String tileOrder;
        BundleTile[] tiles;
        BundleFile[] files;
    }

    private static final class BundleGrid { int wide; int tall; }

    private static final class BundleTile {
        String id;
        int index;
        int column;
        int row;
        BundleRef plan;
        BundleRef litematic;
    }

    private static class BundleRef {
        String path;
        int sizeBytes;
        String sha256;
    }

    private static final class BundleFile extends BundleRef { }
}
