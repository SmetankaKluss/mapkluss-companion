package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Produces a phase reference from the pinned full Litematic. Completed and
 * active removal cells become air; every retained cell stays visible.
 */
final class SuppressionReferenceLitematic {
    private static final int MAX_REFERENCE_BYTES = 16 * 1024 * 1024;

    private SuppressionReferenceLitematic() { }

    /** Validates the complete pinned source before it reaches a managed folder or Litematica. */
    static void validateSource(SuppressionPlan plan, byte[] sourceLitematic) throws IOException {
        if (plan == null || plan.phases() == null || plan.phases().isEmpty()) {
            throw new IOException("Invalid Two-layer source plan");
        }
        build("0000000000000000000000000000000000000000000000000000000000000000",
            plan, sourceLitematic, plan.phases().size() - 1);
    }

    static Path install(
        Path runDir,
        String planSha256,
        SuppressionPlan plan,
        byte[] sourceLitematic,
        int phaseIndex
    ) throws IOException {
        Generated generated = build(planSha256, plan, sourceLitematic, phaseIndex);
        Path folder = LitematicaPaths.defaultSchematicDir(runDir).toAbsolutePath().normalize();
        Files.createDirectories(folder);
        Path target = folder.resolve(generated.filename()).normalize();
        if (!target.startsWith(folder)) throw new IOException("Unsafe Two-layer reference filename");
        if (Files.exists(target)) {
            if (SuppressionHashes.sha256(target, MAX_REFERENCE_BYTES).equals(SuppressionHashes.sha256(generated.bytes()))) {
                return target;
            }
            throw new IOException("Refusing to overwrite a different Two-layer reference");
        }
        Path temp = Files.createTempFile(folder, generated.filename(), ".tmp");
        try {
            Files.write(temp, generated.bytes());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }

    static Generated build(
        String planSha256,
        SuppressionPlan plan,
        byte[] sourceLitematic,
        int phaseIndex
    ) throws IOException {
        if (plan == null || sourceLitematic == null || phaseIndex < 0 || phaseIndex >= plan.phases().size()) {
            throw new IOException("Invalid Two-layer reference phase");
        }
        if (!SuppressionHashes.sha256(sourceLitematic).equals(plan.litematic().sha256())) {
            throw new IOException("Two-layer source Litematic no longer matches the plan");
        }

        SuppressionNbt.Document document = SuppressionNbt.readCompressed(sourceLitematic);
        Map<String, SuppressionNbt.Tag> root = SuppressionNbt.compound(document.root(), "root");
        if (SuppressionNbt.intValue(root.get("Version"), "Version") != 6) {
            throw new IOException("Unsupported Two-layer Litematic version");
        }
        Map<String, SuppressionNbt.Tag> regions = SuppressionNbt.compound(root.get("Regions"), "Regions");
        if (regions.size() != 1) throw new IOException("Two-layer Litematic must have exactly one region");
        Map<String, SuppressionNbt.Tag> region = SuppressionNbt.compound(regions.values().iterator().next(), "Two-layer region");

        SuppressionPlan.LocalPos min = plan.effectiveStructureBounds().min();
        SuppressionPlan.LocalPos max = plan.effectiveStructureBounds().max();
        int sizeX = max.x() - min.x() + 1;
        int sizeY = max.y() - min.y() + 1;
        int sizeZ = max.z() - min.z() + 1;
        long volumeLong = (long) sizeX * sizeY * sizeZ;
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || volumeLong > 2_000_000L) {
            throw new IOException("Two-layer reference volume is outside the safe limit");
        }
        requireVector(region.get("Position"), min.x(), min.y(), min.z(), "Position");
        requireVector(region.get("Size"), sizeX, sizeY, sizeZ, "Size");
        requireMatchingPalette(region.get("BlockStatePalette"), plan);

        int paletteSize = plan.palette().size();
        int bitsPerEntry = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
        int volume = (int) volumeLong;
        int[] states = unpack(SuppressionNbt.longArray(region.get("BlockStates"), "BlockStates"), volume, bitsPerEntry, paletteSize);
        int cleared = 0;
        for (int index = 0; index <= phaseIndex; index++) {
            for (SuppressionPlan.RemovalRun run : plan.phases().get(index).removeRuns()) {
                for (int dx = 0; dx < run.length(); dx++) {
                    int x = run.xStart() + dx - min.x();
                    int y = run.y() - min.y();
                    int z = run.z() - min.z();
                    if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
                        throw new IOException("Two-layer removal leaves the source region");
                    }
                    int volumeIndex = y * sizeZ * sizeX + z * sizeX + x;
                    if (states[volumeIndex] != run.paletteIndex()) {
                        throw new IOException("Two-layer removal does not match the pinned source Litematic");
                    }
                    states[volumeIndex] = 0;
                    cleared++;
                }
            }
        }
        if (cleared < 1 || cleared > 128 * 128) throw new IOException("Invalid cumulative Two-layer removal count");
        region.put("BlockStates", SuppressionNbt.longArrayTag(pack(states, bitsPerEntry)));

