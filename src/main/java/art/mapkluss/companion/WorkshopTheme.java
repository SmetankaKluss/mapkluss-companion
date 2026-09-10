package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable semantic colors; artwork pixels never consume these tokens. */
public record WorkshopTheme(String id, Map<String, Integer> colors) {
    public static final String DEFAULT_ID = "amethyst";
    public static final List<String> IDS = List.of("classic", "deep-ocean", "ember-forge", "amethyst", "acid-grove", "cobalt-pulse", "midnight");
    private static final Map<String, WorkshopTheme> THEMES = load();

    public WorkshopTheme { colors = Map.copyOf(colors); }

    public static String normalize(String id) { return IDS.contains(id == null ? "" : id) ? id : DEFAULT_ID; }
    public static WorkshopTheme of(String id) { return THEMES.get(normalize(id)); }
    public int color(String name) {
        Integer value = colors.get(name);
        if (value == null) throw new IllegalArgumentException("Unknown workshop color: " + name);
        return value;
    }

    private static Map<String, WorkshopTheme> load() {
        var stream = WorkshopTheme.class.getResourceAsStream("/assets/mapkluss-companion/ui/workshop-themes.json");
        if (stream == null) throw new IllegalStateException("Missing workshop palettes");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, WorkshopTheme> result = new LinkedHashMap<>();
            for (String id : IDS) {
                Map<String, Integer> colors = new LinkedHashMap<>();
                for (var entry : root.getAsJsonObject(id).entrySet()) {
                    String hex = entry.getValue().getAsString();
                    if (!hex.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid workshop color");
                    colors.put(entry.getKey(), (int) (0xFF000000L | Long.parseLong(hex.substring(1), 16)));
                }
                result.put(id, new WorkshopTheme(id, colors));
            }
            return Map.copyOf(result);
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read workshop palettes", error); }
    }
}
