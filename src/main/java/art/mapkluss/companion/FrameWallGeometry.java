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
            default -> throw new IllegalArgumentException("Item-frame wall must be horizontal");
        };
    }

    public static BlockPos toBlockPos(Direction facing, Coord coord) {
        return switch (facing) {
            case NORTH -> new BlockPos(-coord.x(), coord.y(), coord.plane());
            case SOUTH -> new BlockPos(coord.x(), coord.y(), coord.plane());
            case EAST -> new BlockPos(coord.plane(), coord.y(), -coord.x());
            case WEST -> new BlockPos(coord.plane(), coord.y(), coord.x());
            default -> throw new IllegalArgumentException("Item-frame wall must be horizontal");
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
        return switch (facing) {
            case NORTH -> new ScanCoord(pos.getZ(), pos.getX(), pos.getY());
            case SOUTH -> new ScanCoord(pos.getZ(), -pos.getX(), pos.getY());
            case EAST -> new ScanCoord(pos.getX(), pos.getZ(), pos.getY());
            case WEST -> new ScanCoord(pos.getX(), -pos.getZ(), pos.getY());
            default -> throw new IllegalArgumentException("Item-frame wall must be horizontal");
        };
    }

    public static MapFrameCorner scanCorner(BlockPos pos, Direction facing) {
        ScanCoord coord = fromScanBlockPos(pos, facing);
        return new MapFrameCorner(facing, coord.plane(), coord.x(), coord.y());
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
