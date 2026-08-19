package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import java.util.Objects;

record UiNode(
    String id,
    Type type,
    String text,
    UiAction action,
    List<UiNode> children,
    Map<String, String> metadata
) {
    UiNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        text = text == null ? "" : text;
        children = children == null ? List.of() : List.copyOf(children);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    static UiNode leaf(String id, Type type, String text) {
        return new UiNode(id, type, text, null, List.of(), Map.of());
    }

    static UiNode action(UiAction action) {
        return new UiNode(action.id(), Type.ACTION, action.label(), action, List.of(), Map.of());
    }

    enum Type {
        SHELL,
        NAVIGATION,
        TOP_BAR,
        CONTENT,
        INSPECTOR,
        PREVIEW,
        LIST,
        GRID,
        TABLE,
        TABS,
        STATUS,
        TEXT,
        ACTION
    }
}
