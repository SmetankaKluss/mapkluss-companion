package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class FrameWallGeometryTest {
    @Test
    void roundTripsEveryFramePlane() {
        BlockPos position = new BlockPos(12, 70, -9);
        for (Direction facing : Direction.values()) {
            FrameWallGeometry.Coord coord = FrameWallGeometry.fromBlockPos(position, facing);
            assertEquals(position, FrameWallGeometry.toBlockPos(facing, coord));
        }
    }

    @Test
    void growsFromLeftBottomToRightAndUp() {
        FrameWallGeometry.Coord anchor = new FrameWallGeometry.Coord(4, 10, 20);
        assertEquals(new FrameWallGeometry.Coord(4, 12, 23), FrameWallGeometry.cell(anchor, 2, 3));
    }

    @Test
    void growsTowardViewerRightForEveryWallFacing() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        assertRightCell(anchor, Direction.NORTH, new BlockPos(-1, 64, 0));
        assertRightCell(anchor, Direction.SOUTH, new BlockPos(1, 64, 0));
        assertRightCell(anchor, Direction.EAST, new BlockPos(0, 64, -1));
        assertRightCell(anchor, Direction.WEST, new BlockPos(0, 64, 1));
        assertRightCell(anchor, Direction.UP, new BlockPos(1, 64, 0));
        assertRightCell(anchor, Direction.DOWN, new BlockPos(-1, 64, 0));
    }

    @Test
    void growsTowardScreenUpForFloorAndCeiling() {
        BlockPos anchor = new BlockPos(0, 64, 0);
        for (Direction facing : new Direction[]{Direction.UP, Direction.DOWN}) {
            FrameWallGeometry.Coord origin = FrameWallGeometry.fromBlockPos(anchor, facing);
            assertEquals(
                new BlockPos(0, 64, -1),
                FrameWallGeometry.toBlockPos(facing, FrameWallGeometry.cell(origin, 0, 1))
            );
        }
    }

    @Test
    void scanOrderingMatchesViewerRight() {
        BlockPos east = new BlockPos(1, 64, 0);
        assertEquals(-1, FrameWallGeometry.fromScanBlockPos(east, Direction.NORTH).x());
        assertEquals(1, FrameWallGeometry.fromScanBlockPos(east, Direction.SOUTH).x());
        assertEquals(1, FrameWallGeometry.fromScanBlockPos(east, Direction.UP).x());
        assertEquals(-1, FrameWallGeometry.fromScanBlockPos(east, Direction.DOWN).x());
    }

    private static void assertRightCell(BlockPos anchor, Direction facing, BlockPos expected) {
        FrameWallGeometry.Coord origin = FrameWallGeometry.fromBlockPos(anchor, facing);
        assertEquals(expected, FrameWallGeometry.toBlockPos(facing, FrameWallGeometry.cell(origin, 1, 0)));
    }
}
