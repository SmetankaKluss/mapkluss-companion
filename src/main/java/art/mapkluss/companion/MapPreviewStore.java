package art.mapkluss.companion;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;

final class MapPreviewStore {
    static final int MAP_PIXELS = 128 * 128;
    private static final int MAGIC = 0x4D4B4D50;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_FILE_BYTES = 64 * 1024;

    private final Path root;

    MapPreviewStore(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    Optional<Entry> read(String connectionKey, int mapId) throws IOException {
        Path path = entryPath(connectionKey, mapId);
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) return Optional.empty();
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(
            new BufferedInputStream(Files.newInputStream(path)), MAX_FILE_BYTES
        ))) {
            if (input.readInt() != MAGIC || input.readUnsignedByte() != FORMAT_VERSION || input.readInt() != mapId) {
                return Optional.empty();
            }
            byte[] hashBytes = input.readNBytes(32);
            byte[] colors = input.readNBytes(MAP_PIXELS);
            if (hashBytes.length != 32 || colors.length != MAP_PIXELS || input.read() != -1) return Optional.empty();
            String hash = HexFormat.of().withUpperCase().formatHex(hashBytes);
            if (!MapColorFingerprint.isValid(hash) || !hash.equals(MapColorFingerprint.sha256(colors))) {
                return Optional.empty();
            }
            return Optional.of(new Entry(mapId, hash, colors));
        } catch (EOFException | ZipException | RuntimeException corrupt) {
            return Optional.empty();
        }
    }

    void write(String connectionKey, Entry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        Path path = entryPath(connectionKey, entry.mapId());
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(temporary))
            ))) {
                output.writeInt(MAGIC);
                output.writeByte(FORMAT_VERSION);
                output.writeInt(entry.mapId());
                output.write(HexFormat.of().parseHex(entry.hash()));
                output.write(entry.colors());
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Path entryPath(String connectionKey, int mapId) {
        if (connectionKey == null || connectionKey.isBlank()) throw new IllegalArgumentException("Connection key is required.");
        if (mapId < 0) throw new IllegalArgumentException("Map ID must be non-negative.");
        return root.resolve(connectionDigest(connectionKey)).resolve(mapId + ".mkmap");
    }

    private static String connectionDigest(String connectionKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(connectionKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    record Entry(int mapId, String hash, byte[] colors) {
        Entry {
            if (mapId < 0) throw new IllegalArgumentException("Map ID must be non-negative.");
            if (!MapColorFingerprint.isValid(hash)) throw new IllegalArgumentException("Map fingerprint is invalid.");
            colors = Objects.requireNonNull(colors, "colors").clone();
            if (colors.length != MAP_PIXELS || !hash.equals(MapColorFingerprint.sha256(colors))) {
                throw new IllegalArgumentException("Map preview colors do not match their fingerprint.");
            }
        }

        @Override
        public byte[] colors() {
            return colors.clone();
        }
    }
}
