package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ManifestCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private StoredManifestCache cache;

    private ManifestCache(Path path, StoredManifestCache cache) {
        this.path = path;
        this.cache = cache;
    }

    public static ManifestCache load(Path path) throws IOException {
        return new ManifestCache(path, readStored(path));
    }

    private static StoredManifestCache readStored(Path path) throws IOException {
        if (!Files.exists(path)) return new StoredManifestCache(new HashMap<>());
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredManifestCache loaded = GSON.fromJson(reader, StoredManifestCache.class);
            if (loaded == null || loaded.manifests() == null) {
                loaded = new StoredManifestCache(new HashMap<>());
            }
            return loaded;
        }
    }

    public Optional<CachedManifest> read(String userId, String artId) {
        return Optional.ofNullable(cache.manifests().get(key(userId, artId)));
    }

    public void write(String userId, CompanionManifest manifest) throws IOException {
        AtomicFiles.withLock(path, () -> {
            Map<String, CachedManifest> updated = new HashMap<>(readStored(path).manifests());
            updated.put(key(userId, manifest.artId()), new CachedManifest(manifest, Instant.now().toString()));
            StoredManifestCache next = new StoredManifestCache(updated);
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            cache = next;
            return null;
        });
    }

    public void remove(String userId, String artId) throws IOException {
        AtomicFiles.withLock(path, () -> {
            StoredManifestCache latest = readStored(path);
            Map<String, CachedManifest> updated = new HashMap<>(latest.manifests());
            if (updated.remove(key(userId, artId)) == null) {
                cache = latest;
                return null;
            }
            StoredManifestCache next = new StoredManifestCache(updated);
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            cache = next;
            return null;
        });
    }

    private static String key(String userId, String artId) {
        return (userId == null || userId.isBlank() ? "anonymous" : userId) + ":" + artId;
    }

    private record StoredManifestCache(Map<String, CachedManifest> manifests) {
    }

    public record CachedManifest(CompanionManifest manifest, String cachedAt) {
    }
}
