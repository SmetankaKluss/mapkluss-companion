package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SerialLatestQueueTest {
    @Test
    void serializesAndCoalescesOnlyAdjacentPendingProgress() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(3);
        List<String> seen = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            SerialLatestQueue<Item> queue = new SerialLatestQueue<>(item -> {
                seen.add(item.value());
                if ("first".equals(item.value())) {
                    firstStarted.countDown();
                    try {
                        assertTrue(releaseFirst.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(error);
                    }
                }
                finished.countDown();
            }, Item::key, executor);

            queue.submit(new Item("mode", "first"));
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            queue.submit(new Item("progress", "old"));
            queue.submit(new Item("progress", "latest"));
            queue.submit(new Item("mode", "last"));
            assertEquals(2, queue.pendingCount());
            releaseFirst.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("first", "latest", "last"), seen);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Item(String key, String value) {
    }
}
