package art.mapkluss.companion.mixin;

import art.mapkluss.companion.SuppressionManager;
import art.mapkluss.companion.MapStackManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MapUpdateMixin {
    @Inject(method = "onMapUpdate", at = @At("TAIL"))
    private void mapkluss$observeSuppressionMapUpdate(MapUpdateS2CPacket packet, CallbackInfo ci) {
        SuppressionManager.instance().onMapUpdate(packet.mapId().id(), packet.updateData().isPresent());
        MapStackManager.instance().onMapUpdate(packet.mapId().id());
    }
}
