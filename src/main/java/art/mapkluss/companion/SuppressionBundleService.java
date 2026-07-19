package art.mapkluss.companion;

import java.io.IOException;

public final class SuppressionBundleService {
    private static final String BUNDLE_MIME = "application/vnd.mapkluss.suppression-bundle+zip;version=2";

    private SuppressionBundleService() { }

    public static SuppressionBundle download(CompanionApiClient api, CompanionManifest manifest) throws IOException, InterruptedException {
        SuppressionBundleCatalog catalog = downloadCatalog(api, manifest);
        if (catalog.tiles().size() != 1) throw new IOException("Choose a Two-layer map part first");
        return catalog.tiles().getFirst().bundle();
    }

    public static SuppressionBundleCatalog downloadCatalog(
        CompanionApiClient api,
        CompanionManifest manifest
    ) throws IOException, InterruptedException {
        try {
            return downloadPinned(api, manifest);
        } catch (IOException firstError) {
            if (!isRefreshableDownloadError(firstError)) throw firstError;
            // Signed URLs are intentionally short-lived. Refresh the exact
            // immutable version, then require the same hashes before retrying.
            CompanionManifest refreshed = api.manifest(manifest.artId(), manifest.versionId());
            requireSamePinnedBundle(manifest, refreshed);
            try {
                return downloadPinned(api, refreshed);
            } catch (IOException retryError) {
                retryError.addSuppressed(firstError);
                throw retryError;
            }
        }
    }

    private static boolean isRefreshableDownloadError(IOException error) {
        String message = error.getMessage();
        if (message == null) return false;
        return message.contains("HTTP 400")
            || message.contains("HTTP 401")
            || message.contains("HTTP 403")
            || message.contains("HTTP 404")
            || message.contains("signed download URL");
    }

    private static SuppressionBundleCatalog downloadPinned(
        CompanionApiClient api,
        CompanionManifest manifest
    ) throws IOException, InterruptedException {
        if (manifest.suppressionBundleArtifact().isPresent()) {
            return downloadMultiMap(api, manifest, manifest.suppressionBundleArtifact().orElseThrow());
        }
        return SuppressionBundleCatalog.single(downloadLegacyPair(api, manifest));
    }

    private static SuppressionBundleCatalog downloadMultiMap(
        CompanionApiClient api,
        CompanionManifest manifest,
        CompanionArtifact artifact
    ) throws IOException, InterruptedException {
        validateMetadata(artifact, SuppressionPlanParser.MAX_BUNDLE_BYTES, ".zip", BUNDLE_MIME);
        byte[] bytes = api.downloadArtifactBounded(artifact, SuppressionPlanParser.MAX_BUNDLE_BYTES);
        if (bytes.length != artifact.sizeBytes()) throw new IOException("Two-layer bundle size changed during download");
        String sha256 = SuppressionHashes.sha256(bytes);
        if (!sha256.equalsIgnoreCase(artifact.sha256())) throw new IOException("Two-layer bundle checksum mismatch");

        SuppressionBundleCatalog catalog = SuppressionBundleReader.readCatalog(bytes, artifact.filename());
        CompanionManifest.Grid grid = manifest.grid();
        if (grid == null || grid.wide() != catalog.gridWide() || grid.tall() != catalog.gridTall()
            || catalog.tiles().size() != grid.wide() * grid.tall()) {
            throw new IOException("Two-layer Cloud bundle grid does not match the pinned art version");
        }
        return catalog.withCloudMetadata(manifest.artId(), manifest.versionId(), manifest.title(), sha256);
    }

