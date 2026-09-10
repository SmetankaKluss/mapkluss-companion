package art.mapkluss.companion;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopTwoLayerLayoutTest {
    @Test void layoutKeepsControlsSeparateAtEveryScale() {
        for (int[] size : new int[][]{{240,180},{320,180},{320,240},{427,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var s = WorkshopTwoLayerLayout.at(size[0], size[1]);
            var bands = new WorkshopLayout.Rect[]{s.navigation(),s.title(),s.list(),s.paging(),s.actions(),s.footer()};
            for (int i=0;i<bands.length;i++) {
                assertTrue(s.frame().contains(bands[i]));
                for (int j=i+1;j<bands.length;j++) assertFalse(bands[i].intersects(bands[j]));
            }
            assertTrue(s.list().contains(s.source(0)));
            assertTrue(s.list().contains(s.source(1)));
            assertFalse(s.source(0).intersects(s.source(1)));
            for (int i=0;i<s.rows();i++) assertTrue(s.list().contains(s.row(i)));
            if(s.split()) {
                assertFalse(s.preview().intersects(s.list()));
                assertFalse(s.metadata().intersects(s.paging()));
            }
        }
    }

    private SuppressionBundleCatalog.Tile tile(int col, int row, int color) {
        byte[] pixels = new byte[16384];
        java.util.Arrays.fill(pixels,(byte)color);
        var bundle = new SuppressionBundle(new SuppressionPlanParser.Parsed(null,pixels,pixels),
            new byte[0],new byte[0],"","","","","test","test");
        return new SuppressionBundleCatalog.Tile("t",1,col,row,bundle);
    }
    private SuppressionBundleCatalog catalog(int wide,int tall,SuppressionBundleCatalog.Tile... tiles) {
        return new SuppressionBundleCatalog("","","","","",wide,tall,List.of(tiles));
    }
    @Test void mosaicUsesActualTileCoordinatesAndPreservesTransparency() {
        var c = catalog(2,2,tile(1,0,200),tile(0,1,4),tile(1,1,0));
        int[] result = SuppressionPreviewPixels.assemble(c,v -> 0xff000000|v);
        assertEquals(256*256,result.length);
        assertEquals(0,result[0]);
        assertEquals(0xff0000c8,result[128]);
        assertEquals(0xff000004,result[128*256]);
        assertEquals(0,result[128*256+128]);
        assertArrayEquals(result,SuppressionPreviewPixels.assemble(c,v -> 0xff000000|v));
    }
    @Test void invalidCatalogsCannotAllocateUnboundedTextures() {
        assertThrows(IllegalArgumentException.class,()->SuppressionPreviewPixels.assemble(catalog(100,1),v->v));
        assertThrows(IllegalArgumentException.class,()->SuppressionPreviewPixels.assemble(catalog(1,1,tile(1,0,4)),v->v));
        assertThrows(IllegalArgumentException.class,()->SuppressionPreviewPixels.assemble(catalog(1,1,tile(0,0,4),tile(0,0,5)),v->v));
    }
}
