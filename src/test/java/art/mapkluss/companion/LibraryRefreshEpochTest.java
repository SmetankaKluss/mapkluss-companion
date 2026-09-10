package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LibraryRefreshEpochTest {
    @Test
    void queuedAuthFailureCannotClearSessionAfterLeavingAndReturning() {
        LibraryRefreshEpoch epoch = new LibraryRefreshEpoch();
        long request = epoch.begin();
        java.util.concurrent.atomic.AtomicBoolean signedIn = new java.util.concurrent.atomic.AtomicBoolean(true);
        Runnable queuedFailure = () -> epoch.runIfCurrent(request, () -> signedIn.set(false));
        epoch.begin(); // screen removed
        epoch.begin(); // a new library request after return
        queuedFailure.run();
        assertTrue(signedIn.get());
    }

    @Test
    void onlyCurrentQueuedResultUpdatesTheScreen() {
        LibraryRefreshEpoch epoch = new LibraryRefreshEpoch();
        long first = epoch.begin();
        long second = epoch.begin();
        java.util.List<String> applied = new java.util.ArrayList<>();
        epoch.runIfCurrent(second, () -> applied.add("new"));
        epoch.runIfCurrent(first, () -> applied.add("old"));
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("new"), applied);
    }

    @Test
    void acceptsOnlyTheMostRecentRefresh() {
        LibraryRefreshEpoch epoch = new LibraryRefreshEpoch();

        long first = epoch.begin();
        long second = epoch.begin();

        assertFalse(epoch.accepts(first));
        assertTrue(epoch.accepts(second));
    }
}
