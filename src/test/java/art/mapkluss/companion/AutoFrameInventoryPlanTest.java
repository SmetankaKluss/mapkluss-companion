package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoFrameInventoryPlanTest {
    @Test
    void selectsMapAlreadyInAnotherHotbarSlot() {
        AutoFrameInventoryPlan plan = AutoFrameInventoryPlan.forInventoryIndex(4, 1);

        assertFalse(plan.swapRequired());
        assertTrue(plan.selectionChangeRequired());
        assertEquals(4, plan.targetHotbarIndex());
        assertEquals(40, plan.sourceScreenSlot());
        assertEquals(1, plan.originalSelectedHotbarIndex());
    }

    @Test
    void swapsMainInventoryMapThroughCurrentHotbarSlot() {
        AutoFrameInventoryPlan plan = AutoFrameInventoryPlan.forInventoryIndex(22, 6);

        assertTrue(plan.swapRequired());
        assertFalse(plan.selectionChangeRequired());
        assertEquals(22, plan.sourceScreenSlot());
        assertEquals(6, plan.targetHotbarIndex());
    }

    @Test
    void rejectsSlotsOutsidePlayerMainInventory() {
        assertThrows(IllegalArgumentException.class, () -> AutoFrameInventoryPlan.forInventoryIndex(36, 0));
        assertThrows(IllegalArgumentException.class, () -> AutoFrameInventoryPlan.forInventoryIndex(0, 9));
    }
}
