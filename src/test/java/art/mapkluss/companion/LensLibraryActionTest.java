package art.mapkluss.companion;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LensLibraryActionTest {
    @Test void selectedArtButtonStartsLensInEveryAdapter() throws Exception {
        for (String adapter : new String[]{"minecraft1214", "minecraft1218plus", "minecraft262"}) {
            String source = Files.readString(Path.of("src", adapter, "java/art/mapkluss/companion/CompanionLibraryScreen.java"));
            assertTrue(source.contains("this::quickPlaceSelected, this::quickLensSelected, this::quickTrackerSelected"), adapter);
            assertTrue(source.contains("if(!developmentFixtureActive)LensManager.instance().startCloud(client(),item.artId(),item.currentVersionId());"), adapter);
        }
    }
}
