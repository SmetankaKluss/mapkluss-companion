package art.mapkluss.companion;

import java.util.List;
import java.util.Map;

public record SuppressionPlan(
    String schema,
    int version,
    String method,
    String direction,
    Target target,
    Axes axes,
    Bounds bounds,
    Bounds structureBounds,
    Bounds workflowBounds,
    Canvas canvas,
    List<PaletteEntry> palette,
    String initialMapBytesB64,
    String targetMapBytesB64,
    Verification verification,
    InitialCapture initialCapture,
    List<Phase> phases,
    Materials materials,
    Litematic litematic
) {
    public SuppressionPlan {
        palette = palette == null ? List.of() : List.copyOf(palette);
        phases = phases == null ? List.of() : List.copyOf(phases);
    }

    public record Target(String minecraftVersion, String dimension, int scale, int width, int height) { }

    public record Axes(String anchor, String east, String south, String up) { }

    public record Bounds(LocalPos min, LocalPos max) { }

    /** Legacy plan-v2 field. Plan v3 and newer intentionally contain no generated canvas. */
    public record Canvas(String schema, int version, String material, Bounds bounds, int standSurfaceY, int blockCount) { }

    public Bounds effectiveStructureBounds() {
        return version == 1 ? bounds : structureBounds;
    }

    public Bounds effectiveWorkflowBounds() {
        return version == 1 ? bounds : workflowBounds;
    }

    public record PaletteEntry(String state, Map<String, String> properties, List<String> roles) {
        public PaletteEntry {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record Verification(
        String initialParity,
        String phaseParity,
        SelectiveRing selectiveRing,
        int minDwellTicks
    ) { }

    public record SelectiveRing(int innerExclusive, int outerExclusive) { }

    public record InitialCapture(List<StandPoint> standPoints) {
        public InitialCapture {
            standPoints = standPoints == null ? List.of() : List.copyOf(standPoints);
        }
    }

    public record Phase(
        String id,
        int index,
        List<Integer> columns,
        String labelRu,
        String labelEn,
        List<RemovalRun> removeRuns,
        List<PixelRun> updatePixelRuns,
        List<StandPoint> standPoints,
        int verifiedTargetPixels
    ) {
        public Phase {
            columns = columns == null ? List.of() : List.copyOf(columns);
            removeRuns = removeRuns == null ? List.of() : List.copyOf(removeRuns);
            updatePixelRuns = updatePixelRuns == null ? List.of() : List.copyOf(updatePixelRuns);
            standPoints = standPoints == null ? List.of() : List.copyOf(standPoints);
        }
    }

    public record RemovalRun(int xStart, int y, int z, int length, int paletteIndex) { }

    public record PixelRun(int xStart, int z, int length) { }

    public record StandPoint(LocalPos standOn, int minTicks) { }

    public record LocalPos(int x, int y, int z) { }

    public record Materials(
        List<MaterialCount> initial,
        List<MaterialCount> recoverable,
        int totalBlocks,
        int recoverableBlocks
    ) {
        public Materials {
            initial = initial == null ? List.of() : List.copyOf(initial);
            recoverable = recoverable == null ? List.of() : List.copyOf(recoverable);
        }
    }

    public record MaterialCount(int paletteIndex, int count) { }

    public record Litematic(String filename, String sha256) { }
}
