package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MapScanDraftTest {
    @Test
    void encodesPngAsDataUrl() {
        MapScanDraft draft = new MapScanDraft("scan", "wall", 2, 2, 1, new byte[] {1, 2, 3});

        assertTrue(draft.dataUrl().startsWith("data:image/png;base64,"));
        assertTrue(draft.dataUrl().endsWith("AQID"));
    }
}
