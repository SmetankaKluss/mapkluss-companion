package art.mapkluss.companion;

final class SuppressionStartLayout {
    private SuppressionStartLayout() { }

    static Layout calculate(int screenWidth, int screenHeight) {
        int panelWidth = Math.max(216, Math.min(420, screenWidth - 24));
        boolean splitSources = panelWidth >= 340;
        boolean compact = screenHeight < 220;
        int panelHeight = splitSources ? 176 : 200;
        int top = Math.max(6, (screenHeight - panelHeight) / 2);
        int bottom = Math.min(screenHeight - 6, top + panelHeight);
        int left = (screenWidth - panelWidth) / 2;
        int cloudY = top + 70;
        int localY = splitSources ? cloudY : cloudY + 24;
        int sessionY = splitSources ? top + 98 : top + 118;
        int backY = splitSources ? top + 124 : top + 144;
        int statusY = compact ? top + 54 : Math.min(bottom - 17, splitSources ? top + 151 : top + 171);
        int guidanceLines = compact ? 1 : 2;
        return new Layout(left, top, panelWidth, bottom, splitSources, compact, guidanceLines,
            cloudY, localY, sessionY, backY, statusY);
    }

    record Layout(
        int left,
        int top,
        int panelWidth,
        int bottom,
        boolean splitSources,
        boolean compact,
        int guidanceLines,
        int cloudY,
        int localY,
        int sessionY,
        int backY,
        int statusY
    ) { }
}
