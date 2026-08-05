package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CompanionPreviewKeyTest {
    @Test
    void rotatingSignedUrlsReuseTheSameLibraryTextureKey() {
        CompanionLibraryItem first = item("https://example.invalid/preview?token=first");
        CompanionLibraryItem second = item("https://example.invalid/preview?token=second");

        assertEquals(
            CompanionPreviewKey.forLibraryItem(first, first.previewUrl()),
            CompanionPreviewKey.forLibraryItem(second, second.previewUrl())
        );
    }

    private static CompanionLibraryItem item(String previewUrl) {
        return new CompanionLibraryItem(
            "art-id",
            "version-id",
            "Fixture",
            "private",
            new CompanionManifest.Grid(1, 1),
            "2d",
            previewUrl,
            "2026-08-01T00:00:00Z",
            false
        );
    }
}
