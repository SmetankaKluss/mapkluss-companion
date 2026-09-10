package art.mapkluss.companion;

import java.util.List;
import java.util.Set;

public final class LensStateLogic {
    private LensStateLogic() {
    }

    public static boolean acceptsRevision(long currentRevision, long responseRevision) {
        return responseRevision > currentRevision;
    }

    public static boolean needsPreviewDownload(boolean responseChanged, boolean atlasReady, long sessionRevision) {
        return sessionRevision > 0 && (responseChanged || !atlasReady);
    }

    public static double squaredDistanceToBounds(
        double x, double y, double z,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {
        double dx = x < minX ? minX - x : Math.max(0, x - maxX);
        double dy = y < minY ? minY - y : Math.max(0, y - maxY);
        double dz = z < minZ ? minZ - z : Math.max(0, z - maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    public static List<LensDtos.Placement> visiblePlacements(
        List<LensDtos.Placement> placements,
        Set<String> hiddenPlacementIds,
        Set<String> blockedOwnerKeys
    ) {
        if (placements == null) return List.of();
        return placements.stream()
            .filter(placement -> placement != null && placement.placementId() != null)
            .filter(placement -> !hiddenPlacementIds.contains(placement.placementId()))
            .filter(placement -> placement.ownerKey() == null || !blockedOwnerKeys.contains(placement.ownerKey()))
            .toList();
    }
}
