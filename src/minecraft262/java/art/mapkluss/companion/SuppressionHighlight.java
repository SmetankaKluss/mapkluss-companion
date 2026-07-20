package art.mapkluss.companion;

import net.minecraft.core.BlockPos;

public record SuppressionHighlight(BlockPos blockPos, Kind kind) {
    public enum Kind { REMOVE, STAND, ANCHOR, FOOTPRINT }
}
