package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopAccountLayoutTest {
    @Test void accountRowsStayInsideBodyAtSupportedSizes() {
        for (int[] size : new int[][] {{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var form = WorkshopLayout.account(size[0], size[1]);
            assertTrue(new WorkshopLayout.Rect(0,0,size[0],size[1]).contains(form.frame()));
            for (var region : new WorkshopLayout.Rect[] {form.navigation(),form.tabs(),form.preview(),form.footer()}) {
                assertTrue(form.frame().contains(region));
            }
            for (int row=0; row<4; row++) {
                var bounds = new WorkshopLayout.Rect(form.preview().x(),form.preview().y()+row*30,form.preview().width(),24);
                assertTrue(form.preview().contains(bounds));
                assertFalse(bounds.intersects(form.footer()));
            }
        }
    }
}
