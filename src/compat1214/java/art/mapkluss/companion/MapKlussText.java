package art.mapkluss.companion;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class MapKlussText {
    private static final Identifier WORKSHOP_FONT = Identifier.of(MapKlussCompanionClient.MOD_ID, "workshop");

    static Text workshop(String value) {
        return Text.literal(value == null ? "" : value).setStyle(Style.EMPTY.withFont(WORKSHOP_FONT));
    }

    private static final Identifier FONT = Identifier.of(MapKlussCompanionClient.MOD_ID, "inter");

    private MapKlussText() {
    }

    static Text text(String value) {
        return Text.literal(value == null ? "" : value).setStyle(Style.EMPTY.withFont(FONT));
    }
}
