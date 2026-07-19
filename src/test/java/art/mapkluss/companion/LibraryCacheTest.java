package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LibraryCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndReloadsLibraryView() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        CompanionLibraryItem item = new CompanionLibraryItem(
            "art-1",
            "version-1",
            "Castle",
            "unlisted",
            new CompanionManifest.Grid(2, 3),
            "classic",
            null,
            "2026-06-30T12:00:00Z",
            true
        );

        cache.write("user-1", "recent", List.of(item));
        LibraryCache loaded = LibraryCache.load(cachePath);
        LibraryCache.CachedLibrary recent = loaded.read("user-1", "recent");

        assertFalse(recent.isEmpty());
        assertEquals("Castle", recent.items().getFirst().title());
        assertEquals("2x3", recent.items().getFirst().gridLabel());
    }

    @Test
    void keepsUsersSeparated() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        cache.write("user-1", "my", List.of(new CompanionLibraryItem(
            "art-1",
            "version-1",
            "First",
            "private",
            new CompanionManifest.Grid(1, 1),
            "classic",
            null,
            "2026-06-30T12:00:00Z",
            false
        )));

        assertTrue(cache.read("user-2", "my").isEmpty());
    }

    @Test
    void savesAndReloadsCollections() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        cache.writeCollections("user-1", List.of(
            new CompanionCollection("collection-1", "Favorites", "2026-07-01T00:00:00Z", "2026-07-01T01:00:00Z", 3)
        ));

        LibraryCache loaded = LibraryCache.load(cachePath);
        LibraryCache.CachedCollections collections = loaded.readCollections("user-1");

        assertFalse(collections.isEmpty());
        assertEquals("Favorites", collections.items().getFirst().name());
        assertEquals(3, collections.items().getFirst().itemCount());
    }

    @Test
    void keepsCollectionItemsSeparatedByCollectionId() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        cache.writeCollectionItems("user-1", "collection-1", List.of(new CompanionLibraryItem(
            "art-1",
            "version-1",
            "Dragon",
            "unlisted",
            new CompanionManifest.Grid(2, 2),
            "classic",
            null,
            "2026-07-01T12:00:00Z",
            false
        )));

        LibraryCache loaded = LibraryCache.load(cachePath);
        assertEquals("Dragon", loaded.readCollectionItems("user-1", "collection-1").items().getFirst().title());
        assertTrue(loaded.readCollectionItems("user-1", "collection-2").isEmpty());
    }

    @Test
    void updatesCachedCollectionItemsSelection() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        CompanionLibraryItem dragon = new CompanionLibraryItem(
            "art-1",
            "version-1",
            "Dragon",
            "unlisted",
            new CompanionManifest.Grid(2, 2),
            "classic",
            null,
            "2026-07-01T12:00:00Z",
            false
        );
        cache.writeCollectionItems("user-1", "collection-1", List.of(dragon));

        CompanionLibraryItem castle = new CompanionLibraryItem(
            "art-2",
            "version-2",
            "Castle",
            "unlisted",
            new CompanionManifest.Grid(1, 1),
            "classic",
            null,
            "2026-07-01T13:00:00Z",
            true
        );
        cache.updateCollectionItems("user-1", "collection-1", castle, true);

        LibraryCache loaded = LibraryCache.load(cachePath);
        assertEquals("Castle", loaded.readCollectionItems("user-1", "collection-1").items().getFirst().title());

        loaded.updateCollectionItems("user-1", "collection-1", castle, false);
        LibraryCache reloaded = LibraryCache.load(cachePath);
        assertEquals(1, reloaded.readCollectionItems("user-1", "collection-1").items().size());
        assertEquals("Dragon", reloaded.readCollectionItems("user-1", "collection-1").items().getFirst().title());
    }

    @Test
    void replacesExistingItemInCachedView() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        cache.write("user-1", "recent", List.of(
            new CompanionLibraryItem(
                "art-1",
                "version-1",
                "Old Castle",
                "unlisted",
                new CompanionManifest.Grid(2, 3),
                "classic",
                null,
                "2026-07-01T10:00:00Z",
                false
            )
        ));

        cache.replaceViewItem("user-1", "recent", new CompanionLibraryItem(
            "art-1",
            "version-2",
            "New Castle",
            "unlisted",
            new CompanionManifest.Grid(2, 3),
            "classic",
            null,
            "2026-07-01T11:00:00Z",
            true
        ));

        LibraryCache loaded = LibraryCache.load(cachePath);
        CompanionLibraryItem item = loaded.read("user-1", "recent").items().getFirst();
        assertEquals("New Castle", item.title());
        assertTrue(item.isFavorite());
    }

    @Test
    void upsertsAndRemovesFavoriteViewItems() throws Exception {
        Path cachePath = tempDir.resolve("library-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        cache.write("user-1", "favorites", List.of(
            new CompanionLibraryItem(
                "art-1",
                "version-1",
                "Dragon",
                "unlisted",
                new CompanionManifest.Grid(2, 2),
                "classic",
                null,
                "2026-07-01T10:00:00Z",
                true
            )
        ));

        cache.upsertViewItem("user-1", "favorites", new CompanionLibraryItem(
            "art-2",
            "version-2",
            "Castle",
            "unlisted",
            new CompanionManifest.Grid(1, 1),
            "classic",
            null,
            "2026-07-01T11:00:00Z",
            true
        ));

        LibraryCache loaded = LibraryCache.load(cachePath);
        assertEquals("Castle", loaded.read("user-1", "favorites").items().getFirst().title());
        assertEquals(2, loaded.read("user-1", "favorites").items().size());

        loaded.removeViewItem("user-1", "favorites", "art-2");
        LibraryCache reloaded = LibraryCache.load(cachePath);
        assertEquals(1, reloaded.read("user-1", "favorites").items().size());
        assertEquals("Dragon", reloaded.read("user-1", "favorites").items().getFirst().title());
    }
}
