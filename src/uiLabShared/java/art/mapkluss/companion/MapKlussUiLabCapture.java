package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class MapKlussUiLabCapture {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private MapKlussUiLabCapture() {
    }

    static Path capture(MapKlussUiLabModel model) throws IOException {
        Path root = findRepositoryRoot();
        Path output = root.resolve("run/ui-lab-captures");
        Files.createDirectories(output);
        String name = "%s-%s-%s-%s.png".formatted(
            STAMP.format(LocalDateTime.now()),
            model.page().name().toLowerCase(java.util.Locale.ROOT),
            model.state().name().toLowerCase(java.util.Locale.ROOT),
            model.viewport().name().toLowerCase(java.util.Locale.ROOT)
        );
        Path target = output.resolve(name);
        Process process = new ProcessBuilder("screencapture", "-x", target.toString()).start();
        try {
            if (process.waitFor() != 0) throw new IOException("screencapture exited with " + process.exitValue());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Screenshot capture was interrupted", error);
        }
        return target;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("gradlew"))) return current;
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
