package art.mapkluss.companion;

public record InstalledArtifact(
    String artId,
    String artifactId,
    String sha256,
    String path,
    String filename,
    long installedAt
) {
}
