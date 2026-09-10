package art.mapkluss.companion;

final class LensUiPermissions {
    static boolean allowed(String action, LensDtos.Session session, LensDtos.Placement placement, boolean ownsPlacementSession) {
        return switch (action) {
            case "lens.place" -> session != null && session.ownedByUser();
            case "lens.leave" -> session != null && !session.ownedByUser();
            case "lens.open_editor" -> session != null && session.ownedByUser();
            case "lens.hide_placement", "lens.hide_author", "lens.report" -> placement != null && !ownsPlacementSession;
            case "lens.remove_placement" -> placement != null && placement.ownedByDevice();
            default -> true;
        };
    }
}
