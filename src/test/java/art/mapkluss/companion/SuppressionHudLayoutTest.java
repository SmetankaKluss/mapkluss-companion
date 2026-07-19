package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionHudLayoutTest {
    @Test
    void staysInsideSmallScreensAndAboveTheHotbar() {
        for (int[] size : new int[][] {{320, 180}, {427, 240}, {854, 480}, {1920, 1080}}) {
            SuppressionHudLayout.Layout layout = SuppressionHudLayout.calculate(size[0], size[1], 500, 4);
            assertTrue(layout.x() >= 8);
            assertTrue(layout.x() + layout.width() <= size[0] - 8);
            assertTrue(layout.y() >= 8);
            assertTrue(layout.y() + layout.height() <= size[1] - 54);
        }
    }
}
