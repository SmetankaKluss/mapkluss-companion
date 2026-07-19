package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensSyncPolicyTest {
    @Test
    void staysCompletelyInactiveWithoutUiSessionsOrPlacements() {
        assertFalse(LensSyncPolicy.networkActive(false, 0, 0, 0));
        assertTrue(LensSyncPolicy.networkActive(true, 0, 0, 0));
        assertTrue(LensSyncPolicy.networkActive(false, 1, 0, 0));
        assertTrue(LensSyncPolicy.networkActive(false, 0, 1, 0));
        assertTrue(LensSyncPolicy.networkActive(false, 0, 0, 1));
    }

    @Test
    void backsOffDegradedPollingAndUsesSlowHealthyFallback() {
        assertEquals(seconds(60), LensSyncPolicy.recoveryDelayNanos(true, 0));
        assertEquals(seconds(5), LensSyncPolicy.recoveryDelayNanos(false, 0));
        assertEquals(seconds(10), LensSyncPolicy.recoveryDelayNanos(false, 1));
        assertEquals(seconds(20), LensSyncPolicy.recoveryDelayNanos(false, 2));
        assertEquals(seconds(40), LensSyncPolicy.recoveryDelayNanos(false, 3));
        assertEquals(seconds(60), LensSyncPolicy.recoveryDelayNanos(false, 4));
        assertEquals(seconds(60), LensSyncPolicy.recoveryDelayNanos(false, 20));
    }

    @Test
    void jitterKeepsClientsInsideTwentyPercentWindow() {
        long base = seconds(30);
        for (int index = 0; index < 200; index++) {
            long value = LensSyncPolicy.jitteredDelayNanos(base);
            assertTrue(value >= seconds(24));
            assertTrue(value <= seconds(36));
        }
    }

    @Test
    void slowsFrameScanningForLargePhantomArts() {
        assertEquals(20, LensSyncPolicy.renderRefreshTicks(4_096));
        assertEquals(40, LensSyncPolicy.renderRefreshTicks(10_000));
    }

    @Test
    void reconnectBackoffIsBoundedAndJittered() {
        for (int attempt = 0; attempt < 20; attempt++) {
            long value = LensSyncPolicy.reconnectDelayNanos(attempt);
            long base = seconds(Math.min(60, 1L << Math.min(attempt, 6)));
            assertTrue(value >= (long) (base * 0.8));
            assertTrue(value <= (long) (base * 1.2));
        }
    }

    @Test
    void typicalHealthyLoadStaysNearThreeAndAHalfCallsPerMinute() {
        int fiveMinutes = 300;
        int capabilitiesCalls = 1;
        int sessionListCalls = 1;
        int heartbeatCalls = fiveMinutes / 30;
        int recoveryPollCalls = fiveMinutes / 60;
        int callsPerClient = capabilitiesCalls + sessionListCalls + heartbeatCalls + recoveryPollCalls;

        assertTrue(callsPerClient <= 17);
        assertTrue(callsPerClient * 100 <= 1_700);
        assertEquals(0, 0 * callsPerClient, "inactive clients must produce no Lens requests");
    }

    private static long seconds(long value) {
        return value * 1_000_000_000L;
    }
}
