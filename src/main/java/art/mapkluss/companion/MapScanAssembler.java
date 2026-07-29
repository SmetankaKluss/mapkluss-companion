package art.mapkluss.companion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public final class MapScanAssembler {
    public static final int MAP_SIZE = 128;
    public static final int MAX_GRID_TILES = 1024;
    public static final long MAX_IMAGE_PIXELS = 16_777_216L;

    private MapScanAssembler() {
    }

    public static byte[] assemblePng(List<Tile> tiles, int wide, int tall) throws IOException {
        if (wide <= 0 || tall <= 0) throw new IOException("Scan grid is empty.");
        long gridTiles = (long) wide * tall;
        long imageWidth = (long) wide * MAP_SIZE;
        long imageHeight = (long) tall * MAP_SIZE;
        long imagePixels = imageWidth * imageHeight;
        if (gridTiles > MAX_GRID_TILES || tiles.size() > MAX_GRID_TILES || imagePixels > MAX_IMAGE_PIXELS
            || imageWidth > Integer.MAX_VALUE || imageHeight > Integer.MAX_VALUE) {
            throw new IOException("Scan grid is too large to process safely.");
        }
        BufferedImage image = new BufferedImage((int) imageWidth, (int) imageHeight, BufferedImage.TYPE_INT_ARGB);
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

    public static int[] rotateTileClockwise(int[] source, int quarterTurns) {
        if (source == null || source.length != MAP_SIZE * MAP_SIZE) {
            throw new IllegalArgumentException("Map tile must contain 128x128 pixels.");
        }
        int turns = Math.floorMod(quarterTurns, 4);
        int[] result = new int[source.length];
        for (int sourceY = 0; sourceY < MAP_SIZE; sourceY++) {
            for (int sourceX = 0; sourceX < MAP_SIZE; sourceX++) {
                int targetX;
                int targetY;
                switch (turns) {
                    case 0 -> {
                        targetX = sourceX;
                        targetY = sourceY;
                    }
                    case 1 -> {
                        targetX = MAP_SIZE - 1 - sourceY;
                        targetY = sourceX;
                    }
                    case 2 -> {
                        targetX = MAP_SIZE - 1 - sourceX;
                        targetY = MAP_SIZE - 1 - sourceY;
                    }
                    case 3 -> {
                        targetX = sourceY;
                        targetY = MAP_SIZE - 1 - sourceX;
                    }
                    default -> throw new AssertionError("Unexpected map rotation");
                }
                result[targetY * MAP_SIZE + targetX] = source[sourceY * MAP_SIZE + sourceX];
            }
        }
        return result;
    }

    public record Tile(int column, int row, int[] argb) {
        public Tile {
            if (argb.length != MAP_SIZE * MAP_SIZE) {
                throw new IllegalArgumentException("Map tile must contain 128x128 pixels.");
            }
        }
    }
}
