package art.mapkluss.companion;

final class CompanionLayout {
    static final int EDGE_MARGIN = 6;
    static final int NAV_BUTTON_HEIGHT = 20;

    private CompanionLayout() {
    }

    static int actionTop(
        int screenHeight,
        int minTop,
        int rows,
        int rowHeight,
        int buttonHeight,
        int bottomMargin
    ) {
        int rowSpan = Math.max(0, rows - 1) * rowHeight;
        int preferred = screenHeight - bottomMargin - rowSpan;
        int panelBottom = Math.max(0, screenHeight - 8);
        int maxTop = panelBottom - buttonHeight - rowSpan;
        if (maxTop < minTop) return Math.max(0, maxTop);
        return Math.max(minTop, Math.min(preferred, maxTop));
    }

    static int visibleRows(int screenHeight, int firstRowY, int bottomReserve, int rowHeight, int maxRows) {
        int available = Math.max(0, screenHeight - bottomReserve - firstRowY);
        return Math.max(0, Math.min(maxRows, available / Math.max(1, rowHeight)));
    }

    static int pageSize(int visibleRows) {
        return Math.max(1, visibleRows);
    }

    static int topRightX(int screenWidth, int controlWidth) {
        return Math.max(EDGE_MARGIN, screenWidth - EDGE_MARGIN - controlWidth);
    }

    static int panelBottom(int screenHeight) {
        return Math.max(EDGE_MARGIN + 1, screenHeight - 8);
    }

    static int insidePanelButtonY(int panelBottom, int controlHeight) {
        return Math.max(EDGE_MARGIN, panelBottom - controlHeight - 4);
    }

    static int contentBottom(int screenHeight) {
        return Math.max(EDGE_MARGIN + 1, insidePanelButtonY(panelBottom(screenHeight), NAV_BUTTON_HEIGHT) - 4);
    }

    static Frame frame(int screenHeight, int top, int headerHeight, int footerHeight) {
        int safeTop = Math.max(8, top);
        int bottom = Math.max(safeTop + 64, screenHeight - 8);
        int footerTop = Math.max(safeTop + headerHeight + 20, bottom - Math.max(28, footerHeight));
        int bodyTop = Math.min(footerTop, safeTop + Math.max(40, headerHeight));
        return new Frame(safeTop, bodyTop, footerTop, bottom);
    }

    static int lastButtonBottom(int top, int rows, int rowHeight, int buttonHeight) {
        return top + Math.max(0, rows - 1) * rowHeight + buttonHeight;
    }

    record Frame(int top, int bodyTop, int footerTop, int bottom) {
        int bodyHeight() {
            return Math.max(0, footerTop - bodyTop);
        }
    }
}
