package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScanHistoryStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void remembersMostRecentScansFirst() throws Exception {
        Path historyPath = tempDir.resolve("scan-history.json");
        Path firstPng = tempDir.resolve("first.png");
        Path secondPng = tempDir.resolve("second.png");
        Files.write(firstPng, new byte[] {1, 2, 3});
        Files.write(secondPng, new byte[] {4, 5, 6});

        ScanHistoryStore store = ScanHistoryStore.load(historyPath);
        store.remember(new MapScanDraft("first", "hand", 1, 1, 0, new byte[] {1}), firstPng);
        store.remember(new MapScanDraft("second", "wall", 2, 3, 1, new byte[] {2}), secondPng);

        List<ScanHistoryEntry> entries = ScanHistoryStore.load(historyPath).entries();
        assertEquals(2, entries.size());
        assertEquals("second", entries.getFirst().title());
        assertEquals("first", entries.get(1).title());
    }

    @Test
    void independentlyLoadedStoresMergeAndRenameWithoutLeavingADuplicate() throws Exception {
        Path historyPath = tempDir.resolve("shared-scan-history.json");
        Path firstPng = tempDir.resolve("first.png");
        Path renamedPng = tempDir.resolve("first-renamed.png");
        Path secondPng = tempDir.resolve("second.png");
        ScanHistoryStore first = ScanHistoryStore.load(historyPath);
        ScanHistoryStore second = ScanHistoryStore.load(historyPath);
        first.remember(new MapScanDraft("first", "hand", 1, 1, 0, new byte[] {1}), firstPng);
        second.remember(new MapScanDraft("second", "wall", 1, 1, 0, new byte[] {2}), secondPng);
        first.rename(firstPng.toString(),
            new MapScanDraft("renamed", "hand", 1, 1, 0, new byte[] {1}), renamedPng.toString());

        List<ScanHistoryEntry> entries = ScanHistoryStore.load(historyPath).entries();
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(entry -> entry.localPath().equals(renamedPng.toString())));
        assertTrue(entries.stream().anyMatch(entry -> entry.localPath().equals(secondPng.toString())));
        assertFalse(entries.stream().anyMatch(entry -> entry.localPath().equals(firstPng.toString())));
    }

    @Test
    void attachesUploadMetadataToExistingEntry() throws Exception {
        Path historyPath = tempDir.resolve("scan-history.json");
        Path png = tempDir.resolve("scan.png");
        Files.write(png, new byte[] {1, 2, 3});

        ScanHistoryStore store = ScanHistoryStore.load(historyPath);
        store.remember(new MapScanDraft("scan", "frame", 1, 1, 0, new byte[] {1}), png);
        store.attachUpload(png.toString(), new ScanUploadResponse("import-1", "cloud.png", null, "abc", "2026-07-01T00:00:00Z", false));
        store.attachImportDetails(png.toString(), new ScanImportDetails("import-1", "art-123"));

        ScanHistoryEntry entry = ScanHistoryStore.load(historyPath).entries().getFirst();
        assertEquals("import-1", entry.importId());
        assertEquals("art-123", entry.createdArtId());
        assertEquals("abc", entry.uploadedSha256());
        assertTrue(entry.hasImport());
        assertTrue(entry.hasCreatedArt());
    }

    @Test
    void repeatedUploadKeepsAnExistingCreatedArtLink() throws Exception {
        Path historyPath = tempDir.resolve("repeat-upload-history.json");
        Path png = tempDir.resolve("repeat.png");
        MapScanDraft draft = new MapScanDraft("scan", "frame", 1, 1, 0, new byte[] {1});
        ScanHistoryStore store = ScanHistoryStore.load(historyPath);
        store.remember(draft, png);
        store.attachUpload(png.toString(), new ScanUploadResponse("import-1", "cloud.png", null, "old", null, false));
        store.attachImportDetails(png.toString(), new ScanImportDetails("import-1", "art-123"));

        store.rememberUpload(draft, png,
            new ScanUploadResponse("import-2", "cloud.png", null, "old", null, true));

        ScanHistoryEntry entry = ScanHistoryStore.load(historyPath).entries().getFirst();
        assertEquals("import-2", entry.importId());
        assertEquals("art-123", entry.createdArtId());
        assertEquals("old", entry.uploadedSha256());
    }

    @Test
    void uploadOfDifferentContentClearsTheOldCreatedArtLink() throws Exception {
        Path historyPath = tempDir.resolve("changed-upload-history.json");
        Path png = tempDir.resolve("changed.png");
        MapScanDraft draft = new MapScanDraft("scan", "frame", 1, 1, 0, new byte[] {1});
        ScanHistoryStore store = ScanHistoryStore.load(historyPath);
        store.remember(draft, png);
        store.attachUpload(png.toString(), new ScanUploadResponse("import-1", "cloud.png", null, "old", null, false));
        store.attachImportDetails(png.toString(), new ScanImportDetails("import-1", "art-123"));

        store.rememberUpload(draft, png,
            new ScanUploadResponse("import-2", "cloud.png", null, "new", null, false));

        ScanHistoryEntry entry = ScanHistoryStore.load(historyPath).entries().getFirst();
        assertEquals("import-2", entry.importId());
        assertEquals(null, entry.createdArtId());
        assertEquals("new", entry.uploadedSha256());
    }

    @Test
    void removesEntryByLocalPath() throws Exception {
        Path historyPath = tempDir.resolve("scan-history.json");
        Path firstPng = tempDir.resolve("first.png");
        Path secondPng = tempDir.resolve("second.png");
        Files.write(firstPng, new byte[] {1});
        Files.write(secondPng, new byte[] {2});

        ScanHistoryStore store = ScanHistoryStore.load(historyPath);
        store.remember(new MapScanDraft("first", "hand", 1, 1, 0, new byte[] {1}), firstPng);
        store.remember(new MapScanDraft("second", "frame", 1, 1, 0, new byte[] {2}), secondPng);

        assertTrue(store.remove(firstPng.toString()));

        List<ScanHistoryEntry> entries = ScanHistoryStore.load(historyPath).entries();
        assertEquals(1, entries.size());
        assertEquals("second", entries.getFirst().title());
        assertFalse(store.remove(firstPng.toString()));
    }
}
