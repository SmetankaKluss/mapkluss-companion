package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionBundleInstallerTest {
    @TempDir Path tempDir;

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
