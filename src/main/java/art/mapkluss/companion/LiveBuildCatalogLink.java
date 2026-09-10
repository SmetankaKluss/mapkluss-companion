package art.mapkluss.companion;

/** Exact catalog and tile, not a plan-hash lookup shared by unrelated artworks. */
public record LiveBuildCatalogLink(String sourceSha256, int tile) {
    public LiveBuildCatalogLink {
        if (sourceSha256 == null || !sourceSha256.matches("[a-f0-9]{64}") || tile < 0 || tile >= 100)
            throw new IllegalArgumentException("Invalid tracker catalog link");
    }
    public LiveBuildSourceCache.Reference reference() {
        return new LiveBuildSourceCache.Reference(LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP, sourceSha256);
    }
    public void validate(LiveBuildSourceCache.Loaded loaded, SuppressionBundle selected) {
        if (!reference().equals(loaded.reference()) || loaded.bundle() == null || tile >= loaded.bundle().tileCount())
            throw new IllegalArgumentException("Tracker catalog changed");
        var actual = loaded.bundle().catalog().tiles().get(tile).bundle();
        if (!actual.planSha256().equals(selected.planSha256())
            || !actual.litematicSha256().equals(selected.litematicSha256()))
            throw new IllegalArgumentException("Tracker tile changed");
    }
}
