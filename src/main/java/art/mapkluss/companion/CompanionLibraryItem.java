package art.mapkluss.companion;

public record CompanionLibraryItem(
    String artId,
    String currentVersionId,
    String title,
    String privacy,
    CompanionManifest.Grid grid,
    String mode,
    String previewUrl,
    String updatedAt,
    boolean isFavorite
) {
    public String gridLabel() {
        return grid == null ? "?x?" : grid.wide() + "x" + grid.tall();
    }

    public String modeLabel() {
        if ("2d".equalsIgnoreCase(mode)) return "2D";
        if ("3d".equalsIgnoreCase(mode)) return "3D";
        return mode == null || mode.isBlank() ? "режим ?" : mode;
    }

    public String privacyLabel() {
        if ("private".equalsIgnoreCase(privacy)) return "приватный";
        if ("unlisted".equalsIgnoreCase(privacy)) return "по ссылке";
        if ("public".equalsIgnoreCase(privacy)) return "публичный";
        return privacy == null || privacy.isBlank() ? "доступ ?" : privacy;
    }

    public CompanionLibraryItem withFavorite(boolean favorite) {
        return new CompanionLibraryItem(
            artId,
            currentVersionId,
            title,
            privacy,
            grid,
            mode,
            previewUrl,
            updatedAt,
            favorite
        );
    }
}
