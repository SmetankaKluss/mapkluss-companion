package art.mapkluss.companion.mixin;

import art.mapkluss.companion.AutoFrameManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void mapkluss$autoFrameUse(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (AutoFrameManager.instance().interceptItemUse(client)) ci.cancel();
    }
}
