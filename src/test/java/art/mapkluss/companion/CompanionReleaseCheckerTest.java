package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionReleaseCheckerTest {
    @Test
    void acceptsOnlyANewerStableRelease() {
        CompanionVersion current = CompanionVersion.parse("0.8.0").orElseThrow();
        byte[] body = "{\"tag_name\":\"v0.9.0\"}".getBytes(StandardCharsets.UTF_8);

        CompanionReleaseChecker.Release release =
            CompanionReleaseChecker.parseLatestRelease(200, body, current).orElseThrow();

        assertEquals("0.9.0", release.version());
        assertTrue(CompanionReleaseChecker.parseLatestRelease(
            200, "{\"tag_name\":\"v0.8.0\"}".getBytes(StandardCharsets.UTF_8), current
        ).isEmpty());
    }

    @Test
    void rejectsErrorsMalformedJsonAndOversizedBodies() {
        CompanionVersion current = CompanionVersion.parse("0.8.0").orElseThrow();
        assertTrue(CompanionReleaseChecker.parseLatestRelease(
            500, "{\"tag_name\":\"v0.9.0\"}".getBytes(StandardCharsets.UTF_8), current
        ).isEmpty());
        assertTrue(CompanionReleaseChecker.parseLatestRelease(
            200, "not-json".getBytes(StandardCharsets.UTF_8), current
        ).isEmpty());
        assertTrue(CompanionReleaseChecker.parseLatestRelease(
            200, new byte[CompanionReleaseChecker.MAX_RESPONSE_BYTES + 1], current
        ).isEmpty());
    }
}
