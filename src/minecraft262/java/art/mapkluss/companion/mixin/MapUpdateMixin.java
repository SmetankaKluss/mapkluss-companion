package art.mapkluss.companion.mixin;

import art.mapkluss.companion.SuppressionManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import art.mapkluss.companion.MapStackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MapUpdateMixin {
    @Inject(method = "handleMapItemData", at = @At("TAIL"))
    private void mapkluss$observeSuppressionMapUpdate(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
        SuppressionManager.instance().onMapUpdate(packet.mapId().id(), packet.colorPatch().isPresent());
        MapStackManager.instance().onMapUpdate(packet.mapId().id());
    }
}
