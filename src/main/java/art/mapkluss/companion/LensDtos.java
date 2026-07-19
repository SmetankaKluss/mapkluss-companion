package art.mapkluss.companion;

import java.util.List;

public final class LensDtos {
    public static final int API_VERSION = 1;

    private LensDtos() {
    }

    public record Grid(int wide, int tall) {
        public Grid {
            if (wide < 1 || wide > 100 || tall < 1 || tall > 100) {
                throw new IllegalArgumentException("Lens grid must be between 1x1 and 100x100");
            }
        }
    }

    public record Realtime(String websocketUrl, String apiKey, String topic) {
        public boolean usable() {
            return websocketUrl != null && !websocketUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && topic != null && !topic.isBlank();
        }
    }

    public record Anchor(int x, int y, int z) {
    }

    public record Session(
        String sessionId,
        String title,
        String status,
        Grid grid,
        String mapMode,
        long revision,
        int tileResolution,
        int previewWidth,
        int previewHeight,
        int viewerCount,
        String editorLastSeenAt,
        String expiresAt,
        String sessionCode,
        boolean ownedByUser,
        Realtime realtime
    ) {
        public boolean active() {
            return "active".equals(status) || "offline".equals(status);
        }
    }

    public record Placement(
        String placementId,
        String sessionId,
        String ownerKey,
        String title,
        String visibility,
        String serverHash,
        String dimensionId,
        Anchor anchor,
        String facing,
        Grid grid,
        long revision,
        int tileResolution,
        String lastSeenAt,
        Double distanceBlocks,
        boolean ownedByDevice,
        Realtime realtime
    ) {
        public boolean groupPlacement() {
            return "group".equals(visibility);
        }
    }

    public record Limits(
        Integer maxPreviewBytes,
        Integer maxPreviewSide,
        Integer maxPreviewPixels,
        Integer maxGridWide,
        Integer maxGridTall,
        Integer maxPlacements,
        Integer signedUrlSeconds,
        List<Integer> tileResolutions
    ) {
    }

    public record Timing(
        Integer publishDebounceMs,
        Integer publishMinimumIntervalMs,
        Integer editorHeartbeatMs,
        Integer editorOfflineMs,
        Integer sessionExpiresMs,
        Integer placementHeartbeatMs,
        Integer placementOfflineMs,
        Integer recoveryPollMs
    ) {
    }

    public record Capabilities(int apiVersion, boolean enabled, Limits limits, Timing timing) {
    }

    public record SessionList(int apiVersion, List<Session> sessions) {
        public List<Session> safeSessions() {
            return sessions == null ? List.of() : List.copyOf(sessions);
        }
    }

    public record SessionResult(int apiVersion, Session session) {
    }

    public record PollResult(
        int apiVersion,
        boolean changed,
        Session session,
        String signedPreviewUrl,
        List<Placement> placements
    ) {
        public List<Placement> safePlacements() {
            return placements == null ? List.of() : List.copyOf(placements);
        }
    }

    public record PlacementResult(int apiVersion, Placement placement) {
    }

    public record ActionResult(int apiVersion) {
    }
}
