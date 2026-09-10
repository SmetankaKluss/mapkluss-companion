package art.mapkluss.companion;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Private recovery packaging. Reuses the production ZIP validator before any cache write. */
final class LiveBuildCatalogSource {
    private LiveBuildCatalogSource() { }
    private record Grid(int wide, int tall) { }
    private record Tile(String id, int index, int column, int row,
                        Map<String, Object> plan, Map<String, Object> litematic) { }

    static byte[] encode(SuppressionBundleCatalog catalog) throws IOException {
        if (catalog == null || catalog.gridWide() < 1 || catalog.gridWide() > 10
            || catalog.gridTall() < 1 || catalog.gridTall() > 10
            || catalog.tiles().size() != catalog.gridWide() * catalog.gridTall())
            throw new IOException("Invalid recovery catalog grid");
        boolean single = catalog.tiles().size() == 1;
        var payloads = new LinkedHashMap<String, byte[]>();
        var files = new ArrayList<Map<String, Object>>();
        var tiles = new ArrayList<Tile>();
        long expanded = 0;
        for (int i = 0; i < catalog.tiles().size(); i++) {
            var tile = catalog.tiles().get(i);
            if (tile.index() != i + 1 || tile.column() != i % catalog.gridWide()
                || tile.row() != i / catalog.gridWide() || tile.id() == null
                || !tile.id().matches("tile_[0-9]{3}")) throw new IOException("Invalid recovery tile order");
            var bundle = tile.bundle();
            byte[] plan = bundle.planBytes(), schematic = bundle.litematicBytes();
            if (plan.length > SuppressionPlanParser.MAX_PLAN_BYTES
                || schematic.length > SuppressionPlanParser.MAX_LITEMATIC_BYTES
                || !SuppressionHashes.sha256(plan).equals(bundle.planSha256())
                || !SuppressionHashes.sha256(schematic).equals(bundle.litematicSha256()))
                throw new IOException("Recovery payload changed");
            expanded += (long) plan.length + schematic.length;
            if (expanded > SuppressionPlanParser.MAX_BUNDLE_EXPANDED_BYTES)
                throw new IOException("Recovery catalog exceeds budget");
            // Read the filename from the actual plan, never trust the parsed-object cache.
            String filename = SuppressionPlanParser.parse(plan).plan().litematic().filename();
            String prefix = single ? "" : tile.id() + "/";
            String planPath = prefix + "tracker_suppression_plan.json";
            String schematicPath = prefix + filename;
            payloads.put(planPath, plan);
            if (payloads.put(schematicPath, schematic) != null) throw new IOException("Duplicate recovery payload");
            var planRef = reference(planPath, plan);
            var schematicRef = reference(schematicPath, schematic);
            files.add(planRef); files.add(schematicRef);
            tiles.add(new Tile(tile.id(), tile.index(), tile.column(), tile.row(), planRef, schematicRef));
        }
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("schema", "mapkluss.suppression-bundle");
        manifest.put("version", single ? 1 : 2);
        manifest.put("title", "Two-layer");
        manifest.put("grid", new Grid(catalog.gridWide(), catalog.gridTall()));
        manifest.put("tileOrder", "row_major_top_left");
        manifest.put("tiles", tiles); manifest.put("files", files);
        payloads.put("SHA256.json", new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8));
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var payload : payloads.entrySet()) {
                var entry = new ZipEntry(payload.getKey()); entry.setTime(0);
                zip.putNextEntry(entry); zip.write(payload.getValue()); zip.closeEntry();
                if (out.size() > SuppressionPlanParser.MAX_BUNDLE_BYTES)
                    throw new IOException("Recovery ZIP exceeds budget");
            }
        }
        if (out.size() > SuppressionPlanParser.MAX_BUNDLE_BYTES) throw new IOException("Recovery ZIP exceeds budget");
        return out.toByteArray();
    }

    private static Map<String, Object> reference(String path, byte[] bytes) throws IOException {
        var ref = new LinkedHashMap<String, Object>();
        ref.put("path", path); ref.put("sizeBytes", bytes.length);
        ref.put("sha256", SuppressionHashes.sha256(bytes));
        return ref;
    }
}
