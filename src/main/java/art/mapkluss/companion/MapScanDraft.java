package art.mapkluss.companion;

import java.util.Base64;

public record MapScanDraft(
    String title,
    String source,
    int wide,
    int tall,
    int missingMaps,
    byte[] pngBytes
) {
    public String dataUrl() {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);
    }
}
