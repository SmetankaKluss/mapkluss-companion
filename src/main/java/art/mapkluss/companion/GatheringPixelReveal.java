package art.mapkluss.companion;

import java.util.*;

/** Colour accounting for old preview-only sessions, not evidence of world placement. */
final class GatheringPixelReveal {
    private final int[] original;
    private final int[] group;
    private final long[] order;
    private final Map<Integer, Integer> sizes = new HashMap<>();

    GatheringPixelReveal(int[] pixels, Map<String, Integer> colours) {
        original = pixels;
        group = new int[pixels.length];
        Arrays.fill(group, -1);
        order = new long[pixels.length];
        var shades = new TreeMap<Integer, Integer>();
        for (int base : new TreeSet<>(colours.values())) {
            for (int shade : new int[]{180, 220, 255, 135}) {
                int rgb = (((base >> 16) & 255) * shade / 255 << 16)
                    | (((base >> 8) & 255) * shade / 255 << 8) | ((base & 255) * shade / 255);
                shades.putIfAbsent(rgb, base);
            }
        }
        var resolved = new HashMap<Integer, Integer>();
        for (int i = 0; i < pixels.length; i++) {
            int hash = i;
            hash = (hash ^ (hash >>> 16)) * 0x7feb352d;
            hash = (hash ^ (hash >>> 15)) * 0x846ca68b;
            hash ^= hash >>> 16;
            order[i] = ((long) hash << 32) | (i & 0xffffffffL);
            if ((pixels[i] >>> 24) == 0 || shades.isEmpty()) continue;
            int rgb = pixels[i] & 0xffffff;
            // Legacy Cloud previews are rescaled thumbnails, so their RGB may be interpolated.
            int owner = resolved.computeIfAbsent(rgb, colour -> {
                Integer exact = shades.get(colour);
                if (exact != null) return exact;
                long best = Long.MAX_VALUE;
                int selected = -1;
                for (var entry : shades.entrySet()) {
                    int candidate = entry.getKey();
                    int r = (colour >> 16) - (candidate >> 16);
                    int g = ((colour >> 8) & 255) - ((candidate >> 8) & 255);
                    int b = (colour & 255) - (candidate & 255);
                    long distance = 3L*r*r + 6L*g*g + 2L*b*b;
                    if (distance < best) { best = distance; selected = entry.getValue(); }
                }
                return selected;
            });
            group[i] = owner;
            sizes.merge(owner, 1, Integer::sum);
        }
        Arrays.sort(order);
    }

    int[] render(BuildSessionState session, Map<String, Integer> colours) {
        var totals = new HashMap<Integer, long[]>();
        if (session.materials() != null) for (var material : session.materials()) {
            Integer colour = colours.get(material.nbtName());
            if (colour == null) continue;
            long required = Math.max(0, material.count());
            long gathered = session.gathered() == null ? 0 : session.gathered().getOrDefault(material.nbtName(), 0);
            var count = totals.computeIfAbsent(colour, ignored -> new long[2]);
            count[0] += required;
            count[1] += Math.max(0, Math.min(required, gathered));
        }
        var remaining = new HashMap<Integer, Integer>();
        for (var entry : totals.entrySet()) {
            long[] count = entry.getValue();
            remaining.put(entry.getKey(), count[0] == 0 ? 0
                : (int) (sizes.getOrDefault(entry.getKey(), 0) * count[1] / count[0]));
        }
        int[] output = new int[original.length];
        for (long ranked : order) {
            int i = (int) ranked, pixel = original[i];
            int left = remaining.getOrDefault(group[i], 0);
            if (left > 0 || (pixel >>> 24) == 0) {
                output[i] = pixel;
                if (left > 0) remaining.put(group[i], left - 1);
            } else {
                int grey = (54*((pixel >>> 16)&255) + 183*((pixel >>> 8)&255) + 19*(pixel&255) + 128) >> 8;
                output[i] = (pixel & 0xff000000) | grey * 0x010101;
            }
        }
        return output;
    }
}
