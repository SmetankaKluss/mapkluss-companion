package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoFrameMapRegistryTest {
    @TempDir
    Path temporary;

    @Test
    void keepsMapIdsSeparatePerConnectionAndPersistsLatestBinding() throws Exception {
        Path path = temporary.resolve("registry.json");
        AutoFrameTemplate first = template("first");
        AutoFrameTemplate second = template("second");
        AutoFrameMapRegistry registry = AutoFrameMapRegistry.load(path);

        registry.remember("server-a|overworld", 17, first, 0);
        registry.remember("server-b|overworld", 17, second, 1);
        registry.remember("server-a|overworld", 17, second, 0);
        registry.save();

        AutoFrameMapRegistry restored = AutoFrameMapRegistry.load(path);
        assertEquals("second", restored.find("server-a|overworld", 17).orElseThrow().artId());
        assertEquals(0, restored.find("server-a|overworld", 17).orElseThrow().tileIndex());
        assertEquals("A".repeat(64), restored.find("server-a|overworld", 17).orElseThrow().tileHash());
        assertEquals("second", restored.find("server-b|overworld", 17).orElseThrow().artId());
        assertEquals(1, restored.find("server-b|overworld", 17).orElseThrow().tileIndex());
        assertTrue(restored.find("server-c|overworld", 17).isEmpty());
    }

    @Test
    void readsLegacyBindingWithoutFingerprintAndCanForgetStaleMapId() throws Exception {
        Path path = temporary.resolve("legacy.json");
        Files.writeString(path, """
            {
              "bindings": [{
                "connectionKey": "server|overworld",
                "mapId": 9,
                "artId": "legacy",
                "versionId": "v1",
                "tileIndex": 0
              }]
            }
            """);

        AutoFrameMapRegistry registry = AutoFrameMapRegistry.load(path);

        assertTrue(registry.find("server|overworld", 9).isPresent());
        assertNull(registry.find("server|overworld", 9).orElseThrow().tileHash());
        assertTrue(registry.forget("server|overworld", 9));
        assertTrue(registry.find("server|overworld", 9).isEmpty());
    }

    private static AutoFrameTemplate template(String id) {
        return new AutoFrameTemplate(
            id,
            id + "-v1",
            id,
            2,
            1,
            List.of("A".repeat(64), "B".repeat(64)),
            "2026-07-10T00:00:00Z"
        );
    }
}
