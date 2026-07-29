package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ScanHistoryStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_ENTRIES = 20;

    private final Path path;
    private StoredScanHistory history;

    private ScanHistoryStore(Path path, StoredScanHistory history) {
        this.path = path;
        this.history = history;
    }

    public static ScanHistoryStore load(Path path) throws IOException {
        return new ScanHistoryStore(path, readStored(path));
    }

    private static StoredScanHistory readStored(Path path) throws IOException {
        if (!Files.exists(path)) return new StoredScanHistory(new ArrayList<>());
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredScanHistory loaded = GSON.fromJson(reader, StoredScanHistory.class);
            if (loaded == null || loaded.entries() == null) loaded = new StoredScanHistory(new ArrayList<>());
            return loaded;
        }
    }

    public List<ScanHistoryEntry> entries() {
        return List.copyOf(history.entries());
    }

    public ScanHistoryEntry remember(MapScanDraft draft, Path localPath) throws IOException {
        ScanHistoryEntry entry = new ScanHistoryEntry(
            draft.title(),
            draft.source(),
            draft.wide(),
            draft.tall(),
            draft.missingMaps(),
            localPath.toString(),
            null,
            null,
            null,
            null,
            Instant.now().toString()
        );
        return upsert(entry);
    }

    public ScanHistoryEntry attachUpload(String localPath, ScanUploadResponse response) throws IOException {
        return update(localPath, "Scan history entry was not found for upload result.", current ->
            new ScanHistoryEntry(
                current.title(), current.source(), current.wide(), current.tall(), current.missingMaps(),
                current.localPath(), response.importId(), current.createdArtId(), response.sha256(),
                Instant.now().toString(), current.createdAt()
            ));
    }

    public ScanHistoryEntry rememberUpload(
        MapScanDraft draft,
        Path localPath,
        ScanUploadResponse response
    ) throws IOException {
        return AtomicFiles.withLock(path, () -> {
            StoredScanHistory latest = readStored(path);
            String key = localPath.toString();
            ScanHistoryEntry current = latest.entries().stream()
                .filter(entry -> entry.localPath().equals(key))
                .findFirst()
                .orElse(null);
            String now = Instant.now().toString();
            boolean sameUpload = current != null && (
                (response.sha256() != null && !response.sha256().isBlank()
                    && response.sha256().equals(current.uploadedSha256()))
                || (response.importId() != null && !response.importId().isBlank()
                    && response.importId().equals(current.importId()))
            );
            ScanHistoryEntry updated = new ScanHistoryEntry(
                draft.title(), draft.source(), draft.wide(), draft.tall(), draft.missingMaps(), key,
                response.importId(), sameUpload ? current.createdArtId() : null, response.sha256(), now,
                current == null ? now : current.createdAt()
            );
            StoredScanHistory next = upsertState(latest, updated, key);
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            history = next;
            return updated;
        });
    }

    public ScanHistoryEntry attachImportDetails(String localPath, ScanImportDetails details) throws IOException {
        return update(localPath, "Scan history entry was not found for import details.", current ->
            new ScanHistoryEntry(
                current.title(), current.source(), current.wide(), current.tall(), current.missingMaps(),
                current.localPath(),
                details.importId() == null || details.importId().isBlank() ? current.importId() : details.importId(),
                details.createdArtId(), current.uploadedSha256(), current.uploadedAt(), current.createdAt()
            ));
    }

    public ScanHistoryEntry rename(String localPath, MapScanDraft draft, String newLocalPath) throws IOException {
        return update(localPath, "Scan history entry was not found for rename.", current ->
            new ScanHistoryEntry(
                draft.title(), draft.source(), draft.wide(), draft.tall(), draft.missingMaps(), newLocalPath,
                current.importId(), current.createdArtId(), current.uploadedSha256(), current.uploadedAt(), current.createdAt()
            ));
    }

    public boolean remove(String localPath) throws IOException {
        return AtomicFiles.withLock(path, () -> {
            StoredScanHistory latest = readStored(path);
            List<ScanHistoryEntry> updated = new ArrayList<>(latest.entries());
            boolean removed = updated.removeIf(entry -> entry.localPath().equals(localPath));
            if (!removed) {
                history = latest;
                return false;
            }
            StoredScanHistory next = new StoredScanHistory(updated);
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            history = next;
            return true;
        });
    }

    private ScanHistoryEntry upsert(ScanHistoryEntry entry) throws IOException {
        return AtomicFiles.withLock(path, () -> {
            StoredScanHistory latest = readStored(path);
            StoredScanHistory next = upsertState(latest, entry, entry.localPath());
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            history = next;
            return entry;
        });
    }

    private ScanHistoryEntry update(
        String localPath,
        String missingMessage,
        Function<ScanHistoryEntry, ScanHistoryEntry> mutation
    ) throws IOException {
        return AtomicFiles.withLock(path, () -> {
            StoredScanHistory latest = readStored(path);
            ScanHistoryEntry current = latest.entries().stream()
                .filter(entry -> entry.localPath().equals(localPath))
                .findFirst()
                .orElseThrow(() -> new IOException(missingMessage));
            ScanHistoryEntry updated = mutation.apply(current);
            StoredScanHistory next = upsertState(latest, updated, localPath);
            AtomicFiles.writePrivateUtf8(path, GSON.toJson(next));
            history = next;
            return updated;
        });
    }

    private static StoredScanHistory upsertState(
        StoredScanHistory source,
        ScanHistoryEntry entry,
        String replacedLocalPath
    ) {
        List<ScanHistoryEntry> updated = new ArrayList<>();
        updated.add(entry);
        for (ScanHistoryEntry existing : source.entries()) {
            if (!existing.localPath().equals(entry.localPath())
                && !existing.localPath().equals(replacedLocalPath)) {
                updated.add(existing);
            }
            if (updated.size() >= MAX_ENTRIES) break;
        }
        return new StoredScanHistory(updated);
    }

    private record StoredScanHistory(List<ScanHistoryEntry> entries) {
    }
}
