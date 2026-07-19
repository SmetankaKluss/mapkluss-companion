package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensPreferencesTest {
    @TempDir
    Path runDirectory;

    @Test
    void ownPlacementRecoversFromAccidentalLocalModeration() throws Exception {
        LensPreferences preferences = LensPreferences.load(runDirectory);
        preferences.hide("placement-1");
        preferences.block("owner-1");

        preferences.allowOwnPlacement("placement-1", "owner-1");

        assertFalse(preferences.hiddenPlacementIds().contains("placement-1"));
        assertFalse(preferences.blockedOwnerKeys().contains("owner-1"));

        LensPreferences reloaded = LensPreferences.load(runDirectory);
        assertFalse(reloaded.hiddenPlacementIds().contains("placement-1"));
        assertFalse(reloaded.blockedOwnerKeys().contains("owner-1"));
    }

    @Test
    void recoveryDoesNotClearUnrelatedModeration() throws Exception {
        LensPreferences preferences = LensPreferences.load(runDirectory);
        preferences.hide("other-placement");
        preferences.block("other-owner");

        preferences.allowOwnPlacement("placement-1", "owner-1");

        assertTrue(preferences.hiddenPlacementIds().contains("other-placement"));
        assertTrue(preferences.blockedOwnerKeys().contains("other-owner"));
    }
}
