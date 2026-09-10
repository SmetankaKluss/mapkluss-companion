package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopCollectionLayoutTest {
    @Test void rowsAndControlsFitAllSupportedViewports() {
        for(int[] size:new int[][]{{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var s=WorkshopCollectionLayout.at(size[0],size[1]);
            var bands=new WorkshopLayout.Rect[]{s.navigation(),s.heading(),s.manage(),s.search(),s.list(),s.footer()};
            for(int i=0;i<bands.length;i++) {
                assertTrue(s.frame().contains(bands[i]));
                for(int j=i+1;j<bands.length;j++)assertFalse(bands[i].intersects(bands[j]));
            }
            for(int i=0;i<s.rows();i++)assertTrue(s.list().contains(s.row(i)));
            assertThrows(IllegalArgumentException.class,()->s.row(s.rows()));
        }
    }
}
