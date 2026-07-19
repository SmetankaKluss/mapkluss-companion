package art.mapkluss.companion;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record CompanionManifest(
    String artId,
    String versionId,
    String ownerId,
    String title,
    String privacy,
    Grid grid,
    String mode,
    String minecraftVersion,
    String buildTechnique,
    String previewUrl,
    boolean isFavorite,
    List<String> collectionIds,
    List<CompanionArtifact> artifacts,
    String updatedAt
) {
    public CompanionManifest {
        collectionIds = collectionIds == null ? Collections.emptyList() : List.copyOf(collectionIds);
        artifacts = artifacts == null ? Collections.emptyList() : List.copyOf(artifacts);
    }

    public Optional<CompanionArtifact> litematicArtifact() {
        return artifacts.stream().filter(CompanionArtifact::isLitematic).findFirst();
    }

    public Optional<CompanionArtifact> litematicTilesArtifact() {
        return artifacts.stream().filter(CompanionArtifact::isLitematicTilesZip).findFirst();
    }

    public Optional<CompanionArtifact> mapDatArtifact() {
        return artifacts.stream().filter(artifact -> "mapdat_zip".equals(artifact.kind())).findFirst();
    }

    public Optional<CompanionArtifact> suppressionLitematicArtifact() {
        return artifacts.stream().filter(CompanionArtifact::isSuppressionLitematic).findFirst();
    }

    public Optional<CompanionArtifact> suppressionPlanArtifact() {
        return artifacts.stream().filter(CompanionArtifact::isSuppressionPlan).findFirst();
    }

    public Optional<CompanionArtifact> suppressionBundleArtifact() {
        return artifacts.stream().filter(CompanionArtifact::isSuppressionBundle).findFirst();
    }

    public boolean hasSuppressionBundle() {
        return suppressionBundleArtifact().isPresent()
            || (suppressionLitematicArtifact().isPresent() && suppressionPlanArtifact().isPresent());
    }

    public record Grid(int wide, int tall) {
    }
}
