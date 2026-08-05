package art.mapkluss.companion;

import java.util.Arrays;

/**
 * Skips expensive inventory map recognition while the visible map slots and
 * the registry revision remain unchanged.
 */
final class MapStackScanGate {
    private int[] lastSlotMapIds;
    private long lastMappingRevision = Long.MIN_VALUE;

    boolean shouldScan(int[] slotMapIds, long mappingRevision, boolean forced) {
        if (forced || lastSlotMapIds == null) return true;
        return mappingRevision != lastMappingRevision || !Arrays.equals(lastSlotMapIds, slotMapIds);
    }

    void markScanned(int[] slotMapIds, long mappingRevision) {
        lastSlotMapIds = slotMapIds.clone();
        lastMappingRevision = mappingRevision;
    }

    void reset() {
        lastSlotMapIds = null;
        lastMappingRevision = Long.MIN_VALUE;
    }
}
