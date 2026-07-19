package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapPreviewStoreTest {
    @TempDir
    Path temporary;

    @Test
    void persistsPreviewPerConnectionWithoutLeakingConnectionIntoPath() throws Exception {
        MapPreviewStore store = new MapPreviewStore(temporary.resolve("previews"));
        byte[] colors = colors(7);
        MapPreviewStore.Entry expected = new MapPreviewStore.Entry(42, MapColorFingerprint.sha256(colors), colors);

        store.write("server.example|minecraft:overworld", expected);

        MapPreviewStore.Entry restored = store.read("server.example|minecraft:overworld", 42).orElseThrow();
        assertArrayEquals(colors, restored.colors());
        assertFalse(store.entryPath("server.example|minecraft:overworld", 42).toString().contains("server.example"));
        assertTrue(store.read("other.example|minecraft:overworld", 42).isEmpty());
        assertFalse(Files.exists(store.entryPath("server.example|minecraft:overworld", 42).resolveSibling("42.mkmap.tmp")));
    }

    @Test
    void rejectsCorruptOrMismatchedEntries() throws Exception {
        MapPreviewStore store = new MapPreviewStore(temporary.resolve("previews"));
        byte[] colors = colors(11);
        MapPreviewStore.Entry entry = new MapPreviewStore.Entry(5, MapColorFingerprint.sha256(colors), colors);
        store.write("world", entry);
        Path path = store.entryPath("world", 5);
        Files.write(path, new byte[]{1, 2, 3, 4});

        assertTrue(store.read("world", 5).isEmpty());
        assertNotEquals(store.entryPath("world-a", 5), store.entryPath("world-b", 5));
    }

    private static byte[] colors(int seed) {
        byte[] colors = new byte[MapPreviewStore.MAP_PIXELS];
        for (int index = 0; index < colors.length; index++) colors[index] = (byte) (index * 31 + seed);
        return colors;
    }
}
