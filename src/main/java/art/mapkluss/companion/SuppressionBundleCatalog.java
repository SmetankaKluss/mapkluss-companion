package art.mapkluss.companion;

import java.util.List;

public record SuppressionBundleCatalog(
    String artId,
    String versionId,
    String title,
    String source,
    String bundleSha256,
    int gridWide,
    int gridTall,
    List<Tile> tiles
) {
    public SuppressionBundleCatalog {
        tiles = tiles == null ? List.of() : List.copyOf(tiles);
    }

    public boolean multiMap() {
        return tiles.size() > 1;
    }

    public SuppressionBundleCatalog withCloudMetadata(String artId, String versionId, String title, String bundleSha256) {
        int total = tiles.size();
        List<Tile> cloudTiles = tiles.stream().map(tile -> {
            String tileTitle = total > 1
                ? title + " · карта " + tile.index() + "/" + total
                : title;
            SuppressionBundle current = tile.bundle();
            SuppressionBundle bundle = new SuppressionBundle(
                current.parsed(), current.planBytes(), current.litematicBytes(),
                current.planSha256(), current.litematicSha256(),
                artId, versionId, tileTitle, "cloud"
            );
            return new Tile(tile.id(), tile.index(), tile.column(), tile.row(), bundle);
        }).toList();
        return new SuppressionBundleCatalog(
            artId, versionId, title, "cloud", bundleSha256,
            gridWide, gridTall, cloudTiles
        );
    }

    public static SuppressionBundleCatalog single(SuppressionBundle bundle) {
        return new SuppressionBundleCatalog(
            bundle.artId(), bundle.versionId(), bundle.title(), bundle.source(), null,
            1, 1, List.of(new Tile("tile_001", 1, 0, 0, bundle))
        );
    }

    public record Tile(String id, int index, int column, int row, SuppressionBundle bundle) { }
}
