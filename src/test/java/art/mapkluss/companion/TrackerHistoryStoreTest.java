package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrackerHistoryStoreTest {
    @TempDir Path tempDir;

    @Test
    void independentlyLoadedStoresMergeDifferentSessions() throws Exception {
        Path path = tempDir.resolve("tracker-history.json");
        TrackerHistoryStore first = TrackerHistoryStore.load(path);
        TrackerHistoryStore second = TrackerHistoryStore.load(path);
        first.remember(session("session-1", "First"));
        second.remember(session("session-2", "Second"));

        List<TrackerHistoryEntry> entries = TrackerHistoryStore.load(path).entries();
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(entry -> entry.sessionId().equals("session-1")));
        assertTrue(entries.stream().anyMatch(entry -> entry.sessionId().equals("session-2")));
    }

    private static BuildSessionState session(String id, String title) {
        return new BuildSessionState(
            id, new CompanionManifest.Grid(1, 1), null, List.of(), Map.of(), Map.of(), "gathering",
            new BuildSessionInfo(title, null, null, null), "art-1", "version-1"
        );
    }
}
