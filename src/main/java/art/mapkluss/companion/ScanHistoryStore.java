package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        if (!Files.exists(path)) return new ScanHistoryStore(path, new StoredScanHistory(new ArrayList<>()));
        try (Reader reader = Files.newBufferedReader(path)) {
            StoredScanHistory loaded = GSON.fromJson(reader, StoredScanHistory.class);
            if (loaded == null || loaded.entries() == null) loaded = new StoredScanHistory(new ArrayList<>());
            return new ScanHistoryStore(path, loaded);
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
        upsert(entry);
        return entry;
    }

    public ScanHistoryEntry attachUpload(String localPath, ScanUploadResponse response) throws IOException {
        ScanHistoryEntry current = history.entries().stream()
            .filter(entry -> entry.localPath().equals(localPath))
            .findFirst()
            .orElseThrow(() -> new IOException("Scan history entry was not found for upload result."));
        ScanHistoryEntry updated = new ScanHistoryEntry(
            current.title(),
            current.source(),
            current.wide(),
            current.tall(),
            current.missingMaps(),
            current.localPath(),
            response.importId(),
            current.createdArtId(),
            response.sha256(),
            Instant.now().toString(),
            current.createdAt()
        );
        upsert(updated);
        return updated;
    }

    public ScanHistoryEntry attachImportDetails(String localPath, ScanImportDetails details) throws IOException {
        ScanHistoryEntry current = history.entries().stream()
            .filter(entry -> entry.localPath().equals(localPath))
            .findFirst()
            .orElseThrow(() -> new IOException("Scan history entry was not found for import details."));
        ScanHistoryEntry updated = new ScanHistoryEntry(
            current.title(),
            current.source(),
            current.wide(),
            current.tall(),
            current.missingMaps(),
            current.localPath(),
            details.importId() == null || details.importId().isBlank() ? current.importId() : details.importId(),
            details.createdArtId(),
            current.uploadedSha256(),
            current.uploadedAt(),
            current.createdAt()
        );
        upsert(updated);
        return updated;
    }

    public ScanHistoryEntry rename(String localPath, MapScanDraft draft, String newLocalPath) throws IOException {
        ScanHistoryEntry current = history.entries().stream()
            .filter(entry -> entry.localPath().equals(localPath))
            .findFirst()
            .orElseThrow(() -> new IOException("Scan history entry was not found for rename."));
        ScanHistoryEntry updated = new ScanHistoryEntry(
            draft.title(),
            draft.source(),
            draft.wide(),
            draft.tall(),
            draft.missingMaps(),
            newLocalPath,
            current.importId(),
            current.createdArtId(),
            current.uploadedSha256(),
            current.uploadedAt(),
            current.createdAt()
        );
        upsert(updated);
        return updated;
    }

    public boolean remove(String localPath) throws IOException {
        boolean removed = history.entries().removeIf(entry -> entry.localPath().equals(localPath));
        if (!removed) return false;
        save();
        return true;
    }

    private void upsert(ScanHistoryEntry entry) throws IOException {
        List<ScanHistoryEntry> updated = new ArrayList<>();
        updated.add(entry);
        for (ScanHistoryEntry existing : history.entries()) {
            if (!existing.localPath().equals(entry.localPath())) {
                updated.add(existing);
            }
            if (updated.size() >= MAX_ENTRIES) break;
        }
        history = new StoredScanHistory(updated);
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(history, writer);
        }
    }

    private record StoredScanHistory(List<ScanHistoryEntry> entries) {
    }
}
