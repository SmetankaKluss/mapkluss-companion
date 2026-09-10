package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LibraryActionPolicyTest {
    @Test
    void realActionsKeepTheirOwnServiceAuthorization() {
        for (String id : new String[]{"library.install", "library.place", "library.sync", "library.toggle_favorite",
                "library.open_tracker", "library.open_editor"}) {
            assertTrue(LibraryActionPolicy.permits(false, false, id), id);
            assertFalse(LibraryActionPolicy.permits(true, false, id), id);
        }
    }

    @Test
    void fixtureNavigationIncludesTheContextualLensButton() {
        for (String id : new String[]{"nav.library", "nav.lens", "nav.scan", "nav.tracker",
                "nav.account", "library.open_lens", "library.select_art", "library.open_two_layer"}) {
            assertTrue(LibraryActionPolicy.permits(true, false, id), id);
        }
        assertTrue(LibraryActionPolicy.permits(true, true, "library.import_two_layer"));
    }
}
