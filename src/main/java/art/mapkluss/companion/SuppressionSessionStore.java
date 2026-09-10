package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SuppressionSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;

    private SuppressionSessionStore(Path path) {
        this.path = path;
    }

    public static SuppressionSessionStore forRunDir(Path runDir) {
        return new SuppressionSessionStore(runDir.resolve("config").resolve("mapkluss-companion").resolve("suppression-session.json"));
    }

    public StoredSession load() throws IOException {
        if (!Files.exists(path)) return null;
        final StoredSession session;
        try {
            byte[] bytes;
            try (InputStream input = Files.newInputStream(path)) {
                bytes = CompanionApiClient.readBounded(input, 1024 * 1024);
            }
            if (bytes.length == 0) throw new IOException("Stored Two-layer session is empty");
            session = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), StoredSession.class);
        } catch (RuntimeException error) {
            throw new IOException("Stored Two-layer session is invalid", error);
        }
        if (session == null || (session.formatVersion() < 1 || session.formatVersion() > 4)
            || session.planPath() == null || session.schematicPath() == null
            || session.planSha256() == null || !session.planSha256().matches("[a-f0-9]{64}")
            || session.litematicSha256() == null || !session.litematicSha256().matches("[a-f0-9]{64}")
            || session.stage() == null
            || (session.formatVersion() >= 3 && session.mapId() < 0)
            || (session.stage() != SuppressionStage.WAITING_ANCHOR
                && (session.worldHash() == null || !session.worldHash().matches("[a-f0-9]{64}")
                    || !"minecraft:overworld".equals(session.dimension()) || session.mapId() < 0
                    || Math.abs((long) session.anchorX()) > 30_000_000L || Math.abs((long) session.anchorZ()) > 30_000_000L
                    || Math.abs((long) session.anchorY()) > 4096L))
            || session.phaseIndex() < 0 || session.phaseIndex() > 64
            || session.standPointIndex() < 0 || session.standPointIndex() > 32 || session.dwellTicks() < 0 || session.dwellTicks() > 12000) {
            throw new IOException("Stored Two-layer session failed validation");
        }
        return session;
    }

    public void save(StoredSession session) throws IOException {
        AtomicFiles.writePrivateUtf8(path, GSON.toJson(session));
    }

    static void validateForPlan(StoredSession session, SuppressionPlan plan, SuppressionStage restoredStage) throws IOException {
        if (session == null || plan == null || restoredStage == null || plan.phases() == null || plan.phases().isEmpty()) {
            throw new IOException("Stored Two-layer session has no matching plan state");
        }
        int phase = session.phaseIndex();
        int point = session.standPointIndex();
        int lastPhase = plan.phases().size() - 1;
        if (phase < 0 || phase > lastPhase) throw new IOException("Stored Two-layer phase is outside the plan");

        boolean valid = switch (restoredStage) {
            case WAITING_ANCHOR, ANCHOR_CONFIRM, BUILDING, INITIAL_MOVE -> phase == 0 && point == 0;
            case INITIAL_VERIFY -> phase == 0 && point == plan.initialCapture().standPoints().size();
            case REMOVE -> point == 0;
            case MOVE -> point >= 0 && point < plan.phases().get(phase).standPoints().size();
            case VERIFY, READY_NEXT -> point == plan.phases().get(phase).standPoints().size();
            case COMPLETE -> phase == lastPhase && point == plan.phases().get(lastPhase).standPoints().size();
            case INITIAL_EQUIP, INITIAL_DWELL, INITIAL_STOW, EQUIP, DWELL, STOW, PAUSED -> false;
        };
        if (!valid) throw new IOException("Stored Two-layer stage does not match its phase progress");
    }

    public void clear() throws IOException {
        Files.deleteIfExists(path);
    }

    public record StoredSession(
        int formatVersion,
        String planPath,
        String schematicPath,
        String planSha256,
        String litematicSha256,
        String artId,
        String versionId,
        String title,
        SuppressionStage stage,
        int anchorX,
        int anchorY,
        int anchorZ,
        String worldHash,
        String dimension,
        int mapId,
        int phaseIndex,
        int standPointIndex,
        int dwellTicks,
        boolean manualOverrideArmed,
        long updatedAt,
        LiveBuildCatalogLink trackerSource
    ) {
        public StoredSession(int formatVersion, String planPath, String schematicPath, String planSha256,
            String litematicSha256, String artId, String versionId, String title, SuppressionStage stage,
            int anchorX, int anchorY, int anchorZ, String worldHash, String dimension, int mapId,
            int phaseIndex, int standPointIndex, int dwellTicks, boolean manualOverrideArmed, long updatedAt) {
            this(formatVersion, planPath, schematicPath, planSha256, litematicSha256, artId, versionId,
                title, stage, anchorX, anchorY, anchorZ, worldHash, dimension, mapId, phaseIndex,
                standPointIndex, dwellTicks, manualOverrideArmed, updatedAt, null);
        }
    }
}
