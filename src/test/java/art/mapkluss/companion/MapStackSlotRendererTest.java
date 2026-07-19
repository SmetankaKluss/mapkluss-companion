package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MapStackSlotRendererTest {
    @Test
    void keepsOrdinaryNumbersExactAndBoundsVeryLargeBadges() {
        assertEquals("1", MapStackSlotRenderer.compactTileNumber(1));
        assertEquals("9999", MapStackSlotRenderer.compactTileNumber(9_999));
        assertEquals("10K", MapStackSlotRenderer.compactTileNumber(10_000));
        assertEquals("99K", MapStackSlotRenderer.compactTileNumber(999_999));
    }
}
