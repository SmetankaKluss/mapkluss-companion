package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MapArtLayoutSolver {
    private static final int MAP_SIZE = 128;
    private static final int MAX_UNKNOWN_TILES = 54;
    private static final int BEAM_WIDTH = 160;
    private static final double MIN_RELIABLE_QUALITY = 0.74;

    private MapArtLayoutSolver() {
    }

    public static Layout solve(List<Tile> input, String bottomLeftHash) {
        return solve(input, bottomLeftHash, null, null);
    }

    public static Layout solveByMapId(List<Tile> input, Integer bottomLeftMapId) {
        return solve(input, null, bottomLeftMapId, null);
    }

    public static Layout solveWithDimensions(List<Tile> input, int wide, int tall) {
        return solve(input, null, null, new Dimensions(wide, tall));
    }

    public static Layout solveByMapIdWithDimensions(
        List<Tile> input,
        Integer bottomLeftMapId,
        int wide,
        int tall
    ) {
        return solve(input, null, bottomLeftMapId, new Dimensions(wide, tall));
    }

    private static Layout solve(
        List<Tile> input,
        String bottomLeftHash,
        Integer bottomLeftMapId,
        Dimensions requiredDimensions
    ) {
        List<Tile> tiles = List.copyOf(Objects.requireNonNull(input, "input"));
        if (tiles.isEmpty()) throw new IllegalArgumentException("At least one map tile is required.");
        if (tiles.size() > MAX_UNKNOWN_TILES) {
            throw new IllegalArgumentException("Unknown layouts are limited to " + MAX_UNKNOWN_TILES + " maps.");
        }
        if (requiredDimensions != null && requiredDimensions.cellCount() != tiles.size()) {
            throw new IllegalArgumentException(
                "Expected " + requiredDimensions.cellCount() + " maps for "
                    + requiredDimensions.wide() + "x" + requiredDimensions.tall() + ", got " + tiles.size() + "."
            );
        }
        for (Tile tile : tiles) tile.validate();
        if (tiles.size() == 1) {
            return new Layout(
                1,
                1,
                List.of(tiles.getFirst().hash()),
                List.of(tiles.getFirst().mapId()),
                1.0,
                true
            );
        }

        double[][] right = compatibility(tiles, true);
        double[][] down = compatibility(tiles, false);
        List<SolvedCandidate> solved = new ArrayList<>();
        int count = tiles.size();
        for (int wide = 1; wide <= count; wide++) {
            if (count % wide != 0) continue;
            int tall = count / wide;
            if (requiredDimensions != null
                && (requiredDimensions.wide() != wide || requiredDimensions.tall() != tall)) continue;
            SolvedCandidate candidate = solveDimensions(tiles, right, down, wide, tall, bottomLeftHash, bottomLeftMapId);
            if (candidate != null) solved.add(candidate);
        }
        if (solved.isEmpty()) throw new IllegalArgumentException("Could not resolve the map layout.");
        solved.sort(Comparator.comparingDouble(SolvedCandidate::quality).reversed());
        SolvedCandidate best = solved.getFirst();
        double dimensionMargin = solved.size() == 1 ? 1.0 : best.quality() - solved.get(1).quality();
        boolean reliable = best.quality() >= MIN_RELIABLE_QUALITY && dimensionMargin >= 0.008;
        List<String> hashes = new ArrayList<>(count);
        List<Integer> mapIds = new ArrayList<>(count);
        for (int tileIndex : best.order()) {
            hashes.add(tiles.get(tileIndex).hash());
            mapIds.add(tiles.get(tileIndex).mapId());
        }
        return new Layout(best.wide(), best.tall(), hashes, mapIds, best.quality(), reliable);
    }

    private record Dimensions(int wide, int tall) {
        private Dimensions {
            if (wide <= 0 || tall <= 0) throw new IllegalArgumentException("Layout dimensions must be positive.");
        }

        private int cellCount() {
            try {
                return Math.multiplyExact(wide, tall);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Layout dimensions are too large.", overflow);
            }
        }
    }

    private static SolvedCandidate solveDimensions(
        List<Tile> tiles,
        double[][] right,
        double[][] down,
        int wide,
        int tall,
        String bottomLeftHash,
        Integer bottomLeftMapId
    ) {
        int count = tiles.size();
        int anchorPosition = (tall - 1) * wide;
        int anchorIndex = -1;
        if (bottomLeftMapId != null || (bottomLeftHash != null && !bottomLeftHash.isBlank())) {
            for (int index = 0; index < count; index++) {
                Tile tile = tiles.get(index);
                if ((bottomLeftMapId != null && bottomLeftMapId == tile.mapId())
                    || (bottomLeftMapId == null && bottomLeftHash.equals(tile.hash()))) {
                    anchorIndex = index;
                    break;
                }
            }
            if (anchorIndex < 0) return null;
        }

        List<BeamState> beam = List.of(BeamState.empty(count));
        for (int position = 0; position < count; position++) {
            List<BeamState> next = new ArrayList<>();
            for (BeamState state : beam) {
                if (position == anchorPosition && anchorIndex >= 0) {
                    if (!state.used().get(anchorIndex)) {
                        next.add(append(state, tiles, right, down, wide, position, anchorIndex));
                    }
                    continue;
                }
                for (int tileIndex = 0; tileIndex < count; tileIndex++) {
                    if (state.used().get(tileIndex) || tileIndex == anchorIndex) continue;
                    next.add(append(state, tiles, right, down, wide, position, tileIndex));
                }
            }
            if (next.isEmpty()) return null;
            next.sort(Comparator.comparingDouble(BeamState::rankingScore).reversed());
            if (next.size() > BEAM_WIDTH) next = new ArrayList<>(next.subList(0, BEAM_WIDTH));
            beam = next;
        }

        BeamState best = beam.getFirst();
        int edges = horizontalEdges(wide, tall) + verticalEdges(wide, tall);
        double average = edges == 0 ? 1.0 : best.visualScore() / edges;
        double variance = edges == 0 ? 0.0 : Math.max(0.0, best.visualSquareScore() / edges - average * average);
        double quality = clamp01(average - Math.sqrt(variance) * 0.12);
        return new SolvedCandidate(wide, tall, best.order(), quality);
    }

    private static BeamState append(
        BeamState state,
        List<Tile> tiles,
        double[][] right,
        double[][] down,
        int wide,
        int position,
        int tileIndex
    ) {
        int[] order = state.order().clone();
        order[position] = tileIndex;
        BitSet used = (BitSet) state.used().clone();
        used.set(tileIndex);
        double addedVisual = 0.0;
        double addedVisualSquares = 0.0;
        double addedRanking = 0.0;
        if (position % wide != 0) {
            int left = order[position - 1];
            double visual = right[left][tileIndex];
            addedVisual += visual;
            addedVisualSquares += visual * visual;
            addedRanking += visual + sequentialBonus(tiles.get(left), tiles.get(tileIndex), 1);
        }
        if (position >= wide) {
            int top = order[position - wide];
            double visual = down[top][tileIndex];
            addedVisual += visual;
            addedVisualSquares += visual * visual;
            addedRanking += visual + sequentialBonus(tiles.get(top), tiles.get(tileIndex), wide);
        }
        return new BeamState(
            order,
            used,
            position + 1,
            state.visualScore() + addedVisual,
            state.visualSquareScore() + addedVisualSquares,
            state.rankingScore() + addedRanking
        );
    }

    private static double sequentialBonus(Tile first, Tile second, int delta) {
        return second.mapId() == first.mapId() + delta ? 0.025 : 0.0;
    }

    private static double[][] compatibility(List<Tile> tiles, boolean horizontal) {
        int count = tiles.size();
        double[][] values = new double[count][count];
        for (int first = 0; first < count; first++) {
            for (int second = 0; second < count; second++) {
                if (first == second) continue;
                values[first][second] = horizontal
                    ? horizontalScore(tiles.get(first).argb, tiles.get(second).argb)
                    : verticalScore(tiles.get(first).argb, tiles.get(second).argb);
            }
        }
        return values;
    }

    private static double horizontalScore(int[] left, int[] right) {
        double score = 0.0;
        for (int y = 0; y < MAP_SIZE; y++) {
            score += pixelSimilarity(left[y * MAP_SIZE + MAP_SIZE - 1], right[y * MAP_SIZE]);
        }
        return score / MAP_SIZE;
    }

    private static double verticalScore(int[] top, int[] bottom) {
        double score = 0.0;
        int topRow = (MAP_SIZE - 1) * MAP_SIZE;
        for (int x = 0; x < MAP_SIZE; x++) {
            score += pixelSimilarity(top[topRow + x], bottom[x]);
        }
        return score / MAP_SIZE;
    }

    private static double pixelSimilarity(int first, int second) {
        int dr = Math.abs((first >> 16 & 255) - (second >> 16 & 255));
        int dg = Math.abs((first >> 8 & 255) - (second >> 8 & 255));
        int db = Math.abs((first & 255) - (second & 255));
        double similarity = 1.0 - (dr + dg + db) / 765.0;
        return first == second ? Math.min(1.0, similarity + 0.08) : similarity;
    }

    private static int horizontalEdges(int wide, int tall) {
        return Math.max(0, wide - 1) * tall;
    }

    private static int verticalEdges(int wide, int tall) {
        return wide * Math.max(0, tall - 1);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Tile(int mapId, String hash, int[] argb) {
        public Tile {
            hash = Objects.requireNonNull(hash, "hash");
            argb = Objects.requireNonNull(argb, "argb").clone();
        }

        @Override
        public int[] argb() {
            return argb.clone();
        }

        private void validate() {
            if (!MapColorFingerprint.isValid(hash)) throw new IllegalArgumentException("Tile hash is invalid.");
            if (argb.length != MAP_SIZE * MAP_SIZE) throw new IllegalArgumentException("Map tile must be 128x128.");
        }
    }

    public record Layout(
        int wide,
        int tall,
        List<String> tileHashes,
        List<Integer> tileMapIds,
        double quality,
        boolean reliable
    ) {
        public Layout {
            tileHashes = List.copyOf(tileHashes);
            tileMapIds = List.copyOf(tileMapIds);
        }
    }

    private record SolvedCandidate(int wide, int tall, int[] order, double quality) {
    }

    private record BeamState(
        int[] order,
        BitSet used,
        int filled,
        double visualScore,
        double visualSquareScore,
        double rankingScore
    ) {
        private static BeamState empty(int count) {
            int[] order = new int[count];
            java.util.Arrays.fill(order, -1);
            return new BeamState(order, new BitSet(count), 0, 0.0, 0.0, 0.0);
        }
    }
}
