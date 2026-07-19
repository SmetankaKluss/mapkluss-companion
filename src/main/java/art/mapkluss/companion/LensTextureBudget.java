package art.mapkluss.companion;

import java.util.concurrent.atomic.AtomicLong;

final class LensTextureBudget {
    static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;

    private final long maxBytes;
    private final AtomicLong reservedBytes = new AtomicLong();

    LensTextureBudget(long maxBytes) {
        this.maxBytes = Math.max(0, maxBytes);
    }

    boolean resize(long previousBytes, long nextBytes) {
        long previous = Math.max(0, previousBytes);
        long next = Math.max(0, nextBytes);
        while (true) {
            long current = reservedBytes.get();
            long updated = Math.max(0, current - previous) + next;
            if (updated > maxBytes) return false;
            if (reservedBytes.compareAndSet(current, updated)) return true;
        }
    }

    void release(long bytes) {
        long released = Math.max(0, bytes);
        reservedBytes.updateAndGet(current -> Math.max(0, current - released));
    }

    long reservedBytes() {
        return reservedBytes.get();
    }
}
