package art.mapkluss.companion.mixin;

import art.mapkluss.companion.MapStackSlotRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "renderHotbarItem", at = @At("TAIL"))
    private void mapkluss$drawMapPreview(
        DrawContext context,
        int x,
        int y,
        RenderTickCounter tickCounter,
        PlayerEntity player,
        ItemStack stack,
        int seed,
        CallbackInfo ci
    ) {
        MapStackSlotRenderer.draw(context, stack, x, y);
    }
}
