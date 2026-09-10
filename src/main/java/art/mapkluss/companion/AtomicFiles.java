package art.mapkluss.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Crash-safe local file writes shared by Companion stores. */
final class AtomicFiles {
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private static final Set<PosixFilePermission> OWNER_DIRECTORY = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE,
        PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> OWNER_FILE = Set.of(
        PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE
    );

    private AtomicFiles() {
    }

    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws IOException;
    }

    static <T> T withLock(Path path, IoSupplier<T> operation) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Object lock = LOCKS.computeIfAbsent(absolute, ignored -> new Object());
        synchronized (lock) {
            return operation.get();
        }
    }

    static void writeUtf8(Path path, String value) throws IOException {
        write(path, value.getBytes(StandardCharsets.UTF_8), false);
    }

    static void writePrivateUtf8(Path path, String value) throws IOException {
        write(path, value.getBytes(StandardCharsets.UTF_8), true);
    }

    static void write(Path path, byte[] value, boolean privateFile) throws IOException {
        write(path, value, privateFile, false);
    }

    static void writePrivateAtomic(Path path, byte[] value) throws IOException {
        write(path, value, true, true);
    }

    private static void write(Path path, byte[] value, boolean privateFile, boolean requireAtomic) throws IOException {
        withLock(path, () -> {
            Path absolute = path.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent == null) throw new IOException("Local file has no parent folder: " + path);
            Files.createDirectories(parent);
            if (privateFile) setPermissions(parent, OWNER_DIRECTORY);
            Path temporary = Files.createTempFile(parent, absolute.getFileName().toString() + ".", ".tmp");
            try {
                Files.write(temporary, value);
                if (requireAtomic) {
                    try (var channel = java.nio.channels.FileChannel.open(temporary, java.nio.file.StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }
                }
                if (privateFile) setPermissions(temporary, OWNER_FILE);
                try {
                    Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    if (requireAtomic) throw ignored;
                    Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
                }
                if (privateFile) setPermissions(absolute, OWNER_FILE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return null;
        });
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some custom filesystems do not expose POSIX permissions.
        }
    }
}
