package art.mapkluss.companion;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapDatImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void findsFirstContiguousFreeRun() {
        assertEquals(3, MapDatImporter.firstContiguousFreeRun(Set.of(0, 1, 2, 5), 2));
        assertEquals(6, MapDatImporter.firstContiguousFreeRun(Set.of(0, 1, 2, 3, 4, 5), 3));
    }

    @Test
    void importsMapDatZipIntoFreeIdsAndBacksUpData() throws Exception {
        Path world = tempDir.resolve("world");
        Path data = world.resolve("data");
        Files.createDirectories(data);
        Files.write(data.resolve("map_0.dat"), new byte[] {9});
        Files.write(data.resolve("map_2.dat"), new byte[] {8});

        MapDatImportResult result = MapDatImporter.importZip(world, zipWithMaps());

        assertEquals(2, result.count());
        assertEquals(3, result.startMapId());
        assertEquals(4, result.endMapId());
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(data.resolve("map_3.dat")));
        assertArrayEquals(new byte[] {4, 5, 6}, Files.readAllBytes(data.resolve("map_4.dat")));
        assertTrue(Files.exists(result.backupPath().resolve("map_0.dat")));
        assertTrue(Files.exists(result.backupPath().resolve("map_2.dat")));
        assertEquals(4, readMapIdCount(data.resolve("idcounts.dat")));
    }

    @Test
    void doesNotLowerExistingIdCount() throws Exception {
        Path data = tempDir.resolve("world-with-idcounts").resolve("data");
        Files.createDirectories(data);
        NbtCompound idCounts = new NbtCompound();
        idCounts.putInt("map", 100);
        NbtIo.writeCompressed(idCounts, data.resolve("idcounts.dat"));

        MapDatImporter.updateIdCounts(data, 4);

        assertEquals(100, readMapIdCount(data.resolve("idcounts.dat")));
    }

    private static byte[] zipWithMaps() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("data/map_0.dat"));
            zip.write(new byte[] {1, 2, 3});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("data/map_1.dat"));
            zip.write(new byte[] {4, 5, 6});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write("ignore me".getBytes());
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static int readMapIdCount(Path idCounts) throws Exception {
        return NbtIo.readCompressed(idCounts, NbtSizeTracker.ofUnlimitedBytes()).getInt("map", -1);
    }
}
