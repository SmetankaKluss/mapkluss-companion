package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionVersionTest {
    @Test
    void parsesReleaseTagsAndOrdersSemanticVersions() {
        CompanionVersion current = CompanionVersion.parse("0.8.9").orElseThrow();
        CompanionVersion patch = CompanionVersion.parse("v0.8.10").orElseThrow();
        CompanionVersion minor = CompanionVersion.parse("0.9.0").orElseThrow();

        assertTrue(patch.compareTo(current) > 0);
        assertTrue(minor.compareTo(patch) > 0);
        assertEquals("0.9.0", minor.toString());
    }

    @Test
    void rejectsUnsupportedOrMalformedVersions() {
        assertTrue(CompanionVersion.parse("0.9").isEmpty());
        assertTrue(CompanionVersion.parse("0.9.0-beta").isEmpty());
        assertTrue(CompanionVersion.parse("latest").isEmpty());
    }
}
