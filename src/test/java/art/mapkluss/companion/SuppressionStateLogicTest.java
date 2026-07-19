package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SuppressionStateLogicTest {
    @Test
    void exposesWhetherFreshMapDataWasObservedWithoutClaimingAFullCycle() {
        assertEquals("данные карты …", SuppressionStateLogic.captureDataLabel(false));
        assertEquals("данные карты ✓", SuppressionStateLogic.captureDataLabel(true));
    }
}
