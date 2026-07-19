package art.mapkluss.companion;

public record ScanHistoryEntry(
    String title,
    String source,
    int wide,
    int tall,
    int missingMaps,
    String localPath,
    String importId,
    String createdArtId,
    String uploadedSha256,
    String uploadedAt,
    String createdAt
) {
    public boolean hasImport() {
        return importId != null && !importId.isBlank();
    }

    public boolean hasCreatedArt() {
        return createdArtId != null && !createdArtId.isBlank();
    }
}
