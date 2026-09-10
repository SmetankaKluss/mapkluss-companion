package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LibraryOpenPolicyTest {
    @Test void resizeDoesNotReloadOrRepeatInitialSync() {
        var policy = new LibraryOpenPolicy();
        assertEquals(LibraryOpenPolicy.Load.SYNC, policy.enter(true));
        for (int i = 0; i < 20; i++) assertEquals(LibraryOpenPolicy.Load.NONE, policy.enter(true));
        policy.leave();
        assertEquals(LibraryOpenPolicy.Load.REFRESH, policy.enter(true));
    }

    @Test void returnRefreshesButInvalidatesOldRequest() {
        var policy = new LibraryOpenPolicy();
        var epoch = new LibraryRefreshEpoch();
        assertEquals(LibraryOpenPolicy.Load.REFRESH, policy.enter(false));
        long old = epoch.begin();
        policy.leave();
        epoch.begin();
        assertFalse(epoch.accepts(old));
        assertEquals(LibraryOpenPolicy.Load.REFRESH, policy.enter(false));
        assertFalse(epoch.accepts(old));
        assertTrue(epoch.accepts(epoch.begin()));
    }

    @Test void allAdaptersWireLifecycleAndGuardTrackerNavigation() throws Exception {
        for (String family : new String[]{"minecraft1214", "minecraft1218plus", "minecraft262"}) {
            String source = Files.readString(Path.of("src/" + family + "/java/art/mapkluss/companion/CompanionLibraryScreen.java"));
            assertTrue(source.contains("openPolicy.enter(syncOnOpen)"));
            assertTrue(source.contains("public void removed()"));
            assertTrue(source.contains("openPolicy.leave();"));
            assertTrue(source.contains("developmentFixtureActive || applyDevelopmentFixtureIfEnabled()"));
            assertTrue(source.contains("if (!libraryRefresh.accepts(trackerEpoch)"));
        }
    }
}
