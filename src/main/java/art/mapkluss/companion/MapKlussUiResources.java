package art.mapkluss.companion;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class MapKlussUiResources {
    private static final String THEME_RESOURCE = "/assets/mapkluss-companion/ui/theme.json";
    private static final String LAYOUT_RESOURCE = "/assets/mapkluss-companion/ui/layout.json";

    private MapKlussUiResources() {
    }

    static void register() {
        try (
            InputStream themeStream = MapKlussUiResources.class.getResourceAsStream(THEME_RESOURCE);
            InputStream layoutStream = MapKlussUiResources.class.getResourceAsStream(LAYOUT_RESOURCE)
        ) {
            if (themeStream == null || layoutStream == null) {
                throw new IllegalStateException("MapKluss UI resources are missing");
            }
            try (
                BufferedReader theme = new BufferedReader(new InputStreamReader(themeStream, StandardCharsets.UTF_8));
                BufferedReader layout = new BufferedReader(new InputStreamReader(layoutStream, StandardCharsets.UTF_8))
            ) {
                apply(MapKlussUiResourceData.read(theme, layout, MapKlussUiResourceData.current()));
            }
        } catch (Exception error) {
            MapKlussCompanionClient.LOGGER.warn("Could not load MapKluss UI resources; using built-in defaults.", error);
            apply(MapKlussUiResourceData.defaults());
        }
    }

    static boolean reloadDevelopmentSources() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return false;
        Path resources = findSourceResources();
        if (resources == null) return false;
        Path themePath = resources.resolve("assets/mapkluss-companion/ui/theme.json");
        Path layoutPath = resources.resolve("assets/mapkluss-companion/ui/layout.json");
        try (
            BufferedReader theme = Files.newBufferedReader(themePath);
            BufferedReader layout = Files.newBufferedReader(layoutPath)
        ) {
            apply(MapKlussUiResourceData.read(theme, layout, MapKlussUiResourceData.current()));
            return true;
        } catch (Exception error) {
            MapKlussCompanionClient.LOGGER.warn("Could not reload MapKluss UI resources from source.", error);
            return false;
        }
    }

    private static void apply(MapKlussUiResourceData.Snapshot snapshot) {
        MapKlussUiResourceData.apply(snapshot);
        MapKlussUi.applyResources(snapshot);
    }

    private static Path findSourceResources() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            Path candidate = current.resolve("src/main/resources");
            if (Files.isRegularFile(candidate.resolve("assets/mapkluss-companion/ui/theme.json"))) {
                return candidate;
            }
        }
        return null;
    }
}
