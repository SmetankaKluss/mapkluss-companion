package art.mapkluss.companion;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;

public record LensRenderSnapshot(
    String placementId,
    String title,
    Direction facing,
    Box bounds,
    Identifier atlas,
    long revision,
    List<CellBatch> batches,
    int frameCount,
    int cellCount
) {
    public record CellBatch(Box bounds, List<Cell> cells) {
    }

    public record Cell(BlockPos blockPos, boolean hasFrame, float minU, float minV, float maxU, float maxV) {
    }
}
