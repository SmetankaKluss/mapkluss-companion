package art.mapkluss.companion;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LensQuadGeometryTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void placesPreviewOnTheFrontFaceOfTheItemFrame() {
        BlockPos block = new BlockPos(12, 64, -7);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            LensQuadGeometry.Quad quad = LensQuadGeometry.quad(block, facing);
            Vec3d center = average(quad.bottomLeft(), quad.bottomRight(), quad.topRight(), quad.topLeft());
            Vec3d expected = block.toCenterPos().add(
                facing.getOffsetX() * LensQuadGeometry.ART_PLANE_OFFSET,
                0,
                facing.getOffsetZ() * LensQuadGeometry.ART_PLANE_OFFSET
            );
            assertVec(expected, center);
        }
    }

    @Test
    void adjacentCellsShareTheSameEdgeWithoutGaps() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        for (Direction facing : Direction.Type.HORIZONTAL) {
            FrameWallGeometry.Coord origin = FrameWallGeometry.fromBlockPos(anchor, facing);
            BlockPos rightCell = FrameWallGeometry.toBlockPos(facing, FrameWallGeometry.cell(origin, 1, 0));
            LensQuadGeometry.Quad left = LensQuadGeometry.quad(anchor, facing);
            LensQuadGeometry.Quad right = LensQuadGeometry.quad(rightCell, facing);
            assertVec(left.bottomRight(), right.bottomLeft());
            assertVec(left.topRight(), right.topLeft());
        }
    }

    @Test
    void everyCellIsExactlyOneBlockWideAndTall() {
        for (Direction facing : Direction.Type.HORIZONTAL) {
            LensQuadGeometry.Quad quad = LensQuadGeometry.quad(BlockPos.ORIGIN, facing);
            assertEquals(1.0, quad.bottomLeft().distanceTo(quad.bottomRight()), EPSILON);
            assertEquals(1.0, quad.bottomLeft().distanceTo(quad.topLeft()), EPSILON);
        }
    }

    private static Vec3d average(Vec3d... points) {
        Vec3d sum = Vec3d.ZERO;
        for (Vec3d point : points) sum = sum.add(point);
        return sum.multiply(1.0 / points.length);
    }

    private static void assertVec(Vec3d expected, Vec3d actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
