package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class LensWorldIdentityTest {
    @Test
    void normalizesDefaultAndExplicitServerPorts() {
        assertEquals("example.org:25565", LensWorldIdentity.normalizeAddress(" Example.Org "));
        assertEquals("example.org:25570", LensWorldIdentity.normalizeAddress("EXAMPLE.org:25570"));
        assertEquals("[2001:db8::1]:25565", LensWorldIdentity.normalizeAddress("[2001:DB8::1]"));
    }

    @Test
    void isolatesSingleplayerSavesWithoutUploadingTheirPaths() {
        String first = LensWorldIdentity.localWorldHash(Path.of("worlds", "first"));
        String second = LensWorldIdentity.localWorldHash(Path.of("worlds", "second"));

        assertEquals(64, first.length());
        assertNotEquals(first, second);
    }
}
