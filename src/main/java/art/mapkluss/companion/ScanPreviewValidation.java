package art.mapkluss.companion;

import java.io.IOException;
import java.nio.ByteBuffer;

final class ScanPreviewValidation {
    static void validate(MapScanDraft draft) throws IOException {
        byte[] bytes = draft.pngBytes();
        if (bytes == null || bytes.length < 33 || bytes.length > 80 * 1024 * 1024)
            throw new IOException("Invalid scan preview size");
        var header = ByteBuffer.wrap(bytes);
        if (header.getLong() != 0x89504e470d0a1a0aL || header.getInt() != 13 || header.getInt() != 0x49484452)
            throw new IOException("Invalid scan PNG");
        int width = header.getInt(), height = header.getInt();
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192
            || (long) width * height > MapScanAssembler.MAX_IMAGE_PIXELS
            || width != (long) draft.wide() * 128 || height != (long) draft.tall() * 128)
            throw new IOException("Invalid scan preview dimensions");
    }
}
