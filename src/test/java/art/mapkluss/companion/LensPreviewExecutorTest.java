package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensPreviewExecutorTest {
    @Test
    void usesASeparateBoundedDaemonPool() throws Exception {
        CountDownLatch entered = new CountDownLatch(LensPreviewExecutor.MAX_PARALLEL_DOWNLOADS);
        CountDownLatch release = new CountDownLatch(1);
        var threadNames = ConcurrentHashMap.<String>newKeySet();
        List<CompletableFuture<Boolean>> tasks = new ArrayList<>();

        for (int index = 0; index < LensPreviewExecutor.MAX_PARALLEL_DOWNLOADS + 2; index++) {
            tasks.add(LensPreviewExecutor.supply(() -> {
                threadNames.add(Thread.currentThread().getName());
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
                return Thread.currentThread().isDaemon();
            }));
        }

        assertTrue(entered.await(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(LensPreviewExecutor.MAX_PARALLEL_DOWNLOADS, threadNames.size());
        release.countDown();
        assertTrue(CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS) == null);
        assertTrue(tasks.stream().allMatch(CompletableFuture::join));
        assertTrue(threadNames.stream().allMatch(name -> name.startsWith("mapkluss-lens-preview-")));
    }
}
