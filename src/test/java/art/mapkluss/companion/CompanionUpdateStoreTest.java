package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionUpdateStoreTest {
    @TempDir
    Path temp;

    @Test
    void remembersTheReleaseAlreadyShown() throws Exception {
        Path path = temp.resolve("update-notice.json");
        CompanionUpdateStore store = CompanionUpdateStore.load(path);

        assertTrue(store.shouldShow("0.9.0"));
        store.markShown("0.9.0");

        CompanionUpdateStore reloaded = CompanionUpdateStore.load(path);
        assertFalse(reloaded.shouldShow("0.9.0"));
        assertTrue(reloaded.shouldShow("0.9.1"));
    }

    @Test
    void ignoresCorruptStateWithoutTouchingOtherFiles() throws Exception {
        Path path = temp.resolve("update-notice.json");
        Path other = temp.resolve("keep.txt");
        Files.writeString(path, "{broken");
        Files.writeString(other, "keep");

        assertTrue(CompanionUpdateStore.load(path).shouldShow("0.9.0"));
        assertTrue(Files.exists(other));
    }
}
