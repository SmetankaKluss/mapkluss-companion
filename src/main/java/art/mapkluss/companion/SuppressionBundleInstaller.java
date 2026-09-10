package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SuppressionBundleInstaller {
    private SuppressionBundleInstaller() { }

    public static Installed install(Path runDir, SuppressionBundle bundle) throws IOException {
        if (bundle == null || bundle.parsed() == null || bundle.parsed().plan().version() < 3) {
            throw new IOException("Этот старый Two-layer план нельзя запускать. Экспортируйте новый ZIP версии 3 на сайте");
        }
        SuppressionReferenceLitematic.validateSource(bundle.parsed().plan(), bundle.litematicBytes());
        Path root = runDir.resolve("config").resolve("mapkluss-companion").resolve("suppression");
        Path plans = root.resolve("plans");
        Path schematics = LitematicaPaths.defaultSchematicDir(runDir);
        Files.createDirectories(plans);
        Files.createDirectories(schematics);

        String planName = bundle.planSha256() + ".json";
        Path planPath = plans.resolve(planName);
        writePinned(planPath, bundle.planBytes(), bundle.planSha256());

        String requestedName = SafeNames.downloadFilename(
            bundle.parsed().plan().litematic().filename(),
            "mapkluss_two_layer_" + shortSha(bundle.litematicSha256()) + ".litematic"
        );
        if (!requestedName.toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")) {
            requestedName = "mapkluss_two_layer_" + shortSha(bundle.litematicSha256()) + ".litematic";
        }
        Path schematicPath = choosePinnedPath(schematics, requestedName, bundle.litematicSha256());
        writePinned(schematicPath, bundle.litematicBytes(), bundle.litematicSha256());
        return new Installed(planPath.toAbsolutePath().normalize(), schematicPath.toAbsolutePath().normalize());
    }

    private static Path choosePinnedPath(Path folder, String filename, String sha256) throws IOException {
        Path normalizedFolder = folder.toAbsolutePath().normalize();
        Path candidate = normalizedFolder.resolve(filename).normalize();
        if (!candidate.startsWith(normalizedFolder)) {
            throw new IOException("Unsafe Two-layer schematic filename");
        }
        if (!Files.exists(candidate) || SuppressionHashes.sha256(candidate, SuppressionPlanParser.MAX_LITEMATIC_BYTES).equalsIgnoreCase(sha256)) return candidate;
        String base = filename.substring(0, filename.length() - ".litematic".length());
        return folder.resolve(base + "_" + shortSha(sha256) + ".litematic");
    }

    private static void writePinned(Path target, byte[] bytes, String expectedSha) throws IOException {
        if (Files.exists(target)) {
            int limit = target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")
                ? SuppressionPlanParser.MAX_LITEMATIC_BYTES : SuppressionPlanParser.MAX_PLAN_BYTES;
            if (SuppressionHashes.sha256(target, limit).equalsIgnoreCase(expectedSha)) return;
            throw new IOException("Refusing to overwrite a different Two-layer file: " + target.getFileName());
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            if (!SuppressionHashes.sha256(temp, bytes.length).equalsIgnoreCase(expectedSha)) {
                throw new IOException("Two-layer file checksum changed while saving");
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 12 ? "bundle" : sha.substring(0, 12);
    }

    public static Installed installCatalog(Path runDir, SuppressionBundleCatalog catalog, int tile) throws IOException {
        if (tile < 0 || tile >= catalog.tiles().size()) throw new IOException("Invalid selected tile");
        try (var cached = LiveBuildSourceCache.forRunDir(runDir).importCatalog(catalog)) {
            var bundle = catalog.tiles().get(tile).bundle();
            var link = new LiveBuildCatalogLink(cached.reference().sha256(), tile);
            link.validate(cached, bundle);
            var installed = install(runDir, bundle);
            return new Installed(installed.planPath(), installed.schematicPath(), link);
        }
    }

    public record Installed(Path planPath, Path schematicPath, LiveBuildCatalogLink trackerSource) {
        public Installed(Path planPath, Path schematicPath) { this(planPath, schematicPath, null); }
    }
}
