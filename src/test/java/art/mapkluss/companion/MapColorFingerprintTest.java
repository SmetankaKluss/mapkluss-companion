package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapColorFingerprintTest {
    @Test
    void hashesRawColorsAsExactUppercaseSha256() {
        assertEquals(
            "4FE7B59AF6DE3B665B67788CC2F99892AB827EFAE3A467342B3BB4E3BC8E5BFE",
            MapColorFingerprint.sha256(new byte[MapColorFingerprint.COLOR_COUNT])
        );
    }

    @Test
    void requiresOneCompleteMapAndValidatesCanonicalHashes() {
        assertThrows(IllegalArgumentException.class, () -> MapColorFingerprint.sha256(new byte[10]));
        assertTrue(MapColorFingerprint.isValid("A".repeat(64)));
        assertFalse(MapColorFingerprint.isValid("a".repeat(64)));
        assertFalse(MapColorFingerprint.isValid("A".repeat(63)));
    }
}
