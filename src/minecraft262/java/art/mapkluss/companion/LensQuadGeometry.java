package art.mapkluss.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

final class LensQuadGeometry {
    // Item frames are centered at -0.46875 along their facing and are 0.0625 blocks thick.
    // Their visible face is therefore at -0.4375; the small outward epsilon avoids z-fighting.
    static final double ART_PLANE_OFFSET = -0.4365;
    static final double HALF_CELL = 0.5;

    private LensQuadGeometry() {
    }

    static Quad quad(BlockPos block, Direction facing) {
        if (!facing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Lens supports vertical frame walls only");
        }
        Vec3 center = Vec3.atCenterOf(block).add(
            facing.getStepX() * ART_PLANE_OFFSET,
            facing.getStepY() * ART_PLANE_OFFSET,
            facing.getStepZ() * ART_PLANE_OFFSET
        );
        Vec3 right = switch (facing) {
            case NORTH -> new Vec3(-HALF_CELL, 0, 0);
            case SOUTH -> new Vec3(HALF_CELL, 0, 0);
            case EAST -> new Vec3(0, 0, -HALF_CELL);
            case WEST -> new Vec3(0, 0, HALF_CELL);
            default -> throw new IllegalArgumentException("Lens supports vertical frame walls only");
        };
        Vec3 up = new Vec3(0, HALF_CELL, 0);
        return new Quad(
            center.subtract(right).subtract(up),
            center.add(right).subtract(up),
            center.add(right).add(up),
            center.subtract(right).add(up),
            facing
        );
    }

    record Quad(Vec3 bottomLeft, Vec3 bottomRight, Vec3 topRight, Vec3 topLeft, Direction normal) {
    }
}
