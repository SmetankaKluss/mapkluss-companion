package art.mapkluss.companion;

import net.minecraft.core.BlockPos;

final class SuppressionPlacementGeometry {
    private static final int PLATFORM_CLEARANCE_Y = 1;
    private static final int LEGACY_SUPPORT_TO_PLAN_Y = 1;

    private SuppressionPlacementGeometry() { }

    static BlockPos planOrigin(BlockPos supportAnchor, SuppressionPlan plan) {
        // Litematica adds the region Position to this placement origin. Since
        // the complete Two-layer region starts at local Y=-1, the origin must
        // also compensate for that lower bound. This keeps the lowest block of
        // the whole schematic exactly one block above the captured platform.
        return supportAnchor.above(PLATFORM_CLEARANCE_Y - plan.effectiveStructureBounds().min().y());
    }

    static BlockPos legacyPlanOrigin(BlockPos supportAnchor) {
        return supportAnchor.above(LEGACY_SUPPORT_TO_PLAN_Y);
    }

    static BlockPos worldPos(BlockPos supportAnchor, SuppressionPlan plan, SuppressionPlan.LocalPos local) {
        return planOrigin(supportAnchor, plan).offset(local.x(), local.y(), local.z());
    }
}
