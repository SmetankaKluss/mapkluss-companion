package art.mapkluss.companion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class MapScanAssembler {
    public static final int MAP_SIZE = 128;

    private MapScanAssembler() {
    }

    public static byte[] assemblePng(List<Tile> tiles, int wide, int tall) throws IOException {
        if (wide <= 0 || tall <= 0) throw new IOException("Scan grid is empty.");
        BufferedImage image = new BufferedImage(wide * MAP_SIZE, tall * MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (Tile tile : tiles) {
            if (tile.column() < 0 || tile.column() >= wide || tile.row() < 0 || tile.row() >= tall) continue;
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    image.setRGB(tile.column() * MAP_SIZE + x, tile.row() * MAP_SIZE + y, tile.argb()[x + y * MAP_SIZE]);
                }
            }
        }
        return writePng(image);
    }

    public static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG encoder is unavailable.");
        }
        return output.toByteArray();
    }

    public record Tile(int column, int row, int[] argb) {
        public Tile {
            if (argb.length != MAP_SIZE * MAP_SIZE) {
                throw new IllegalArgumentException("Map tile must contain 128x128 pixels.");
            }
        }
    }
}
