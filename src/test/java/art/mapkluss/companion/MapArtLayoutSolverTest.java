package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapArtLayoutSolverTest {
    @Test
    void rebuildsSmoothThreeByTwoMosaicFromShuffledTiles() {
        List<MapArtLayoutSolver.Tile> expected = mosaic(3, 2);
        List<MapArtLayoutSolver.Tile> shuffled = new ArrayList<>(expected);
        Collections.shuffle(shuffled, new Random(17));

        MapArtLayoutSolver.Layout layout = MapArtLayoutSolver.solve(shuffled, null);

        assertEquals(3, layout.wide());
        assertEquals(2, layout.tall());
        assertEquals(expected.stream().map(MapArtLayoutSolver.Tile::hash).toList(), layout.tileHashes());
        assertEquals(expected.stream().map(MapArtLayoutSolver.Tile::mapId).toList(), layout.tileMapIds());
        assertTrue(layout.reliable());
    }

    @Test
    void honorsBottomLeftAnchor() {
        List<MapArtLayoutSolver.Tile> expected = mosaic(2, 2);
        List<MapArtLayoutSolver.Tile> shuffled = new ArrayList<>(expected);
        Collections.shuffle(shuffled, new Random(3));

        MapArtLayoutSolver.Layout layout = MapArtLayoutSolver.solve(shuffled, expected.get(2).hash());

        assertEquals(2, layout.wide());
        assertEquals(2, layout.tall());
        assertEquals(expected.get(2).hash(), layout.tileHashes().get(2));
        assertEquals(expected.get(2).mapId(), layout.tileMapIds().get(2));
    }

    @Test
    void honorsBottomLeftMapIdWhenTileColorsRepeat() {
        List<MapArtLayoutSolver.Tile> expected = mosaic(2, 2);
        MapArtLayoutSolver.Tile duplicateColor = new MapArtLayoutSolver.Tile(
            99,
            expected.get(2).hash(),
            expected.get(2).argb()
        );
        List<MapArtLayoutSolver.Tile> values = new ArrayList<>(expected);
        values.set(0, duplicateColor);
        Collections.shuffle(values, new Random(7));

        MapArtLayoutSolver.Layout layout = MapArtLayoutSolver.solveByMapId(values, expected.get(2).mapId());

        assertEquals(expected.get(2).hash(), layout.tileHashes().get(2));
    }

    @Test
    void refusesAnAmbiguousUniformGrid() {
        int[] pixels = new int[128 * 128];
        java.util.Arrays.fill(pixels, 0xFF335577);
        List<MapArtLayoutSolver.Tile> tiles = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            tiles.add(new MapArtLayoutSolver.Tile(index, "%064X".formatted(index + 1), pixels));
        }

        MapArtLayoutSolver.Layout layout = MapArtLayoutSolver.solve(tiles, null);

        assertFalse(layout.reliable());
    }

    private static List<MapArtLayoutSolver.Tile> mosaic(int wide, int tall) {
        int imageWidth = wide * 128;
        int imageHeight = tall * 128;
        List<MapArtLayoutSolver.Tile> tiles = new ArrayList<>();
        for (int row = 0; row < tall; row++) {
            for (int column = 0; column < wide; column++) {
                int[] pixels = new int[128 * 128];
                for (int y = 0; y < 128; y++) {
                    for (int x = 0; x < 128; x++) {
                        int globalX = column * 128 + x;
                        int globalY = row * 128 + y;
                        int red = globalX * 255 / Math.max(1, imageWidth - 1);
                        int green = globalY * 255 / Math.max(1, imageHeight - 1);
                        int blue = (globalX * 3 + globalY * 5) & 255;
                        pixels[y * 128 + x] = 0xFF000000 | red << 16 | green << 8 | blue;
                    }
                }
                int id = row * wide + column;
                tiles.add(new MapArtLayoutSolver.Tile(id, "%064X".formatted(id + 1), pixels));
            }
        }
        return tiles;
    }
}
