package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensStateLogicTest {
    @Test
    void rejectsEqualAndOlderRevisions() {
        assertFalse(LensStateLogic.acceptsRevision(7, 7));
        assertFalse(LensStateLogic.acceptsRevision(7, 6));
        assertTrue(LensStateLogic.acceptsRevision(7, 8));
    }

    @Test
    void appliesHiddenAndOwnerFilters() {
        LensDtos.Grid grid = new LensDtos.Grid(1, 1);
        LensDtos.Anchor anchor = new LensDtos.Anchor(0, 64, 0);
        LensDtos.Placement personal = placement("mine", "owner-a", "personal", grid, anchor);
        LensDtos.Placement group = placement("group", "owner-b", "group", grid, anchor);

        assertEquals(List.of(personal), LensStateLogic.visiblePlacements(
            List.of(personal, group), Set.of("group"), Set.of()
        ));
        assertEquals(List.of(), LensStateLogic.visiblePlacements(
            List.of(personal, group), Set.of("mine"), Set.of("owner-b")
        ));
    }

    @Test
    void measuresDistanceToNearestPointOnLargePlacementBounds() {
        assertEquals(0.0, LensStateLogic.squaredDistanceToBounds(
            5, 5, 5, 0, 0, 0, 10, 10, 10
        ));
        assertEquals(25.0, LensStateLogic.squaredDistanceToBounds(
            15, 5, 5, 0, 0, 0, 10, 10, 10
        ));
        assertEquals(50.0, LensStateLogic.squaredDistanceToBounds(
            15, 15, 5, 0, 0, 0, 10, 10, 10
        ));
    }

    private static LensDtos.Placement placement(
        String id,
        String owner,
        String visibility,
        LensDtos.Grid grid,
        LensDtos.Anchor anchor
    ) {
        return new LensDtos.Placement(id, "session", owner, id, visibility, "server", "dimension",
            anchor, "north", grid, 1, 16, null, 1.0, false, null);
    }
}
