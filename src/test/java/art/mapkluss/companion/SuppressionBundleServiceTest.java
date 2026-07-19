package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionBundleServiceTest {
    @Test
    void refreshesExpiredUrlsForTheExactPinnedVersion() throws Exception {
        byte[] litematic = SuppressionTestFixtures.litematicV3Bytes();
        byte[] plan = SuppressionTestFixtures.planV3Bytes(litematic);
        CompanionManifest original = manifest(plan, litematic, "expired", null);
        CompanionManifest refreshed = manifest(plan, litematic, "fresh", null);
        FakeApi api = new FakeApi(refreshed, plan, litematic, true);

        SuppressionBundle bundle = SuppressionBundleService.download(api, original);

        assertEquals("art-1", bundle.artId());
        assertEquals("version-7", bundle.versionId());
        assertEquals("art-1", api.requestedArtId);
        assertEquals("version-7", api.requestedVersionId);
        assertEquals(1, api.refreshCount);
    }

    @Test
    void rejectsARefreshThatChangesPinnedHashes() throws Exception {
        byte[] litematic = SuppressionTestFixtures.litematicV3Bytes();
        byte[] plan = SuppressionTestFixtures.planV3Bytes(litematic);
        CompanionManifest original = manifest(plan, litematic, "expired", null);
        CompanionManifest changed = manifest(plan, litematic, "fresh", "0".repeat(64));
        FakeApi api = new FakeApi(changed, plan, litematic, true);

        IOException error = assertThrows(IOException.class, () -> SuppressionBundleService.download(api, original));

        assertTrue(error.getMessage().contains("changed pinned artifact hashes"));
        assertEquals("version-7", api.requestedVersionId);
    }

    @Test
    void downloadsAndPinsAMultiMapCloudBundle() throws Exception {
        byte[] zip = SuppressionTestFixtures.multiZipBytes();
        CompanionArtifact artifact = new CompanionArtifact(
            "artifact-bundle",
            "suppression_bundle",
            "fixture_2x1_two_layer.zip",
            "private/fixture_2x1_two_layer.zip",
            "https://example.invalid/bundle",
            "application/vnd.mapkluss.suppression-bundle+zip;version=2",
            zip.length,
            SuppressionHashes.sha256(zip),
            "2026-07-19T00:00:00Z"
        );
        CompanionManifest manifest = new CompanionManifest(
            "art-1", "version-8", "owner-3", "Cloud Fixture", "private",
            new CompanionManifest.Grid(2, 1), "3d", "1.21.11", "suppression_two_layer",
            null, false, List.of(), List.of(artifact), "2026-07-19T00:00:00Z"
        );
        FakeBundleApi api = new FakeBundleApi(zip);

        SuppressionBundleCatalog catalog = SuppressionBundleService.downloadCatalog(api, manifest);

        assertEquals(2, catalog.tiles().size());
        assertEquals("cloud", catalog.source());
        assertEquals("version-8", catalog.versionId());
        assertEquals("art-1", catalog.tiles().get(1).bundle().artId());
        assertTrue(catalog.tiles().get(1).bundle().title().contains("2/2"));
    }

    @Test
    void doesNotRedownloadACorruptPinnedBundle() throws Exception {
        byte[] zip = SuppressionTestFixtures.multiZipBytes();
        CompanionArtifact artifact = new CompanionArtifact(
            "artifact-bundle", "suppression_bundle", "fixture_2x1_two_layer.zip",
            "private/fixture_2x1_two_layer.zip", "https://example.invalid/bundle",
            "application/vnd.mapkluss.suppression-bundle+zip;version=2",
            zip.length, "0".repeat(64), "2026-07-19T00:00:00Z"
        );
        CompanionManifest manifest = new CompanionManifest(
            "art-1", "version-8", "owner-3", "Cloud Fixture", "private",
            new CompanionManifest.Grid(2, 1), "3d", "1.21.11", "suppression_two_layer",
            null, false, List.of(), List.of(artifact), "2026-07-19T00:00:00Z"
        );
        FakeBundleApi api = new FakeBundleApi(zip);

        IOException error = assertThrows(IOException.class, () -> SuppressionBundleService.downloadCatalog(api, manifest));

        assertTrue(error.getMessage().contains("checksum mismatch"));
        assertEquals(0, api.refreshCount);
    }

    private static CompanionManifest manifest(
        byte[] plan,
        byte[] litematic,
        String urlGeneration,
        String planShaOverride
    ) throws Exception {
        String planSha = planShaOverride == null ? SuppressionHashes.sha256(plan) : planShaOverride;
        List<CompanionArtifact> artifacts = List.of(
            new CompanionArtifact(
                "artifact-plan",
                "suppression_plan",
                "fixture_1x1_suppression_plan.json",
                "private/fixture-plan.json",
                "https://example.invalid/" + urlGeneration + "/plan",
                "application/vnd.mapkluss.suppression-plan+json;version=3",
                plan.length,
                planSha,
                "2026-07-17T00:00:00Z"
            ),
            new CompanionArtifact(
                "artifact-litematic",
                "suppression_litematic",
                "fixture_1x1_suppression.litematic",
                "private/fixture.litematic",
                "https://example.invalid/" + urlGeneration + "/litematic",
                "application/octet-stream",
                litematic.length,
                SuppressionHashes.sha256(litematic),
                "2026-07-17T00:00:00Z"
            )
        );
        return new CompanionManifest(
            "art-1",
            "version-7",
            "owner-3",
            "Fixture",
            "private",
            new CompanionManifest.Grid(1, 1),
            "3d",
            "1.21.11",
            "suppression_two_layer",
            null,
            false,
            List.of(),
            artifacts,
            "2026-07-17T00:00:00Z"
        );
    }

    private static final class FakeApi extends CompanionApiClient {
        private final CompanionManifest refreshed;
        private final byte[] plan;
        private final byte[] litematic;
        private final boolean expireFirstRequest;
        private int downloadCount;
        private int refreshCount;
        private String requestedArtId;
        private String requestedVersionId;

        private FakeApi(
            CompanionManifest refreshed,
            byte[] plan,
            byte[] litematic,
            boolean expireFirstRequest
        ) {
            super("https://example.invalid", "anon");
            this.refreshed = refreshed;
            this.plan = plan;
            this.litematic = litematic;
            this.expireFirstRequest = expireFirstRequest;
        }

        @Override
        public CompanionManifest manifest(String artId, String versionId) {
            requestedArtId = artId;
            requestedVersionId = versionId;
            refreshCount++;
            return refreshed;
        }

        @Override
        public byte[] downloadArtifactBounded(CompanionArtifact artifact, int maxBytes) throws IOException {
            downloadCount++;
            if (expireFirstRequest && downloadCount == 1) {
                throw new IOException("MapKluss API failed with HTTP 403: expired signed URL");
            }
            return artifact.isSuppressionPlan() ? plan : litematic;
        }
    }

    private static final class FakeBundleApi extends CompanionApiClient {
        private final byte[] bytes;
        private int refreshCount;

        private FakeBundleApi(byte[] bytes) {
            super("https://example.invalid", "anon");
            this.bytes = bytes;
        }

        @Override
        public byte[] downloadArtifactBounded(CompanionArtifact artifact, int maxBytes) {
            return bytes;
        }

        @Override
        public CompanionManifest manifest(String artId, String versionId) {
            refreshCount++;
            throw new AssertionError("Corrupt bundles must not refresh signed URLs");
        }
    }
}
