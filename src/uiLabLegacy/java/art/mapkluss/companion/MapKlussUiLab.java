package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class MapKlussUiLab {
    private MapKlussUiLab() {
    }

    public static void register() {
        MapKlussUiLabResourceWatcher.start();
        KeyBinding.Category category = KeyBinding.Category.create(
            Identifier.of(MapKlussCompanionClient.MOD_ID, "ui_lab")
        );
        KeyBinding key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mapkluss-companion.ui_lab",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            category
        ));
        boolean autoOpen = Boolean.getBoolean("mapkluss.uiLab.autoOpen");
        boolean[] opened = {false};
        int[] readyTicks = {0};
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (autoOpen && !opened[0] && client.currentScreen instanceof TitleScreen && ++readyTicks[0] >= 20) {
                open(client, client.currentScreen);
                opened[0] = true;
            } else if (!(client.currentScreen instanceof TitleScreen)) {
                readyTicks[0] = 0;
            }
            while (key.wasPressed()) {
                if (client.currentScreen instanceof MapKlussUiLabScreen lab) {
                    client.setScreen(lab.parent());
                } else {
                    open(client, client.currentScreen);
                }
            }
        });
        MapKlussCompanionClient.LOGGER.info("MapKluss UI Lab enabled. Press F8 to open it.");
    }

    private static void open(MinecraftClient client, net.minecraft.client.gui.screen.Screen parent) {
        MapKlussUiLabModel model = new MapKlussUiLabModel();
        client.setScreen(new MapKlussUiLabScreen(parent, model));
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
