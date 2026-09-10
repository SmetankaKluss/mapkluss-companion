package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LiveBuildProgressTest {
    @Test void liveCountsMatchFullSummaryAcrossRepairUnloadAndExpiry() {
        var tracker = tracker();
        var random = new java.util.Random(741);
        for (long time = 0; time < 300_000; time += 50) {
            tracker.scan(identity(0), pos -> switch(random.nextInt(4)) {
                case 0 -> null; case 1 -> STONE; case 2 -> state("minecraft:air"); default -> state("minecraft:dirt");
            }, 1, time);
            assertEquals(tracker.summary(time, LiveBuildProgress.LIVE_FRESHNESS_MS), tracker.liveSummary(time));
        }
        assertEquals(tracker.summary(500_000, LiveBuildProgress.LIVE_FRESHNESS_MS), tracker.liveSummary(500_000));
        tracker.close();
        assertEquals(tracker.summary(500_000, LiveBuildProgress.LIVE_FRESHNESS_MS), tracker.liveSummary(500_000));
    }
    @Test void liveExpiryIsConservativeAndRepairAfterExpiryRestoresCounts() {
        var tracker = tracker();
        tracker.scan(identity(0), pos -> STONE, 3, 49);
        assertEquals(3,tracker.liveSummary(120_000).correct());
        assertEquals(0,tracker.liveSummary(120_001).correct());
        tracker.scan(identity(0),pos -> STONE,3,120_050);
        assertEquals(3,tracker.liveSummary(120_050).correct());
        tracker.scan(identity(0),pos -> null,3,120_100);
        assertEquals(3,tracker.liveSummary(120_100).stale());
    }
    private static final LiveBuildProgress.State STONE = state("minecraft:stone");
    private static LiveBuildProgress.State state(String id) { return new LiveBuildProgress.State(id, Map.of()); }
    private static LiveBuildProgress.Identity identity(long revision) {
        return new LiveBuildProgress.Identity("a".repeat(64), "local-world", "minecraft:overworld",
            new LiveBuildProgress.Position(100, 64, -40), revision);
    }
    private static LiveBuildProgress.Cell cell(int x, LiveBuildProgress.State state) {
        return new LiveBuildProgress.Cell(new LiveBuildProgress.Position(x, 0, 0), state);
    }
    private static LiveBuildProgress tracker() {
        return new LiveBuildProgress(identity(0), List.of(cell(0, STONE), cell(1, STONE), cell(2, STONE)));
    }

    @Test void observesCorrectMissingWrongAndUsesWorldOrigin() {
        var tracker = tracker();
        tracker.scan(identity(0), pos -> {
            assertEquals(64, pos.y()); assertEquals(-40, pos.z());
            return switch (pos.x()) { case 100 -> STONE; case 101 -> state("minecraft:air"); default -> state("minecraft:dirt"); };
        }, 3, 10);
        assertEquals(new LiveBuildProgress.Summary(3, 1, 1, 1, 0, 0), tracker.summary(10, 100));
        assertEquals(1.0 / 3, tracker.summary(10, 100).completion());
    }

    @Test void blockRemovalReducesProgressAndRepairRestoresIt() {
        var tracker = tracker();
        tracker.scan(identity(0), pos -> STONE, 3, 1);
        assertEquals(1, tracker.summary(1, 100).completion());
        tracker.scan(identity(0), pos -> state("minecraft:air"), 1, 2);
        assertEquals(2, tracker.summary(2, 100).correct());
        tracker.scan(identity(0), pos -> STONE, 3, 3);
        assertEquals(1, tracker.summary(3, 100).completion());
    }

    @Test void unloadedAndStaleAreNotEmptyOrComplete() {
        var tracker = tracker();
        tracker.scan(identity(0), pos -> null, 3, 0);
        assertEquals(3, tracker.summary(0, 100).unknown());
        tracker.scan(identity(0), pos -> STONE, 3, 1);
        tracker.scan(identity(0), pos -> null, 3, 2);
        assertEquals(3, tracker.summary(2, 100).stale());
        assertEquals(0, tracker.summary(2, 100).completion());
        assertEquals(LiveBuildProgress.Status.CORRECT, tracker.observation(0, 2, 100).lastKnown());
        tracker.scan(identity(0), pos -> STONE, 3, 3);
        assertEquals(3, tracker.summary(104, 100).stale());
    }

    @Test void rotationRelevantStatePropertiesMustMatchExactly() {
        var expected = new LiveBuildProgress.State("minecraft:oak_log", Map.of("axis", "x"));
        var tracker = new LiveBuildProgress(identity(0), List.of(cell(0, expected)));
        tracker.scan(identity(0), pos -> new LiveBuildProgress.State("minecraft:oak_log", Map.of("axis", "y")), 1, 1);
        assertEquals(1, tracker.summary(1, 100).wrong());
    }

    @Test void hardBudgetAndPlacementIdentityPreventCrossWorldReads() {
        var tracker = tracker(); var reads = new AtomicInteger();
        LiveBuildProgress.WorldReader reader = pos -> { reads.incrementAndGet(); return STONE; };
        assertEquals(0, tracker.scan(identity(1), reader, 3, 0));
        assertEquals(0, reads.get());
        assertEquals(2, tracker.scan(identity(0), reader, 2, 1));
        assertEquals(2, reads.get());
        assertEquals(1, tracker.summary(1, 100).unknown());
        assertThrows(IllegalArgumentException.class, () -> tracker.scan(identity(0), reader, 4097, 2));
        tracker.close();
        assertEquals(0, tracker.scan(identity(0), reader, 3, 3));
        assertEquals(2, tracker.summary(3, 100).stale());
    }

    @Test void malformedTargetsAndClockAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LiveBuildProgress(identity(0), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new LiveBuildProgress(identity(0), List.of(cell(0, STONE), cell(0, STONE))));
        assertThrows(IllegalArgumentException.class, () -> cell(0, state("minecraft:air")));
        assertThrows(ArithmeticException.class, () -> new LiveBuildProgress(identity(0), List.of(cell(Integer.MAX_VALUE, STONE))));
        var tracker = tracker();
        tracker.scan(identity(0), pos -> STONE, 1, 2);
        assertThrows(IllegalArgumentException.class, () -> tracker.scan(identity(0), pos -> STONE, 1, 1));
    }
}
