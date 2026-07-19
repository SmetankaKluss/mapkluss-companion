package art.mapkluss.companion;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionManifestContractTest {
    private static final Gson GSON = new Gson();

    @Test
    void parsesManifestReturnedByCompanionApi() {
        String json = """
            {
              "artId": "art-1",
              "versionId": "version-2",
              "ownerId": "owner-3",
              "title": "Castle 01",
              "privacy": "unlisted",
              "grid": { "wide": 2, "tall": 3 },
              "mode": "2d",
              "minecraftVersion": "1.21.11",
              "previewUrl": "https://example.com/preview.png",
              "isFavorite": true,
              "collectionIds": ["collection-a", "collection-b"],
              "artifacts": [
                {
                  "id": "artifact-litematic",
                  "kind": "litematic",
                  "filename": "castle_01_2x3.litematic",
                  "storagePath": "companion/owner/art/version/castle_01_2x3.litematic",
                  "signedUrl": "https://example.com/castle_01_2x3.litematic",
                  "contentType": "application/octet-stream",
                  "sizeBytes": 1234,
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                  "updatedAt": "2026-07-01T00:00:00Z"
                }
              ],
              "updatedAt": "2026-07-01T00:00:00Z"
            }
            """;

        CompanionManifest manifest = GSON.fromJson(json, CompanionManifest.class);

        assertEquals("art-1", manifest.artId());
        assertEquals("Castle 01", manifest.title());
        assertEquals(2, manifest.grid().wide());
        assertEquals(3, manifest.grid().tall());
        assertTrue(manifest.isFavorite());
        assertEquals(2, manifest.collectionIds().size());
        assertTrue(manifest.litematicArtifact().isPresent());
        assertEquals("castle_01_2x3.litematic", manifest.litematicArtifact().orElseThrow().filename());
    }

    @Test
    void toleratesMissingOptionalLists() {
        CompanionManifest manifest = GSON.fromJson("""
            {
              "artId": "art-1",
              "versionId": "version-1",
              "title": "Partial",
              "grid": { "wide": 1, "tall": 1 }
            }
            """, CompanionManifest.class);

        assertTrue(manifest.collectionIds().isEmpty());
        assertTrue(manifest.artifacts().isEmpty());
        assertFalse(manifest.litematicArtifact().isPresent());
    }
}
