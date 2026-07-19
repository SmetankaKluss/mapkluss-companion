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
