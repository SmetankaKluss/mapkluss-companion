package art.mapkluss.companion;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

record ScreenViewModel(
    CompanionUiLayout.Destination destination,
    String title,
    List<String> breadcrumb,
    UiNode root,
    String status,
    StatusKind statusKind
) {
    ScreenViewModel {
        Objects.requireNonNull(destination, "destination");
        title = title == null ? "" : title;
        breadcrumb = breadcrumb == null ? List.of() : List.copyOf(breadcrumb);
        Objects.requireNonNull(root, "root");
        status = status == null ? "" : status;
        statusKind = statusKind == null ? StatusKind.IDLE : statusKind;
    }

    static ScreenViewModel shell(
        CompanionUiLayout.Destination destination,
        String title,
        String status
    ) {
        return shell(destination, title, List.of(), status);
    }

    static ScreenViewModel shell(
        CompanionUiLayout.Destination destination,
        String title,
        List<String> breadcrumb,
        String status
    ) {
        return shell(destination, title, breadcrumb, status, classifyStatus(status));
    }

    static ScreenViewModel shell(
        CompanionUiLayout.Destination destination,
        String title,
        List<String> breadcrumb,
        String status,
        StatusKind statusKind
    ) {
        UiNode root = new UiNode(
            "screen." + destination.name().toLowerCase(Locale.ROOT),
            UiNode.Type.SHELL,
            title,
            null,
            List.of(),
            Map.of("destination", destination.name().toLowerCase(Locale.ROOT))
        );
        return new ScreenViewModel(destination, title, breadcrumb, root, status, statusKind);
    }

    String heading() {
        if (breadcrumb.isEmpty()) return title;
        return title + " / " + String.join(" / ", breadcrumb);
    }

    static StatusKind classifyStatus(String status) {
        if (status == null || status.isBlank()) return StatusKind.IDLE;
        String value = status.toLowerCase(Locale.ROOT);
        if (containsAny(value, "ошиб", "не удалось", "error", "failed")) return StatusKind.ERROR;
        if (containsAny(value, "предуп", "истек", "недоступ", "warning")) return StatusKind.WARNING;
        if (containsAny(value, "готов", "загружено", "сохран", "подтверж", "ready", "loaded", "saved", "complete")) {
            return StatusKind.SUCCESS;
        }
        if (containsAny(value, "загруз", "синхрон", "провер", "loading")) return StatusKind.LOADING;
        return StatusKind.IDLE;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) return true;
        }
        return false;
    }

    enum StatusKind {
        IDLE,
        LOADING,
        SUCCESS,
        WARNING,
        ERROR
    }
}
