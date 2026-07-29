package art.mapkluss.companion;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record AutoFramePlacement(
    AutoFrameTemplate template,
    BlockPos leftBottom,
    Direction facing,
    Direction planeUp,
    String worldKey
) {
    public AutoFramePlacement(
        AutoFrameTemplate template,
        BlockPos leftBottom,
        Direction facing,
        String worldKey
    ) {
        this(template, leftBottom, facing, defaultPlaneUp(facing), worldKey);
    }

    public AutoFramePlacement {
        if (template == null) throw new IllegalArgumentException("AutoFrame template is required");
        if (leftBottom == null) throw new IllegalArgumentException("AutoFrame anchor is required");
        if (facing == null) throw new IllegalArgumentException("AutoFrame requires an item-frame plane");
        if (planeUp == null || planeUp.getAxis() == facing.getAxis()) {
            throw new IllegalArgumentException("AutoFrame plane-up direction must lie inside the item-frame plane");
        }
        if (facing.getAxis().isHorizontal() && planeUp != Direction.UP) {
            throw new IllegalArgumentException("Wall AutoFrame plane-up direction must be vertical");
        }
        if (facing.getAxis().isVertical() && !planeUp.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Horizontal AutoFrame plane-up direction must be horizontal");
        }
        worldKey = worldKey == null ? "" : worldKey;
    }

    public Optional<Cell> cellAt(BlockPos attachedBlockPos, Direction frameFacing) {
        if (attachedBlockPos == null || frameFacing != facing) return Optional.empty();
        int deltaX = attachedBlockPos.getX() - leftBottom.getX();
        int deltaY = attachedBlockPos.getY() - leftBottom.getY();
        int deltaZ = attachedBlockPos.getZ() - leftBottom.getZ();
        if (project(deltaX, deltaY, deltaZ, facing) != 0) return Optional.empty();
        int column = project(deltaX, deltaY, deltaZ, rightDirection());
        int rowFromBottom = project(deltaX, deltaY, deltaZ, planeUp);
        if (column < 0 || column >= template.wide() || rowFromBottom < 0 || rowFromBottom >= template.tall()) {
            return Optional.empty();
        }
        return Optional.of(new Cell(
            column,
            rowFromBottom,
            template.rowMajorIndex(column, rowFromBottom),
            template.hashAt(column, rowFromBottom),
            requiredMapRotation()
        ));
    }

    public static PlacementResolution resolveFromFrames(
        AutoFrameTemplate template,
        BlockPos target,
        Direction facing,
        Direction planeUp,
        String worldKey,
        Collection<BlockPos> attachedFramePositions
    ) {
        Set<FrameGridResolver.Point> occupied = relativeCells(
            target,
            facing,
            planeUp,
            attachedFramePositions
        );
        if (!FrameGridResolver.isCompleteFromOrigin(occupied, template.wide(), template.tall())) {
            return new PlacementResolution(FrameGridResolver.Status.MISSING, null);
        }
        return new PlacementResolution(
            FrameGridResolver.Status.FOUND,
            new AutoFramePlacement(template, target, facing, planeUp, worldKey)
        );
    }

    public static FrameGridResolver.DimensionResolution inferDimensionsFromFrames(
        BlockPos target,
        Direction facing,
        Direction planeUp,
        Collection<BlockPos> attachedFramePositions,
        int cellCount
    ) {
        return FrameGridResolver.inferFromOrigin(
            relativeCells(target, facing, planeUp, attachedFramePositions),
            cellCount
        );
    }

    public BlockPos blockAt(int column, int rowFromBottom) {
        if (column < 0 || column >= template.wide() || rowFromBottom < 0 || rowFromBottom >= template.tall()) {
            throw new IllegalArgumentException("AutoFrame cell is outside the template");
        }
        Direction right = rightDirection();
        return leftBottom.offset(
            right.getStepX() * column + planeUp.getStepX() * rowFromBottom,
            right.getStepY() * column + planeUp.getStepY() * rowFromBottom,
            right.getStepZ() * column + planeUp.getStepZ() * rowFromBottom
        );
    }

    static Direction planeUpFromPlayerView(Direction frameFacing, Direction playerFacing) {
        if (frameFacing == null) {
            throw new IllegalArgumentException("AutoFrame requires an item-frame plane");
        }
        if (frameFacing.getAxis().isHorizontal()) return Direction.UP;
        if (playerFacing == null || !playerFacing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Horizontal AutoFrame requires the player's horizontal direction");
        }
        return frameFacing == Direction.DOWN ? playerFacing.getOpposite() : playerFacing;
    }

    private Direction rightDirection() {
        return rightDirection(facing, planeUp);
    }

    static Direction rightDirection(Direction facing, Direction planeUp) {
        if (facing.getAxis().isHorizontal()) return facing.getCounterClockWise();
        return facing == Direction.UP
            ? planeUp.getClockWise()
            : planeUp.getCounterClockWise();
    }

    int requiredMapRotation() {
        if (facing.getAxis().isHorizontal()) return 0;
        if (facing == Direction.UP) {
            return switch (planeUp) {
                case NORTH -> 0;
                case EAST -> 1;
                case SOUTH -> 2;
                case WEST -> 3;
                default -> throw new IllegalStateException("Floor AutoFrame requires a horizontal up direction");
            };
        }
        return switch (planeUp) {
            case SOUTH -> 0;
            case EAST -> 1;
            case NORTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalStateException("Ceiling AutoFrame requires a horizontal up direction");
        };
    }

    private static Set<FrameGridResolver.Point> relativeCells(
        BlockPos target,
        Direction facing,
        Direction planeUp,
        Collection<BlockPos> attachedFramePositions
    ) {
        if (target == null || facing == null || planeUp == null || attachedFramePositions == null) {
            throw new IllegalArgumentException("Frame-grid scan requires an origin, orientation, and frame positions.");
        }
        Direction right = rightDirection(facing, planeUp);
        Set<FrameGridResolver.Point> cells = new HashSet<>();
        for (BlockPos position : attachedFramePositions) {
            if (position == null) continue;
            int deltaX = position.getX() - target.getX();
            int deltaY = position.getY() - target.getY();
            int deltaZ = position.getZ() - target.getZ();
            if (project(deltaX, deltaY, deltaZ, facing) != 0) continue;
            cells.add(new FrameGridResolver.Point(
                project(deltaX, deltaY, deltaZ, right),
                project(deltaX, deltaY, deltaZ, planeUp)
            ));
        }
        return Set.copyOf(cells);
    }

    private static int project(int x, int y, int z, Direction direction) {
        return x * direction.getStepX() + y * direction.getStepY() + z * direction.getStepZ();
    }

    private static Direction defaultPlaneUp(Direction facing) {
        return facing != null && facing.getAxis().isVertical() ? Direction.NORTH : Direction.UP;
    }

    public record Cell(int column, int rowFromBottom, int tileIndex, String expectedHash, int mapRotation) {
    }

    public record PlacementResolution(FrameGridResolver.Status status, AutoFramePlacement placement) {
        public PlacementResolution {
            if (status == null) throw new IllegalArgumentException("AutoFrame placement status is required.");
            if ((status == FrameGridResolver.Status.FOUND) != (placement != null)) {
                throw new IllegalArgumentException("Resolved AutoFrame placement must match its status.");
            }
        }

        public boolean found() {
            return status == FrameGridResolver.Status.FOUND;
        }
    }
}
