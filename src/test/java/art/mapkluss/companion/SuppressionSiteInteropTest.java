package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
final class SuppressionSiteInteropTest {
    @Test
    void readsTheActualSiteGeneratedV3BundlesForLegacyPublishedTargets() throws Exception {
        Path fixtureDir = fixtureDirectory();

        for (String minecraftVersion : new String[] {"1.21.11", "1.21.8"}) {
            Path zip = fixtureDir.resolve("site-v3-" + minecraftVersion + ".zip");
            SuppressionBundle bundle = SuppressionBundleReader.read(zip);
            SuppressionPlan plan = bundle.parsed().plan();

            assertEquals(3, plan.version());
            assertEquals(minecraftVersion, plan.target().minecraftVersion());
            assertEquals(127, plan.effectiveStructureBounds().max().x());
            assertEquals(253, plan.effectiveWorkflowBounds().max().x());
            assertEquals(null, plan.canvas());
            assertEquals(plan.litematic().sha256(), bundle.litematicSha256());
            assertTrue(plan.phases().size() == 64);
            assertFalse(plan.palette().stream().anyMatch(entry -> entry.roles().contains("marker")));
        }
    }

    @Test
    void readsEveryTileFromTheActualSiteGeneratedMultiMapBundle() throws Exception {
        Path fixtureDir = fixtureDirectory();

        SuppressionBundleCatalog catalog = SuppressionBundleReader.readCatalog(
            fixtureDir.resolve("site-v2-multimap-2x2.zip"));

        assertEquals(2, catalog.gridWide());
        assertEquals(2, catalog.gridTall());
        assertEquals(4, catalog.tiles().size());
        for (int offset = 0; offset < catalog.tiles().size(); offset++) {
            SuppressionBundleCatalog.Tile tile = catalog.tiles().get(offset);
            assertEquals(offset + 1, tile.index());
            assertEquals(offset % 2, tile.column());
            assertEquals(offset / 2, tile.row());
            assertEquals(3, tile.bundle().parsed().plan().version());
            assertEquals("1.21.11", tile.bundle().parsed().plan().target().minecraftVersion());
        }
    }

    private static Path fixtureDirectory() {
        String override = System.getenv("MAPKLUSS_SUPPRESSION_INTEROP_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of("src", "test", "resources", "interop");
    }
}
