package art.mapkluss.companion;

import java.util.concurrent.ThreadLocalRandom;

final class LensSyncPolicy {
    static final long HEARTBEAT_NANOS = seconds(30);
    static final long HEALTHY_POLL_NANOS = seconds(60);
    static final long SESSION_LIST_NANOS = seconds(300);
    static final long CAPABILITIES_NANOS = seconds(600);
    static final int MAX_SYNC_SESSIONS = 20;
    static final int LARGE_RENDER_CELL_COUNT = 4_096;

    private static final long DEGRADED_POLL_BASE_NANOS = seconds(5);
    private static final long DEGRADED_POLL_MAX_NANOS = seconds(60);
    private static final double JITTER_FRACTION = 0.20;

    private LensSyncPolicy() {
    }

    static boolean networkActive(boolean screenOpen, int sessions, int placements, int ownedPlacements) {
        return screenOpen || sessions > 0 || placements > 0 || ownedPlacements > 0;
    }

    static long recoveryDelayNanos(boolean realtimeHealthy, int degradedAttempt) {
        if (realtimeHealthy) return HEALTHY_POLL_NANOS;
        int shift = Math.max(0, Math.min(degradedAttempt, 4));
        return Math.min(DEGRADED_POLL_MAX_NANOS, DEGRADED_POLL_BASE_NANOS << shift);
    }

    static long jitteredDelayNanos(long delayNanos) {
        if (delayNanos <= 1) return Math.max(0, delayNanos);
        long spread = Math.max(1, (long) (delayNanos * JITTER_FRACTION));
        return Math.max(1, delayNanos + ThreadLocalRandom.current().nextLong(-spread, spread + 1));
    }

    static int nextDegradedAttempt(boolean realtimeHealthy, int currentAttempt) {
        return realtimeHealthy ? 0 : Math.min(5, Math.max(0, currentAttempt) + 1);
    }

    static long reconnectDelayNanos(int attempt) {
        int shift = Math.max(0, Math.min(attempt, 6));
        return jitteredDelayNanos(Math.min(seconds(60), seconds(1) << shift));
    }

    static int renderRefreshTicks(long totalCells) {
        return totalCells > LARGE_RENDER_CELL_COUNT ? 40 : 20;
    }

    private static long seconds(long value) {
        return value * 1_000_000_000L;
    }
}
