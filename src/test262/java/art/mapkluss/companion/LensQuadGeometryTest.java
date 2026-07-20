package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

final class LensQuadGeometryTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void placesPreviewOnTheFrontFaceOfTheItemFrame() {
        BlockPos block = new BlockPos(12, 64, -7);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            LensQuadGeometry.Quad quad = LensQuadGeometry.quad(block, facing);
            Vec3 center = average(quad.bottomLeft(), quad.bottomRight(), quad.topRight(), quad.topLeft());
            Vec3 expected = Vec3.atCenterOf(block).add(
                facing.getStepX() * LensQuadGeometry.ART_PLANE_OFFSET,
                0,
                facing.getStepZ() * LensQuadGeometry.ART_PLANE_OFFSET
            );
            assertVec(expected, center);
        }
    }

    @Test
    void adjacentCellsShareTheSameEdgeWithoutGaps() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
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
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            LensQuadGeometry.Quad quad = LensQuadGeometry.quad(BlockPos.ZERO, facing);
            assertEquals(1.0, quad.bottomLeft().distanceTo(quad.bottomRight()), EPSILON);
            assertEquals(1.0, quad.bottomLeft().distanceTo(quad.topLeft()), EPSILON);
        }
    }

    private static Vec3 average(Vec3... points) {
        Vec3 sum = Vec3.ZERO;
        for (Vec3 point : points) sum = sum.add(point);
        return sum.scale(1.0 / points.length);
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
