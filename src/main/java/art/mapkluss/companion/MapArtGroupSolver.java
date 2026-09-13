package art.mapkluss.companion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Separates a mixed inventory using distinctive reciprocal seams, never slot or map-ID order. */
final class MapArtGroupSolver {
    private static final int SIZE = 128;
    private static final int LIMIT = 90;
    private static final double MAX_COST = 0.12;
    private static final double MARGIN = 0.012;
    private static final int[] DX = {1, 0, -1, 0};
    private static final int[] DY = {0, 1, 0, -1};

    private MapArtGroupSolver() {}

    static List<MapArtLayoutSolver.Layout> solve(List<MapArtLayoutSolver.Tile> input) {
        Map<Integer, MapArtLayoutSolver.Tile> unique = new LinkedHashMap<>();
        for (var tile : input) unique.putIfAbsent(tile.mapId(), tile);
        List<MapArtLayoutSolver.Tile> tiles = unique.values().stream()
            .sorted(Comparator.comparingInt(MapArtLayoutSolver.Tile::mapId)).toList();
        int count = tiles.size();
        if (count == 0 || count > LIMIT) return List.of();
        int[][] pixels = tiles.stream().map(MapArtLayoutSolver.Tile::argb).toArray(int[][]::new);
        for (int[] image : pixels) if (image.length != SIZE * SIZE) return List.of();
        boolean[] ambiguous = new boolean[count];
        int[][] best = new int[count][4];
        double[][] firstCosts = new double[count][4];
        double[][] secondCosts = new double[count][4];
        double[] strongest = new double[count];
        for (int a = 0; a < count; a++) {
            Arrays.fill(best[a], -1);
            strongest[a] = Double.POSITIVE_INFINITY;
            for (int direction = 0; direction < 4; direction++) {
                double first = Double.POSITIVE_INFINITY, second = first;
                int chosen = -1;
                for (int b = 0; b < count; b++) {
                    double cost = a == b ? Double.POSITIVE_INFINITY : seam(pixels[a], pixels[b], direction, 0);
                    if (cost < first) { second = first; first = cost; chosen = b; }
                    else if (cost < second) second = cost;
                }
                best[a][direction] = chosen;
                firstCosts[a][direction] = first;
                secondCosts[a][direction] = second;
                strongest[a] = Math.min(strongest[a], first);
            }
        }
        for (int a = 0; a < count; a++) {
            for (int direction = 0; direction < 4; direction++) {
                int chosen = best[a][direction];
                double first = firstCosts[a][direction];
                best[a][direction] = -1;
                // A weak outer-edge resemblance must not bridge two otherwise coherent artworks.
                double threshold = Math.min(MAX_COST, Math.max(MARGIN, strongest[a] * 2.5));
                if (chosen < 0 || first > threshold) continue;
                // Flat borders and unrelated dark maps can have a low absolute error too.
                double displaced = seam(pixels[a], pixels[chosen], direction, 17);
                boolean distinctive = displaced - first >= MARGIN && first <= displaced * 0.8;
                if (distinctive && secondCosts[a][direction] - first >= MARGIN) best[a][direction] = chosen;
                else ambiguous[a] = true;
            }
        }
        int[][] links = new int[count][4];
        for (int a = 0; a < count; a++) {
            Arrays.fill(links[a], -1);
            for (int d = 0; d < 4; d++) {
                int b = best[a][d];
                if (b >= 0 && best[b][(d + 2) % 4] == a) links[a][d] = b;
                else if (b >= 0) ambiguous[a] = true;
            }
        }
        List<MapArtLayoutSolver.Layout> result = new ArrayList<>();
        boolean[] visited = new boolean[count];
        for (int root = 0; root < count; root++) {
            if (visited[root]) continue;
            Map<Integer, Cell> positions = new LinkedHashMap<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            positions.put(root, new Cell(0, 0)); queue.add(root); visited[root] = true;
            boolean valid = true;
            while (!queue.isEmpty()) {
                int a = queue.removeFirst(); Cell cell = positions.get(a);
                for (int d = 0; d < 4; d++) {
                    int b = links[a][d]; if (b < 0) continue;
                    Cell target = new Cell(cell.x + DX[d], cell.y + DY[d]);
                    Cell previous = positions.putIfAbsent(b, target);
                    if (previous != null && !previous.equals(target)) valid = false;
                    if (previous == null) { visited[b] = true; queue.add(b); }
                }
            }
            if (!valid || positions.keySet().stream().anyMatch(index -> ambiguous[index])) continue;
            int minX = positions.values().stream().mapToInt(Cell::x).min().orElseThrow();
            int minY = positions.values().stream().mapToInt(Cell::y).min().orElseThrow();
            int wide = positions.values().stream().mapToInt(Cell::x).max().orElseThrow() - minX + 1;
            int tall = positions.values().stream().mapToInt(Cell::y).max().orElseThrow() - minY + 1;
            if (wide * tall != positions.size()) continue;
            Map<Cell, Integer> at = new HashMap<>();
            for (var entry : positions.entrySet()) if (at.put(entry.getValue(), entry.getKey()) != null) valid = false;
            List<String> hashes = new ArrayList<>(); List<Integer> ids = new ArrayList<>();
            for (int y = minY; y < minY + tall; y++) for (int x = minX; x < minX + wide; x++) {
                Integer a = at.get(new Cell(x, y));
                if (a == null) { valid = false; continue; }
                for (int d = 0; d < 2; d++) {
                    Integer b = at.get(new Cell(x + DX[d], y + DY[d]));
                    if (b != null && links[a][d] != b) valid = false;
                }
                hashes.add(tiles.get(a).hash()); ids.add(tiles.get(a).mapId());
            }
            if (valid) result.add(new MapArtLayoutSolver.Layout(wide, tall, hashes, ids, 1, true));
        }
        return List.copyOf(result);
    }

    private static double seam(int[] a, int[] b, int direction, int offset) {
        double sum = 0;
        for (int i = 0; i < SIZE; i++) {
            int j = (i + offset) % SIZE;
            int first = switch (direction) {
                case 0 -> a[i * SIZE + SIZE - 1]; case 1 -> a[(SIZE - 1) * SIZE + i];
                case 2 -> a[i * SIZE]; default -> a[i];
            };
            int second = switch (direction) {
                case 0 -> b[j * SIZE]; case 1 -> b[j];
                case 2 -> b[j * SIZE + SIZE - 1]; default -> b[(SIZE - 1) * SIZE + j];
            };
            if ((first >>> 24) == 0 || (second >>> 24) == 0) { sum += 1; continue; }
            sum += (Math.abs((first >> 16 & 255) - (second >> 16 & 255))
                + Math.abs((first >> 8 & 255) - (second >> 8 & 255))
                + Math.abs((first & 255) - (second & 255))) / 765.0;
        }
        return sum / SIZE;
    }

    private record Cell(int x, int y) {}
}
