package art.mapkluss.companion;

import java.util.Map;

final class UiTheme {
    static final int CANVAS = 0xF4090B0E;
    static final int SURFACE = 0xF811151A;
    static final int SURFACE_RAISED = 0xFA181D23;
    static final int SURFACE_INPUT = 0xF607090C;
    static final int BORDER = 0xFF303842;
    static final int BORDER_STRONG = 0xFF536171;
    static final int TEXT = 0xFFF2F5F7;
    static final int TEXT_MUTED = 0xFFA8B2BD;
    static final int TEXT_DIM = 0xFF68737F;
    static final int LIME = 0xFF64F58D;
    static final int CYAN = 0xFF51D7F0;
    static final int AMBER = 0xFFF4C75B;
    static final int RED = 0xFFFF6673;
    static final int SCRIM = 0x74000000;

    private UiTheme() {
    }

    static Map<String, Integer> colors() {
        return Map.ofEntries(
            Map.entry("canvas", CANVAS),
            Map.entry("surface", SURFACE),
            Map.entry("surface_raised", SURFACE_RAISED),
            Map.entry("surface_input", SURFACE_INPUT),
            Map.entry("border", BORDER),
            Map.entry("border_strong", BORDER_STRONG),
            Map.entry("text", TEXT),
            Map.entry("text_muted", TEXT_MUTED),
            Map.entry("text_dim", TEXT_DIM),
            Map.entry("lime", LIME),
            Map.entry("cyan", CYAN),
            Map.entry("amber", AMBER),
            Map.entry("red", RED),
            Map.entry("scrim", SCRIM)
        );
    }
}
