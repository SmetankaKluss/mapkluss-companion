package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LensTextureAtlasTest {
    @Test
    void acceptsOnlyBoundedTextureDimensions() {
        assertDoesNotThrow(() -> LensTextureAtlas.validateExpectedDimensions(1_600, 1_600));
        assertDoesNotThrow(() -> LensTextureAtlas.validateExpectedDimensions(4_096, 4_096));
        assertThrows(IOException.class, () -> LensTextureAtlas.validateExpectedDimensions(4_097, 16));
        assertThrows(IOException.class, () -> LensTextureAtlas.validateExpectedDimensions(4_096, 4_097));
    }

    @Test
    void validatesPngSignatureAndDimensionsBeforeDecode() {
        byte[] pngHeader = new byte[24];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, pngHeader, 0, signature.length);
        writeInt(pngHeader, 16, 1_600);
        writeInt(pngHeader, 20, 1_600);
        assertDoesNotThrow(() -> LensTextureAtlas.validatePngHeader(pngHeader, 1_600, 1_600));
        assertThrows(IOException.class, () -> LensTextureAtlas.validatePngHeader(pngHeader, 1_599, 1_600));
        pngHeader[0] = 0;
        assertThrows(IOException.class, () -> LensTextureAtlas.validatePngHeader(pngHeader, 1_600, 1_600));
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
