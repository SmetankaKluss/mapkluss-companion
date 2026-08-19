package art.mapkluss.companion;

import java.util.Objects;

record UiActionPresentation(int accentColor, boolean decorated) {
    static UiActionPresentation from(UiAction action) {
        Objects.requireNonNull(action, "action");
        return from(action.kind());
    }

    static UiActionPresentation from(UiAction.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case DEFAULT -> new UiActionPresentation(UiTheme.LIME, false);
            case PRIMARY -> new UiActionPresentation(UiTheme.LIME, true);
            case TECHNICAL -> new UiActionPresentation(UiTheme.CYAN, true);
            case WARNING -> new UiActionPresentation(UiTheme.AMBER, true);
            case DANGER -> new UiActionPresentation(UiTheme.RED, true);
        };
    }
}
