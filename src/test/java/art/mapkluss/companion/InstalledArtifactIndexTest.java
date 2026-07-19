package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InstalledArtifactIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndFindsInstalledArtifact() throws Exception {
        Path indexPath = tempDir.resolve("installed.json");
        InstalledArtifactIndex index = InstalledArtifactIndex.load(indexPath);
        index.upsert(new InstalledArtifact("art", "artifact", "abc", "file.litematic", "file.litematic", 1));
        index.save();

        InstalledArtifactIndex loaded = InstalledArtifactIndex.load(indexPath);

        assertTrue(loaded.findSameArtifact("art", "artifact", "abc").isPresent());
    }

    @Test
    void findsAllEntriesForArt() throws Exception {
        Path indexPath = tempDir.resolve("installed-by-art.json");
        InstalledArtifactIndex index = InstalledArtifactIndex.load(indexPath);
        index.upsert(new InstalledArtifact("art", "artifact-a", "abc", "a.litematic", "a.litematic", 1));
        index.upsert(new InstalledArtifact("art", "artifact-b", "def", "b.litematic", "b.litematic", 2));
        index.upsert(new InstalledArtifact("other", "artifact-c", "ghi", "c.litematic", "c.litematic", 3));

        assertEquals(2, index.findByArt("art").size());
    }
}
