package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopLensLayoutTest {
    @Test void controlsAndRowsNeverOverlap() {
        for (int[] size : new int[][]{{320,240},{427,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var s = WorkshopLensLayout.at(size[0], size[1]);
            var bands = new WorkshopLayout.Rect[]{s.navigation(),s.status(),s.join(),s.tabs(),s.list(),s.visibility(),s.actions(),s.footer()};
            for (int i=0;i<bands.length;i++) {
                assertTrue(s.frame().contains(bands[i]), java.util.Arrays.toString(size));
                for (int j=i+1;j<bands.length;j++) assertFalse(bands[i].intersects(bands[j]));
            }
            for (int i=0;i<s.rows();i++) assertTrue(s.list().contains(s.row(i)));
            assertThrows(IndexOutOfBoundsException.class, () -> s.row(s.rows()));
            if(s.split()) {
                assertFalse(s.preview().intersects(s.list()));
                assertFalse(s.metadata().intersects(s.visibility()));
            }
        }
    }

    @Test void sessionOwnershipAndDeviceOwnershipAreDifferent() {
        var grid = new LensDtos.Grid(1,1);
        for (boolean owner : new boolean[]{true,false}) {
            var session = new LensDtos.Session("s","t","active",grid,"2d",1,128,128,128,1,"","","",owner,null);
            assertEquals(owner,LensUiPermissions.allowed("lens.place",session,null,false));
            assertEquals(!owner,LensUiPermissions.allowed("lens.leave",session,null,false));
            for(boolean device : new boolean[]{true,false}) {
                var p = new LensDtos.Placement("p","s","o","t","group","","",new LensDtos.Anchor(0,0,0),"north",grid,1,128,"",0.0,device,null);
                assertEquals(device,LensUiPermissions.allowed("lens.remove_placement",session,p,owner));
                for(String action:new String[]{"lens.hide_placement","lens.hide_author","lens.report"})
                    assertEquals(!owner,LensUiPermissions.allowed(action,session,p,owner));
            }
        }
        for(String action:new String[]{"lens.place","lens.leave","lens.hide_placement","lens.hide_author","lens.report","lens.remove_placement"})
            assertFalse(LensUiPermissions.allowed(action,null,null,false));
    }
}
