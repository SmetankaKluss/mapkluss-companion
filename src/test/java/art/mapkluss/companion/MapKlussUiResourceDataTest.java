package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MapKlussUiResourceDataTest {
    @Test
    void readsRgbArgbAndLayoutOverridesWithoutDroppingDefaults() throws IOException {
        MapKlussUiResourceData.Snapshot snapshot = MapKlussUiResourceData.read(
            new StringReader("""
                {
                  "colors": {
                    "cyan": "#123456",
                    "backdrop": "#80112233"
                  }
                }
                """),
            new StringReader("""
                {
                  "metrics": {
                    "header_inset": 13
                  }
                }
                """),
            MapKlussUiResourceData.defaults()
        );

        assertEquals(0xFF123456, snapshot.color("cyan"));
        assertEquals(0x80112233, snapshot.color("backdrop"));
        assertEquals(13, snapshot.metric("header_inset"));
        assertEquals(UiTheme.LIME, snapshot.color("accent"));
        assertEquals(76, snapshot.metric("back_width"));
    }

    @Test
    void rejectsMalformedColorsAndUnboundedMetrics() {
        assertThrows(IOException.class, () -> MapKlussUiResourceData.read(
            new StringReader("{\"colors\":{\"cyan\":\"#12345\"}}"),
            null,
            null
        ));
        assertThrows(IOException.class, () -> MapKlussUiResourceData.read(
            null,
            new StringReader("{\"metrics\":{\"header_inset\":513}}"),
            null
        ));
    }
}
