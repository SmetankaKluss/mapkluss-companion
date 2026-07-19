package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LibraryCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private StoredCache cache;

    private LibraryCache(Path path, StoredCache cache) {
        this.path = path;
        this.cache = cache;
    }

    public static LibraryCache load(Path path) throws IOException {
        if (!Files.exists(path)) return new LibraryCache(path, new StoredCache(new HashMap<>(), new HashMap<>(), new HashMap<>()));
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredCache loaded = GSON.fromJson(reader, StoredCache.class);
            if (loaded == null) loaded = new StoredCache(new HashMap<>(), new HashMap<>(), new HashMap<>());
            if (loaded.views() == null) loaded = new StoredCache(new HashMap<>(), loaded.collections(), loaded.collectionItems());
            if (loaded.collections() == null) loaded = new StoredCache(loaded.views(), new HashMap<>(), loaded.collectionItems());
            if (loaded.collectionItems() == null) loaded = new StoredCache(loaded.views(), loaded.collections(), new HashMap<>());
            return new LibraryCache(path, loaded);
        }
    }

    public CachedLibrary read(String userId, String view) {
        CachedLibrary library = cache.views().get(key(userId, view));
        if (library == null || library.items() == null) return new CachedLibrary(List.of(), null);
        return library;
    }

    public void write(String userId, String view, List<CompanionLibraryItem> items) throws IOException {
        cache.views().put(key(userId, view), new CachedLibrary(List.copyOf(items), Instant.now().toString()));
        save();
    }

    public void replaceViewItem(String userId, String view, CompanionLibraryItem item) throws IOException {
        String key = key(userId, view);
        CachedLibrary current = cache.views().get(key);
        if (current == null || current.items() == null) return;

        boolean found = false;
        List<CompanionLibraryItem> updated = new ArrayList<>();
        for (CompanionLibraryItem existing : current.items()) {
            if (existing.artId().equals(item.artId())) {
                updated.add(item);
                found = true;
            } else {
                updated.add(existing);
            }
        }
        if (!found) return;
        cache.views().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
        save();
    }

    public void upsertViewItem(String userId, String view, CompanionLibraryItem item) throws IOException {
        String key = key(userId, view);
        CachedLibrary current = cache.views().get(key);
        if (current == null || current.items() == null) return;

        List<CompanionLibraryItem> updated = new ArrayList<>();
        updated.add(item);
        for (CompanionLibraryItem existing : current.items()) {
            if (!existing.artId().equals(item.artId())) {
                updated.add(existing);
            }
        }
        cache.views().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
        save();
    }

    public void removeViewItem(String userId, String view, String artId) throws IOException {
        String key = key(userId, view);
        CachedLibrary current = cache.views().get(key);
        if (current == null || current.items() == null) return;

        List<CompanionLibraryItem> updated = new ArrayList<>();
        boolean removed = false;
        for (CompanionLibraryItem existing : current.items()) {
            if (existing.artId().equals(artId)) {
                removed = true;
            } else {
                updated.add(existing);
            }
        }
        if (!removed) return;
        cache.views().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
        save();
    }

    public CachedCollections readCollections(String userId) {
        CachedCollections collections = cache.collections().get(collectionKey(userId));
        if (collections == null || collections.items() == null) return new CachedCollections(List.of(), null);
        return collections;
    }

    public void writeCollections(String userId, List<CompanionCollection> items) throws IOException {
        cache.collections().put(collectionKey(userId), new CachedCollections(List.copyOf(items), Instant.now().toString()));
        save();
    }

    public CachedLibrary readCollectionItems(String userId, String collectionId) {
        CachedLibrary library = cache.collectionItems().get(collectionItemsKey(userId, collectionId));
        if (library == null || library.items() == null) return new CachedLibrary(List.of(), null);
        return library;
    }

    public void writeCollectionItems(String userId, String collectionId, List<CompanionLibraryItem> items) throws IOException {
        cache.collectionItems().put(collectionItemsKey(userId, collectionId), new CachedLibrary(List.copyOf(items), Instant.now().toString()));
        save();
    }

    public void updateCollectionItems(String userId, String collectionId, CompanionLibraryItem item, boolean selected) throws IOException {
        String key = collectionItemsKey(userId, collectionId);
        CachedLibrary current = cache.collectionItems().get(key);
        if (current == null || current.items() == null) return;

        List<CompanionLibraryItem> updated = new ArrayList<>(current.items().stream()
            .filter(existing -> !existing.artId().equals(item.artId()))
            .toList());
        if (selected) {
            updated.add(0, item);
        }
        cache.collectionItems().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(cache, writer);
        }
    }

    private static String key(String userId, String view) {
        return (userId == null || userId.isBlank() ? "anonymous" : userId) + ":" + view;
    }

    private static String collectionKey(String userId) {
        return key(userId, "collections");
    }

    private static String collectionItemsKey(String userId, String collectionId) {
        return key(userId, "collection:" + (collectionId == null ? "" : collectionId));
    }

    private record StoredCache(
        Map<String, CachedLibrary> views,
        Map<String, CachedCollections> collections,
        Map<String, CachedLibrary> collectionItems
    ) {
    }

    public record CachedLibrary(List<CompanionLibraryItem> items, String cachedAt) {
        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }
    }

    public record CachedCollections(List<CompanionCollection> items, String cachedAt) {
        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }
    }
}
