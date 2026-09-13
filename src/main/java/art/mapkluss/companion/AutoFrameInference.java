package art.mapkluss.companion;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

final class AutoFrameInference {
    private AutoFrameInference() {}

    static String namespace(String connection) {
        return "local-v2-" + UUID.nameUUIDFromBytes(connection.getBytes(StandardCharsets.UTF_8)) + "-";
    }

    static AutoFrameTemplate template(MapArtLayoutSolver.Layout layout, String connection) {
        String signature = layout.wide() + "x" + layout.tall() + ":" + layout.tileMapIds()
            + ":" + String.join(",", layout.tileHashes());
        String id = namespace(connection) + UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
        return new AutoFrameTemplate(id, id, "Local " + layout.wide() + "x" + layout.tall(),
            layout.wide(), layout.tall(), layout.tileHashes(), layout.tileMapIds(), Instant.now().toString());
    }
}
