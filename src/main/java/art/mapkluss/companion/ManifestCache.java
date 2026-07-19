package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ManifestCache {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private final StoredManifestCache cache;

    private ManifestCache(Path path, StoredManifestCache cache) {
        this.path = path;
        this.cache = cache;
    }

    public static ManifestCache load(Path path) throws IOException {
        if (!Files.exists(path)) return new ManifestCache(path, new StoredManifestCache(new HashMap<>()));
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredManifestCache loaded = GSON.fromJson(reader, StoredManifestCache.class);
            if (loaded == null || loaded.manifests() == null) {
                loaded = new StoredManifestCache(new HashMap<>());
            }
            return new ManifestCache(path, loaded);
        }
    }

    public Optional<CachedManifest> read(String userId, String artId) {
        return Optional.ofNullable(cache.manifests().get(key(userId, artId)));
    }

    public void write(String userId, CompanionManifest manifest) throws IOException {
        cache.manifests().put(key(userId, manifest.artId()), new CachedManifest(manifest, Instant.now().toString()));
        save();
    }

    public void remove(String userId, String artId) throws IOException {
        if (cache.manifests().remove(key(userId, artId)) != null) {
            save();
        }
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(cache, writer);
        }
    }

    private static String key(String userId, String artId) {
        return (userId == null || userId.isBlank() ? "anonymous" : userId) + ":" + artId;
    }

    private record StoredManifestCache(Map<String, CachedManifest> manifests) {
    }

    public record CachedManifest(CompanionManifest manifest, String cachedAt) {
    }
}
