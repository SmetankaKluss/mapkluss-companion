package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

public final class LensPreferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private final Set<String> hiddenPlacementIds;
    private final Set<String> blockedOwnerKeys;

    LensPreferences(Path path, Set<String> hiddenPlacementIds, Set<String> blockedOwnerKeys) {
        this.path = path;
        this.hiddenPlacementIds = new LinkedHashSet<>(hiddenPlacementIds == null ? Set.of() : hiddenPlacementIds);
        this.blockedOwnerKeys = new LinkedHashSet<>(blockedOwnerKeys == null ? Set.of() : blockedOwnerKeys);
    }

    public static LensPreferences load(Path runDir) throws IOException {
        Path path = runDir.resolve("config").resolve("mapkluss-companion").resolve("lens.json");
        if (!Files.exists(path)) return new LensPreferences(path, Set.of(), Set.of());
        try (Reader reader = Files.newBufferedReader(path)) {
            Stored stored = GSON.fromJson(reader, Stored.class);
            if (stored == null) return new LensPreferences(path, Set.of(), Set.of());
            return new LensPreferences(path, stored.hiddenPlacementIds(), stored.blockedOwnerKeys());
        }
    }

    public synchronized Set<String> hiddenPlacementIds() {
        return Set.copyOf(hiddenPlacementIds);
    }

    public synchronized Set<String> blockedOwnerKeys() {
        return Set.copyOf(blockedOwnerKeys);
    }

    public synchronized void hide(String placementId) throws IOException {
        if (placementId != null && !placementId.isBlank()) hiddenPlacementIds.add(placementId);
        save();
    }

    public synchronized void block(String ownerKey) throws IOException {
        if (ownerKey != null && !ownerKey.isBlank()) blockedOwnerKeys.add(ownerKey);
        save();
    }

    public synchronized void allowOwnPlacement(String placementId, String ownerKey) throws IOException {
        boolean changed = false;
        if (placementId != null && !placementId.isBlank()) changed |= hiddenPlacementIds.remove(placementId);
        if (ownerKey != null && !ownerKey.isBlank()) changed |= blockedOwnerKeys.remove(ownerKey);
        if (changed) save();
    }

    public synchronized void clearModeration() throws IOException {
        hiddenPlacementIds.clear();
        blockedOwnerKeys.clear();
        save();
    }

    synchronized void save() throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            GSON.toJson(new Stored(hiddenPlacementIds, blockedOwnerKeys), writer);
        }
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record Stored(Set<String> hiddenPlacementIds, Set<String> blockedOwnerKeys) {
    }
}
