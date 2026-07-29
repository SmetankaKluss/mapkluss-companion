package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MapScanAssemblerTest {
    @Test
    void assemblesTilesIntoGridPng() throws Exception {
        int[] red = solid(0xFFFF0000);
        int[] green = solid(0xFF00FF00);
        int[] blue = solid(0xFF0000FF);
        int[] white = solid(0xFFFFFFFF);

        byte[] png = MapScanAssembler.assemblePng(List.of(
            new MapScanAssembler.Tile(0, 0, red),
            new MapScanAssembler.Tile(1, 0, green),
            new MapScanAssembler.Tile(0, 1, blue),
            new MapScanAssembler.Tile(1, 1, white)
        ), 2, 2);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(0, 0));
        assertEquals(0xFF00FF00, image.getRGB(128, 0));
        assertEquals(0xFF0000FF, image.getRGB(0, 128));
        assertEquals(0xFFFFFFFF, image.getRGB(128, 128));
    }

    @Test
    void rejectsImageThatWouldAllocateTooMuchMemory() {
        assertThrows(java.io.IOException.class, () -> MapScanAssembler.assemblePng(List.of(), 1024, 1024));
    }

    @Test
    void rotatesDisplayedMapPixelsClockwise() {
        int size = MapScanAssembler.MAP_SIZE;
        int[] source = new int[size * size];
        source[0] = 1;
        source[size - 1] = 2;
        source[(size - 1) * size] = 3;
        source[source.length - 1] = 4;

        int[] once = MapScanAssembler.rotateTileClockwise(source, 1);
        assertEquals(3, once[0]);
        assertEquals(1, once[size - 1]);
        assertEquals(4, once[(size - 1) * size]);
        assertEquals(2, once[once.length - 1]);

        int[] four = MapScanAssembler.rotateTileClockwise(source, 4);
        assertEquals(1, four[0]);
        assertEquals(2, four[size - 1]);
        assertEquals(3, four[(size - 1) * size]);
        assertEquals(4, four[four.length - 1]);
    }

    private static int[] solid(int color) {
        int[] pixels = new int[MapScanAssembler.MAP_SIZE * MapScanAssembler.MAP_SIZE];
        for (int i = 0; i < pixels.length; i++) pixels[i] = color;
        return pixels;
    }
}
