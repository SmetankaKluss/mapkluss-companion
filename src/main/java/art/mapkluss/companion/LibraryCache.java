package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
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
        return new LibraryCache(path, readStored(path));
    }

    private static StoredCache readStored(Path path) throws IOException {
        if (!Files.exists(path)) return emptyCache();
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredCache loaded = GSON.fromJson(reader, StoredCache.class);
            if (loaded == null) loaded = emptyCache();
            if (loaded.views() == null) loaded = new StoredCache(new HashMap<>(), loaded.collections(), loaded.collectionItems(), loaded.collectionMembership());
            if (loaded.collections() == null) loaded = new StoredCache(loaded.views(), new HashMap<>(), loaded.collectionItems(), loaded.collectionMembership());
            if (loaded.collectionItems() == null) loaded = new StoredCache(loaded.views(), loaded.collections(), new HashMap<>(), loaded.collectionMembership());
            if (loaded.collectionMembership() == null) loaded = new StoredCache(loaded.views(), loaded.collections(), loaded.collectionItems(), new HashMap<>());
            return loaded;
        }
    }

    private static StoredCache emptyCache() {
        return new StoredCache(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public CachedLibrary read(String userId, String view) {
        CachedLibrary library = cache.views().get(key(userId, view));
        if (library == null || library.items() == null) return new CachedLibrary(List.of(), null);
        return library;
    }

    public void write(String userId, String view, List<CompanionLibraryItem> items) throws IOException {
        mutate(latest -> {
            latest.views().put(key(userId, view), new CachedLibrary(List.copyOf(items), Instant.now().toString()));
            return true;
        });
    }

    public void replaceViewItem(String userId, String view, CompanionLibraryItem item) throws IOException {
        mutate(latest -> {
            String key = key(userId, view);
            CachedLibrary current = latest.views().get(key);
            if (current == null || current.items() == null) return false;
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
            if (!found) return false;
            latest.views().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
            return true;
        });
    }

    public void upsertViewItem(String userId, String view, CompanionLibraryItem item) throws IOException {
        mutate(latest -> {
            String key = key(userId, view);
            CachedLibrary current = latest.views().get(key);
            if (current == null || current.items() == null) return false;
            List<CompanionLibraryItem> updated = new ArrayList<>();
            updated.add(item);
            for (CompanionLibraryItem existing : current.items()) {
                if (!existing.artId().equals(item.artId())) updated.add(existing);
            }
            latest.views().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
            return true;
        });
    }

    public void removeViewItem(String userId, String view, String artId) throws IOException {
        mutate(latest -> {
            String key = key(userId, view);
            CachedLibrary current = latest.views().get(key);
            if (current == null || current.items() == null) return false;
            List<CompanionLibraryItem> updated = new ArrayList<>();
            boolean removed = false;
            for (CompanionLibraryItem existing : current.items()) {
                if (existing.artId().equals(artId)) removed = true;
                else updated.add(existing);
            }
            if (!removed) return false;
            latest.views().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
            return true;
        });
    }

    public CachedCollections readCollections(String userId) {
        CachedCollections collections = cache.collections().get(collectionKey(userId));
        if (collections == null || collections.items() == null) return new CachedCollections(List.of(), null);
        return collections;
    }

    public void writeCollections(String userId, List<CompanionCollection> items) throws IOException {
        mutate(latest -> {
            latest.collections().put(collectionKey(userId), new CachedCollections(List.copyOf(items), Instant.now().toString()));
            return true;
        });
    }

    public void upsertCollection(String userId, CompanionCollection item) throws IOException {
        upsertCollections(userId, List.of(item));
    }

    public void upsertCollections(String userId, List<CompanionCollection> items) throws IOException {
        mutate(latest -> {
            String key = collectionKey(userId);
            CachedCollections current = latest.collections().get(key);
            List<CompanionCollection> existing = current == null || current.items() == null
                ? List.of() : current.items();
            java.util.LinkedHashMap<String, CompanionCollection> merged = new java.util.LinkedHashMap<>();
            for (CompanionCollection item : items) {
                if (item != null && item.id() != null) merged.put(item.id(), item);
            }
            for (CompanionCollection item : existing) {
                if (item != null && item.id() != null) merged.putIfAbsent(item.id(), item);
            }
            latest.collections().put(key, new CachedCollections(List.copyOf(merged.values()), Instant.now().toString()));
            return true;
        });
    }

    public void removeCollection(String userId, String collectionId) throws IOException {
        mutate(latest -> {
            String key = collectionKey(userId);
            CachedCollections current = latest.collections().get(key);
            if (current == null || current.items() == null) return false;
            List<CompanionCollection> updated = current.items().stream()
                .filter(item -> !item.id().equals(collectionId))
                .toList();
            if (updated.size() == current.items().size()) return false;
            latest.collections().put(key, new CachedCollections(List.copyOf(updated), Instant.now().toString()));
            latest.collectionItems().remove(collectionItemsKey(userId, collectionId));
            String membershipPrefix = collectionMembershipPrefix(userId, collectionId);
            latest.collectionMembership().keySet().removeIf(memberKey -> memberKey.startsWith(membershipPrefix));
            return true;
        });
    }

    public void adjustCollectionCounts(String userId, java.util.Collection<String> collectionIds, int delta)
        throws IOException {
        if (collectionIds == null || collectionIds.isEmpty() || delta == 0) return;
        java.util.Set<String> ids = java.util.Set.copyOf(collectionIds);
        mutate(latest -> {
            String key = collectionKey(userId);
            CachedCollections current = latest.collections().get(key);
            if (current == null || current.items() == null) return false;
            boolean changed = false;
            List<CompanionCollection> updated = new ArrayList<>();
            for (CompanionCollection item : current.items()) {
                if (!ids.contains(item.id())) {
                    updated.add(item);
                    continue;
                }
                int nextCount = Math.max(0, item.itemCount() + delta);
                updated.add(new CompanionCollection(
                    item.id(), item.name(), item.createdAt(), item.updatedAt(), nextCount
                ));
                changed |= nextCount != item.itemCount();
            }
            if (!changed) return false;
            latest.collections().put(key, new CachedCollections(List.copyOf(updated), Instant.now().toString()));
            return true;
        });
    }

    public CachedLibrary readCollectionItems(String userId, String collectionId) {
        CachedLibrary library = cache.collectionItems().get(collectionItemsKey(userId, collectionId));
        if (library == null || library.items() == null) return new CachedLibrary(List.of(), null);
        return library;
    }

    public void writeCollectionItems(String userId, String collectionId, List<CompanionLibraryItem> items) throws IOException {
        mutate(latest -> {
            latest.collectionItems().put(collectionItemsKey(userId, collectionId),
                new CachedLibrary(List.copyOf(items), Instant.now().toString()));
            String membershipPrefix = collectionMembershipPrefix(userId, collectionId);
            latest.collectionMembership().keySet().removeIf(memberKey -> memberKey.startsWith(membershipPrefix));
            for (CompanionLibraryItem item : items) {
                latest.collectionMembership().put(
                    collectionMembershipKey(userId, collectionId, item.artId()), true
                );
            }
            return true;
        });
    }

    public void updateCollectionItems(String userId, String collectionId, CompanionLibraryItem item, boolean selected) throws IOException {
        mutate(latest -> {
            String key = collectionItemsKey(userId, collectionId);
            CachedLibrary current = latest.collectionItems().get(key);
            if (current == null || current.items() == null) return false;
            List<CompanionLibraryItem> updated = new ArrayList<>(current.items().stream()
                .filter(existing -> !existing.artId().equals(item.artId()))
                .toList());
            if (selected) updated.add(0, item);
            latest.collectionItems().put(key, new CachedLibrary(List.copyOf(updated), Instant.now().toString()));
            latest.collectionMembership().put(
                collectionMembershipKey(userId, collectionId, item.artId()), selected
            );
            return true;
        });
    }

    public void setCollectionItemState(
        String userId,
        String collectionId,
        CompanionLibraryItem item,
        boolean previouslySelected,
        boolean selected
    ) throws IOException {
        mutate(latest -> {
            String membershipKey = collectionMembershipKey(userId, collectionId, item.artId());
            Boolean knownSelection = latest.collectionMembership().get(membershipKey);
            boolean currentSelection = knownSelection == null ? previouslySelected : knownSelection;
            String itemKey = collectionItemsKey(userId, collectionId);
            CachedLibrary currentItems = latest.collectionItems().get(itemKey);
            if (currentItems != null && currentItems.items() != null) {
                List<CompanionLibraryItem> updatedItems = new ArrayList<>(currentItems.items().stream()
                    .filter(existing -> !existing.artId().equals(item.artId()))
                    .toList());
                if (selected) updatedItems.add(0, item);
                latest.collectionItems().put(itemKey,
                    new CachedLibrary(List.copyOf(updatedItems), Instant.now().toString()));
            }
            latest.collectionMembership().put(membershipKey, selected);
            if (currentSelection == selected) return knownSelection == null || currentItems != null;

            String collectionsKey = collectionKey(userId);
            CachedCollections currentCollections = latest.collections().get(collectionsKey);
            if (currentCollections == null || currentCollections.items() == null) return true;
            List<CompanionCollection> updatedCollections = new ArrayList<>();
            for (CompanionCollection collection : currentCollections.items()) {
                if (!collection.id().equals(collectionId)) {
                    updatedCollections.add(collection);
                    continue;
                }
                int nextCount = Math.max(0, collection.itemCount() + (selected ? 1 : -1));
                updatedCollections.add(new CompanionCollection(
                    collection.id(), collection.name(), collection.createdAt(), collection.updatedAt(), nextCount
                ));
            }
            latest.collections().put(collectionsKey,
                new CachedCollections(List.copyOf(updatedCollections), Instant.now().toString()));
            return true;
        });
    }

    private void mutate(CacheMutation mutation) throws IOException {
        AtomicFiles.withLock(path, () -> {
            StoredCache source = readStored(path);
            StoredCache latest = new StoredCache(
                new HashMap<>(source.views()),
                new HashMap<>(source.collections()),
                new HashMap<>(source.collectionItems()),
                new HashMap<>(source.collectionMembership())
            );
            if (mutation.apply(latest)) {
                AtomicFiles.writePrivateUtf8(path, GSON.toJson(latest));
            }
            cache = latest;
            return null;
        });
    }

    @FunctionalInterface
    private interface CacheMutation {
        boolean apply(StoredCache cache);
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

    private static String collectionMembershipPrefix(String userId, String collectionId) {
        return key(userId, "collection-membership:" + (collectionId == null ? "" : collectionId) + ":");
    }

    private static String collectionMembershipKey(String userId, String collectionId, String artId) {
        return collectionMembershipPrefix(userId, collectionId) + (artId == null ? "" : artId);
    }

    private record StoredCache(
        Map<String, CachedLibrary> views,
        Map<String, CachedCollections> collections,
        Map<String, CachedLibrary> collectionItems,
        Map<String, Boolean> collectionMembership
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
