package art.mapkluss.companion;

public record ArtRefreshResult(
    CompanionManifest manifest,
    InstalledArtifact installedArtifact,
    boolean syncedInstalledLitematic
) {
}
