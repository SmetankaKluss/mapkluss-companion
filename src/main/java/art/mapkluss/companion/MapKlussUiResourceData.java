package art.mapkluss.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

final class MapKlussUiResourceData {
    private static final Map<String, Integer> DEFAULT_COLORS = Map.ofEntries(
        Map.entry("white", UiTheme.TEXT),
        Map.entry("muted", UiTheme.TEXT_MUTED),
        Map.entry("dim", UiTheme.TEXT_DIM),
        Map.entry("accent", UiTheme.LIME),
        Map.entry("cyan", UiTheme.CYAN),
        Map.entry("amethyst", UiTheme.CYAN),
        Map.entry("gold", UiTheme.AMBER),
        Map.entry("export", UiTheme.CYAN),
        Map.entry("danger", UiTheme.RED),
        Map.entry("warning", UiTheme.AMBER),
        Map.entry("success", UiTheme.LIME),
        Map.entry("shadow", 0x00000000),
        Map.entry("chassis", UiTheme.SURFACE),
        Map.entry("panel_bg", UiTheme.SURFACE),
        Map.entry("panel_raised", UiTheme.SURFACE_RAISED),
        Map.entry("panel_inset", UiTheme.SURFACE_INPUT),
        Map.entry("edge_highlight", UiTheme.BORDER),
        Map.entry("edge_mid", UiTheme.BORDER),
        Map.entry("edge_dark", UiTheme.CANVAS),
        Map.entry("brass", UiTheme.TEXT_DIM),
        Map.entry("section_bg", UiTheme.SURFACE),
        Map.entry("section_border", UiTheme.BORDER),
        Map.entry("panel_inner_highlight", UiTheme.BORDER),
        Map.entry("panel_inner_shadow", UiTheme.CANVAS),
        Map.entry("preview_inner_dark", UiTheme.SURFACE_INPUT),
        Map.entry("preview_inner_light", UiTheme.BORDER),
        Map.entry("well_inner_dark", UiTheme.SURFACE_INPUT),
        Map.entry("backdrop", UiTheme.SCRIM),
        Map.entry("backdrop_edge", 0x00000000),
        Map.entry("backdrop_side", 0x00000000)
    );
    private static final Map<String, Integer> DEFAULT_METRICS = Map.ofEntries(
        Map.entry("language_width", 34),
        Map.entry("back_width", 76),
        Map.entry("header_inset", 12),
        Map.entry("header_title_y_offset", 8),
        Map.entry("header_brand_x", 10),
        Map.entry("section_label_x", 10),
        Map.entry("page_inset_compact", 12),
        Map.entry("page_inset_medium", 16),
        Map.entry("page_inset_wide", 20),
        Map.entry("rail_width", 56),
        Map.entry("rail_width_medium", 44),
        Map.entry("bottom_nav_height", 34),
        Map.entry("top_bar_height", 38),
        Map.entry("control_height", 24),
        Map.entry("content_gap", 12)
    );

    private static volatile Snapshot current = defaults();

    private MapKlussUiResourceData() {
    }

    static Snapshot current() {
        return current;
    }

    static void apply(Snapshot snapshot) {
        current = snapshot == null ? defaults() : snapshot;
    }

    static Snapshot defaults() {
        return new Snapshot(DEFAULT_COLORS, DEFAULT_METRICS);
    }

    static Snapshot read(Reader themeReader, Reader layoutReader, Snapshot fallback) throws IOException {
        Snapshot base = fallback == null ? defaults() : fallback;
        Map<String, Integer> colors = new LinkedHashMap<>(base.colors());
        Map<String, Integer> metrics = new LinkedHashMap<>(base.metrics());
        if (themeReader != null) {
            readColors(JsonParser.parseReader(themeReader), colors);
        }
        if (layoutReader != null) {
            readMetrics(JsonParser.parseReader(layoutReader), metrics);
        }
        return new Snapshot(colors, metrics);
    }

    private static void readColors(JsonElement root, Map<String, Integer> colors) throws IOException {
        JsonObject object = requireObject(root, "theme");
        JsonObject values = object.has("colors") ? requireObject(object.get("colors"), "theme.colors") : object;
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            String raw = entry.getValue().getAsString().trim();
            colors.put(entry.getKey(), parseColor(raw, entry.getKey()));
        }
    }

    private static void readMetrics(JsonElement root, Map<String, Integer> metrics) throws IOException {
        JsonObject object = requireObject(root, "layout");
        JsonObject values = object.has("metrics") ? requireObject(object.get("metrics"), "layout.metrics") : object;
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            int value = entry.getValue().getAsInt();
            if (value < 0 || value > 512) {
                throw new IOException("UI metric out of range: " + entry.getKey());
            }
            metrics.put(entry.getKey(), value);
        }
    }

    private static JsonObject requireObject(JsonElement element, String name) throws IOException {
        if (element == null || !element.isJsonObject()) {
            throw new IOException(name + " must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static int parseColor(String raw, String name) throws IOException {
        String value = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            if (value.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(value, 16));
            }
            if (value.length() == 8) {
                return (int) Long.parseLong(value, 16);
            }
        } catch (NumberFormatException ignored) {
            // Converted below into one bounded resource error.
        }
        throw new IOException("Invalid UI color: " + name);
    }

    record Snapshot(Map<String, Integer> colors, Map<String, Integer> metrics) {
        Snapshot {
            colors = Map.copyOf(colors);
            metrics = Map.copyOf(metrics);
        }

        int color(String key) {
            return colors.getOrDefault(key, DEFAULT_COLORS.getOrDefault(key, 0xFFFFFFFF));
        }

        int metric(String key) {
            return metrics.getOrDefault(key, DEFAULT_METRICS.getOrDefault(key, 0));
        }
    }
}
