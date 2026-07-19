package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BuildSessionStateTest {
    @Test
    void updatesGatheringProgressWithClamping() {
        BuildSessionState session = session("gathering", Map.of(), Map.of());

        BuildSessionState updated = session.withProgress("stone", 200);
        BuildSessionState lowered = updated.withProgress("stone", -500);

        assertEquals(100, updated.gathered().get("stone"));
        assertEquals(100, updated.gatheredBlocks());
        assertEquals(0, lowered.gathered().get("stone"));
    }

    @Test
    void updatesBuildingProgressSeparately() {
        BuildSessionState session = session("building", Map.of("stone", 80), Map.of());

        BuildSessionState updated = session.withProgress("stone", 64);

        assertEquals(80, updated.gathered().get("stone"));
        assertEquals(64, updated.placed().get("stone"));
        assertEquals(64, updated.placedBlocks());
    }

    @Test
    void setsAbsoluteProgressWithClamping() {
        BuildSessionState session = session("gathering", Map.of("stone", 12), Map.of());

        BuildSessionState maxed = session.withAbsoluteProgress("stone", 999);
        BuildSessionState cleared = maxed.withAbsoluteProgress("stone", -5);

        assertEquals(100, maxed.gathered().get("stone"));
        assertEquals(0, cleared.gathered().get("stone"));
    }

    private static BuildSessionState session(String mode, Map<String, Integer> gathered, Map<String, Integer> placed) {
        return new BuildSessionState(
            "session",
            new CompanionManifest.Grid(1, 1),
            "",
            List.of(new BuildSessionMaterial("stone", "Stone", 100)),
            gathered,
            placed,
            mode,
            new BuildSessionInfo(null, null, null, null),
            null,
            null
        );
    }
}
