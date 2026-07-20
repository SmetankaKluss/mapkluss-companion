package art.mapkluss.companion;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

public record LensRenderSnapshot(
    String placementId,
    String title,
    Direction facing,
    AABB bounds,
    Identifier atlas,
    long revision,
    List<CellBatch> batches,
    int frameCount,
    int cellCount
) {
    public record CellBatch(AABB bounds, List<Cell> cells) {
    }

    public record Cell(BlockPos blockPos, boolean hasFrame, float minU, float minV, float maxU, float maxV) {
    }
}
