package art.mapkluss.companion;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

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
        Vec3d center = block.toCenterPos().add(
            facing.getOffsetX() * ART_PLANE_OFFSET,
            facing.getOffsetY() * ART_PLANE_OFFSET,
            facing.getOffsetZ() * ART_PLANE_OFFSET
        );
        Vec3d right = switch (facing) {
            case NORTH -> new Vec3d(-HALF_CELL, 0, 0);
            case SOUTH -> new Vec3d(HALF_CELL, 0, 0);
            case EAST -> new Vec3d(0, 0, -HALF_CELL);
            case WEST -> new Vec3d(0, 0, HALF_CELL);
            default -> throw new IllegalArgumentException("Lens supports vertical frame walls only");
        };
        Vec3d up = new Vec3d(0, HALF_CELL, 0);
        return new Quad(
            center.subtract(right).subtract(up),
            center.add(right).subtract(up),
            center.add(right).add(up),
            center.subtract(right).add(up),
            facing
        );
    }

    record Quad(Vec3d bottomLeft, Vec3d bottomRight, Vec3d topRight, Vec3d topLeft, Direction normal) {
    }
}
