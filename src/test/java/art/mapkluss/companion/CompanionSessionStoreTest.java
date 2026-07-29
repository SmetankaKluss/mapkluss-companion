package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.Instant;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionSessionStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndClearsSession() throws Exception {
        CompanionSessionStore store = CompanionSessionStore.load(tempDir);
        store.saveSession("token-123", "user-123");

        CompanionSessionStore loaded = CompanionSessionStore.load(tempDir);
        assertTrue(loaded.hasAccessToken());
        assertEquals("user-123", loaded.userId());
        assertNotNull(loaded.savedAt());

        loaded.clear();
        CompanionSessionStore cleared = CompanionSessionStore.load(tempDir);

        assertFalse(cleared.hasAccessToken());
    }

    @Test
    void derivesSessionInfoFromStoredJwt() throws Exception {
        long exp = Instant.now().plusSeconds(3600).getEpochSecond();
        String token = "header." + java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"exp\":" + exp + "}").getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".sig";

        CompanionSessionStore store = CompanionSessionStore.load(tempDir);
        store.saveSession(token, "user-abc-123456789");

        CompanionSessionInfo info = CompanionSessionStore.load(tempDir).sessionInfo();
        assertTrue(info.isSignedIn());
        assertFalse(info.isExpired());
        assertEquals("user-abc...", info.shortUserId());
        assertNotNull(info.expiresAt());
    }

    @Test
    void storesSessionWithOwnerOnlyPermissionsWhenSupported() throws Exception {
        CompanionSessionStore store = CompanionSessionStore.load(tempDir);
        store.saveSession("private-token", "user");
        Path path = tempDir.resolve("config/mapkluss-companion/session.json");
        try {
            assertEquals(java.util.Set.of(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
            ), java.nio.file.Files.getPosixFilePermissions(path));
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are unavailable on this filesystem.
        }
    }
}
