package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        if (Files.size(path) > 1024 * 1024) throw new IOException("Stored Two-layer session is too large");
        final StoredSession session;
        try {
            session = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), StoredSession.class);
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
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "suppression-session", ".tmp");
        try {
            Files.writeString(temp, GSON.toJson(session), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
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
        long updatedAt
    ) { }
}
