package art.mapkluss.companion;

import java.util.function.IntUnaryOperator;

final class SuppressionPreviewPixels {
    static int[] assemble(SuppressionBundleCatalog catalog, IntUnaryOperator color) {
        int wide = catalog.gridWide(), tall = catalog.gridTall();
        if (wide < 1 || tall < 1 || wide > 10 || tall > 10 || catalog.tiles().size() > 100)
            throw new IllegalArgumentException("Preview grid out of bounds");
        int[] result = new int[wide * tall * 128 * 128];
        boolean[] occupied = new boolean[wide * tall];
        for (var tile : catalog.tiles()) {
            if (tile.column() < 0 || tile.row() < 0 || tile.column() >= wide || tile.row() >= tall)
                throw new IllegalArgumentException("Preview tile out of bounds");
            int slot = tile.row() * wide + tile.column();
            if (occupied[slot]) throw new IllegalArgumentException("Duplicate preview tile");
            occupied[slot] = true;
            byte[] bytes = tile.bundle().parsed().targetMapBytes();
            if (bytes.length != 128 * 128) throw new IllegalArgumentException("Invalid preview pixels");
            for (int p = 0; p < bytes.length; p++) {
                int value = bytes[p] & 255;
                result[(tile.row() * 128 + p / 128) * wide * 128 + tile.column() * 128 + p % 128]
                    = value == 0 ? 0 : color.applyAsInt(value);
            }
        }
        return result;
    }
}
