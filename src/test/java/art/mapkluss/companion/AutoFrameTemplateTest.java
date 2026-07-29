package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AutoFrameTemplateTest {
    @Test
    void mapsBottomBasedCoordinatesIntoTopDownRowMajorHashes() {
        List<String> hashes = hashes(6);
        AutoFrameTemplate template = template(3, 2, hashes);

        assertEquals(hashes.get(3), template.bottomLeftHash());
        assertEquals(hashes.get(3), template.hashAt(0, 0));
        assertEquals(hashes.get(5), template.hashAt(2, 0));
        assertEquals(hashes.get(0), template.hashAt(0, 1));
        assertEquals(hashes.get(2), template.hashAt(2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> template.hashAt(3, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> template.hashAt(0, -1));
    }

    @Test
    void numbersRectangularMosaicsLeftToRightThenTopToBottom() {
        AutoFrameTemplate threeByFour = template(3, 4, hashes(12));
        assertEquals(0, threeByFour.rowMajorIndex(0, 3));
        assertEquals(2, threeByFour.rowMajorIndex(2, 3));
        assertEquals(3, threeByFour.rowMajorIndex(0, 2));
        assertEquals(11, threeByFour.rowMajorIndex(2, 0));

        AutoFrameTemplate fourByThree = template(4, 3, hashes(12));
        assertEquals(0, fourByThree.rowMajorIndex(0, 2));
        assertEquals(3, fourByThree.rowMajorIndex(3, 2));
        assertEquals(4, fourByThree.rowMajorIndex(0, 1));
        assertEquals(11, fourByThree.rowMajorIndex(3, 0));
    }

    @Test
    void isDefensivelyImmutableAndValidatesShapeAndHashes() {
        ArrayList<String> hashes = new ArrayList<>(hashes(1));
        AutoFrameTemplate template = template(1, 1, hashes);
        hashes.clear();

        assertEquals(1, template.tileHashes().size());
        assertThrows(UnsupportedOperationException.class, () -> template.tileHashes().add("A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> template(2, 1, hashes(1)));
        assertThrows(IllegalArgumentException.class, () -> template(1, 1, List.of("a".repeat(64))));
        assertThrows(IllegalArgumentException.class, () -> template(0, 1, List.of()));
    }

    @Test
    void keepsOptionalExactMapIdsAndRejectsAmbiguousIds() {
        AutoFrameTemplate template = new AutoFrameTemplate(
            "art-1", "version-1", "Title", 2, 1, hashes(2), List.of(80, 81), "2026-07-10T12:00:00Z"
        );

        assertEquals(0, template.tileIndexForMapId(80));
        assertEquals(1, template.tileIndexForMapId(81));
        assertEquals(-1, template.tileIndexForMapId(82));
        assertThrows(IllegalArgumentException.class, () -> new AutoFrameTemplate(
            "art-1", "version-1", "Title", 2, 1, hashes(2), List.of(80, 80), "2026-07-10T12:00:00Z"
        ));
    }

    private static AutoFrameTemplate template(int wide, int tall, List<String> hashes) {
        return new AutoFrameTemplate("art-1", "version-1", "Title", wide, tall, hashes, "2026-07-10T12:00:00Z");
    }

    private static List<String> hashes(int count) {
        ArrayList<String> hashes = new ArrayList<>();
        for (int index = 0; index < count; index++) hashes.add("%064X".formatted(index + 1));
        return hashes;
    }
}
