package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionBundleInstallerTest {
    @TempDir Path tempDir;

    @Test void selectedTileKeepsTheFullRecoveryCatalog() throws Exception {
        var catalog=SuppressionBundleReader.readCatalog(SuppressionTestFixtures.multiZipBytes(),"fixture");
        var installed=SuppressionBundleInstaller.installCatalog(tempDir,catalog,1);
        org.junit.jupiter.api.Assertions.assertEquals(1,installed.trackerSource().tile());
        try(var loaded=LiveBuildSourceCache.forRunDir(tempDir).load(installed.trackerSource().reference())) {
            org.junit.jupiter.api.Assertions.assertEquals(2,loaded.bundle().tileCount());
            installed.trackerSource().validate(loaded,catalog.tiles().get(1).bundle());
            assertThrows(IllegalArgumentException.class,()->new LiveBuildCatalogLink("e".repeat(64),1)
                .validate(loaded,catalog.tiles().get(1).bundle()));
        }
        assertThrows(java.io.IOException.class,()->SuppressionBundleInstaller.installCatalog(tempDir,catalog,2));
    }

    @Test
    void installsOnlyTheCurrentArtOnlyPlanContract() throws Exception {
        byte[] litematic = SuppressionTestFixtures.litematicV3Bytes();
        byte[] plan = SuppressionTestFixtures.planV3Bytes(litematic);
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(plan);
        SuppressionBundle bundle = bundle(parsed, plan, litematic);

        SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.install(tempDir, bundle);

        assertTrue(Files.isRegularFile(installed.planPath()));
        assertTrue(Files.isRegularFile(installed.schematicPath()));
    }

    @Test
    void rejectsLegacyPlansBeforeCreatingManagedFolders() throws Exception {
        byte[] litematic = SuppressionTestFixtures.litematicBytes();
        byte[] plan = SuppressionTestFixtures.planBytes(litematic);
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(plan);

        assertThrows(java.io.IOException.class,
            () -> SuppressionBundleInstaller.install(tempDir, bundle(parsed, plan, litematic)));
        assertFalse(Files.exists(tempDir.resolve("config/mapkluss-companion/suppression")));
        assertFalse(Files.exists(tempDir.resolve("schematics")));
    }

    @Test
    void rejectsInvalidSourceBeforeCreatingManagedFolders() throws Exception {
        byte[] litematic = SuppressionTestFixtures.litematicV3Bytes();
        byte[] corrupted = litematic.clone();
        corrupted[corrupted.length - 1] ^= 1;
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planV3Bytes(litematic), StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.getAsJsonObject("litematic").addProperty("sha256", SuppressionHashes.sha256(corrupted));
        byte[] plan = root.toString().getBytes(StandardCharsets.UTF_8);
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(plan);

        assertThrows(java.io.IOException.class,
            () -> SuppressionBundleInstaller.install(tempDir, bundle(parsed, plan, corrupted)));
        assertFalse(Files.exists(tempDir.resolve("config/mapkluss-companion/suppression")));
        assertFalse(Files.exists(tempDir.resolve("schematics")));
    }

    private static SuppressionBundle bundle(
        SuppressionPlanParser.Parsed parsed,
        byte[] plan,
        byte[] litematic
    ) throws Exception {
        return new SuppressionBundle(
            parsed,
            plan,
            litematic,
            SuppressionHashes.sha256(plan),
            SuppressionHashes.sha256(litematic),
            null,
            null,
            "Fixture",
            "test"
        );
    }
}
