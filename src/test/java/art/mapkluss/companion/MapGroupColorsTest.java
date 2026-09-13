package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapGroupColorsTest {
    @Test
    void threeArtsHaveVisiblySeparatedColors() {
        for (int sample = 0; sample < 100; sample++) {
            var colors = new ArrayList<>(MapGroupColors.assign(List.of("art-" + sample,
                "other-" + sample, "third-" + sample)).values());
            for (int a = 0; a < colors.size(); a++) for (int b = a + 1; b < colors.size(); b++) {
                int distance = 0;
                for (int shift : new int[]{0, 8, 16}) {
                    int delta = (colors.get(a) >> shift & 255) - (colors.get(b) >> shift & 255);
                    distance += delta * delta;
                }
                assertTrue(distance >= 90 * 90);
            }
        }
    }

    @Test
    void assignsStableNonRepeatingBrightColorsToFullContainer() {
        List<String> groups = new ArrayList<>();
        for (int index = 0; index < 54; index++) groups.add("art-" + index);

        Map<String, Integer> first = MapGroupColors.assign(groups);
        Map<String, Integer> second = MapGroupColors.assign(groups.reversed());

        assertEquals(first, second);
        assertEquals(groups.size(), new HashSet<>(first.values()).size());
        for (int color : first.values()) {
            int red = color >>> 16 & 255;
            int green = color >>> 8 & 255;
            int blue = color & 255;
            assertTrue(Math.max(red, Math.max(green, blue)) >= 170);
        }
    }
}
