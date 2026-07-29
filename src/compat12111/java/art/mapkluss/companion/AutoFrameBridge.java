package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.MinecraftClient;

public final class AutoFrameBridge {
    private AutoFrameBridge() {
    }

    public static void register(AutoFrameManager manager) {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MapKlussCompanionClient.MOD_ID, "main"));
        KeyBinding openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mapkluss-companion.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            category
        ));
        KeyBinding autoFrameKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.mapkluss-companion.autoframe",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new CompanionLibraryScreen(null));
            }
            while (autoFrameKey.wasPressed()) manager.activateTargetWall(client);
            manager.tick(client);
            MapStackManager.instance().tick(client);
        });
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            String label = manager.hudText();
            if (client.options.hudHidden || label.isBlank()) return;
            int width = client.textRenderer.getWidth(label);
            context.fill(5, 23, 13 + width, 37, 0xA0000000);
            context.drawTextWithShadow(client.textRenderer, label, 9, 26, 0xFFFFD85A);
        });
    }
}
