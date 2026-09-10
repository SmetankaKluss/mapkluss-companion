package art.mapkluss.companion;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanionLibraryItemTest {
    @Test void twoLayerLabelSurvivesDecodeAndFavoriteUpdate() {
        var item=new Gson().fromJson("{\"mode\":\"3d\",\"buildTechnique\":\"suppression_two_layer\"}",CompanionLibraryItem.class);
        assertEquals("Two-layer",item.modeLabel());
        assertEquals("Two-layer",item.withFavorite(true).modeLabel());
        assertEquals("3d",item.mode());
    }
    @Test void oldResponsesAndOrdinaryModesRemainCompatible() {
        var gson=new Gson();
        assertEquals("3D",gson.fromJson("{\"mode\":\"3d\"}",CompanionLibraryItem.class).modeLabel());
        assertEquals("2D",gson.fromJson("{\"mode\":\"2d\",\"buildTechnique\":\"standard\"}",CompanionLibraryItem.class).modeLabel());
    }
}
