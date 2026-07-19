package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SuppressionBundleReaderTest {
    @Test
    void readsChecksummedLocalBundle() throws Exception {
        SuppressionBundle bundle = SuppressionBundleReader.read(SuppressionTestFixtures.zipBytes(), "fixture.zip");

        assertEquals("local_zip", bundle.source());
        assertEquals(bundle.litematicSha256(), bundle.parsed().plan().litematic().sha256());
        assertEquals(64, bundle.parsed().plan().phases().size());
    }

    @Test
    void rejectsZipTraversalBeforeWritingAnything() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("../escape.json"));
            zip.write("{}".getBytes());
            zip.closeEntry();
        }

        assertThrows(java.io.IOException.class, () -> SuppressionBundleReader.read(out.toByteArray(), "bad.zip"));
    }

    @Test
    void rejectsCorruptGzipPayload() {
        byte[] corrupt = new byte[64];
        corrupt[0] = 0x1f;
        corrupt[1] = (byte) 0x8b;

        assertThrows(java.io.IOException.class, () -> SuppressionBundleReader.validateLitematic(corrupt));
    }

    @Test
    void readsEveryMultiMapTileInDeterministicRowMajorOrder() throws Exception {
        SuppressionBundleCatalog catalog = SuppressionBundleReader.readCatalog(
            SuppressionTestFixtures.multiZipBytes(), "fixture_2x1.zip");

        assertEquals(2, catalog.gridWide());
        assertEquals(1, catalog.gridTall());
        assertEquals(2, catalog.tiles().size());
        assertEquals("tile_001", catalog.tiles().get(0).id());
        assertEquals(0, catalog.tiles().get(0).column());
        assertEquals("tile_002", catalog.tiles().get(1).id());
        assertEquals(1, catalog.tiles().get(1).column());
        assertEquals(catalog.tiles().get(0).bundle().litematicSha256(),
            catalog.tiles().get(1).bundle().litematicSha256());
    }

    @Test
    void legacySingleReaderRefusesToChooseAMultiMapTileImplicitly() throws Exception {
        assertThrows(java.io.IOException.class,
            () -> SuppressionBundleReader.read(SuppressionTestFixtures.multiZipBytes(), "fixture_2x1.zip"));
    }
}
