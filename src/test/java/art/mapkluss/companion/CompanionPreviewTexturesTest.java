package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CompanionPreviewTexturesTest {
    @Test
    void acceptsBoundedPngDimensionsAndRejectsTextureBombs() {
        assertDoesNotThrow(() -> CompanionPreviewTextures.validatePngHeader(pngHeader(2048, 2048)));
        assertThrows(IOException.class, () -> CompanionPreviewTextures.validatePngHeader(pngHeader(4096, 4097)));
        assertThrows(IOException.class, () -> CompanionPreviewTextures.validatePngHeader(pngHeader(5000, 1)));
        assertThrows(IOException.class, () -> CompanionPreviewTextures.validatePngHeader(new byte[24]));
    }

    private static byte[] pngHeader(int width, int height) {
        byte[] bytes = new byte[24];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        writeInt(bytes, 16, width);
        writeInt(bytes, 20, height);
        return bytes;
    }

    private static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }
}
