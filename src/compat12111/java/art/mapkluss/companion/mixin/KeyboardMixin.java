package art.mapkluss.companion.mixin;

import art.mapkluss.companion.CompanionLibraryScreen;
import art.mapkluss.companion.SuppressionManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void mapkluss$openCompanion(long window, int action, KeyInput input, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen != null || client.getWindow().getHandle() != window) {
            return;
        }

        if (input != null && input.key() == GLFW.GLFW_KEY_K && action == GLFW.GLFW_PRESS) {
            client.setScreen(new CompanionLibraryScreen(null));
        } else if (input != null && input.key() == GLFW.GLFW_KEY_J && action == GLFW.GLFW_PRESS) {
            SuppressionManager.instance().handleWorldAction(client);
        }
    }
}
