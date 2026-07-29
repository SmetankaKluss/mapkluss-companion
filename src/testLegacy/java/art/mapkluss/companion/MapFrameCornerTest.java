package art.mapkluss.companion;

import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapFrameCornerTest {
    @Test
    void requiresSameFacingAndPlane() {
        MapFrameCorner first = new MapFrameCorner(Direction.NORTH, 10, 1, 2);

        assertTrue(first.samePlane(new MapFrameCorner(Direction.NORTH, 10, 4, 5)));
        assertFalse(first.samePlane(new MapFrameCorner(Direction.SOUTH, 10, 4, 5)));
        assertFalse(first.samePlane(new MapFrameCorner(Direction.NORTH, 11, 4, 5)));
    }

    @Test
    void requiresSameFloorOrCeilingViewBasis() {
        MapFrameCorner northUp = new MapFrameCorner(Direction.UP, Direction.NORTH, 64, 1, 2);
        MapFrameCorner eastUp = new MapFrameCorner(Direction.UP, Direction.EAST, 64, 1, 2);
        assertFalse(northUp.samePlane(eastUp));
    }
}
