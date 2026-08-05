package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionSyncServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void removesAllInstalledLitematicsForArtOnly() throws Exception {
        Path schematics = tempDir.resolve("schematics");
        Files.createDirectories(schematics);
        Path oldFile = schematics.resolve("old.litematic");
        Path renamedFile = schematics.resolve("renamed.litematic");
        Path otherFile = schematics.resolve("other.litematic");
        Files.writeString(oldFile, "old");
        Files.writeString(renamedFile, "renamed");
        Files.writeString(otherFile, "other");

        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed.json"));
        index.upsert(new InstalledArtifact("art", "artifact-old", "sha-old", oldFile.toString(), oldFile.getFileName().toString(), 1L));
        index.upsert(new InstalledArtifact("art", "artifact-renamed", "sha-renamed", renamedFile.toString(), renamedFile.getFileName().toString(), 2L));
        index.upsert(new InstalledArtifact("other-art", "artifact-other", "sha-other", otherFile.toString(), otherFile.getFileName().toString(), 3L));

        CompanionApiClient api = new CompanionApiClient("http://127.0.0.1", "anon");
        CompanionSyncService service = new CompanionSyncService(api, tempDir, index, schematics);

        assertTrue(service.removeLitematicsForArt("art"));

        assertFalse(Files.exists(oldFile));
        assertFalse(Files.exists(renamedFile));
        assertTrue(Files.exists(otherFile));
        assertEquals(0, index.findByArt("art").size());
        assertEquals(1, index.findByArt("other-art").size());
    }

    @Test
    void removeLitematicsForArtReturnsFalseWhenNothingInstalled() throws Exception {
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("empty-installed.json"));
        CompanionApiClient api = new CompanionApiClient("http://127.0.0.1", "anon");
        CompanionSyncService service = new CompanionSyncService(api, tempDir, index, tempDir.resolve("schematics"));

        assertFalse(service.removeLitematicsForArt("missing-art"));
    }

    @Test
    void refreshArtDoesNotDownloadAnUnchangedInstalledLitematic() throws Exception {
        Path schematics = tempDir.resolve("schematics-current");
        Files.createDirectories(schematics);
        Path installedFile = schematics.resolve("current.litematic");
        Files.writeString(installedFile, "already installed");

        CompanionArtifact artifact = artifact("artifact-current", "litematic", "abc");
        CompanionManifest manifest = manifest("art-current", List.of(artifact));
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("current-installed.json"));
        index.upsert(new InstalledArtifact(
            manifest.artId(), artifact.id(), artifact.sha256(), installedFile.toString(), installedFile.getFileName().toString(), 1L
        ));
        FakeApi api = new FakeApi(manifest);
        CompanionSyncService service = new CompanionSyncService(api, tempDir, index, schematics);

        ArtRefreshResult result = service.refreshArt(manifest.artId());

        assertFalse(result.syncedInstalledLitematic());
        assertEquals(0, api.downloadCount);
        assertEquals(installedFile.toString(), result.installedArtifact().path());
    }

    @Test
    void refreshInstalledDoesNotRedownloadAnUnchangedSplitBundle() throws Exception {
        Path schematics = tempDir.resolve("schematics-split");
        Files.createDirectories(schematics);
        Path first = schematics.resolve("tile-1.litematic");
        Path second = schematics.resolve("tile-2.litematic");
        Files.writeString(first, "tile one");
        Files.writeString(second, "tile two");

        CompanionArtifact whole = artifact("whole", "litematic", "whole-sha");
        CompanionArtifact bundle = artifact("bundle-current", "litematic_tiles_zip", "bundle-sha");
        CompanionManifest manifest = manifest("art-split", List.of(whole, bundle));
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("split-installed.json"));
        index.upsert(new InstalledArtifact(manifest.artId(), bundle.id() + "#1", "tile-1-sha", first.toString(), first.getFileName().toString(), 1L));
        index.upsert(new InstalledArtifact(manifest.artId(), bundle.id() + "#2", "tile-2-sha", second.toString(), second.getFileName().toString(), 1L));
        FakeApi api = new FakeApi(manifest);
        CompanionSyncService service = new CompanionSyncService(api, tempDir, index, schematics);

        SyncInstalledResult result = service.refreshInstalledLitematics();

        assertEquals(1, result.checked());
        assertEquals(0, result.refreshed());
        assertEquals(0, result.failed());
        assertEquals(0, api.downloadCount);
    }

    private static CompanionArtifact artifact(String id, String kind, String sha256) {
        return new CompanionArtifact(
            id,
            kind,
            id + ("litematic".equals(kind) ? ".litematic" : ".zip"),
            "companion/test/" + id,
            "https://example.invalid/" + id,
            "application/octet-stream",
            1,
            sha256,
            "2026-08-01T00:00:00Z"
        );
    }

    private static CompanionManifest manifest(String artId, List<CompanionArtifact> artifacts) {
        return new CompanionManifest(
            artId,
            "version",
            "owner",
            "Fixture",
            "private",
            new CompanionManifest.Grid(1, 1),
            "3d",
            "1.21.11",
            "classic",
            null,
            false,
            List.of(),
            artifacts,
            "2026-08-01T00:00:00Z"
        );
    }

    private static final class FakeApi extends CompanionApiClient {
        private final CompanionManifest manifest;
        private int downloadCount;

        private FakeApi(CompanionManifest manifest) {
            super("https://example.invalid", "anon");
            this.manifest = manifest;
        }

        @Override
        public CompanionManifest manifest(String artId) {
            assertEquals(manifest.artId(), artId);
            return manifest;
        }

        @Override
        public byte[] downloadArtifact(CompanionArtifact artifact) {
            downloadCount += 1;
            throw new AssertionError("unchanged artifacts must not be downloaded");
        }
    }
}
