package art.mapkluss.companion;

import java.util.concurrent.atomic.AtomicLong;

final class LibraryRefreshEpoch {
    private final AtomicLong current = new AtomicLong();

    long begin() {
        return current.incrementAndGet();
    }

    boolean accepts(long requestEpoch) {
        return current.get() == requestEpoch;
    }

    void runIfCurrent(long requestEpoch, Runnable task) {
        if (accepts(requestEpoch)) task.run();
    }
}
