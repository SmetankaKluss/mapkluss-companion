package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.List;

class WorkshopScanTest {
    @Test void controlsFitEveryViewport() {
        for (int[] size : new int[][] {{240,180},{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var s = WorkshopScanLayout.at(size[0], size[1]);
            var rows = List.of(s.navigation(), s.title(), s.modes(), s.tabs(), s.actions(), s.footer());
            for (var row : rows) assertTrue(s.frame().contains(row), size[0] + " " + row);
            for (int a = 0; a < rows.size(); a++)
                for (int b = a + 1; b < rows.size(); b++) assertFalse(rows.get(a).intersects(rows.get(b)));
            assertTrue(s.frame().contains(s.preview()));
            assertTrue(s.preview().bottom() <= s.actions().y());
            if (s.split()) assertFalse(s.details().intersects(s.preview()));
            for (int count : new int[] {3,4,5}) for (int i = 0; i < count; i++)
                assertTrue(s.actions().contains(WorkshopScanLayout.part(s.actions(), i, count)));
        }
    }

    @Test void validatesRealScanPngAndRejectsWrongGeometry() throws Exception {
        byte[] png = MapScanAssembler.assemblePng(List.of(new MapScanAssembler.Tile(0,0,new int[128*128])),2,1);
        assertDoesNotThrow(() -> ScanPreviewValidation.validate(new MapScanDraft("","",2,1,1,png)));
        assertThrows(IOException.class, () -> ScanPreviewValidation.validate(new MapScanDraft("","",1,1,0,png)));
        assertThrows(IOException.class, () -> ScanPreviewValidation.validate(new MapScanDraft("","",1,1,0,new byte[40])));
        var oversized = png.clone();
        java.nio.ByteBuffer.wrap(oversized).putInt(16, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> ScanPreviewValidation.validate(new MapScanDraft("","",1,1,0,oversized)));
    }

    @Test void nativeAdaptersRetainLifecycleAndMutationGuards() throws Exception {
        for (String version : List.of("minecraftLegacy", "minecraft262")) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src", version, "java/art/mapkluss/companion/ScanScreen.java"));
            assertTrue(source.contains("draft == requestedDraft"));
            assertTrue(source.contains("previewDraft != draft"));
            assertTrue(source.contains("historyLoaded = false;"));
            assertTrue(source.contains("requests.detach();"));
            assertTrue(source.contains("previewTexture.close();"));
            assertTrue(source.contains("actionPage == 1 && !isSelectedHistoryActiveDraft()"));
            assertTrue(source.contains("(!fixture || localAction(id))"));
            assertFalse(source.contains("MapKlussUi.drawShell"));
        }
    }
}
