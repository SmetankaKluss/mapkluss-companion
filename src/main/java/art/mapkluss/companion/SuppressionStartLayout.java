package art.mapkluss.companion;

final class SuppressionStartLayout {
    private SuppressionStartLayout() { }

    static Layout calculate(int screenWidth, int screenHeight) {
        int panelWidth = Math.max(216, Math.min(520, screenWidth - 24));
        boolean splitSources = panelWidth >= 360;
        boolean compact = screenHeight < 220;
        int panelHeight = Math.min(screenHeight - 12, compact ? 168 : 176);
        int top = Math.max(6, (screenHeight - panelHeight) / 2);
        int bottom = Math.min(screenHeight - 6, top + panelHeight);
        int left = (screenWidth - panelWidth) / 2;
        int cloudY = top + (compact ? 48 : 64);
        int localY = splitSources ? cloudY : cloudY + 24;
        int sessionY = (splitSources ? cloudY : localY) + 30;
        int backY = bottom - 24;
        int statusY = Math.min(sessionY + 30, backY - 18);
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
