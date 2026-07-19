package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CompanionApiClientBoundedDownloadTest {
    @Test
    void acceptsResponseAtTheLimit() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3, 4};
        assertArrayEquals(bytes, CompanionApiClient.readBounded(new ByteArrayInputStream(bytes), bytes.length));
    }

    @Test
    void abortsBeforeKeepingAnOversizedResponse() {
        byte[] bytes = new byte[32];
        assertThrows(IOException.class, () -> CompanionApiClient.readBounded(new ByteArrayInputStream(bytes), 16));
    }

    @Test
    void refusesPublicStorageFallbackWhenSignedUrlIsMissing() {
        CompanionArtifact artifact = new CompanionArtifact(
            "id", "suppression_plan", "plan.json", "companion/owner/art/version/plan.json", null,
            "application/json", 4, "sha", "2026-07-18T00:00:00Z"
        );
        assertThrows(IOException.class, () -> CompanionApiClient.requireSignedArtifactUri(artifact));
    }

    @Test
    void acceptsOnlyAbsoluteHttpSignedUrls() throws Exception {
        CompanionArtifact signed = new CompanionArtifact(
            "id", "suppression_plan", "plan.json", "companion/owner/art/version/plan.json",
            "https://project.supabase.co/storage/v1/object/sign/private/file", "application/json", 4, "sha", "2026-07-18T00:00:00Z"
        );
        assertEquals("https://project.supabase.co/storage/v1/object/sign/private/file", CompanionApiClient.requireSignedArtifactUri(signed).toString());

        CompanionArtifact relative = new CompanionArtifact(
            "id", "suppression_plan", "plan.json", "companion/owner/art/version/plan.json",
            "/unsigned/path", "application/json", 4, "sha", "2026-07-18T00:00:00Z"
        );
        assertThrows(IOException.class, () -> CompanionApiClient.requireSignedArtifactUri(relative));

        CompanionArtifact privateNetwork = new CompanionArtifact(
            "id", "suppression_plan", "plan.json", "companion/owner/art/version/plan.json",
            "http://192.168.1.20/private", "application/json", 4, "sha", "2026-07-18T00:00:00Z"
        );
        assertThrows(IOException.class, () -> CompanionApiClient.requireSignedArtifactUri(privateNetwork));

        CompanionArtifact localDev = new CompanionArtifact(
            "id", "suppression_plan", "plan.json", "companion/owner/art/version/plan.json",
            "http://127.0.0.1:54321/storage/signed", "application/json", 4, "sha", "2026-07-18T00:00:00Z"
        );
        assertEquals("http://127.0.0.1:54321/storage/signed",
            CompanionApiClient.requireSignedArtifactUri(localDev).toString());

        assertThrows(IOException.class, () -> CompanionApiClient.requireTrustedDownloadUri("https://example.com/preview.png"));
    }
}
