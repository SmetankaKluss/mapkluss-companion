package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionStartLayoutTest {
    @Test
    void keepsControlsInsideDesktopAndSmallGuiScreens() {
        for (int[] size : new int[][] {{240, 180}, {320, 180}, {427, 240}, {854, 480}}) {
            SuppressionStartLayout.Layout layout = SuppressionStartLayout.calculate(size[0], size[1]);
            assertTrue(layout.left() >= 12);
            assertTrue(layout.left() + layout.panelWidth() <= size[0] - 12);
            assertTrue(layout.top() >= 6);
            assertTrue(layout.backY() + 20 <= layout.bottom());
            assertTrue(layout.statusY() < layout.bottom());
            assertTrue(layout.compact() || layout.statusY() >= layout.backY() + 20);
            assertTrue(!layout.compact() || layout.statusY() < layout.cloudY());
            assertTrue(layout.guidanceLines() == (layout.compact() ? 1 : 2));
            assertTrue(!layout.compact() || layout.statusY() >= layout.top() + 38 + 11);
        }
    }
}
