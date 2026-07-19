package art.mapkluss.companion;

public record ScanImportDetails(
    String importId,
    String createdArtId
) {
    public boolean hasCreatedArt() {
        return createdArtId != null && !createdArtId.isBlank();
    }
}
