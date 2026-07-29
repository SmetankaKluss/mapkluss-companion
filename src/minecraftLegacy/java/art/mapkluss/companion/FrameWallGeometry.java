package art.mapkluss.companion;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

public final class FrameWallGeometry {
    private FrameWallGeometry() {
    }

    public static Coord fromBlockPos(BlockPos pos, Direction facing) {
        return switch (facing) {
            case NORTH -> new Coord(pos.getZ(), -pos.getX(), pos.getY());
            case SOUTH -> new Coord(pos.getZ(), pos.getX(), pos.getY());
            case EAST -> new Coord(pos.getX(), -pos.getZ(), pos.getY());
            case WEST -> new Coord(pos.getX(), pos.getZ(), pos.getY());
            case UP -> new Coord(pos.getY(), pos.getX(), -pos.getZ());
            case DOWN -> new Coord(pos.getY(), -pos.getX(), -pos.getZ());
        };
    }

    public static BlockPos toBlockPos(Direction facing, Coord coord) {
        return switch (facing) {
            case NORTH -> new BlockPos(-coord.x(), coord.y(), coord.plane());
            case SOUTH -> new BlockPos(coord.x(), coord.y(), coord.plane());
            case EAST -> new BlockPos(coord.plane(), coord.y(), -coord.x());
            case WEST -> new BlockPos(coord.plane(), coord.y(), coord.x());
            case UP -> new BlockPos(coord.x(), coord.plane(), -coord.y());
            case DOWN -> new BlockPos(-coord.x(), coord.plane(), -coord.y());
        };
    }

    public static Coord cell(Coord leftBottom, int column, int row) {
        if (column < 0 || row < 0) throw new IllegalArgumentException("Grid offsets cannot be negative");
        return new Coord(leftBottom.plane(), leftBottom.x() + column, leftBottom.y() + row);
    }

    public static MapFrameCorner corner(BlockPos pos, Direction facing) {
        Coord coord = fromBlockPos(pos, facing);
        return new MapFrameCorner(facing, coord.plane(), coord.x(), coord.y());
    }

    public static ScanCoord fromScanBlockPos(BlockPos pos, Direction facing) {
        return fromScanBlockPos(pos, facing, defaultPlaneUp(facing));
    }

    public static ScanCoord fromScanBlockPos(BlockPos pos, Direction facing, Direction planeUp) {
        Direction right = AutoFramePlacement.rightDirection(facing, planeUp);
        return new ScanCoord(axisCoordinate(pos, facing), project(pos, right), project(pos, planeUp));
    }

    public static MapFrameCorner scanCorner(BlockPos pos, Direction facing) {
        return scanCorner(pos, facing, defaultPlaneUp(facing));
    }

    public static MapFrameCorner scanCorner(BlockPos pos, Direction facing, Direction planeUp) {
        ScanCoord coord = fromScanBlockPos(pos, facing, planeUp);
        return new MapFrameCorner(facing, planeUp, coord.plane(), coord.x(), coord.y());
    }

    static Direction defaultPlaneUp(Direction facing) {
        return facing != null && facing.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
    }

    private static int axisCoordinate(BlockPos pos, Direction facing) {
        return switch (facing.getAxis()) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static int project(BlockPos pos, Direction direction) {
        return pos.getX() * direction.getOffsetX()
            + pos.getY() * direction.getOffsetY()
            + pos.getZ() * direction.getOffsetZ();
    }

    public record Coord(int plane, int x, int y) {
        public List<Coord> neighbors() {
            return List.of(
                new Coord(plane, x + 1, y),
                new Coord(plane, x - 1, y),
                new Coord(plane, x, y + 1),
                new Coord(plane, x, y - 1)
            );
        }
    }

    public record ScanCoord(int plane, int x, int y) {
        public List<ScanCoord> neighbors() {
            return List.of(
                new ScanCoord(plane, x + 1, y),
                new ScanCoord(plane, x - 1, y),
                new ScanCoord(plane, x, y + 1),
                new ScanCoord(plane, x, y - 1)
            );
        }
    }
}
