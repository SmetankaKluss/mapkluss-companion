package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapRecognitionButtonLayoutTest {
    @Test
    void placesTheSquareButtonBesideAStandardInventoryWithoutCoveringSlots() {
        MapRecognitionButtonLayout.Position position =
            MapRecognitionButtonLayout.place(320, 240, 72, 37, 176, 166);

        assertEquals(251, position.x());
        assertEquals(37, position.y());
        assertEquals(18, position.width());
        assertEquals(18, position.height());
        assertTrue(position.x() >= 72 + 176);
    }

    @Test
    void keepsTheSquareButtonBesideATallChest() {
        MapRecognitionButtonLayout.Position position =
            MapRecognitionButtonLayout.place(320, 240, 72, 12, 176, 166);

        assertEquals(251, position.x());
        assertEquals(12, position.y());
        assertEquals(18, position.width());
        assertEquals(18, position.height());
    }

    @Test
    void staysInsideACompactScreen() {
        MapRecognitionButtonLayout.Position position =
            MapRecognitionButtonLayout.place(120, 80, 2, 2, 116, 76);

        assertTrue(position.x() >= 4);
        assertTrue(position.y() >= 4);
        assertTrue(position.x() + position.width() <= 116);
        assertTrue(position.y() + position.height() <= 76);
        assertEquals(position.width(), position.height());
    }
}