    private static SuppressionBundle downloadLegacyPair(
        CompanionApiClient api,
        CompanionManifest manifest
    ) throws IOException, InterruptedException {
        CompanionArtifact planArtifact = manifest.suppressionPlanArtifact()
            .orElseThrow(() -> new IOException("Art has no Two-layer plan"));
        CompanionArtifact litematicArtifact = manifest.suppressionLitematicArtifact()
            .orElseThrow(() -> new IOException("Art has no Two-layer Litematic"));
        validatePlanMetadata(planArtifact);
        validateMetadata(litematicArtifact, SuppressionPlanParser.MAX_LITEMATIC_BYTES, ".litematic", "application/octet-stream");
        byte[] planBytes = api.downloadArtifactBounded(planArtifact, SuppressionPlanParser.MAX_PLAN_BYTES);
        byte[] litematicBytes = api.downloadArtifactBounded(litematicArtifact, SuppressionPlanParser.MAX_LITEMATIC_BYTES);
        if (planBytes.length != planArtifact.sizeBytes() || litematicBytes.length != litematicArtifact.sizeBytes()) {
            throw new IOException("Two-layer artifact size changed during download");
        }
        String planSha = SuppressionHashes.sha256(planBytes);
        String litematicSha = SuppressionHashes.sha256(litematicBytes);
        if (!planSha.equalsIgnoreCase(planArtifact.sha256()) || !litematicSha.equalsIgnoreCase(litematicArtifact.sha256())) {
            throw new IOException("Two-layer artifact checksum mismatch");
        }
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(planBytes);
        String parsedContentType = "application/vnd.mapkluss.suppression-plan+json;version=" + parsed.plan().version();
        if (!parsedContentType.equals(planArtifact.contentType())) {
            throw new IOException("Two-layer plan content type does not match its schema version");
        }
        if (!parsed.plan().litematic().filename().equals(litematicArtifact.filename())
            || !parsed.plan().litematic().sha256().equalsIgnoreCase(litematicSha)) {
            throw new IOException("Two-layer plan is pinned to a different Litematic");
        }
        SuppressionBundleReader.validateLitematic(litematicBytes);
        return new SuppressionBundle(
            parsed,
            planBytes,
            litematicBytes,
            planSha,
            litematicSha,
            manifest.artId(),
            manifest.versionId(),
            manifest.title(),
            "cloud"
        );
    }

    private static void requireSamePinnedBundle(CompanionManifest previous, CompanionManifest refreshed) throws IOException {
        if (refreshed == null || !previous.artId().equals(refreshed.artId())
            || !previous.versionId().equals(refreshed.versionId())) {
            throw new IOException("Two-layer Cloud retry returned a different art version");
        }
        if (previous.suppressionBundleArtifact().isPresent()) {
            CompanionArtifact oldBundle = previous.suppressionBundleArtifact().orElseThrow();
            CompanionArtifact newBundle = refreshed.suppressionBundleArtifact()
                .orElseThrow(() -> new IOException("Refreshed manifest has no pinned Two-layer bundle"));
            if (!sameArtifact(oldBundle, newBundle)) {
                throw new IOException("Two-layer Cloud retry changed pinned artifact hashes");
            }
            return;
        }
        CompanionArtifact oldPlan = previous.suppressionPlanArtifact()
            .orElseThrow(() -> new IOException("Original manifest has no Two-layer plan"));
        CompanionArtifact newPlan = refreshed.suppressionPlanArtifact()
            .orElseThrow(() -> new IOException("Refreshed manifest has no Two-layer plan"));
        CompanionArtifact oldLitematic = previous.suppressionLitematicArtifact()
            .orElseThrow(() -> new IOException("Original manifest has no Two-layer Litematic"));
        CompanionArtifact newLitematic = refreshed.suppressionLitematicArtifact()
            .orElseThrow(() -> new IOException("Refreshed manifest has no Two-layer Litematic"));
        if (!sameArtifact(oldPlan, newPlan) || !sameArtifact(oldLitematic, newLitematic)) {
            throw new IOException("Two-layer Cloud retry changed pinned artifact hashes");
        }
    }

    private static boolean sameArtifact(CompanionArtifact first, CompanionArtifact second) {
        return first.sha256().equals(second.sha256())
            && first.filename().equals(second.filename())
            && first.sizeBytes() == second.sizeBytes()
            && first.contentType().equals(second.contentType());
    }

    private static void validateMetadata(CompanionArtifact artifact, int limit, String suffix, String contentType) throws IOException {
        if (artifact.sizeBytes() < 1 || artifact.sizeBytes() > limit || !SuppressionPlanParser.safeFilename(artifact.filename(), suffix)
            || artifact.sha256() == null || !artifact.sha256().matches("[a-f0-9]{64}") || !contentType.equals(artifact.contentType())) {
            throw new IOException("Unsafe Two-layer artifact metadata: " + artifact.kind());
        }
    }

    private static void validatePlanMetadata(CompanionArtifact artifact) throws IOException {
        String contentType = artifact.contentType();
        if (!"application/vnd.mapkluss.suppression-plan+json;version=1".equals(contentType)
            && !"application/vnd.mapkluss.suppression-plan+json;version=2".equals(contentType)
            && !"application/vnd.mapkluss.suppression-plan+json;version=3".equals(contentType)) {
            throw new IOException("Unsafe Two-layer plan content type");
        }
        if (artifact.sizeBytes() < 1 || artifact.sizeBytes() > SuppressionPlanParser.MAX_PLAN_BYTES
            || !SuppressionPlanParser.safeFilename(artifact.filename(), ".json")
            || artifact.sha256() == null || !artifact.sha256().matches("[a-f0-9]{64}")) {
            throw new IOException("Unsafe Two-layer artifact metadata: " + artifact.kind());
        }
    }
}
