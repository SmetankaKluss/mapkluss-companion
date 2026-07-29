package art.mapkluss.companion.mixin;

import art.mapkluss.companion.MapStackSlotRenderer;
import art.mapkluss.companion.MapRecognitionButtonLayout;
import art.mapkluss.companion.MapRecognitionButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import art.mapkluss.companion.MapStackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    protected HandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mapkluss$addMapRecognitionButton(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MapRecognitionButtonLayout.Position position = MapRecognitionButtonLayout.place(
            width, height, leftPos, topPos, imageWidth, imageHeight
        );
        addRenderableWidget(new MapRecognitionButton(
            position.x(), position.y(), position.width(),
            () -> MapStackManager.instance().requestRecognition(
                Minecraft.getInstance(), screen.getMenu()
            )
        ));
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void mapkluss$drawMapStackPreview(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        MapStackSlotRenderer.draw(context, slot.getItem(), slot.x, slot.y);
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void mapkluss$restoreMapBeforeClosing(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MapStackManager.instance().onScreenRemoved(Minecraft.getInstance(), screen.getMenu());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void mapkluss$finishMapPreviewLoading(CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MapStackManager.instance().onScreenRemoved(Minecraft.getInstance(), screen.getMenu());
    }
}
