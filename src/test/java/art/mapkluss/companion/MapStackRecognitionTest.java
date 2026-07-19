package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class MapStackRecognitionTest {
    private static final String A = "A".repeat(64);
    private static final String B = "B".repeat(64);
    private static final String C = "C".repeat(64);

    @Test
    void exactMapIdsDisambiguateRepeatedTileImages() {
        AutoFrameTemplate art = template("art", List.of(A, A, B), List.of(10, 11, 12));

        Map<Integer, MapStackRecognition.Match> result = MapStackRecognition.resolve(
            List.of(
                new MapStackRecognition.Observation(11, A),
                new MapStackRecognition.Observation(10, A),
                new MapStackRecognition.Observation(12, B)
            ),
            List.of(art),
            List.of()
        );

        assertEquals(1, result.get(10).tileNumber());
        assertEquals(2, result.get(11).tileNumber());
        assertEquals(3, result.get(12).tileNumber());
    }

    @Test
    void sharedFingerprintDoesNotGuessBetweenArtsButKnownBindingSeedsItsGroup() {
        AutoFrameTemplate first = template("first", List.of(A, B), List.of());
        AutoFrameTemplate second = template("second", List.of(A, C), List.of());
        List<MapStackRecognition.Observation> observations = List.of(
            new MapStackRecognition.Observation(20, A),
            new MapStackRecognition.Observation(21, B),
            new MapStackRecognition.Observation(22, C)
        );

        Map<Integer, MapStackRecognition.Match> ambiguous = MapStackRecognition.resolve(
            List.of(observations.getFirst()), List.of(first, second), List.of()
        );
        assertFalse(ambiguous.containsKey(20));

        Map<Integer, MapStackRecognition.Match> resolved = MapStackRecognition.resolve(
            observations,
            List.of(first, second),
            List.of(new MapStackRecognition.Known(20, "first|first-v1", 0, A))
        );
        assertEquals("first|first-v1", resolved.get(20).groupKey());
        assertEquals("first|first-v1", resolved.get(21).groupKey());
        assertEquals("second|second-v1", resolved.get(22).groupKey());
    }

    @Test
    void staleBindingIsRejectedWhenFingerprintChanged() {
        AutoFrameTemplate art = template("art", List.of(A), List.of());
        Map<Integer, MapStackRecognition.Match> result = MapStackRecognition.resolve(
            List.of(new MapStackRecognition.Observation(7, B)),
            List.of(art),
            List.of(new MapStackRecognition.Known(7, "art|art-v1", 0, A))
        );

        assertFalse(result.containsKey(7));
    }

    private static AutoFrameTemplate template(String id, List<String> hashes, List<Integer> ids) {
        return new AutoFrameTemplate(id, id + "-v1", id, hashes.size(), 1, hashes, ids, "2026-07-19T00:00:00Z");
    }
}
