package art.mapkluss.companion;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoFramePlacementTest {
    private static final String A = "A".repeat(64);
    private static final String B = "B".repeat(64);
    private static final String C = "C".repeat(64);
    private static final String D = "D".repeat(64);
    private static final AutoFrameTemplate TEMPLATE = new AutoFrameTemplate(
        "art", "version", "Test", 2, 2, List.of(A, B, C, D), "2026-07-10T00:00:00Z"
    );

    @Test
    void mapsWorldFramesFromLeftBottomForEveryFacing() {
        for (Direction facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            BlockPos anchor = new BlockPos(30, 70, -12);
            FrameWallGeometry.Coord anchorCoord = FrameWallGeometry.fromBlockPos(anchor, facing);
            BlockPos topRight = FrameWallGeometry.toBlockPos(facing, FrameWallGeometry.cell(anchorCoord, 1, 1));
            AutoFramePlacement placement = new AutoFramePlacement(TEMPLATE, anchor, facing, "world");

            AutoFramePlacement.Cell cell = placement.cellAt(topRight, facing).orElseThrow();
            assertEquals(1, cell.column());
            assertEquals(1, cell.rowFromBottom());
            assertEquals(1, cell.tileIndex());
            assertEquals(B, cell.expectedHash());
        }
    }

    @Test
    void rejectsFramesOutsideGridOrFacing() {
        AutoFramePlacement placement = new AutoFramePlacement(TEMPLATE, new BlockPos(0, 64, 0), Direction.SOUTH, "world");
        assertTrue(placement.cellAt(new BlockPos(0, 64, 0), Direction.NORTH).isEmpty());
        assertTrue(placement.cellAt(new BlockPos(4, 64, 0), Direction.SOUTH).isEmpty());
    }
}
