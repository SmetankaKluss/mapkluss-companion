package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionSyncServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void removesAllInstalledLitematicsForArtOnly() throws Exception {
        Path schematics = tempDir.resolve("schematics");
        Files.createDirectories(schematics);
        Path oldFile = schematics.resolve("old.litematic");
        Path renamedFile = schematics.resolve("renamed.litematic");
        Path otherFile = schematics.resolve("other.litematic");
        Files.writeString(oldFile, "old");
        Files.writeString(renamedFile, "renamed");
        Files.writeString(otherFile, "other");

        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed.json"));
        index.upsert(new InstalledArtifact("art", "artifact-old", "sha-old", oldFile.toString(), oldFile.getFileName().toString(), 1L));
        index.upsert(new InstalledArtifact("art", "artifact-renamed", "sha-renamed", renamedFile.toString(), renamedFile.getFileName().toString(), 2L));
        index.upsert(new InstalledArtifact("other-art", "artifact-other", "sha-other", otherFile.toString(), otherFile.getFileName().toString(), 3L));

        CompanionApiClient api = new CompanionApiClient("http://127.0.0.1", "anon");
        CompanionSyncService service = new CompanionSyncService(api, tempDir, index, schematics);

        assertTrue(service.removeLitematicsForArt("art"));

        assertFalse(Files.exists(oldFile));
        assertFalse(Files.exists(renamedFile));
        assertTrue(Files.exists(otherFile));
        assertEquals(0, index.findByArt("art").size());
        assertEquals(1, index.findByArt("other-art").size());
    }

    @Test
    void removeLitematicsForArtReturnsFalseWhenNothingInstalled() throws Exception {
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("empty-installed.json"));
        CompanionApiClient api = new CompanionApiClient("http://127.0.0.1", "anon");
        CompanionSyncService service = new CompanionSyncService(api, tempDir, index, tempDir.resolve("schematics"));

        assertFalse(service.removeLitematicsForArt("missing-art"));
    }
}
