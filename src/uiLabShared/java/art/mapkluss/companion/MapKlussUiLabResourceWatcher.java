package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class MapKlussUiLabResourceWatcher {
    private static final long DEBOUNCE_MILLIS = 220L;
    private static volatile boolean started;

    private MapKlussUiLabResourceWatcher() {
    }

    static synchronized void start() {
        if (started) return;
        started = true;
        Thread watcher = new Thread(MapKlussUiLabResourceWatcher::watch, "mapkluss-ui-resource-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void watch() {
        Path root = Path.of("").toAbsolutePath().normalize();
        Map<Path, Long> known = snapshot(root);
        long changedAt = 0L;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                TimeUnit.MILLISECONDS.sleep(180L);
                Map<Path, Long> current = snapshot(root);
                if (!current.equals(known)) {
                    known = current;
                    changedAt = System.currentTimeMillis();
                    continue;
                }
                if (changedAt != 0L && System.currentTimeMillis() - changedAt >= DEBOUNCE_MILLIS) {
                    changedAt = 0L;
                    boolean reloaded = MapKlussUiResources.reloadDevelopmentSources();
                    MapKlussCompanionClient.LOGGER.info(
                        reloaded ? "MapKluss UI Lab resources reloaded automatically." : "MapKluss UI Lab automatic resource reload failed."
                    );
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException error) {
                MapKlussCompanionClient.LOGGER.debug("UI Lab resource watcher retrying after an error.", error);
            }
        }
    }

    private static Map<Path, Long> snapshot(Path root) {
        LinkedHashMap<Path, Long> result = new LinkedHashMap<>();
        record(result, root.resolve("src/main/resources/assets/mapkluss-companion/ui"));
        record(result, root.resolve("src/uiLab/resources"));
        return result;
    }

    private static void record(Map<Path, Long> result, Path directory) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                .filter(MapKlussUiLabResourceWatcher::reloadable)
                .sorted()
                .forEach(path -> result.put(path, modified(path)));
        } catch (IOException ignored) {
            // A following scan retries after an editor finishes replacing the file.
        }
    }

    private static boolean reloadable(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".json") || name.endsWith(".png") || name.endsWith(".ttf");
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
