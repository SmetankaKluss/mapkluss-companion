package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import net.minecraft.core.BlockPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OptionalLitematicaAdapterTest {
    @Test
    void derivesExactStablePlacementOwnershipFromFullPlanAndRole() {
        String sha = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        BlockPos origin = new BlockPos(-64, 70, 192);
        UUID build = OptionalLitematicaAdapter.placementUuid(sha, "build", origin);
        UUID art = OptionalLitematicaAdapter.placementUuid(sha, "art", origin);
        assertTrue(OptionalLitematicaAdapter.ownsPlacementUuid(art, sha, origin));
        assertFalse(art.equals(build));
        assertFalse(OptionalLitematicaAdapter.ownsPlacementUuid(art, sha.replace('a', 'b'), origin));
        assertEquals(build, OptionalLitematicaAdapter.placementUuid(sha, "build", origin));
        assertTrue(OptionalLitematicaAdapter.ownsPlacementUuid(build, sha, origin));
        assertFalse(OptionalLitematicaAdapter.ownsPlacementUuid(build, sha, origin.east()));
        assertFalse(OptionalLitematicaAdapter.ownsPlacementUuid(
            OptionalLitematicaAdapter.placementUuid(sha, "reference-01", origin), sha.replace('a', 'b'), origin));
    }
}
