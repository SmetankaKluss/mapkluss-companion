package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapDatTileSetTest {
    @Test
    void extractsColorsAndSortsNumericMapIdsIntoRowMajorTiles() throws Exception {
        byte[] colors2 = filledColors(2);
        byte[] colors10 = filledColors(10);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("data/map_10.dat", gzipMapNbt(colors10, true));
        entries.put("README.txt", new byte[] {1, 2, 3});
        entries.put("data/map_2.dat", gzipMapNbt(colors2, false));

        MapDatTileSet tiles = MapDatTileSet.read(zip(entries), 2, 1);

        assertEquals(java.util.List.of(2, 10), tiles.mapIds());
        assertArrayEquals(colors2, tiles.colorsAt(0));
        assertArrayEquals(colors10, tiles.colorsAt(1));
        assertEquals(MapColorFingerprint.sha256(colors2), tiles.tileHashes().get(0));
        byte[] leaked = tiles.tileColors().get(0);
        leaked[0] = 99;
        assertEquals(2, tiles.colorsAt(0)[0]);
    }

    @Test
    void rejectsCorruptMissingAndWrongLengthNbt() throws Exception {
        IOException corrupt = assertThrows(IOException.class, () -> MapDatTileSet.read(
            zip(Map.of("data/map_0.dat", new byte[] {1, 2, 3})), 1, 1
        ));
        assertTrue(corrupt.getMessage().contains("Invalid gzip NBT"));

        assertThrows(IOException.class, () -> MapDatTileSet.read(
            zip(Map.of("data/map_0.dat", gzipNbtWithoutColors())), 1, 1
        ));
        assertThrows(IOException.class, () -> MapDatTileSet.read(
            zip(Map.of("data/map_0.dat", gzipMapNbt(new byte[16_383], false))), 1, 1
        ));
    }

    @Test
    void rejectsOversizedEntriesAndGridCountMismatches() throws Exception {
        byte[] oversized = new byte[MapDatTileSet.MAX_ZIP_ENTRY_BYTES + 1];
        Arrays.fill(oversized, (byte) 7);
        assertThrows(IOException.class, () -> MapDatTileSet.read(
            zip(Map.of("data/map_0.dat", oversized)), 1, 1
        ));

        byte[] oneMap = zip(Map.of("data/map_0.dat", gzipMapNbt(filledColors(1), false)));
        IOException count = assertThrows(IOException.class, () -> MapDatTileSet.read(oneMap, 2, 1));
        assertTrue(count.getMessage().contains("Expected 2 map tiles"));
        assertThrows(IOException.class, () -> MapDatTileSet.read(zip(Map.of("README.txt", new byte[0])), 1, 1));
    }

    private static byte[] filledColors(int value) {
        byte[] colors = new byte[MapColorFingerprint.COLOR_COUNT];
        Arrays.fill(colors, (byte) value);
        return colors;
    }

    private static byte[] gzipMapNbt(byte[] colors, boolean includeOtherTags) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes); DataOutputStream nbt = new DataOutputStream(gzip)) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            if (includeOtherTags) {
                nbt.writeByte(8);
                nbt.writeUTF("DataVersionName");
                nbt.writeUTF("ignored");
            }
            nbt.writeByte(10);
            nbt.writeUTF("data");
            if (includeOtherTags) {
                nbt.writeByte(3);
                nbt.writeUTF("scale");
                nbt.writeInt(0);
            }
            nbt.writeByte(7);
            nbt.writeUTF("colors");
            nbt.writeInt(colors.length);
            nbt.write(colors);
            nbt.writeByte(0);
            nbt.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] gzipNbtWithoutColors() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes); DataOutputStream nbt = new DataOutputStream(gzip)) {
            nbt.writeByte(10);
            nbt.writeUTF("");
            nbt.writeByte(10);
            nbt.writeUTF("data");
            nbt.writeByte(0);
            nbt.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
