package art.mapkluss.companion;

import java.nio.file.Path;

public record MapDatImportResult(
    int count,
    int startMapId,
    int endMapId,
    Path backupPath
) {
}
