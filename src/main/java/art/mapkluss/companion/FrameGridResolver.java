package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class FrameGridResolver {
    private FrameGridResolver() {
    }

    public static boolean isCompleteFromOrigin(Collection<Point> occupied, int wide, int tall) {
        validateDimensions(wide, tall);
        Set<Point> cells = Set.copyOf(Objects.requireNonNull(occupied, "occupied"));
        for (int row = 0; row < tall; row++) {
            for (int column = 0; column < wide; column++) {
                if (!cells.contains(new Point(column, row))) return false;
            }
        }
        return true;
    }

    public static DimensionResolution inferFromOrigin(Collection<Point> occupied, int cellCount) {
        if (cellCount <= 0) throw new IllegalArgumentException("Frame-grid cell count must be positive.");
        Set<Point> cells = new HashSet<>(Objects.requireNonNull(occupied, "occupied"));
        if (!cells.contains(Point.ORIGIN)) return new DimensionResolution(Status.MISSING, null);

        List<Dimensions> candidates = new ArrayList<>();
        for (int wide = 1; wide <= cellCount; wide++) {
            if (cellCount % wide != 0) continue;
            int tall = cellCount / wide;
            if (isCompleteFromOrigin(cells, wide, tall)) candidates.add(new Dimensions(wide, tall));
        }
        if (candidates.isEmpty()) return new DimensionResolution(Status.MISSING, null);
        if (candidates.size() > 1) return new DimensionResolution(Status.AMBIGUOUS, null);
        return new DimensionResolution(Status.FOUND, candidates.getFirst());
    }

    private static void validateDimensions(int wide, int tall) {
        if (wide <= 0 || tall <= 0) {
            throw new IllegalArgumentException("Frame-grid dimensions must be positive.");
        }
        try {
            Math.multiplyExact(wide, tall);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Frame-grid dimensions are too large.", overflow);
        }
    }

    public enum Status {
        FOUND,
        MISSING,
        AMBIGUOUS
    }

    public record Point(int column, int row) {
        public static final Point ORIGIN = new Point(0, 0);
    }

    public record Dimensions(int wide, int tall) {
        public Dimensions {
            validateDimensions(wide, tall);
        }

        public int cellCount() {
            return Math.multiplyExact(wide, tall);
        }
    }

    public record DimensionResolution(Status status, Dimensions dimensions) {
        public DimensionResolution {
            Objects.requireNonNull(status, "status");
            if ((status == Status.FOUND) != (dimensions != null)) {
                throw new IllegalArgumentException("Resolved frame-grid dimensions must match the resolution status.");
            }
        }

        public boolean found() {
            return status == Status.FOUND;
        }
    }
}
