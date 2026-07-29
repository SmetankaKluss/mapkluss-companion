package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        for (Direction facing : Direction.values()) {
            BlockPos anchor = new BlockPos(30, 70, -12);
            AutoFramePlacement placement = new AutoFramePlacement(TEMPLATE, anchor, facing, "world");
            FrameWallGeometry.Coord anchorCoord = FrameWallGeometry.fromBlockPos(anchor, facing);
            BlockPos topRight = facing.getAxis().isHorizontal()
                ? FrameWallGeometry.toBlockPos(facing, FrameWallGeometry.cell(anchorCoord, 1, 1))
                : placement.blockAt(1, 1);

            AutoFramePlacement.Cell cell = placement.cellAt(topRight, facing).orElseThrow();
            assertEquals(1, cell.column());
            assertEquals(1, cell.rowFromBottom());
            assertEquals(1, cell.tileIndex());
            assertEquals(B, cell.expectedHash());
            assertEquals(placement.requiredMapRotation(), cell.mapRotation());
        }
    }

    @Test
    void rejectsFramesOutsideGridOrFacing() {
        AutoFramePlacement placement = new AutoFramePlacement(TEMPLATE, new BlockPos(0, 64, 0), Direction.SOUTH, "world");
        assertTrue(placement.cellAt(new BlockPos(0, 64, 0), Direction.NORTH).isEmpty());
        assertTrue(placement.cellAt(new BlockPos(4, 64, 0), Direction.SOUTH).isEmpty());
    }

    @Test
    void orientsFloorGridFromPlayersView() {
        BlockPos anchor = new BlockPos(30, 70, -12);
        AutoFramePlacement placement = new AutoFramePlacement(
            TEMPLATE,
            anchor,
            Direction.UP,
            Direction.SOUTH,
            "world"
        );

        BlockPos topRight = anchor.offset(-1, 0, 1);
        AutoFramePlacement.Cell cell = placement.cellAt(topRight, Direction.UP).orElseThrow();
        assertEquals(topRight, placement.blockAt(1, 1));
        assertEquals(1, cell.column());
        assertEquals(1, cell.rowFromBottom());
        assertEquals(B, cell.expectedHash());
        assertEquals(2, cell.mapRotation());
    }

    @Test
    void orientsCeilingGridFromPlayersView() {
        BlockPos anchor = new BlockPos(30, 70, -12);
        AutoFramePlacement placement = new AutoFramePlacement(
            TEMPLATE,
            anchor,
            Direction.DOWN,
            Direction.EAST,
            "world"
        );

        BlockPos topRight = anchor.offset(1, 0, -1);
        AutoFramePlacement.Cell cell = placement.cellAt(topRight, Direction.DOWN).orElseThrow();
        assertEquals(topRight, placement.blockAt(1, 1));
        assertEquals(1, cell.column());
        assertEquals(1, cell.rowFromBottom());
        assertEquals(B, cell.expectedHash());
        assertEquals(1, cell.mapRotation());
    }

    @Test
    void findsCeilingGridInVisibleUpAndRightDirections() {
        AutoFrameTemplate template = new AutoFrameTemplate(
            "ceiling-3x3", "version", "Ceiling", 3, 3, hashes(9), "2026-07-24T00:00:00Z"
        );
        BlockPos origin = new BlockPos(30, 70, -12);
        Direction planeUp = AutoFramePlacement.planeUpFromPlayerView(Direction.DOWN, Direction.SOUTH);
        List<BlockPos> frames = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                frames.add(origin.offset(-column, 0, -row));
            }
        }

        AutoFramePlacement.PlacementResolution resolved = AutoFramePlacement.resolveFromFrames(
            template,
            origin,
            Direction.DOWN,
            planeUp,
            "world",
            frames
        );

        assertEquals(Direction.NORTH, planeUp);
        assertTrue(resolved.found());
        AutoFramePlacement.Cell topRight = resolved.placement()
            .cellAt(origin.offset(-2, 0, -2), Direction.DOWN)
            .orElseThrow();
        assertEquals(2, topRight.tileIndex());
        assertEquals(2, topRight.mapRotation());
    }

    @Test
    void convertsPlayerViewToPlaneUpForWallsFloorAndCeiling() {
        assertEquals(
            Direction.UP,
            AutoFramePlacement.planeUpFromPlayerView(Direction.NORTH, Direction.EAST)
        );
        assertEquals(
            Direction.SOUTH,
            AutoFramePlacement.planeUpFromPlayerView(Direction.UP, Direction.SOUTH)
        );
        assertEquals(
            Direction.NORTH,
            AutoFramePlacement.planeUpFromPlayerView(Direction.DOWN, Direction.SOUTH)
        );
    }

    @Test
    void rotatesFloorMapsToMatchPlayersView() {
        assertEquals(0, horizontalPlacement(Direction.UP, Direction.NORTH).requiredMapRotation());
        assertEquals(1, horizontalPlacement(Direction.UP, Direction.EAST).requiredMapRotation());
        assertEquals(2, horizontalPlacement(Direction.UP, Direction.SOUTH).requiredMapRotation());
        assertEquals(3, horizontalPlacement(Direction.UP, Direction.WEST).requiredMapRotation());
    }

    @Test
    void rotatesCeilingMapsWithoutMirroringTheGrid() {
        assertEquals(0, horizontalPlacement(Direction.DOWN, Direction.SOUTH).requiredMapRotation());
        assertEquals(1, horizontalPlacement(Direction.DOWN, Direction.EAST).requiredMapRotation());
        assertEquals(2, horizontalPlacement(Direction.DOWN, Direction.NORTH).requiredMapRotation());
        assertEquals(3, horizontalPlacement(Direction.DOWN, Direction.WEST).requiredMapRotation());
    }

    @Test
    void resolvesThreeByFourFloorGridFromActualFramesAndKeepsZOrder() {
        AutoFrameTemplate template = new AutoFrameTemplate(
            "art-3x4", "version", "3x4", 3, 4, hashes(12), "2026-07-24T00:00:00Z"
        );
        BlockPos origin = new BlockPos(30, 70, -12);
        List<BlockPos> frames = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 3; column++) {
                frames.add(origin.offset(-column, 0, row));
            }
        }

        AutoFramePlacement.PlacementResolution resolved = AutoFramePlacement.resolveFromFrames(
            template,
            origin,
            Direction.UP,
            Direction.SOUTH,
            "world",
            frames
        );

        assertTrue(resolved.found());
        AutoFramePlacement.Cell topRight = resolved.placement()
            .cellAt(origin.offset(-2, 0, 3), Direction.UP)
            .orElseThrow();
        assertEquals(2, topRight.tileIndex());
        assertEquals(hashes(12).get(2), topRight.expectedHash());
        assertEquals(2, topRight.mapRotation());
    }

    @Test
    void rejectsPlaneUpOutsideTheFramePlane() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new AutoFramePlacement(TEMPLATE, BlockPos.ZERO, Direction.UP, Direction.UP, "world")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new AutoFramePlacement(TEMPLATE, BlockPos.ZERO, Direction.SOUTH, Direction.EAST, "world")
        );
    }

    private static List<String> hashes(int count) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < count; index++) values.add("%064X".formatted(index + 1));
        return values;
    }

    private static AutoFramePlacement horizontalPlacement(Direction facing, Direction planeUp) {
        return new AutoFramePlacement(TEMPLATE, BlockPos.ZERO, facing, planeUp, "world");
    }
}
