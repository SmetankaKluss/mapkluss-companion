package art.mapkluss.companion;

final class SuppressionHudLayout {
    private static final int EDGE = 8;
    private static final int HOTBAR_CLEARANCE = 54;
    private static final int MIN_WIDTH = 150;
    private static final int MAX_WIDTH = 300;

    private SuppressionHudLayout() { }

    static Layout calculate(int screenWidth, int screenHeight, int measuredContentWidth, int lineCount) {
        int maxPanelWidth = Math.max(80, screenWidth - EDGE * 2);
        int desired = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, measuredContentWidth + 12));
        int width = Math.min(maxPanelWidth, desired);
        int height = Math.max(19, Math.min(4, Math.max(1, lineCount)) * 11 + 8);
        int x = Math.max(EDGE, screenWidth - width - EDGE);
        int y = Math.max(EDGE, screenHeight - HOTBAR_CLEARANCE - height);
        return new Layout(x, y, width, height, Math.max(20, width - 12));
    }

    record Layout(int x, int y, int width, int height, int contentWidth) { }
}
