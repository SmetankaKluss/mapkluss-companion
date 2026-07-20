package art.mapkluss.companion.mixin;

import art.mapkluss.companion.CompanionLibraryScreen;
import art.mapkluss.companion.SuppressionManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void mapkluss$openCompanion(long window, int action, KeyEvent input, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui.screen() != null || client.getWindow().handle() != window) {
            return;
        }

        if (input != null && input.key() == GLFW.GLFW_KEY_K && action == GLFW.GLFW_PRESS) {
            client.gui.setScreen(new CompanionLibraryScreen(null));
        } else if (input != null && input.key() == GLFW.GLFW_KEY_J && action == GLFW.GLFW_PRESS) {
            SuppressionManager.instance().handleWorldAction(client);
        }
    }
}
