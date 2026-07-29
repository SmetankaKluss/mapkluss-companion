package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FrameGridResolverTest {
    @Test
    void infersThreeByFourOnlyFromOccupiedFrames() {
        FrameGridResolver.DimensionResolution resolution = FrameGridResolver.inferFromOrigin(rectangle(3, 4), 12);

        assertTrue(resolution.found());
        assertEquals(new FrameGridResolver.Dimensions(3, 4), resolution.dimensions());
    }

    @Test
    void infersFourByThreeOnlyFromOccupiedFrames() {
        FrameGridResolver.DimensionResolution resolution = FrameGridResolver.inferFromOrigin(rectangle(4, 3), 12);

        assertTrue(resolution.found());
        assertEquals(new FrameGridResolver.Dimensions(4, 3), resolution.dimensions());
    }

    @Test
    void rejectsAPathThatCrossesAFrameGap() {
        List<FrameGridResolver.Point> occupied = new ArrayList<>(rectangle(3, 4));
        occupied.remove(new FrameGridResolver.Point(1, 2));

        assertFalse(FrameGridResolver.isCompleteFromOrigin(occupied, 3, 4));
        assertEquals(
            FrameGridResolver.Status.MISSING,
            FrameGridResolver.inferFromOrigin(occupied, 12).status()
        );
    }

    @Test
    void reportsAmbiguousShapeWhenSeveralFactorRectanglesArePresent() {
        List<FrameGridResolver.Point> occupied = rectangle(4, 4);

        FrameGridResolver.DimensionResolution resolution = FrameGridResolver.inferFromOrigin(occupied, 12);

        assertEquals(FrameGridResolver.Status.AMBIGUOUS, resolution.status());
    }

    private static List<FrameGridResolver.Point> rectangle(int wide, int tall) {
        List<FrameGridResolver.Point> cells = new ArrayList<>();
        for (int row = 0; row < tall; row++) {
            for (int column = 0; column < wide; column++) {
                cells.add(new FrameGridResolver.Point(column, row));
            }
        }
        return cells;
    }
}
