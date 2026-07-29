package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompanionSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private String accessToken;
    private String userId;
    private String savedAt;

    private CompanionSessionStore(Path path) {
        this.path = path;
    }

    public static CompanionSessionStore load(Path minecraftRunDir) throws IOException {
        Path path = minecraftRunDir.resolve("config").resolve("mapkluss-companion").resolve("session.json");
        if (!Files.exists(path)) return new CompanionSessionStore(path);
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredSession loaded = GSON.fromJson(reader, StoredSession.class);
            CompanionSessionStore store = new CompanionSessionStore(path);
            if (loaded != null) {
                store.accessToken = loaded.accessToken();
                store.userId = loaded.userId();
                store.savedAt = loaded.savedAt();
            }
            return store;
        }
    }

    public String accessToken() {
        return accessToken;
    }

    public String userId() {
        return userId;
    }

    public boolean hasAccessToken() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String savedAt() {
        return savedAt;
    }

    public CompanionSessionInfo sessionInfo() {
        return CompanionSessionInfo.from(accessToken, userId, savedAt);
    }

    public void saveSession(String accessToken, String userId) throws IOException {
        this.accessToken = accessToken;
        this.userId = userId;
        this.savedAt = java.time.Instant.now().toString();
        save();
    }

    public void clear() throws IOException {
        this.accessToken = null;
        this.userId = null;
        this.savedAt = null;
        save();
    }

    private void save() throws IOException {
        AtomicFiles.writePrivateUtf8(path, GSON.toJson(new StoredSession(accessToken, userId, savedAt)));
    }

    private record StoredSession(String accessToken, String userId, String savedAt) {
    }
}
