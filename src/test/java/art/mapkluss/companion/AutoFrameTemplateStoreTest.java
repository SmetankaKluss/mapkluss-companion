package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoFrameTemplateStoreTest {
    @TempDir
    Path tempDirectory;

    @Test
    void missingStoreStartsEmptyAndPersistsActiveTemplate() throws Exception {
        Path path = tempDirectory.resolve("nested").resolve("autoframe.json");
        AutoFrameTemplateStore store = AutoFrameTemplateStore.load(path);
        assertTrue(store.templates().isEmpty());

        AutoFrameTemplate template = new AutoFrameTemplate(
            "art-1", "v1", "Title", 1, 1, List.of("1".repeat(64)), List.of(42), "2026-07-10T12:00:00Z"
        );
        store.upsert(template);
        store.setActive("art-1", "v1");
        store.save();

        AutoFrameTemplateStore loaded = AutoFrameTemplateStore.load(path);
        assertEquals(List.of(template), loaded.templates());
        assertEquals(template, loaded.activeTemplate().orElseThrow());
        assertEquals("art-1", loaded.activeArtId().orElseThrow());
        assertEquals("v1", loaded.activeVersionId().orElseThrow());
        assertEquals(List.of(42), loaded.activeTemplate().orElseThrow().tileMapIds());
        assertFalse(Files.exists(path.resolveSibling("autoframe.json.tmp")));
    }

    @Test
    void upsertKeepsOnlyLatestVersionPerArt() {
        AutoFrameTemplateStore store;
        try {
            store = AutoFrameTemplateStore.load(tempDirectory.resolve("store.json"));
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
        store.upsert(template("art-1", "v1", "1".repeat(64)));
        store.upsert(template("art-1", "v2", "2".repeat(64)));
        store.upsert(template("art-1", "v1", "3".repeat(64)));

        assertEquals(1, store.templates().size());
        assertEquals("3".repeat(64), store.find("art-1", "v1").orElseThrow().bottomLeftHash());
        assertTrue(store.find("art-1", "v2").isEmpty());
    }

    @Test
    void loadKeepsOnlySaneEntriesAndClearsDanglingActiveIdentity() throws Exception {
        Path path = tempDirectory.resolve("sane.json");
        Files.writeString(path, """
            {
              "activeArtId": "bad-art",
              "activeVersionId": "bad-version",
              "templates": [
                {
                  "artId": "good-art", "versionId": "good-version", "title": "Good",
                  "wide": 1, "tall": 1, "tileHashes": ["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"],
                  "updatedAt": "2026-07-10T12:00:00Z"
                },
                {
                  "artId": "bad-art", "versionId": "bad-version", "title": "Bad",
                  "wide": 2, "tall": 1, "tileHashes": ["lowercase-invalid"],
                  "updatedAt": "2026-07-10T12:00:00Z"
                }
              ]
            }
            """);

        AutoFrameTemplateStore store = AutoFrameTemplateStore.load(path);

        assertEquals(1, store.templates().size());
        assertEquals("good-art", store.templates().getFirst().artId());
        assertTrue(store.templates().getFirst().tileMapIds().isEmpty());
        assertTrue(store.activeTemplate().isEmpty());
        assertTrue(store.activeArtId().isEmpty());
    }

    private static AutoFrameTemplate template(String artId, String versionId, String hash) {
        return new AutoFrameTemplate(
            artId, versionId, "Title", 1, 1, List.of(hash), "2026-07-10T12:00:00Z"
        );
    }
}
