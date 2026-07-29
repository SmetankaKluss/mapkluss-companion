package art.mapkluss.companion;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Prevents stale asynchronous screen operations from mutating a reopened or replaced screen. */
final class ScreenRequestGate {
    private final AtomicLong lifecycle = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> lanes = new ConcurrentHashMap<>();
    private volatile boolean attached;

    synchronized void attach() {
        if (attached) return;
        lifecycle.incrementAndGet();
        attached = true;
    }

    synchronized void detach() {
        if (!attached) return;
        attached = false;
        lifecycle.incrementAndGet();
    }

    Token begin(String lane) {
        if (lane == null || lane.isBlank()) throw new IllegalArgumentException("Request lane is required");
        long sequence = lanes.computeIfAbsent(lane, ignored -> new AtomicLong()).incrementAndGet();
        return new Token(lifecycle.get(), lane, sequence);
    }

    boolean isCurrent(Token token) {
        if (token == null || !attached || lifecycle.get() != token.lifecycle()) return false;
        AtomicLong sequence = lanes.get(token.lane());
        return sequence != null && sequence.get() == token.sequence();
    }

    void invalidate(String lane) {
        lanes.computeIfAbsent(lane, ignored -> new AtomicLong()).incrementAndGet();
    }

    record Token(long lifecycle, String lane, long sequence) {
    }
}
