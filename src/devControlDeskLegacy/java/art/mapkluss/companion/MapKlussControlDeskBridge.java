package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** Target-specific bridge that changes only the isolated dev-client window. */
final class MapKlussControlDeskBridge {
    private MapKlussControlDeskBridge() {
    }

    static void apply(MapKlussControlDesk.Selection selection) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            System.setProperty("mapkluss.dev.libraryFixture", selection.fixture());
            System.setProperty("mapkluss.dev.language", selection.language());
            applyGuiScale(client, selection.guiScale());
            applyViewport(client, selection.viewport());
            Screen parent = client.currentScreen;
            client.setScreen(new CompanionLibraryScreen(parent));
        });
    }

    private static void applyGuiScale(MinecraftClient client, String value) {
        int scale = "auto".equals(value) ? 0 : Integer.parseInt(value);
        client.options.getGuiScale().setValue(scale);
        client.onResolutionChanged();
    }

    private static void applyViewport(MinecraftClient client, String value) {
        String[] parts = value.split("x", 2);
        client.getWindow().setWindowedSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        client.onResolutionChanged();
    }
}
