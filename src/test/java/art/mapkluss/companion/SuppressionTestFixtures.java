package art.mapkluss.companion;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SuppressionTestFixtures {
    private static final Gson GSON = new Gson();

    private SuppressionTestFixtures() { }

    static byte[] litematicBytes() throws Exception {
        return litematicBytes(254);
    }

    static byte[] litematicV3Bytes() throws Exception {
        return litematicBytes(128);
    }

    private static byte[] litematicBytes(int sizeX) throws Exception {
        int minX = 0, minY = -1, minZ = -1;
        int sizeY = 5, sizeZ = 129;
        int[] states = new int[sizeX * sizeY * sizeZ];
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                states[(2 - minY) * sizeZ * sizeX + (z - minZ) * sizeX + (x - minX)] = 1;
            }
        }
        // A retained block proves that the reference preserves non-removal cells.
        states[(0 - minY) * sizeZ * sizeX + (0 - minZ) * sizeX] = 1;

        long[] packed = new long[(states.length * 2 + 63) / 64];
        for (int index = 0; index < states.length; index++) {
            int bit = index * 2;
            packed[bit / 64] |= ((long) states[index] & 3L) << (bit % 64);
        }

        Map<String, SuppressionNbt.Tag> metadata = new LinkedHashMap<>();
        metadata.put("Name", SuppressionNbt.stringTag("fixture"));
        metadata.put("Author", SuppressionNbt.stringTag("MapKluss"));
        metadata.put("Description", SuppressionNbt.stringTag(""));
        metadata.put("EnclosingSize", vector(sizeX, sizeY, sizeZ));
        metadata.put("RegionCount", SuppressionNbt.intTag(1));

        List<SuppressionNbt.Tag> palette = List.of(
            SuppressionNbt.compoundTag(Map.of("Name", SuppressionNbt.stringTag("minecraft:air"))),
            SuppressionNbt.compoundTag(Map.of("Name", SuppressionNbt.stringTag("minecraft:stone")))
        );
        Map<String, SuppressionNbt.Tag> region = new LinkedHashMap<>();
        region.put("Position", vector(minX, minY, minZ));
        region.put("Size", vector(sizeX, sizeY, sizeZ));
        region.put("BlockStatePalette", SuppressionNbt.listTag(10, palette));
        region.put("BlockStates", SuppressionNbt.longArrayTag(packed));
        region.put("TileEntities", SuppressionNbt.listTag(10, List.of()));
        region.put("Entities", SuppressionNbt.listTag(10, List.of()));

        Map<String, SuppressionNbt.Tag> root = new LinkedHashMap<>();
        root.put("MinecraftDataVersion", SuppressionNbt.intTag(4671));
        root.put("Version", SuppressionNbt.intTag(6));
        root.put("Metadata", SuppressionNbt.compoundTag(metadata));
        root.put("Regions", SuppressionNbt.compoundTag(Map.of("fixture", SuppressionNbt.compoundTag(region))));
        return SuppressionNbt.writeCompressed(new SuppressionNbt.Document("", SuppressionNbt.compoundTag(root)));
    }

    private static SuppressionNbt.Tag vector(int x, int y, int z) {
        Map<String, SuppressionNbt.Tag> values = new LinkedHashMap<>();
        values.put("x", SuppressionNbt.intTag(x));
        values.put("y", SuppressionNbt.intTag(y));
        values.put("z", SuppressionNbt.intTag(z));
        return SuppressionNbt.compoundTag(values);
    }

    static SuppressionPlan plan(byte[] litematic) throws Exception {
        byte[] initial = new byte[128 * 128];
        byte[] target = new byte[128 * 128];
        java.util.Arrays.fill(target, (byte) 1);
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                if (((x + z) & 1) == 0) initial[z * 128 + x] = 1;
            }
        }
        List<SuppressionPlan.Phase> phases = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            int x = index * 2;
            List<SuppressionPlan.RemovalRun> removals = new ArrayList<>();
            List<SuppressionPlan.PixelRun> updates = new ArrayList<>();
            for (int z = 0; z < 128; z++) {
                removals.add(new SuppressionPlan.RemovalRun(x, 2, z, 2, 1));
                int dominantX = ((x + z) & 1) == 1 ? x : x + 1;
                updates.add(new SuppressionPlan.PixelRun(dominantX, z, 1));
            }
            List<SuppressionPlan.StandPoint> standPoints = List.of(
                new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(x + 127, 2, 16), 32),
                new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(x + 127, 2, 48), 32),
                new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(x + 127, 2, 80), 32),
                new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(x + 127, 2, 112), 32)
            );
            phases.add(new SuppressionPlan.Phase(
                "columns_" + index,
                index,
                List.of(x, x + 1),
                "Этап " + index,
                "Phase " + index,
                removals,
                updates,
                standPoints,
                8192 + (index + 1) * 128
            ));
        }
        return new SuppressionPlan(
            "mapkluss.suppression-plan",
            1,
            "two_layer",
            "west_to_east",
            new SuppressionPlan.Target("1.21.11", "minecraft:overworld", 0, 128, 128),
            new SuppressionPlan.Axes("northwest_baseline", "+x", "+z", "+y"),
            new SuppressionPlan.Bounds(new SuppressionPlan.LocalPos(0, -1, -1), new SuppressionPlan.LocalPos(253, 3, 127)),
            null,
            null,
            null,
            List.of(
                new SuppressionPlan.PaletteEntry("minecraft:air", Map.of(), List.of()),
                new SuppressionPlan.PaletteEntry("minecraft:stone", Map.of(), List.of("dominant_target"))
            ),
            Base64.getEncoder().encodeToString(initial),
            Base64.getEncoder().encodeToString(target),
            new SuppressionPlan.Verification("recessive_even", "dominant_odd", new SuppressionPlan.SelectiveRing(126, 128), 32),
            new SuppressionPlan.InitialCapture(List.of(new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(64, 2, 64), 32))),
            phases,
            new SuppressionPlan.Materials(
                List.of(new SuppressionPlan.MaterialCount(1, 128)),
                List.of(new SuppressionPlan.MaterialCount(1, 64)),
                128,
                64
            ),
            new SuppressionPlan.Litematic("fixture_1x1_suppression.litematic", SuppressionHashes.sha256(litematic))
        );
    }

    static byte[] planBytes(byte[] litematic) throws Exception {
        return GSON.toJson(plan(litematic)).getBytes(StandardCharsets.UTF_8);
    }

    static SuppressionPlan planV3(byte[] litematic) throws Exception {
        SuppressionPlan legacy = plan(litematic);
        return new SuppressionPlan(
            legacy.schema(), 3, legacy.method(), legacy.direction(), legacy.target(), legacy.axes(), null,
            new SuppressionPlan.Bounds(new SuppressionPlan.LocalPos(0, -1, -1), new SuppressionPlan.LocalPos(127, 3, 127)),
            new SuppressionPlan.Bounds(new SuppressionPlan.LocalPos(0, -1, -1), new SuppressionPlan.LocalPos(253, 3, 127)),
            null, legacy.palette(), legacy.initialMapBytesB64(), legacy.targetMapBytesB64(), legacy.verification(),
            legacy.initialCapture(), legacy.phases(), legacy.materials(), legacy.litematic()
        );
    }

    static byte[] planV3Bytes(byte[] litematic) throws Exception {
        return GSON.toJson(planV3(litematic)).getBytes(StandardCharsets.UTF_8);
    }

    static byte[] zipBytes() throws Exception {
        byte[] litematic = litematicV3Bytes();
        byte[] plan = planV3Bytes(litematic);
        String litematicName = planV3(litematic).litematic().filename();
        byte[] readme = "MapKluss Two-layer".getBytes(StandardCharsets.UTF_8);
        record Entry(String path, byte[] bytes) { }
        List<Entry> files = List.of(
            new Entry(litematicName, litematic),
            new Entry("fixture_1x1_suppression_plan.json", plan),
            new Entry("README_RU.txt", readme)
        );
        List<Map<String, Object>> manifestFiles = files.stream().map(file -> {
            try {
                return Map.<String, Object>of("path", file.path(), "sizeBytes", file.bytes().length, "sha256", SuppressionHashes.sha256(file.bytes()));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }).toList();
        byte[] manifest = GSON.toJson(Map.of(
            "schema", "mapkluss.suppression-bundle",
            "version", 1,
            "files", manifestFiles
        )).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Entry file : files) {
                zip.putNextEntry(new ZipEntry(file.path()));
                zip.write(file.bytes());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("SHA256.json"));
            zip.write(manifest);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    static byte[] multiZipBytes() throws Exception {
        byte[] litematic = litematicV3Bytes();
        byte[] plan = planV3Bytes(litematic);
        String litematicName = planV3(litematic).litematic().filename();
        String planName = "fixture_1x1_suppression_plan.json";
        record Entry(String path, byte[] bytes) { }
        List<Entry> files = new ArrayList<>();
        List<Map<String, Object>> tiles = new ArrayList<>();
        for (int index = 1; index <= 2; index++) {
            String id = "tile_00" + index;
            String folder = "tiles/" + id + "_" + index + "x1";
            String planPath = folder + "/" + planName;
            String litematicPath = folder + "/" + litematicName;
            files.add(new Entry(planPath, plan));
            files.add(new Entry(litematicPath, litematic));
            tiles.add(Map.of(
                "id", id,
                "index", index,
                "column", index - 1,
                "row", 0,
                "plan", Map.of("path", planPath, "sizeBytes", plan.length, "sha256", SuppressionHashes.sha256(plan)),
                "litematic", Map.of("path", litematicPath, "sizeBytes", litematic.length, "sha256", SuppressionHashes.sha256(litematic))
            ));
        }
        files.add(new Entry("README_RU.txt", "MapKluss Two-layer multi".getBytes(StandardCharsets.UTF_8)));
        List<Map<String, Object>> manifestFiles = files.stream().map(file -> {
            try {
                return Map.<String, Object>of(
                    "path", file.path(),
                    "sizeBytes", file.bytes().length,
                    "sha256", SuppressionHashes.sha256(file.bytes())
                );
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }).toList();
        byte[] manifest = GSON.toJson(Map.of(
            "schema", "mapkluss.suppression-bundle",
            "version", 2,
            "title", "Fixture 2x1",
            "grid", Map.of("wide", 2, "tall", 1),
            "tileOrder", "row_major_top_left",
            "tiles", tiles,
            "files", manifestFiles
        )).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Entry file : files) {
                zip.putNextEntry(new ZipEntry(file.path()));
                zip.write(file.bytes());
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("SHA256.json"));
            zip.write(manifest);
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
