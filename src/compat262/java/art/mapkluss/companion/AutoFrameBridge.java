package art.mapkluss.companion;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AutoFrameBridge {
    private AutoFrameBridge() {
    }

    public static void register(AutoFrameManager manager) {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "main"));
        KeyMapping openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.mapkluss-companion.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            category
        ));
        KeyMapping autoFrameKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.mapkluss-companion.autoframe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new CompanionLibraryScreen(null));
            }
            while (autoFrameKey.consumeClick()) manager.activateTargetWall(client);
            manager.tick(client);
            MapStackManager.instance().tick(client);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "autoframe_hud"), (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            String label = manager.hudText();
            if (client.gui.hud.isHidden() || label.isBlank()) return;
            int width = client.font.width(label);
            context.fill(5, 23, 13 + width, 37, 0xA0000000);
            context.text(client.font, label, 9, 26, 0xFFFFD85A);
        });
    }
}
