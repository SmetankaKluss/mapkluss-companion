package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class DeviceLoginPollGateTest {
    @Test void manualAndAutomaticPollCannotOverlap() {
        var gate = new DeviceLoginPollGate();
        assertTrue(gate.acquire(1));
        assertFalse(gate.acquire(1));
        assertFalse(gate.acquire(2));
        gate.release(1, false);
        assertTrue(gate.acquire(1));
    }

    @Test void approvedCodeCannotBeConsumedAgainBeforeUiCallback() {
        var gate = new DeviceLoginPollGate();
        assertTrue(gate.acquire(1));
        gate.release(1, true);
        assertTrue(gate.completed(1));
        assertFalse(gate.acquire(1));
        assertTrue(gate.acquire(2));
    }

    @Test void staleReleaseCannotUnlockNewRequest() {
        var gate = new DeviceLoginPollGate();
        assertTrue(gate.acquire(1));
        gate.release(1, false);
        assertTrue(gate.acquire(2));
        gate.release(1, true);
        assertFalse(gate.acquire(2));
        gate.release(2, false);
        assertTrue(gate.acquire(2));
    }
}
