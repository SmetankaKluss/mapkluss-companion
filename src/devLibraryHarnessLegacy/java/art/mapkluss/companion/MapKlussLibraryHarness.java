package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Opens the production Library screen with a dev-only fixture after the title screen is ready. */
public final class MapKlussLibraryHarness {
    public static void drawOverlaySamples(Object graphics) {
        var context=(net.minecraft.client.gui.DrawContext)graphics;
        WorkshopHudDraw.lens(context, "Lens r12  " + CompanionI18n.translate("рамки") + " 5/6");
        WorkshopHudDraw.twoLayer(context, java.util.List.of(
            "Two-layer  2/6", CompanionI18n.translate("Постройка"),
            "128 / 256", CompanionI18n.translate("Подтвердить") + "  J"));
    }
    private MapKlussLibraryHarness() {
    }

    public static void register() {
        if (!Boolean.getBoolean("mapkluss.dev.libraryHarness.autoOpen")) return;
        boolean[] opened = {false};
        int[] readyTicks = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (opened[0]) return;
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen && ++readyTicks[0] >= 40) {
                open(client, client.currentScreen);
                opened[0] = true;
            } else if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.TitleScreen)) {
                readyTicks[0] = 0;
            }
        });
        MapKlussCompanionClient.LOGGER.info("MapKluss Library harness enabled for fixture {}.",
            System.getProperty("mapkluss.dev.libraryFixture", ""));
    }

    private static void open(MinecraftClient client, net.minecraft.client.gui.screen.Screen parent) {
        client.setScreen(new CompanionLibraryScreen(parent));
        MapKlussCompanionClient.LOGGER.info("MapKluss Library harness opened the ordinary Library screen.");
    }
}
