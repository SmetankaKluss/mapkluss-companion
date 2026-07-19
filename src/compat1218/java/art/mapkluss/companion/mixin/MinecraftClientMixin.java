package art.mapkluss.companion.mixin;

import art.mapkluss.companion.AutoFrameManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void mapkluss$autoFrameUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (AutoFrameManager.instance().interceptItemUse(client)) ci.cancel();
    }
}
