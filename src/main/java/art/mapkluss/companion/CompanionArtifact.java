package art.mapkluss.companion;

public record CompanionArtifact(
    String id,
    String kind,
    String filename,
    String storagePath,
    String signedUrl,
    String contentType,
    long sizeBytes,
    String sha256,
    String updatedAt
) {
    public boolean isLitematic() {
        return "litematic".equals(kind);
    }

    public boolean isLitematicTilesZip() {
        return "litematic_tiles_zip".equals(kind);
    }

    public boolean isSuppressionLitematic() {
        return "suppression_litematic".equals(kind);
    }

    public boolean isSuppressionPlan() {
        return "suppression_plan".equals(kind);
    }

    public boolean isSuppressionBundle() {
        return "suppression_bundle".equals(kind);
    }
}
