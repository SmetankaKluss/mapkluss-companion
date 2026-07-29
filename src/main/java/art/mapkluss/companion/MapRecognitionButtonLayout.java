package art.mapkluss.companion;

public final class MapRecognitionButtonLayout {
    private static final int EDGE = 4;
    private static final int GAP = 3;
    private static final int SIZE = 18;

    private MapRecognitionButtonLayout() {
    }

    public static Position place(
        int screenWidth,
        int screenHeight,
        int panelLeft,
        int panelTop,
        int panelWidth,
        int panelHeight
    ) {
        int right = panelLeft + panelWidth + GAP;
        int alignedY = clamp(panelTop, EDGE, Math.max(EDGE, screenHeight - SIZE - EDGE));
        if (right + SIZE <= screenWidth - EDGE) return new Position(right, alignedY, SIZE, SIZE);

        int left = panelLeft - SIZE - GAP;
        if (left >= EDGE) return new Position(left, alignedY, SIZE, SIZE);

        int alignedX = clamp(panelLeft + panelWidth - SIZE, EDGE, Math.max(EDGE, screenWidth - SIZE - EDGE));
        int above = panelTop - SIZE - GAP;
        if (above >= EDGE) return new Position(alignedX, above, SIZE, SIZE);

        int below = panelTop + panelHeight + GAP;
        if (below + SIZE <= screenHeight - EDGE) return new Position(alignedX, below, SIZE, SIZE);

        return new Position(alignedX, alignedY, SIZE, SIZE);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Position(int x, int y, int width, int height) {
    }
}
