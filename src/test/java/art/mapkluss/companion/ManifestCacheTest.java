package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ManifestCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void independentlyLoadedCachesMergeDifferentArts() throws Exception {
        Path cachePath = tempDir.resolve("shared-manifest-cache.json");
        ManifestCache first = ManifestCache.load(cachePath);
        ManifestCache second = ManifestCache.load(cachePath);
        first.write("user-1", manifest("art-1", "First"));
        second.write("user-1", manifest("art-2", "Second"));

        ManifestCache merged = ManifestCache.load(cachePath);
        assertEquals("First", merged.read("user-1", "art-1").orElseThrow().manifest().title());
        assertEquals("Second", merged.read("user-1", "art-2").orElseThrow().manifest().title());
    }

    private static CompanionManifest manifest(String id, String title) {
        return new CompanionManifest(
            id, "version-1", "owner-1", title, "unlisted", new CompanionManifest.Grid(1, 1),
            "classic", "1.21.11", "standard", null, false, List.of(), List.of(),
            "2026-07-24T00:00:00Z"
        );
    }

    @Test
    void savesAndReloadsManifest() throws Exception {
        Path cachePath = tempDir.resolve("manifest-cache.json");
        ManifestCache cache = ManifestCache.load(cachePath);
        CompanionManifest manifest = new CompanionManifest(
            "art-1",
            "version-1",
            "owner-1",
            "Castle",
            "unlisted",
            new CompanionManifest.Grid(2, 3),
            "classic",
            "1.21.11",
            "standard",
            null,
            true,
            List.of("collection-1"),
            List.of(new CompanionArtifact(
                "artifact-1",
                "litematic",
                "castle_2x3.litematic",
                "arts/art-1/litematic",
                null,
                "application/octet-stream",
                128L,
                "abc",
                "2026-07-01T12:00:00Z"
            )),
            "2026-07-01T12:00:00Z"
        );

        cache.write("user-1", manifest);
        ManifestCache loaded = ManifestCache.load(cachePath);

        ManifestCache.CachedManifest cached = loaded.read("user-1", "art-1").orElseThrow();
        assertEquals("Castle", cached.manifest().title());
        assertEquals(1, cached.manifest().artifacts().size());
        assertFalse(cached.cachedAt().isBlank());
    }

    @Test
    void keepsUsersSeparated() throws Exception {
        Path cachePath = tempDir.resolve("manifest-cache.json");
        ManifestCache cache = ManifestCache.load(cachePath);
        cache.write("user-1", new CompanionManifest(
            "art-1",
            "version-1",
            "owner-1",
            "Dragon",
            "private",
            new CompanionManifest.Grid(1, 1),
            "classic",
            "1.21.11",
            "standard",
            null,
            false,
            List.of(),
            List.of(),
            "2026-07-01T12:00:00Z"
        ));

        assertTrue(cache.read("user-2", "art-1").isEmpty());
    }
}
