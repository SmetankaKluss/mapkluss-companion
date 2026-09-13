package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

final class MapArtGroupSolverTest {
    @Test
    void separatesThreeShuffledArtsOfDifferentSizesAndNumbersEachTopDown() {
        var first = mosaic(2, 2, 19);
        var second = mosaic(3, 2, 42);
        var third = mosaic(1, 3, 83);
        var mixed = new ArrayList<>(first);
        mixed.addAll(second); mixed.addAll(third);
        Collections.shuffle(mixed, new Random(100));
        var layouts = MapArtGroupSolver.solve(mixed);
        assertEquals(3, layouts.size(), layouts.toString());
        assertArt(layouts, first, 2, 2);
        assertArt(layouts, second, 3, 2);
        assertArt(layouts, third, 1, 3);
        assertEquals(layouts, MapArtGroupSolver.solve(mixed.reversed()));
    }

    @Test
    void duplicateStacksDoNotCreateExtraTiles() {
        var art = mosaic(2, 2, 12);
        var duplicateStacks = new ArrayList<>(art);
        duplicateStacks.addAll(art);
        var layouts = MapArtGroupSolver.solve(duplicateStacks);
        assertEquals(1, layouts.size());
        assertArt(layouts, art, 2, 2);
    }

    @Test
    void moderateDitheringDoesNotMergeTheThreeArts() {
        var mixed = new ArrayList<MapArtLayoutSolver.Tile>();
        mixed.addAll(mosaic(2, 2, 19)); mixed.addAll(mosaic(3, 2, 42)); mixed.addAll(mosaic(1, 3, 83));
        var noise = new Random(73);
        var dithered = mixed.stream().map(tile -> {
            int[] pixels = tile.argb();
            for (int i = 0; i < pixels.length; i++) {
                int source = pixels[i], color = 0xFF000000;
                for (int shift : new int[]{0, 8, 16}) {
                    int channel = (source >> shift & 255) + noise.nextInt(25) - 12;
                    color |= Math.clamp(channel, 0, 255) << shift;
                }
                pixels[i] = color;
            }
            return new MapArtLayoutSolver.Tile(tile.mapId(), tile.hash(), pixels);
        }).toList();
        assertEquals(MapArtGroupSolver.solve(mixed), MapArtGroupSolver.solve(dithered));
    }

    @Test
    void flatIdenticalBordersDoNotInventAnOrderEvenWithConsecutiveIds() {
        int[] pixels = new int[128 * 128]; Arrays.fill(pixels, 0xFF334455);
        var tiles = new ArrayList<MapArtLayoutSolver.Tile>();
        for (int i = 0; i < 6; i++) tiles.add(new MapArtLayoutSolver.Tile(i, "%064X".formatted(i + 1), pixels));
        assertTrue(MapArtGroupSolver.solve(tiles).isEmpty());
    }

    @Test
    void anIncompleteLShapedArtIsNotRenumberedIntoARectangle() {
        var art = mosaic(2, 2, 32);
        assertTrue(MapArtGroupSolver.solve(art.subList(0, 3)).isEmpty());
    }

    @Test
    void distinctSingleMapArtsRemainSeparate() {
        var tiles = new ArrayList<MapArtLayoutSolver.Tile>();
        for (int color : new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF}) {
            int[] pixels = new int[128 * 128]; Arrays.fill(pixels, color);
            tiles.add(new MapArtLayoutSolver.Tile(tiles.size(), "%064X".formatted(color), pixels));
        }
        var result = MapArtGroupSolver.solve(tiles);
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(layout -> layout.wide() == 1 && layout.tall() == 1));
    }

    private static void assertArt(List<MapArtLayoutSolver.Layout> layouts, List<MapArtLayoutSolver.Tile> expected,
                                  int wide, int tall) {
        var ids = expected.stream().map(MapArtLayoutSolver.Tile::mapId).toList();
        var found = layouts.stream().filter(layout -> layout.tileMapIds().equals(ids)).findFirst().orElseThrow();
        assertEquals(wide, found.wide()); assertEquals(tall, found.tall()); assertTrue(found.reliable());
    }

    // Continuous, textured RGB images, cut into maps. IDs are deliberately nonconsecutive and reversed.
    static List<MapArtLayoutSolver.Tile> mosaic(int wide, int tall, int seed) {
        var tiles = new ArrayList<MapArtLayoutSolver.Tile>();
        for (int row = 0; row < tall; row++) for (int column = 0; column < wide; column++) {
            int[] pixels = new int[128 * 128];
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int gx = column * 128 + x, gy = row * 128 + y;
                int color = 0xFF000000;
                for (int c = 0; c < 3; c++) {
                    double value = 128 + 52 * Math.sin(gx * (0.012 + seed * 0.0003) + gy * 0.03 + c * 2 + seed)
                        + 45 * Math.sin(gy * (0.024 + c * 0.011) - gx * 0.009 + seed * (c + 1));
                    color |= (int) value << (16 - c * 8);
                }
                pixels[y * 128 + x] = color;
            }
            int id = seed * 1000 + 900 - tiles.size() * 13;
            tiles.add(new MapArtLayoutSolver.Tile(id, "%064X".formatted(id + 1), pixels));
        }
        return tiles;
    }
}
