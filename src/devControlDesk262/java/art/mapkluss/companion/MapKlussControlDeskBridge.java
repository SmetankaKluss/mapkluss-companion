package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Target-specific bridge that changes only the isolated dev-client window. */
final class MapKlussControlDeskBridge {
    private MapKlussControlDeskBridge() {
    }

    static void apply(MapKlussControlDesk.Selection selection) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            System.setProperty("mapkluss.dev.libraryFixture", selection.fixture());
            System.setProperty("mapkluss.dev.language", selection.language());
            applyGuiScale(client, selection.guiScale());
            applyViewport(client, selection.viewport());
            Screen parent = client.gui.screen();
            client.gui.setScreen(new CompanionLibraryScreen(parent));
        });
    }

    private static void applyGuiScale(Minecraft client, String value) {
        int scale = "auto".equals(value) ? 0 : Integer.parseInt(value);
        client.options.guiScale().set(scale);
        client.resizeGui();
    }

    private static void applyViewport(Minecraft client, String value) {
        String[] parts = value.split("x", 2);
        client.getWindow().setWindowed(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        client.resizeGui();
    }
}
