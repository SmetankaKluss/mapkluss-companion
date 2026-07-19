package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensTextureBudgetTest {
    @Test
    void capsCombinedAtlasesButAllowsInPlaceReplacement() {
        LensTextureBudget budget = new LensTextureBudget(100);
        assertTrue(budget.resize(0, 60));
        assertFalse(budget.resize(0, 50));
        assertTrue(budget.resize(60, 90));
        assertEquals(90, budget.reservedBytes());
        budget.release(90);
        assertEquals(0, budget.reservedBytes());
    }
}
