package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TrackerHistoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES = 12;

    private final Path path;
    private StoredTrackerHistory history;

    private TrackerHistoryStore(Path path, StoredTrackerHistory history) {
        this.path = path;
        this.history = history;
    }

    public static TrackerHistoryStore load(Path path) throws IOException {
        return new TrackerHistoryStore(path, readStored(path));
    }

    private static StoredTrackerHistory readStored(Path path) throws IOException {
        if (!Files.exists(path)) return new StoredTrackerHistory(new ArrayList<>());
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredTrackerHistory loaded = GSON.fromJson(reader, StoredTrackerHistory.class);
            if (loaded == null || loaded.entries() == null) loaded = new StoredTrackerHistory(new ArrayList<>());
            return loaded;
        }
    }

    public List<TrackerHistoryEntry> entries() {
        return List.copyOf(history.entries());
    }

    public void remember(BuildSessionState session) throws IOException {
        if (session == null || session.id() == null || session.id().isBlank()) return;
        String title = session.info() != null && session.info().title() != null && !session.info().title().isBlank()
            ? session.info().title()
            : session.id();
        TrackerHistoryEntry entry = new TrackerHistoryEntry(
            session.id(),
            title,
            session.art_id(),
            session.mode(),
            Instant.now().toString()
        );
        AtomicFiles.withLock(path, () -> {
            StoredTrackerHistory latest = readStored(path);
            List<TrackerHistoryEntry> updated = new ArrayList<>();
            updated.add(entry);
            for (TrackerHistoryEntry existing : latest.entries()) {
                if (!existing.sessionId().equals(entry.sessionId())) {
                    updated.add(existing);
                }
                if (updated.size() >= MAX_ENTRIES) break;
            }
            StoredTrackerHistory next = new StoredTrackerHistory(updated);
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            history = next;
            return null;
        });
    }

    private record StoredTrackerHistory(List<TrackerHistoryEntry> entries) {
    }
}
