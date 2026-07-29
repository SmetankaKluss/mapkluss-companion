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
        Map.entry("white", 0xFFF0E7D2),
        Map.entry("muted", 0xFFB7AD99),
        Map.entry("dim", 0xFF867C6C),
        Map.entry("accent", 0xFF57FF6E),
        Map.entry("cyan", 0xFF31D8E8),
        Map.entry("amethyst", 0xFFBC94FF),
        Map.entry("gold", 0xFFFFD45A),
        Map.entry("export", 0xFFFF784D),
        Map.entry("danger", 0xFFFF4455),
        Map.entry("warning", 0xFFFFD45A),
        Map.entry("success", 0xFF57FF6E),
        Map.entry("shadow", 0xB8000000),
        Map.entry("chassis", 0xF20C0E12),
        Map.entry("panel_bg", 0xF2161B23),
        Map.entry("panel_raised", 0xF2232B35),
        Map.entry("panel_inset", 0xF0090C10),
        Map.entry("edge_highlight", 0xFF6C7887),
        Map.entry("edge_mid", 0xFF465260),
        Map.entry("edge_dark", 0xFF050609),
        Map.entry("brass", 0xFFA69270),
        Map.entry("section_bg", 0xE0131820),
        Map.entry("section_border", 0xFF465260),
        Map.entry("panel_inner_highlight", 0xFF384552),
        Map.entry("panel_inner_shadow", 0xFF0A0C10),
        Map.entry("preview_inner_dark", 0xFF020305),
        Map.entry("preview_inner_light", 0xFF26303B),
        Map.entry("well_inner_dark", 0xFF080A0D),
        Map.entry("backdrop", 0x32000000),
        Map.entry("backdrop_edge", 0xA8000000),
        Map.entry("backdrop_side", 0x62000000)
    );
    private static final Map<String, Integer> DEFAULT_METRICS = Map.ofEntries(
        Map.entry("language_width", 34),
        Map.entry("back_width", 84),
        Map.entry("header_inset", 8),
        Map.entry("header_title_y_offset", 9),
        Map.entry("header_brand_x", 12),
        Map.entry("section_label_x", 18)
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
