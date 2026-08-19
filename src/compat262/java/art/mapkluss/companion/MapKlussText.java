package art.mapkluss.companion;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

final class MapKlussText {
    private static final FontDescription.Resource FONT = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "inter")
    );

    private MapKlussText() {
    }

    static Component text(String value) {
        return Component.literal(value == null ? "" : value).setStyle(Style.EMPTY.withFont(FONT));
    }
}
