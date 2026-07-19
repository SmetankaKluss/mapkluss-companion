package art.mapkluss.companion;

import net.minecraft.util.math.BlockPos;

public record SuppressionHighlight(BlockPos blockPos, Kind kind) {
    public enum Kind { REMOVE, STAND, ANCHOR, FOOTPRINT }
}
