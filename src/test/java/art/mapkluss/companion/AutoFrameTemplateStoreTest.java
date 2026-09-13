package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    @Test
    void legacyGuessesAreIgnoredButRetainedOnDisk() throws Exception {
        Path path = tempDirectory.resolve("legacy.json");
        var store = AutoFrameTemplateStore.load(path);
        store.upsert(template("local-old-guess", "local-old-guess", "A".repeat(64)));
        store.upsert(template("cloud-art", "cloud-v1", "B".repeat(64)));
        store.save();
        var loaded = AutoFrameTemplateStore.load(path);
        assertEquals(1, loaded.templates().size());
        assertTrue(loaded.find("local-old-guess", "local-old-guess").isEmpty());
        assertTrue(Files.readString(path).contains("local-old-guess"));
    }

    @Test
    void rerunReplacesPartialGuessesWithoutChangingCloudOrAnotherWorld() throws Exception {
        var store = AutoFrameTemplateStore.load(tempDirectory.resolve("groups.json"));
        var art = MapArtGroupSolverTest.mosaic(2, 2, 8);
        var partial = MapArtGroupSolver.solve(art.subList(0, 2)).getFirst();
        var full = MapArtGroupSolver.solve(art).getFirst();
        var old = AutoFrameInference.template(partial, "world-one");
        var otherWorld = AutoFrameInference.template(partial, "world-two");
        var cloud = template("cloud", "v1", "A".repeat(64));
        store.upsert(old); store.upsert(otherWorld); store.upsert(cloud); store.setActive(old);
        var replacement = AutoFrameInference.template(full, "world-one");
        store.replaceInferred("world-one", Set.copyOf(full.tileMapIds()), List.of(replacement));
        store.save();
        var loaded = AutoFrameTemplateStore.load(tempDirectory.resolve("groups.json"));
        assertEquals(Set.of(otherWorld, cloud, replacement), Set.copyOf(loaded.templates()));
        assertTrue(loaded.activeTemplate().isEmpty());
        assertEquals(Set.of(cloud, replacement), Set.copyOf(loaded.templates("world-one")));
        loaded.replaceInferred("world-one", Set.copyOf(partial.tileMapIds()), List.of(old));
        assertEquals(Set.of(otherWorld, cloud, replacement), Set.copyOf(loaded.templates()));
    }
}
