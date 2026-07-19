package art.mapkluss.companion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MapDatTileSet {
    private static final Pattern MAP_ENTRY = Pattern.compile("^data/map_(\\d+)\\.dat$");
    private static final int MAX_TILES = 10_000;
    private static final int MAX_ZIP_ENTRIES = 12_000;
    private static final int MAX_ARCHIVE_BYTES = 256 * 1024 * 1024;
    static final int MAX_ZIP_ENTRY_BYTES = 1024 * 1024;
    private static final int MAX_TOTAL_ENTRY_BYTES = 256 * 1024 * 1024;
    private static final int MAX_NBT_BYTES = 256 * 1024;
    private static final int MAX_NBT_DEPTH = 64;

    private final int wide;
    private final int tall;
    private final List<Integer> mapIds;
    private final List<byte[]> tileColors;
    private final List<String> tileHashes;

    private MapDatTileSet(int wide, int tall, List<MapTile> tiles) {
        this.wide = wide;
        this.tall = tall;
        this.mapIds = tiles.stream().map(MapTile::mapId).toList();
        this.tileColors = tiles.stream().map(tile -> tile.colors().clone()).toList();
        this.tileHashes = tiles.stream().map(tile -> MapColorFingerprint.sha256(tile.colors())).toList();
    }

    public static MapDatTileSet read(Path zipPath, int wide, int tall) throws IOException {
        Objects.requireNonNull(zipPath, "zipPath");
        long size = Files.size(zipPath);
        if (size > MAX_ARCHIVE_BYTES) throw new IOException("MAP.DAT zip exceeds the safe archive limit.");
        return read(Files.readAllBytes(zipPath), wide, tall);
    }

    public static MapDatTileSet read(byte[] zipBytes, int wide, int tall) throws IOException {
        Objects.requireNonNull(zipBytes, "zipBytes");
        int expected = checkedTileCount(wide, tall);
        if (zipBytes.length > MAX_ARCHIVE_BYTES) throw new IOException("MAP.DAT zip exceeds the safe archive limit.");

        List<MapTile> tiles = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        int entries = 0;
        long totalBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ZIP_ENTRIES) throw new IOException("MAP.DAT zip contains too many entries.");
                if (entry.isDirectory()) continue;
                if (entry.getSize() > MAX_ZIP_ENTRY_BYTES) {
                    throw new IOException("ZIP entry exceeds the safe size limit: " + entry.getName());
                }
                byte[] bytes = readLimited(zip, MAX_ZIP_ENTRY_BYTES, "ZIP entry " + entry.getName());
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_ENTRY_BYTES) throw new IOException("MAP.DAT zip expands beyond the safe limit.");

                String name = entry.getName().replace('\\', '/');
                Matcher matcher = MAP_ENTRY.matcher(name);
                if (!matcher.matches()) continue;
                int mapId;
                try {
                    mapId = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException invalidId) {
                    throw new IOException("Invalid map ID in " + name + ".", invalidId);
                }
                if (!ids.add(mapId)) throw new IOException("Duplicate map ID " + mapId + " in MAP.DAT zip.");
                if (tiles.size() >= MAX_TILES) throw new IOException("MAP.DAT zip contains too many map tiles.");
                tiles.add(new MapTile(mapId, readColors(bytes, name)));
            }
        }
        if (tiles.size() != expected) {
            throw new IOException("Expected " + expected + " map tiles for " + wide + "x" + tall + ", got " + tiles.size() + ".");
        }
        tiles.sort(Comparator.comparingInt(MapTile::mapId));
        return new MapDatTileSet(wide, tall, tiles);
    }

    public static MapDatTileSet fromZip(byte[] zipBytes, int wide, int tall) throws IOException {
        return read(zipBytes, wide, tall);
    }

    public int wide() {
        return wide;
    }

    public int tall() {
        return tall;
    }

    public int size() {
        return mapIds.size();
    }

    public List<Integer> mapIds() {
        return mapIds;
    }

    public List<String> tileHashes() {
        return tileHashes;
    }

    public List<byte[]> tileColors() {
        return tileColors.stream().map(byte[]::clone).toList();
    }

    public List<byte[]> colors() {
        return tileColors();
    }

    public byte[] colorsAt(int index) {
        return tileColors.get(index).clone();
    }

    public int mapIdAt(int index) {
        return mapIds.get(index);
    }

    private static int checkedTileCount(int wide, int tall) {
        if (wide <= 0 || tall <= 0) throw new IllegalArgumentException("Tile dimensions must be positive.");
        final int count;
        try {
            count = Math.multiplyExact(wide, tall);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Tile dimensions are too large.", overflow);
        }
        if (count > MAX_TILES) throw new IllegalArgumentException("Tile grid exceeds the safe limit of " + MAX_TILES + ".");
        return count;
    }

    private static byte[] readColors(byte[] gzipNbt, String name) throws IOException {
        byte[] nbt;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipNbt))) {
            nbt = readLimited(gzip, MAX_NBT_BYTES, "NBT " + name);
        } catch (IOException corrupt) {
            throw new IOException("Invalid gzip NBT in " + name + ": " + corrupt.getMessage(), corrupt);
        }
        return new MinimalNbtReader(nbt).readMapColors();
    }

    private static byte[] readLimited(InputStream input, int limit, String label) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 16 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total > limit - read) throw new IOException(label + " exceeds the safe size limit.");
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private record MapTile(int mapId, byte[] colors) {
    }

    private static final class MinimalNbtReader {
        private final DataInputStream input;
        private byte[] colors;

        private MinimalNbtReader(byte[] bytes) {
            input = new DataInputStream(new ByteArrayInputStream(bytes));
        }

        private byte[] readMapColors() throws IOException {
            int rootType = input.readUnsignedByte();
            if (rootType != 10) throw new IOException("NBT root must be a compound tag.");
            readString();
            readCompound(0, false);
            if (colors == null) throw new IOException("NBT is missing data.colors.");
            return colors;
        }

        private void readCompound(int depth, boolean dataCompound) throws IOException {
            checkDepth(depth);
            while (true) {
                int type = input.readUnsignedByte();
                if (type == 0) return;
                String name = readString();
                if (!dataCompound && depth == 0 && type == 10 && name.equals("data")) {
                    readCompound(depth + 1, true);
                } else if (dataCompound && type == 7 && name.equals("colors")) {
                    if (colors != null) throw new IOException("NBT contains duplicate data.colors tags.");
                    int length = readLength(1);
                    if (length != MapColorFingerprint.COLOR_COUNT) {
                        throw new IOException("NBT data.colors must contain exactly " + MapColorFingerprint.COLOR_COUNT + " bytes.");
                    }
                    colors = readExactly(length);
                } else {
                    skipPayload(type, depth + 1);
                }
            }
        }

        private void skipPayload(int type, int depth) throws IOException {
            checkDepth(depth);
            switch (type) {
                case 1 -> skipExactly(1);
                case 2 -> skipExactly(2);
                case 3, 5 -> skipExactly(4);
                case 4, 6 -> skipExactly(8);
                case 7 -> skipExactly(readLength(1));
                case 8 -> readString();
                case 9 -> {
                    int elementType = input.readUnsignedByte();
                    if (elementType < 0 || elementType > 12) throw new IOException("Unknown NBT list tag type " + elementType + ".");
                    int length = readLength(1);
                    if (elementType == 0 && length != 0) throw new IOException("NBT end-tag list must be empty.");
                    for (int index = 0; index < length; index++) skipPayload(elementType, depth + 1);
                }
                case 10 -> readCompound(depth, false);
                case 11 -> skipExactly(readLength(4));
                case 12 -> skipExactly(readLength(8));
                default -> throw new IOException("Unknown NBT tag type " + type + ".");
            }
        }

        private int readLength(int elementBytes) throws IOException {
            int length = input.readInt();
            if (length < 0 || length > MAX_NBT_BYTES / elementBytes) throw new IOException("Invalid or oversized NBT array length.");
            return Math.multiplyExact(length, elementBytes);
        }

        private String readString() throws IOException {
            int length = input.readUnsignedShort();
            return new String(readExactly(length), StandardCharsets.UTF_8);
        }

        private byte[] readExactly(int length) throws IOException {
            byte[] value = input.readNBytes(length);
            if (value.length != length) throw new EOFException("Truncated NBT payload.");
            return value;
        }

        private void skipExactly(int length) throws IOException {
            input.skipNBytes(length);
        }

        private static void checkDepth(int depth) throws IOException {
            if (depth > MAX_NBT_DEPTH) throw new IOException("NBT nesting exceeds the safe depth limit.");
        }
    }
}
