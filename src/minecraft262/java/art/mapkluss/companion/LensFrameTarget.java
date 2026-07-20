package art.mapkluss.companion;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

final class LensFrameTarget {
    private LensFrameTarget() {
    }

    static Target capture(Minecraft client) throws IOException {
        if (client.level == null || client.player == null || client.hitResult == null
            || client.hitResult.getType() != HitResult.Type.ENTITY) {
            throw new IOException("Look at the left-bottom item frame.");
        }
        Entity entity = ((EntityHitResult) client.hitResult).getEntity();
        if (!(entity instanceof ItemFrame frame)) throw new IOException("Look at the left-bottom item frame.");
        Direction facing = frame.getDirection();
        if (!facing.getAxis().isHorizontal()) throw new IOException("Lens supports vertical frame walls only.");
        return new Target(frame.getPos(), facing);
    }

    record Target(net.minecraft.core.BlockPos anchor, Direction facing) {
    }
}
