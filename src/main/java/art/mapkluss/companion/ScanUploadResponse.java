package art.mapkluss.companion;

public record ScanUploadResponse(
    String importId,
    String imagePath,
    String signedUrl,
    String sha256,
    String createdAt,
    boolean reused
) {
}
