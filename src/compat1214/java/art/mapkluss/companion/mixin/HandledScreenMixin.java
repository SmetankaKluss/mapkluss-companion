package art.mapkluss.companion.mixin;

import art.mapkluss.companion.MapStackSlotRenderer;
import art.mapkluss.companion.MapStackManager;
import art.mapkluss.companion.MapRecognitionButtonLayout;
import art.mapkluss.companion.MapRecognitionButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;
    @Shadow protected int x;
    @Shadow protected int y;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mapkluss$addMapRecognitionButton(CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        MapRecognitionButtonLayout.Position position = MapRecognitionButtonLayout.place(
            width, height, x, y, backgroundWidth, backgroundHeight
        );
        addDrawableChild(new MapRecognitionButton(
            position.x(), position.y(), position.width(),
            () -> MapStackManager.instance().requestRecognition(
                MinecraftClient.getInstance(), screen.getScreenHandler()
            )
        ));
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void mapkluss$drawMapStackPreview(DrawContext context, Slot slot, CallbackInfo ci) {
        MapStackSlotRenderer.draw(context, slot.getStack(), slot.x, slot.y);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void mapkluss$restoreMapBeforeClosing(CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        MapStackManager.instance().onScreenRemoved(MinecraftClient.getInstance(), screen.getScreenHandler());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void mapkluss$finishMapPreviewLoading(CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        MapStackManager.instance().onScreenRemoved(MinecraftClient.getInstance(), screen.getScreenHandler());
    }
}
