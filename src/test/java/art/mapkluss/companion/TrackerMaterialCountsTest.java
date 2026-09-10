package art.mapkluss.companion;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrackerMaterialCountsTest {
    @Test void registryNamesMatchResourceKeysAndMissingCountsReset() {
        var materials=List.of(new BuildSessionMaterial("stone","Stone",2),new BuildSessionMaterial("minecraft:sand","Sand",4));
        assertEquals(Map.of("stone",2,"minecraft:sand",1),TrackerMaterialCounts.forSession(materials,Map.of("minecraft:stone",3,"minecraft:sand",1)));
        assertEquals(Map.of("stone",0,"minecraft:sand",0),TrackerMaterialCounts.forSession(materials,Map.of()));
    }
}
