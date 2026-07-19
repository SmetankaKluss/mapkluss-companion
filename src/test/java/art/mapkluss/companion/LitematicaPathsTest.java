package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LitematicaPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsMissingDependencies() {
        LitematicaStatus status = LitematicaPaths.detectLitematica(tempDir);

        assertFalse(status.ready());
        assertTrue(status.warning().contains("Litematica"));
    }

    @Test
    void detectsLitematicaAndMalilibJars() throws Exception {
        Path mods = tempDir.resolve("mods");
        Files.createDirectories(mods);
        Files.writeString(mods.resolve("litematica-fabric-1.21.11.jar"), "");
        Files.writeString(mods.resolve("malilib-fabric-1.21.11.jar"), "");
        Files.writeString(mods.resolve("other-mod.jar"), "");

        LitematicaStatus status = LitematicaPaths.detectLitematica(tempDir);

        assertTrue(status.litematicaPresent());
        assertTrue(status.malilibPresent());
        assertTrue(status.ready());
    }
}
