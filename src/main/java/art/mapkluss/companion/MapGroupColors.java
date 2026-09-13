package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MapGroupColors {
    private MapGroupColors() {
    }

    static Map<String, Integer> assign(Iterable<String> groupKeys) {
        List<String> keys = new ArrayList<>();
        for (String key : groupKeys) {
            if (key != null && !key.isBlank() && !keys.contains(key)) keys.add(key);
        }
        keys.sort(String::compareTo);
        Map<String, Integer> colors = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (String key : keys) {
            int color = 0;
            int bestDistance = -1;
            int requiredDistance = used.size() < 8 ? 90 * 90 : 35 * 35;
            for (int salt = 0; salt < 64; salt++) {
                int hash = mix(key.hashCode() + salt * 0x9E3779B9);
                double hue = Math.floorMod(hash, 3600) / 10.0;
                double saturation = 0.72 + Math.floorMod(hash >>> 12, 11) / 100.0;
                double lightness = 0.61 + Math.floorMod(hash >>> 20, 10) / 100.0;
                int candidate = 0xFF000000 | hslToRgb(hue, saturation, lightness);
                if (used.contains(candidate)) continue;
                int distance = used.stream().mapToInt(value -> distanceSquared(candidate, value)).min().orElse(Integer.MAX_VALUE);
                if (distance > bestDistance) { bestDistance = distance; color = candidate; }
                if (distance >= requiredDistance) break;
            }
            // A bounded search normally suffices; preserve uniqueness even for an unusually large set.
            while (used.contains(color)) color = 0xFF000000 | ((color + 1) & 0xFFFFFF);
            used.add(color);
            colors.put(key, color);
        }
        return Map.copyOf(colors);
    }

    private static int distanceSquared(int first, int second) {
        int r = (first >>> 16 & 255) - (second >>> 16 & 255);
        int g = (first >>> 8 & 255) - (second >>> 8 & 255);
        int b = (first & 255) - (second & 255);
        return r * r + g * g + b * b;
    }

    private static int mix(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        return value ^ value >>> 16;
    }

    private static int hslToRgb(double hue, double saturation, double lightness) {
        double chroma = (1.0 - Math.abs(2.0 * lightness - 1.0)) * saturation;
        double segment = hue / 60.0;
        double secondary = chroma * (1.0 - Math.abs(segment % 2.0 - 1.0));
        double red;
        double green;
        double blue;
        if (segment < 1.0) {
            red = chroma; green = secondary; blue = 0.0;
        } else if (segment < 2.0) {
            red = secondary; green = chroma; blue = 0.0;
        } else if (segment < 3.0) {
            red = 0.0; green = chroma; blue = secondary;
        } else if (segment < 4.0) {
            red = 0.0; green = secondary; blue = chroma;
        } else if (segment < 5.0) {
            red = secondary; green = 0.0; blue = chroma;
        } else {
            red = chroma; green = 0.0; blue = secondary;
        }
        double match = lightness - chroma / 2.0;
        int r = clamp((int) Math.round((red + match) * 255.0));
        int g = clamp((int) Math.round((green + match) * 255.0));
        int b = clamp((int) Math.round((blue + match) * 255.0));
        return r << 16 | g << 8 | b;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
