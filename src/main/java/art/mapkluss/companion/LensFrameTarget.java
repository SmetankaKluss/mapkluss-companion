package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;

import java.io.IOException;

final class LensFrameTarget {
    private LensFrameTarget() {
    }

    static Target capture(MinecraftClient client) throws IOException {
        if (client.world == null || client.player == null || client.crosshairTarget == null
            || client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            throw new IOException("Look at the left-bottom item frame.");
        }
        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (!(entity instanceof ItemFrameEntity frame)) throw new IOException("Look at the left-bottom item frame.");
        Direction facing = frame.getHorizontalFacing();
        if (!facing.getAxis().isHorizontal()) throw new IOException("Lens supports vertical frame walls only.");
        return new Target(frame.getAttachedBlockPos(), facing);
    }

    record Target(net.minecraft.util.math.BlockPos anchor, Direction facing) {
    }
}
