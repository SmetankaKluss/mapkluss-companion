package art.mapkluss.companion.mixin;

import art.mapkluss.companion.MapStackSlotRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class InGameHudMixin {
    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void mapkluss$drawMapPreview(
        GuiGraphicsExtractor context,
        int x,
        int y,
        DeltaTracker tickCounter,
        Player player,
        ItemStack stack,
        int seed,
        CallbackInfo ci
    ) {
        MapStackSlotRenderer.draw(context, stack, x, y);
    }
}
