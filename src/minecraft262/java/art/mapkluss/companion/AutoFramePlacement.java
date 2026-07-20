package art.mapkluss.companion;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record AutoFramePlacement(
    AutoFrameTemplate template,
    BlockPos leftBottom,
    Direction facing,
    String worldKey
) {
    public AutoFramePlacement {
        if (template == null) throw new IllegalArgumentException("AutoFrame template is required");
        if (leftBottom == null) throw new IllegalArgumentException("AutoFrame anchor is required");
        if (facing == null || facing.getAxis().isVertical()) {
            throw new IllegalArgumentException("AutoFrame requires a vertical item-frame wall");
        }
        worldKey = worldKey == null ? "" : worldKey;
    }

    public Optional<Cell> cellAt(BlockPos attachedBlockPos, Direction frameFacing) {
        if (attachedBlockPos == null || frameFacing != facing) return Optional.empty();
        FrameWallGeometry.Coord anchor = FrameWallGeometry.fromBlockPos(leftBottom, facing);
        FrameWallGeometry.Coord target = FrameWallGeometry.fromBlockPos(attachedBlockPos, facing);
        if (anchor.plane() != target.plane()) return Optional.empty();
        int column = target.x() - anchor.x();
        int rowFromBottom = target.y() - anchor.y();
        if (column < 0 || column >= template.wide() || rowFromBottom < 0 || rowFromBottom >= template.tall()) {
            return Optional.empty();
        }
        return Optional.of(new Cell(
            column,
            rowFromBottom,
            template.rowMajorIndex(column, rowFromBottom),
            template.hashAt(column, rowFromBottom)
        ));
    }

    public record Cell(int column, int rowFromBottom, int tileIndex, String expectedHash) {
    }
}
