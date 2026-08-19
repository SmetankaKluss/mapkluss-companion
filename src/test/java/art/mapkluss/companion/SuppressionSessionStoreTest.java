package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SuppressionSessionStoreTest {
    @TempDir Path tempDir;

    @Test
    void roundTripsPinnedLocalProgress() throws Exception {
        SuppressionSessionStore store = SuppressionSessionStore.forRunDir(tempDir);
        SuppressionSessionStore.StoredSession expected = new SuppressionSessionStore.StoredSession(
            4,
            tempDir.resolve("plan.json").toString(),
            tempDir.resolve("plan.litematic").toString(),
            "a".repeat(64),
            "b".repeat(64),
            "art",
            "version",
            "Title",
            SuppressionStage.DWELL,
            10, 64, -30,
            "c".repeat(64),
            "minecraft:overworld",
            42,
            12,
            3,
            19,
            false,
            123L
        );

        store.save(expected);

        assertEquals(expected, store.load());
    }

    @Test
    void stillReadsLegacyFormatOneForSafeMigration() throws Exception {
        SuppressionSessionStore store = SuppressionSessionStore.forRunDir(tempDir);
        SuppressionSessionStore.StoredSession legacy = new SuppressionSessionStore.StoredSession(
            1,
            tempDir.resolve("plan.json").toString(),
            tempDir.resolve("plan.litematic").toString(),
            "a".repeat(64),
            "b".repeat(64),
            "art",
            "version",
            "Title",
            SuppressionStage.BUILDING,
            10, 64, -30,
            "c".repeat(64),
            "minecraft:overworld",
            42,
            0,
            0,
            0,
            false,
            123L
        );

        store.save(legacy);

        assertEquals(legacy, store.load());
    }

    @Test
    void readsFormatThreeWithoutRestoringAutomaticHotbarState() throws Exception {
        SuppressionSessionStore store = SuppressionSessionStore.forRunDir(tempDir);
        Path path = tempDir.resolve("config/mapkluss-companion/suppression-session.json");
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, """
            {
              "formatVersion": 3,
              "planPath": "%s",
              "schematicPath": "%s",
              "planSha256": "%s",
              "litematicSha256": "%s",
              "artId": "art",
              "versionId": "version",
              "title": "Title",
              "stage": "DWELL",
              "anchorX": 10,
              "anchorY": 64,
              "anchorZ": -30,
              "worldHash": "%s",
              "dimension": "minecraft:overworld",
              "mapId": 42,
              "boundMapHotbarSlot": 4,
              "restoreHotbarSlot": 1,
              "phaseIndex": 12,
              "standPointIndex": 3,
              "dwellTicks": 19,
              "manualOverrideArmed": false,
              "updatedAt": 123
            }
            """.formatted(
                tempDir.resolve("plan.json").toString().replace('\\', '/'),
                tempDir.resolve("plan.litematic").toString().replace('\\', '/'),
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64)
            ));

        SuppressionSessionStore.StoredSession restored = store.load();
        assertEquals(3, restored.formatVersion());
        assertEquals(42, restored.mapId());
        assertEquals(SuppressionStage.DWELL, restored.stage());
    }

    @Test
    void validatesStageSpecificProgressAgainstPlan() throws Exception {
        SuppressionPlan plan = SuppressionBundleReader.read(SuppressionTestFixtures.zipBytes(), "fixture.zip").parsed().plan();
        SuppressionSessionStore.StoredSession base = session(SuppressionStage.REMOVE, 0, 0, false);
        SuppressionSessionStore.validateForPlan(base, plan, SuppressionStage.REMOVE);

        assertThrows(java.io.IOException.class, () -> SuppressionSessionStore.validateForPlan(
            session(SuppressionStage.REMOVE, 64, 0, false), plan, SuppressionStage.REMOVE));
        assertThrows(java.io.IOException.class, () -> SuppressionSessionStore.validateForPlan(
            session(SuppressionStage.MOVE, 0, plan.phases().getFirst().standPoints().size(), false), plan, SuppressionStage.MOVE));
        assertThrows(java.io.IOException.class, () -> SuppressionSessionStore.validateForPlan(
            session(SuppressionStage.COMPLETE, 62, plan.phases().get(62).standPoints().size(), false), plan, SuppressionStage.COMPLETE));
    }

    private SuppressionSessionStore.StoredSession session(
        SuppressionStage stage, int phaseIndex, int standPointIndex, boolean manualOverride
    ) {
        return new SuppressionSessionStore.StoredSession(
            4,
            tempDir.resolve("plan.json").toString(),
            tempDir.resolve("plan.litematic").toString(),
            "a".repeat(64), "b".repeat(64), "art", "version", "Title", stage,
            10, 64, -30, "c".repeat(64), "minecraft:overworld", 42,
            phaseIndex, standPointIndex, 0, manualOverride, 123L
        );
    }

    @Test
    void clearRemovesOnlyTheStoredSession() throws Exception {
        SuppressionSessionStore store = SuppressionSessionStore.forRunDir(tempDir);
        Path unrelated = tempDir.resolve("keep-me.txt");
        java.nio.file.Files.writeString(unrelated, "preserve");
        store.save(new SuppressionSessionStore.StoredSession(
            2,
            tempDir.resolve("plan.json").toString(),
            tempDir.resolve("plan.litematic").toString(),
            "a".repeat(64),
            "b".repeat(64),
            "art",
            "version",
            "Title",
            SuppressionStage.WAITING_ANCHOR,
            0, 0, 0,
            null,
            null,
            -1,
            0,
            0,
            0,
            false,
            123L
        ));

        store.clear();

        assertNull(store.load());
        assertEquals("preserve", java.nio.file.Files.readString(unrelated));
    }

    @Test
    void rejectsProgressWithoutExactWorldBinding() throws Exception {
        SuppressionSessionStore store = SuppressionSessionStore.forRunDir(tempDir);
        store.save(new SuppressionSessionStore.StoredSession(
            1,
            tempDir.resolve("plan.json").toString(),
            tempDir.resolve("plan.litematic").toString(),
            "a".repeat(64),
            "b".repeat(64),
            "art",
            "version",
            "Title",
            SuppressionStage.REMOVE,
            10, 64, -30,
            null,
            "minecraft:overworld",
            42,
            12,
            0,
            0,
            false,
            123L
        ));

        assertThrows(java.io.IOException.class, store::load);
    }
}
