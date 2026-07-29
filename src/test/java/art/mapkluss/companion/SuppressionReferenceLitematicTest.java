package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionReferenceLitematicTest {
    @TempDir Path tempDir;

    @Test
    void clearsCompletedAndCurrentCellsButKeepsFutureAndRetainedBlocks() throws Exception {
        byte[] source = SuppressionTestFixtures.litematicBytes();
        SuppressionPlan plan = SuppressionTestFixtures.plan(source);
        String planSha = SuppressionHashes.sha256(SuppressionTestFixtures.planBytes(source));

        SuppressionReferenceLitematic.Generated phaseOne = SuppressionReferenceLitematic.build(planSha, plan, source, 0);
        SuppressionReferenceLitematic.Generated phaseTwo = SuppressionReferenceLitematic.build(planSha, plan, source, 1);
        SuppressionReferenceLitematic.Generated repeat = SuppressionReferenceLitematic.build(planSha, plan, source, 1);

        assertEquals(256, phaseOne.clearedBlocks());
        assertEquals(512, phaseTwo.clearedBlocks());
        assertTrue(phaseTwo.filename().endsWith("_reference_after_02_of_64.litematic"));
        assertArrayEquals(phaseTwo.bytes(), repeat.bytes());
        assertEquals(0, stateAt(phaseTwo.bytes(), plan, 0, 2, 0));
        assertEquals(0, stateAt(phaseTwo.bytes(), plan, 3, 2, 127));
        assertEquals(1, stateAt(phaseTwo.bytes(), plan, 4, 2, 0));
        assertEquals(1, stateAt(phaseTwo.bytes(), plan, 0, 0, 0));

        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(phaseTwo.bytes()))) {
            raw = gzip.readAllBytes();
        }
        String nbtText = new String(raw, StandardCharsets.ISO_8859_1);
        assertTrue(nbtText.contains("REFERENCE ONLY - ACTIVE REMOVALS ARE AIR"));
        assertTrue(!nbtText.contains("minecraft:red_stained_glass"));
    }

    @Test
    void installationIsIdempotentAndSourceHashIsPinned() throws Exception {
        byte[] source = SuppressionTestFixtures.litematicBytes();
        SuppressionPlan plan = SuppressionTestFixtures.plan(source);
        String planSha = SuppressionHashes.sha256(SuppressionTestFixtures.planBytes(source));

        Path first = SuppressionReferenceLitematic.install(tempDir, planSha, plan, source, 0);
        Path second = SuppressionReferenceLitematic.install(tempDir, planSha, plan, source, 0);
        assertEquals(first, second);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));

        byte[] corrupted = source.clone();
        corrupted[corrupted.length - 1] ^= 1;
        assertThrows(Exception.class, () -> SuppressionReferenceLitematic.build(planSha, plan, corrupted, 0));
    }

    @Test
    void validatesTheCompletePinnedSource() throws Exception {
        byte[] source = SuppressionTestFixtures.litematicV3Bytes();
        SuppressionPlan plan = SuppressionTestFixtures.planV3(source);

        SuppressionReferenceLitematic.validateSource(plan, source);
    }

    private static int stateAt(byte[] bytes, SuppressionPlan plan, int localX, int localY, int localZ) throws Exception {
        SuppressionNbt.Document document = SuppressionNbt.readCompressed(bytes);
        Map<String, SuppressionNbt.Tag> root = SuppressionNbt.compound(document.root(), "root");
        Map<String, SuppressionNbt.Tag> regions = SuppressionNbt.compound(root.get("Regions"), "Regions");
        Map<String, SuppressionNbt.Tag> region = SuppressionNbt.compound(regions.values().iterator().next(), "region");
        long[] packed = SuppressionNbt.longArray(region.get("BlockStates"), "BlockStates");
        int sizeX = plan.bounds().max().x() - plan.bounds().min().x() + 1;
        int sizeZ = plan.bounds().max().z() - plan.bounds().min().z() + 1;
        int x = localX - plan.bounds().min().x();
        int y = localY - plan.bounds().min().y();
        int z = localZ - plan.bounds().min().z();
        int volumeIndex = y * sizeZ * sizeX + z * sizeX + x;
        int bit = volumeIndex * 2;
        return (int) ((packed[bit / 64] >>> (bit % 64)) & 3L);
    }
}
