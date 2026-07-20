package art.mapkluss.companion;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class MapDatImporter {
    private static final Pattern MAP_FILE = Pattern.compile("(?:^|/)map_(\\d+)\\.dat$");
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private MapDatImporter() {
    }

    public static MapDatImportResult importZip(Path worldDir, byte[] zipBytes) throws IOException {
        Path dataDir = worldDir.resolve("data");
        Files.createDirectories(dataDir);

        List<MapDatFile> files = readMapDatFiles(zipBytes);
        if (files.isEmpty()) throw new IOException("MAP.DAT zip contains no data/map_*.dat files.");

        Set<Integer> usedIds = existingMapIds(dataDir);
        int startId = firstContiguousFreeRun(usedIds, files.size());
        Path backup = backupDataDir(worldDir, dataDir);

        for (int i = 0; i < files.size(); i++) {
            int targetId = startId + i;
            Path target = dataDir.resolve("map_" + targetId + ".dat");
            if (Files.exists(target)) {
                throw new IOException("Refusing to overwrite existing " + target.getFileName());
            }
            Files.write(target, files.get(i).bytes());
        }
        updateIdCounts(dataDir, startId + files.size() - 1);

        return new MapDatImportResult(files.size(), startId, startId + files.size() - 1, backup);
    }

    static List<MapDatFile> readMapDatFiles(byte[] zipBytes) throws IOException {
        List<MapDatFile> files = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                Matcher matcher = MAP_FILE.matcher(name);
                if (!matcher.find()) continue;
                files.add(new MapDatFile(Integer.parseInt(matcher.group(1)), zip.readAllBytes()));
            }
        }
        files.sort(Comparator.comparingInt(MapDatFile::sourceId));
        return files;
    }

    static int firstContiguousFreeRun(Set<Integer> usedIds, int count) {
        int candidate = 0;
        while (true) {
            boolean free = true;
            for (int i = 0; i < count; i++) {
                if (usedIds.contains(candidate + i)) {
                    candidate += i + 1;
                    free = false;
                    break;
                }
            }
            if (free) return candidate;
        }
    }

    static void updateIdCounts(Path dataDir, int lastImportedMapId) throws IOException {
        Path idCounts = dataDir.resolve("idcounts.dat");
        NbtCompound nbt = Files.exists(idCounts)
            ? NbtIo.readCompressed(idCounts, NbtSizeTracker.ofUnlimitedBytes())
            : new NbtCompound();
        int current = nbt.contains("map") ? nbt.getInt("map") : -1;
        if (current < lastImportedMapId) {
            nbt.putInt("map", lastImportedMapId);
            NbtIo.writeCompressed(nbt, idCounts);
        } else if (!Files.exists(idCounts)) {
            NbtIo.writeCompressed(nbt, idCounts);
        }
    }

    private static Set<Integer> existingMapIds(Path dataDir) throws IOException {
        Set<Integer> ids = new HashSet<>();
        if (!Files.isDirectory(dataDir)) return ids;
        try (Stream<Path> stream = Files.list(dataDir)) {
            for (Path file : stream.toList()) {
                Matcher matcher = MAP_FILE.matcher(file.getFileName().toString());
                if (matcher.matches()) ids.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return ids;
    }

    private static Path backupDataDir(Path worldDir, Path dataDir) throws IOException {
        Path backup = worldDir
            .resolve("mapkluss-backups")
            .resolve("data-" + LocalDateTime.now().format(BACKUP_TIME));
        Files.createDirectories(backup);
        if (!Files.isDirectory(dataDir)) return backup;
        try (Stream<Path> stream = Files.walk(dataDir)) {
            for (Path source : stream.toList()) {
                Path relative = dataDir.relativize(source);
                Path target = backup.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return backup;
    }

    record MapDatFile(int sourceId, byte[] bytes) {
    }
}
