package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AtomicFilesTest {
    @TempDir Path tempDir;

    @Test
    void pathLockIncludesTheWholeReadModifyWriteOperation() throws Exception {
        Path file = tempDir.resolve("counter.txt");
        AtomicFiles.writeUtf8(file, "0");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                AtomicFiles.withLock(file, () -> {
                    int value = Integer.parseInt(Files.readString(file));
                    AtomicFiles.writeUtf8(file, Integer.toString(value + 1));
                    return null;
                });
                return null;
            });
            Future<?> second = executor.submit(() -> {
                AtomicFiles.withLock(file, () -> {
                    int value = Integer.parseInt(Files.readString(file));
                    AtomicFiles.writeUtf8(file, Integer.toString(value + 1));
                    return null;
                });
                return null;
            });
            first.get();
            second.get();
            assertEquals("2", Files.readString(file));
        } finally {
            executor.shutdownNow();
        }
    }
}
