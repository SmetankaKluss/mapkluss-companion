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
            assertTrue(layout.statusY() >= layout.sessionY() + 12);
            assertTrue(layout.statusY() + 11 <= layout.backY());
            assertTrue(layout.guidanceLines() == (layout.compact() ? 1 : 2));
            assertTrue(layout.cloudY() >= layout.top() + 40);
            assertTrue(layout.localY() >= layout.cloudY());
        }
    }
}
