package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapStackScanGateTest {
    private final MapStackScanGate gate = new MapStackScanGate();

    @Test
    void scansFirstSnapshotAndSkipsAnIdenticalOne() {
        int[] snapshot = {12, -1, 14};

        assertTrue(gate.shouldScan(snapshot, 3, false));
        gate.markScanned(snapshot, 3);

        assertFalse(gate.shouldScan(new int[]{12, -1, 14}, 3, false));
    }

    @Test
    void detectsInsertedRemovedReorderedAndChangedMaps() {
        gate.markScanned(new int[]{12, -1, 14}, 3);

        assertTrue(gate.shouldScan(new int[]{12, 13, 14}, 3, false));
        assertTrue(gate.shouldScan(new int[]{12, -1, -1}, 3, false));
        assertTrue(gate.shouldScan(new int[]{14, -1, 12}, 3, false));
        assertTrue(gate.shouldScan(new int[]{12, -1, 15}, 3, false));
    }

    @Test
    void scansWhenMappingsChangeOrCallerForcesRefresh() {
        int[] snapshot = {12, -1, 14};
        gate.markScanned(snapshot, 3);

        assertTrue(gate.shouldScan(snapshot, 4, false));
        assertTrue(gate.shouldScan(snapshot, 3, true));
    }

    @Test
    void resetForcesTheNextScan() {
        int[] snapshot = {12};
        gate.markScanned(snapshot, 3);
        gate.reset();

        assertTrue(gate.shouldScan(snapshot, 3, false));
    }
}
