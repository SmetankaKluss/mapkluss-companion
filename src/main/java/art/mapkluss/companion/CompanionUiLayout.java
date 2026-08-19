package art.mapkluss.companion;

import java.util.Objects;

final class CompanionUiLayout {
    static final int WIDE_MIN_WIDTH = 760;
    static final int WIDE_MIN_HEIGHT = 420;
    static final int MEDIUM_MIN_WIDTH = 500;
    static final int MEDIUM_MIN_HEIGHT = 300;

    private CompanionUiLayout() {
    }

    static Mode modeFor(int width, int height) {
        if (width >= WIDE_MIN_WIDTH && height >= WIDE_MIN_HEIGHT) return Mode.WIDE;
        if (width >= MEDIUM_MIN_WIDTH && height >= MEDIUM_MIN_HEIGHT) return Mode.MEDIUM;
        return Mode.COMPACT;
    }

    static Shell shell(int width, int height, boolean inspectorRequested) {
        Mode mode = modeFor(width, height);
        int inset = switch (mode) {
            case WIDE -> 20;
            case MEDIUM -> 16;
            case COMPACT -> 12;
        };
        int topBarHeight = mode == Mode.COMPACT ? 32 : 38;
        int railWidth = switch (mode) {
            case WIDE -> 56;
            case MEDIUM -> 44;
            case COMPACT -> 0;
        };
        int bottomNavHeight = mode == Mode.COMPACT ? 34 : 0;
        Rect app = new Rect(inset, inset, Math.max(1, width - inset * 2), Math.max(1, height - inset * 2));
        Rect navigation = mode == Mode.COMPACT
            ? new Rect(app.x(), app.bottom() - bottomNavHeight, app.width(), bottomNavHeight)
            : new Rect(app.x(), app.y(), railWidth, app.height());
        int bodyX = mode == Mode.COMPACT ? app.x() : navigation.right();
        int bodyWidth = mode == Mode.COMPACT ? app.width() : app.right() - bodyX;
        int bodyBottom = mode == Mode.COMPACT ? navigation.y() : app.bottom();
        Rect topBar = new Rect(bodyX, app.y(), bodyWidth, topBarHeight);
        Rect body = new Rect(bodyX, topBar.bottom(), bodyWidth, Math.max(1, bodyBottom - topBar.bottom()));

        Rect inspector = Rect.EMPTY;
        Rect content = body;
        if (mode == Mode.WIDE && inspectorRequested && body.width() >= 620) {
            int inspectorWidth = clamp(body.width() * 36 / 100, 260, 380);
            inspector = new Rect(body.right() - inspectorWidth, body.y(), inspectorWidth, body.height());
            content = new Rect(body.x(), body.y(), Math.max(1, inspector.x() - body.x()), body.height());
        }
        return new Shell(mode, app, navigation, topBar, content, inspector);
    }

    static ArtStage artStage(Rect content, int gap) {
        Objects.requireNonNull(content, "content");
        int stageHeight = Math.max(80, content.height() * 68 / 100);
        Rect preview = new Rect(content.x() + gap, content.y() + gap, Math.max(1, content.width() - gap * 2), Math.max(1, stageHeight - gap * 2));
        Rect strip = new Rect(content.x() + gap, preview.bottom() + gap, Math.max(1, content.width() - gap * 2), Math.max(1, content.bottom() - preview.bottom() - gap * 2));
        return new ArtStage(preview, strip);
    }

    static Rect focusedPanel(Rect content, int desiredWidth, int desiredHeight) {
        Objects.requireNonNull(content, "content");
        int margin = content.width() < 360 || content.height() < 240 ? 8 : 14;
        int width = Math.max(1, Math.min(desiredWidth, content.width() - margin * 2));
        int height = Math.max(1, Math.min(desiredHeight, content.height() - margin * 2));
        return new Rect(
            content.x() + (content.width() - width) / 2,
            content.y() + (content.height() - height) / 2,
            width,
            height
        );
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static Rect navigationButton(Shell shell, int index) {
        if (shell.mode() == Mode.COMPACT) {
            int count = Destination.ACCOUNT.ordinal() + 1;
            int width = shell.navigation().width() / count;
            int x = shell.navigation().x() + index * width;
            int actualWidth = index == count - 1 ? shell.navigation().right() - x : width;
            return new Rect(x, shell.navigation().y(), actualWidth, shell.navigation().height());
        }
        int size = shell.mode() == Mode.WIDE ? 40 : 36;
        int x = shell.navigation().x() + (shell.navigation().width() - size) / 2;
        int y = shell.navigation().y() + 12 + index * (size + 6);
        return new Rect(x, y, size, size);
    }

    enum Mode {
        WIDE,
        MEDIUM,
        COMPACT
    }

    enum Destination {
        LIBRARY,
        LENS,
        SCAN,
        TRACKER,
        ACCOUNT,
        ART,
        TWO_LAYER
    }

    record Rect(int x, int y, int width, int height) {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);

        Rect {
            width = Math.max(0, width);
            height = Math.max(0, height);
        }

        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean intersects(Rect other) {
            return width > 0 && height > 0 && other.width > 0 && other.height > 0
                && x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    record Shell(Mode mode, Rect app, Rect navigation, Rect topBar, Rect content, Rect inspector) {
        boolean hasInspector() {
            return inspector.width() > 0;
        }
    }

    record ArtStage(Rect preview, Rect strip) {
    }
}
