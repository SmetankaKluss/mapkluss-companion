package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static art.mapkluss.companion.SuppressionNbt.*;

/** A map-local ghost made from the same cells as the tracker, including its north reference row. */
final class LiveBuildGroupSchematic {
    record Installed(Path path, String sha256) { }

    static UUID placementId(String world, String group, int tile) {
        UUID.fromString(group);
        if (world == null || world.isBlank() || tile < 0 || tile >= 100) throw new IllegalArgumentException("Invalid group placement");
        return UUID.nameUUIDFromBytes(("mapkluss:group-placement:" + world + ':' + group + ':' + tile)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static Installed install(Path runDir, LiveBuildParts.Part part, LiveBuildSharedPlacement placement,
                             Map<LiveBuildProgress.State, LiveBuildProgress.State> palette, int dataVersion) throws IOException {
        if (!part.targetSha256().equals(placement.targetSha256()) || part.cells().size() != placement.cellCount())
            throw new IOException("Shared map target changed");
        byte[] bytes = encode(part.cells(), placement.transform(), palette, dataVersion);
        String sha = SuppressionHashes.sha256(bytes);
        Path folder = LitematicaPaths.defaultSchematicDir(runDir).resolve("mapkluss-group");
        Files.createDirectories(folder);
        Path path = folder.resolve(sha + ".litematic");
        AtomicFiles.withLock(path, () -> {
            if (Files.exists(path)) {
                if (!SuppressionHashes.sha256(path, LiveBuildSchematic.MAX_FILE_BYTES).equals(sha))
                    throw new IOException("Existing group schematic changed");
            } else AtomicFiles.write(path, bytes, false);
            return null;
        });
        return new Installed(path, sha);
    }

    /** Palette states are transformed by Minecraft on the main thread; encoding stays on the worker. */
    static byte[] encode(List<LiveBuildProgress.Cell> cells, LiveBuildTransform transform,
                         Map<LiveBuildProgress.State, LiveBuildProgress.State> transformed, int dataVersion) throws IOException {
        if (dataVersion < 1) throw new IOException("Invalid Minecraft data version");
        if (cells.isEmpty() || cells.size() > LiveBuildProgress.MAX_CELLS) throw new IOException("Invalid map cells");
        var bounds = LiveBuildSchematic.Bounds.occupied(cells);
        var corners = List.of(transform.apply(new LiveBuildProgress.Position(bounds.minX(), bounds.minY(), bounds.minZ())),
            transform.apply(new LiveBuildProgress.Position(bounds.maxX(), bounds.maxY(), bounds.maxZ())));
        int x = Math.min(corners.get(0).x(), corners.get(1).x()), z = Math.min(corners.get(0).z(), corners.get(1).z());
        int sx = Math.abs(corners.get(0).x() - corners.get(1).x()) + 1;
        int sz = Math.abs(corners.get(0).z() - corners.get(1).z()) + 1;
        int sy = Math.addExact(Math.subtractExact(bounds.maxY(), bounds.minY()), 1);
        long volume = (long)sx * sy * sz;
        if (volume > LiveBuildProgress.MAX_CELLS) throw new IOException("Group schematic too large");
        var air = new LiveBuildProgress.State("minecraft:air", Map.of());
        var palette = new LinkedHashMap<LiveBuildProgress.State, Integer>();
        palette.put(air, 0);
        int[] states = new int[(int)volume];
        for (var cell : cells) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            if (cell.requiresAir()) continue;
            var state = transformed.get(cell.expected());
            if (state == null) throw new IOException("Unresolved group palette");
            int index = palette.computeIfAbsent(state, ignored -> palette.size());
            var p = transform.apply(cell.relativePosition());
            states[((p.y()-bounds.minY())*sz + p.z()-z)*sx + p.x()-x] = index;
        }
        var tags = new ArrayList<Tag>();
        for (var state : palette.keySet()) {
            var properties = new java.util.TreeMap<String, Tag>();
            state.properties().forEach((key, value) -> properties.put(key, stringTag(value)));
            tags.add(compoundTag(Map.of("Name", stringTag(state.block()), "Properties", compoundTag(properties))));
        }
        int bits = Math.max(2, 32-Integer.numberOfLeadingZeros(Math.max(1, palette.size()-1)));
        var region = compoundTag(Map.of("Position", vector(x, bounds.minY(), z), "Size", vector(sx, sy, sz),
            "BlockStatePalette", listTag(10, tags), "BlockStates", longArrayTag(SuppressionReferenceLitematic.pack(states, bits)),
            "Entities", listTag(10, List.of()), "TileEntities", listTag(10, List.of())));
        var metadata = compoundTag(Map.of("Name", stringTag("MapKluss group map"), "Author", stringTag("MapKluss"),
            "RegionCount", intTag(1), "TotalVolume", intTag((int)volume),
            "TotalBlocks", intTag((int)java.util.Arrays.stream(states).filter(i -> i != 0).count()),
            "EnclosingSize", vector(sx, sy, sz)));
        var root = compoundTag(Map.of("Version", intTag(6), "SubVersion", intTag(1),
            "MinecraftDataVersion", intTag(dataVersion), "Metadata", metadata, "Regions", compoundTag(Map.of("map", region))));
        return writeCompressed(new Document("", root));
    }

    private static Tag vector(int x, int y, int z) {
        return compoundTag(Map.of("x", intTag(x), "y", intTag(y), "z", intTag(z)));
    }
}
