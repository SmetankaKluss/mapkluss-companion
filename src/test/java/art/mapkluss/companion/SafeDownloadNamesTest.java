package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SafeDownloadNamesTest {
    @Test
    void sanitizesDownloadedArtifactFilename() {
        assertEquals("castle_2x3.png", SafeNames.downloadFilename("castle 2x3.png", "preview_png"));
        assertEquals("preview_png", SafeNames.downloadFilename("..\\/:*?\"<>|", "preview_png"));
    }
}