        String phaseLabel = String.format("reference_after_%02d_of_%02d", phaseIndex + 1, plan.phases().size());
        SuppressionNbt.Tag metadataTag = root.get("Metadata");
        if (metadataTag != null) {
            Map<String, SuppressionNbt.Tag> metadata = SuppressionNbt.compound(metadataTag, "Metadata");
            metadata.put("Name", SuppressionNbt.stringTag("MapKluss " + phaseLabel));
            metadata.put("Description", SuppressionNbt.stringTag("REFERENCE ONLY - ACTIVE REMOVALS ARE AIR"));
        }
        byte[] bytes = SuppressionNbt.writeCompressed(document);
        if (bytes.length > MAX_REFERENCE_BYTES) throw new IOException("Two-layer reference exceeds the safe size limit");
        String filename = "mapkluss_two_layer_" + shortSha(planSha256) + "_" + phaseLabel + ".litematic";
        return new Generated(filename, bytes, cleared);
    }

    private static void requireVector(
        SuppressionNbt.Tag tag,
        int expectedX,
        int expectedY,
        int expectedZ,
        String label
    ) throws IOException {
        Map<String, SuppressionNbt.Tag> vector = SuppressionNbt.compound(tag, label);
        int x = SuppressionNbt.intValue(vector.get("x"), label + ".x");
        int y = SuppressionNbt.intValue(vector.get("y"), label + ".y");
        int z = SuppressionNbt.intValue(vector.get("z"), label + ".z");
        if (x != expectedX || y != expectedY || z != expectedZ) {
            throw new IOException("Two-layer " + label + " does not match the plan bounds");
        }
    }

    private static void requireMatchingPalette(SuppressionNbt.Tag tag, SuppressionPlan plan) throws IOException {
        SuppressionNbt.NbtList palette = SuppressionNbt.list(tag, 10, "BlockStatePalette");
        if (palette.values().size() != plan.palette().size()) throw new IOException("Two-layer palette size does not match the plan");
        if (plan.palette().isEmpty() || !"minecraft:air".equals(plan.palette().getFirst().state())
            || !plan.palette().getFirst().properties().isEmpty()) {
            throw new IOException("Two-layer palette index zero must be air");
        }
        for (int index = 0; index < palette.values().size(); index++) {
            Map<String, SuppressionNbt.Tag> source = SuppressionNbt.compound(palette.values().get(index), "palette entry");
            SuppressionPlan.PaletteEntry expected = plan.palette().get(index);
            if (!expected.state().equals(SuppressionNbt.stringValue(source.get("Name"), "palette Name"))) {
                throw new IOException("Two-layer palette state does not match the plan");
            }
            Map<String, String> sourceProperties = new LinkedHashMap<>();
            SuppressionNbt.Tag propertiesTag = source.get("Properties");
            if (propertiesTag != null) {
                Map<String, SuppressionNbt.Tag> properties = SuppressionNbt.compound(propertiesTag, "palette Properties");
                for (Map.Entry<String, SuppressionNbt.Tag> property : properties.entrySet()) {
                    sourceProperties.put(property.getKey(), SuppressionNbt.stringValue(property.getValue(), "palette property"));
                }
            }
            if (!sourceProperties.equals(expected.properties())) {
                throw new IOException("Two-layer palette properties do not match the plan");
            }
        }
    }

    static int[] unpack(long[] packed, int volume, int bitsPerEntry, int paletteSize) throws IOException {
        long expectedLongs = ((long) volume * bitsPerEntry + 63) / 64;
        if (packed.length != expectedLongs) throw new IOException("Two-layer BlockStates length is invalid");
        long mask = (1L << bitsPerEntry) - 1L;
        int[] states = new int[volume];
        for (int index = 0; index < volume; index++) {
            long bitPosition = (long) index * bitsPerEntry;
            int longIndex = (int) (bitPosition >>> 6);
            int bitOffset = (int) (bitPosition & 63);
            long value = packed[longIndex] >>> bitOffset;
            if (bitOffset + bitsPerEntry > 64) value |= packed[longIndex + 1] << (64 - bitOffset);
            int paletteIndex = (int) (value & mask);
            if (paletteIndex < 0 || paletteIndex >= paletteSize) throw new IOException("Two-layer BlockStates uses an unknown palette entry");
            states[index] = paletteIndex;
        }
        return states;
    }

    static long[] pack(int[] states, int bitsPerEntry) {
        long[] packed = new long[(int) (((long) states.length * bitsPerEntry + 63) / 64)];
        long mask = (1L << bitsPerEntry) - 1L;
        for (int index = 0; index < states.length; index++) {
            long value = states[index] & mask;
            long bitPosition = (long) index * bitsPerEntry;
            int longIndex = (int) (bitPosition >>> 6);
            int bitOffset = (int) (bitPosition & 63);
            packed[longIndex] |= value << bitOffset;
            if (bitOffset + bitsPerEntry > 64) packed[longIndex + 1] |= value >>> (64 - bitOffset);
        }
        return packed;
    }

    private static String shortSha(String sha) throws IOException {
        if (sha == null || !sha.matches("[a-f0-9]{64}")) throw new IOException("Invalid Two-layer plan checksum");
        return sha.substring(0, 12);
    }

    record Generated(String filename, byte[] bytes, int clearedBlocks) {
        Generated {
            bytes = bytes.clone();
        }

        @Override public byte[] bytes() { return bytes.clone(); }
    }
}
