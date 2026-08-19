package art.mapkluss.companion;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MapKlussUiLab {
    private MapKlussUiLab() {
    }

    public static void register() {
        MapKlussUiLabResourceWatcher.start();
        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "ui_lab")
        );
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.mapkluss-companion.ui_lab",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            category
        ));
        boolean autoOpen = Boolean.getBoolean("mapkluss.uiLab.autoOpen");
        boolean[] opened = {false};
        int[] readyTicks = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (autoOpen && !opened[0] && client.gui.screen() instanceof TitleScreen && ++readyTicks[0] >= 20) {
                open(client, client.gui.screen());
                opened[0] = true;
            } else if (!(client.gui.screen() instanceof TitleScreen)) {
                readyTicks[0] = 0;
            }
            while (key.consumeClick()) {
                if (client.gui.screen() instanceof MapKlussUiLabScreen lab) {
                    client.gui.setScreen(lab.parent());
                } else {
                    open(client, client.gui.screen());
                }
            }
        });
        MapKlussCompanionClient.LOGGER.info("MapKluss UI Lab enabled. Press F8 to open it.");
    }

    private static void open(Minecraft client, net.minecraft.client.gui.screens.Screen parent) {
        MapKlussUiLabModel model = new MapKlussUiLabModel();
        client.gui.setScreen(new MapKlussUiLabScreen(parent, model));
        if (!Boolean.getBoolean("mapkluss.uiLab.captureOnOpen")) return;
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(1_500);
                MapKlussUiLabCapture.capture(model);
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not capture the initial UI Lab screen.", error);
            }
        });
    }
}
