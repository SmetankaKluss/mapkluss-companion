package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScreenRequestGateTest {
    @Test
    void newestRequestWinsWithinLane() {
        ScreenRequestGate gate = new ScreenRequestGate();
        gate.attach();
        ScreenRequestGate.Token old = gate.begin("load");
        ScreenRequestGate.Token current = gate.begin("load");
        ScreenRequestGate.Token other = gate.begin("mutation");
        assertFalse(gate.isCurrent(old));
        assertTrue(gate.isCurrent(current));
        assertTrue(gate.isCurrent(other));
    }

    @Test
    void detachedAndReopenedScreenNeverRevivesOldToken() {
        ScreenRequestGate gate = new ScreenRequestGate();
        gate.attach();
        ScreenRequestGate.Token old = gate.begin("load");
        gate.detach();
        assertFalse(gate.isCurrent(old));
        gate.attach();
        assertFalse(gate.isCurrent(old));
        assertTrue(gate.isCurrent(gate.begin("load")));
    }
}
