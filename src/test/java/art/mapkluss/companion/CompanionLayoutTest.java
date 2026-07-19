package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionLayoutTest {
    @Test
    void actionRowsStayInsidePanelAtCommonGuiHeights() {
        assertActionRowsFit(540, 136, 4, 24, 20, 56);
        assertActionRowsFit(360, 136, 4, 24, 20, 56);
        assertActionRowsFit(240, 136, 4, 24, 20, 56);

        assertActionRowsFit(540, 156, 3, 24, 20, 32);
        assertActionRowsFit(320, 156, 3, 24, 20, 32);

        assertActionRowsFit(540, 88, 4, 24, 20, 32);
        assertActionRowsFit(220, 88, 4, 24, 20, 32);

        assertActionRowsFit(540, 138, 2, 26, 20, 32);
        assertActionRowsFit(180, 138, 2, 26, 20, 32);
    }

    @Test
    void actionRowsRespectMinimumTopWhenThereIsSpace() {
        assertEquals(136, CompanionLayout.actionTop(240, 136, 4, 24, 20, 56));
        assertTrue(CompanionLayout.actionTop(320, 156, 3, 24, 20, 32) >= 156);
        int scanTop = CompanionLayout.actionTop(220, 88, 4, 24, 20, 32);
        assertTrue(scanTop >= 0);
        assertTrue(CompanionLayout.lastButtonBottom(scanTop, 4, 24, 20) <= 212);
    }

    @Test
    void actionRowsMoveUpInsteadOfLeavingPanelOnTinyScreens() {
        int top = CompanionLayout.actionTop(180, 136, 4, 24, 20, 56);
        assertTrue(top < 136);
        assertTrue(CompanionLayout.lastButtonBottom(top, 4, 24, 20) <= 172);

        int scanTop = CompanionLayout.actionTop(150, 88, 4, 24, 20, 32);
        assertTrue(scanTop < 88);
        assertTrue(CompanionLayout.lastButtonBottom(scanTop, 4, 24, 20) <= 142);

        int loginTop = CompanionLayout.actionTop(130, 138, 2, 26, 20, 32);
        assertTrue(loginTop < 138);
        assertTrue(CompanionLayout.lastButtonBottom(loginTop, 2, 26, 20) <= 122);
    }

    @Test
    void visibleRowsCanReturnZeroInsteadOfForcingContentIntoTheFooter() {
        assertEquals(0, CompanionLayout.visibleRows(130, 120, 40, 24, 7));
        assertEquals(3, CompanionLayout.visibleRows(240, 120, 40, 24, 7));
        assertEquals(7, CompanionLayout.visibleRows(900, 120, 40, 24, 7));
        assertEquals(1, CompanionLayout.pageSize(0));
        assertEquals(3, CompanionLayout.pageSize(3));
    }

    @Test
    void frameAlwaysReservesAHeaderBodyAndStickyFooter() {
        CompanionLayout.Frame regular = CompanionLayout.frame(360, 10, 54, 34);
        assertEquals(10, regular.top());
        assertEquals(64, regular.bodyTop());
        assertEquals(318, regular.footerTop());
        assertEquals(352, regular.bottom());
        assertTrue(regular.bodyHeight() > 0);

        CompanionLayout.Frame compact = CompanionLayout.frame(120, 10, 54, 34);
        assertTrue(compact.footerTop() >= compact.bodyTop());
        assertTrue(compact.bottom() > compact.footerTop());
    }

    @Test
    void globalNavigationStaysAtTopRightAndInsidePanelBottomLeft() {
        assertEquals(600, CompanionLayout.topRightX(640, 34));
        assertEquals(352, CompanionLayout.panelBottom(360));
        assertEquals(328, CompanionLayout.insidePanelButtonY(352, 20));
        assertEquals(324, CompanionLayout.contentBottom(360));

        assertEquals(6, CompanionLayout.topRightX(30, 34));
        assertEquals(12, CompanionLayout.panelBottom(20));
        assertEquals(6, CompanionLayout.insidePanelButtonY(12, 20));
    }

    @Test
    void artPreviewUsesTheRightHalfAtNormalLargeGuiWidths() {
        assertTrue(!CompanionArtScreen.usesSidePreview(619));
        assertTrue(CompanionArtScreen.usesSidePreview(620));
        assertTrue(CompanionArtScreen.usesSidePreview(735));
    }

    private static void assertActionRowsFit(int height, int minTop, int rows, int rowHeight, int buttonHeight, int bottomMargin) {
        int top = CompanionLayout.actionTop(height, minTop, rows, rowHeight, buttonHeight, bottomMargin);
        int panelBottom = height - 8;
        assertTrue(CompanionLayout.lastButtonBottom(top, rows, rowHeight, buttonHeight) <= panelBottom);
    }
}
