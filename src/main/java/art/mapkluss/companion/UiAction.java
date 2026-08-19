package art.mapkluss.companion;

import java.util.Objects;

record UiAction(
    String id,
    String label,
    Kind kind,
    boolean enabled,
    boolean selected,
    String tooltip
) {
    UiAction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        kind = kind == null ? Kind.DEFAULT : kind;
        tooltip = tooltip == null ? "" : tooltip;
    }

    enum Kind {
        DEFAULT,
        PRIMARY,
        TECHNICAL,
        WARNING,
        DANGER
    }
}
