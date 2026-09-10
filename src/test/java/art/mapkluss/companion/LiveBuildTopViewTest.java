package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildTopViewTest {
    private static LiveBuildProgress.Cell cell(int x, int y, int z) {
        return new LiveBuildProgress.Cell(new LiveBuildProgress.Position(x,y,z),
            new LiveBuildProgress.State("minecraft:stone", Map.of()));
    }
    @Test void highestTargetWinsAndEmptyColumnsRemainEmpty() {
        var view = new LiveBuildTopView(List.of(cell(-1,0,0),cell(-1,2,0),cell(1,0,0)));
        assertEquals(3,view.width());
        assertEquals(1,view.height());
        assertEquals(1,view.index(0));
        assertEquals(-1,view.index(1));
        assertEquals(2,view.index(2));
    }
    @Test void largeFootprintUsesBoundedSampling() {
        var view = new LiveBuildTopView(List.of(cell(0,0,0),cell(8191,1,4095)));
        assertEquals(128,view.width());
        assertEquals(64,view.height());
        assertEquals(1,view.index(128*64-1));
    }
    @Test void rejectsEmptyAndOverflowingFootprints() {
        assertThrows(IllegalArgumentException.class,()->new LiveBuildTopView(List.of()));
        assertThrows(IllegalArgumentException.class,()->new LiveBuildTopView(List.of(cell(Integer.MIN_VALUE,0,0),cell(Integer.MAX_VALUE,0,0))));
    }
}
