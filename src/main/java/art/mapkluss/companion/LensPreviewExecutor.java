package art.mapkluss.companion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

final class LensPreviewExecutor {
    static final int MAX_PARALLEL_DOWNLOADS = 2;

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
        MAX_PARALLEL_DOWNLOADS,
        runnable -> {
            Thread thread = new Thread(runnable, "mapkluss-lens-preview-" + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    );

    private LensPreviewExecutor() {
    }

    static <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, EXECUTOR);
    }
}
