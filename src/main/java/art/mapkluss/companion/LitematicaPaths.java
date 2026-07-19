package art.mapkluss.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

public final class LitematicaPaths {
    private LitematicaPaths() {
    }

    public static Path defaultSchematicDir(Path minecraftRunDir) {
        Path litematicaDir = minecraftRunDir.resolve("schematics");
        if (Files.isDirectory(litematicaDir)) return litematicaDir;
        return litematicaDir;
    }

    public static Path companionIndexPath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("installed-artifacts.json");
    }

    public static Path libraryCachePath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("library-cache.json");
    }

    public static Path manifestCachePath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("manifest-cache.json");
    }

    public static Path scanHistoryPath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("scan-history.json");
    }

    public static Path trackerHistoryPath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("tracker-history.json");
    }

    public static Path autoFrameTemplatesPath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("autoframe-templates.json");
    }

    public static Path autoFrameMapRegistryPath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("autoframe-map-registry.json");
    }

    public static Path mapPreviewCacheDir(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("map-previews");
    }

    public static Path companionConfigPath(Path minecraftRunDir) {
        return minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("config.json");
    }

    public static Path modsDir(Path minecraftRunDir) {
        return minecraftRunDir.resolve("mods");
    }

    public static LitematicaStatus detectLitematica(Path minecraftRunDir) {
        Path mods = modsDir(minecraftRunDir);
        if (!Files.isDirectory(mods)) return new LitematicaStatus(false, false);
        boolean litematica = false;
        boolean malilib = false;
        try (Stream<Path> stream = Files.list(mods)) {
            for (Path file : stream.toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!name.endsWith(".jar")) continue;
                if (name.startsWith("litematica")) litematica = true;
                if (name.startsWith("malilib") || name.startsWith("ma-li-lib")) malilib = true;
            }
        } catch (Exception ignored) {
            return new LitematicaStatus(false, false);
        }
        return new LitematicaStatus(litematica, malilib);
    }
}
