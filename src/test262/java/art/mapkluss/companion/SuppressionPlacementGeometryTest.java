package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;

final class SuppressionPlacementGeometryTest {
    @Test
    void placesTheLowestSchematicBlockOneAboveTheCapturedPlatform() throws Exception {
        BlockPos support = new BlockPos(10, 64, -5);
        byte[] litematic = SuppressionTestFixtures.litematicBytes();
        SuppressionPlan plan = SuppressionPlanParser.parse(SuppressionTestFixtures.planBytes(litematic)).plan();

        assertEquals(new BlockPos(10, 66, -5), SuppressionPlacementGeometry.planOrigin(support, plan));
        assertEquals(
            new BlockPos(10, 65, -6),
            SuppressionPlacementGeometry.worldPos(support, plan, plan.bounds().min())
        );
        assertEquals(
            new BlockPos(137, 68, 35),
            SuppressionPlacementGeometry.worldPos(support, plan, new SuppressionPlan.LocalPos(127, 2, 40))
        );
    }
}
