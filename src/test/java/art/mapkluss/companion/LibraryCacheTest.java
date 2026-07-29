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
    void independentlyLoadedCachesDoNotLoseEachOthersViews() throws Exception {
        Path cachePath = tempDir.resolve("shared-library-cache.json");
        LibraryCache first = LibraryCache.load(cachePath);
        LibraryCache second = LibraryCache.load(cachePath);
        first.write("user-1", "recent", List.of(item("art-1", "First")));
        second.write("user-2", "recent", List.of(item("art-2", "Second")));

        LibraryCache merged = LibraryCache.load(cachePath);
        assertEquals("First", merged.read("user-1", "recent").items().getFirst().title());
        assertEquals("Second", merged.read("user-2", "recent").items().getFirst().title());
    }

    @Test
    void semanticCollectionUpdatesPreserveConcurrentCollections() throws Exception {
        Path cachePath = tempDir.resolve("shared-collections-cache.json");
        LibraryCache first = LibraryCache.load(cachePath);
        LibraryCache second = LibraryCache.load(cachePath);
        CompanionCollection alpha = new CompanionCollection("a", "Alpha", null, null, 1);
        CompanionCollection beta = new CompanionCollection("b", "Beta", null, null, 2);
        first.upsertCollection("user-1", alpha);
        second.upsertCollection("user-1", beta);
        first.removeCollection("user-1", "a");

        List<CompanionCollection> collections = LibraryCache.load(cachePath)
            .readCollections("user-1").items();
        assertEquals(1, collections.size());
        assertEquals("b", collections.getFirst().id());
    }

    @Test
    void collectionMembershipTransitionUpdatesCountOnlyOnce() throws Exception {
        Path cachePath = tempDir.resolve("membership-cache.json");
        LibraryCache first = LibraryCache.load(cachePath);
        LibraryCache second = LibraryCache.load(cachePath);
        first.writeCollections("user-1", List.of(
            new CompanionCollection("a", "Alpha", null, null, 0)
        ));
        CompanionLibraryItem art = item("art-1", "First");

        first.setCollectionItemState("user-1", "a", art, false, true);
        second.setCollectionItemState("user-1", "a", art, false, true);

        LibraryCache loaded = LibraryCache.load(cachePath);
        assertEquals(1, loaded.readCollections("user-1").items().getFirst().itemCount());
    }

    @Test
    void initialMembershipSeedPreservesAuthoritativeCollectionCount() throws Exception {
        Path cachePath = tempDir.resolve("membership-seed-cache.json");
        LibraryCache cache = LibraryCache.load(cachePath);
        cache.writeCollections("user-1", List.of(
            new CompanionCollection("a", "Alpha", null, null, 3)
        ));
        CompanionLibraryItem art = item("art-1", "First");

        cache.setCollectionItemState("user-1", "a", art, true, true);
        assertEquals(3, LibraryCache.load(cachePath)
            .readCollections("user-1").items().getFirst().itemCount());

        LibraryCache.load(cachePath).setCollectionItemState("user-1", "a", art, true, false);
        assertEquals(2, LibraryCache.load(cachePath)
            .readCollections("user-1").items().getFirst().itemCount());
    }

    private static CompanionLibraryItem item(String id, String title) {
        return new CompanionLibraryItem(
            id, "version-1", title, "unlisted", new CompanionManifest.Grid(1, 1),
            "classic", null, "2026-07-24T00:00:00Z", false
        );
    }

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
