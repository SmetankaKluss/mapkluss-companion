package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildOrbitPreviewTest {
    @Test void rendersRealHeightRotationAndNonblankContainedPixels(){
        int[] heights=new int[128*128],colours=new int[heights.length];
        for(int z=0;z<128;z++)for(int x=0;x<128;x++){int i=z*128+x;heights[i]=x/4;colours[i]=x<64?0xffee5577:0xff44ccaa;}
        var scene=new LiveBuildOrbitPreview.Surface(128,128,heights);
        var first=LiveBuildOrbitPreview.render(scene,colours,.3);
        assertTrue(Arrays.stream(first).filter(p->p!=0).count()>10000);
        assertArrayEquals(first,LiveBuildOrbitPreview.render(scene,colours,.3));
        assertFalse(Arrays.equals(first,LiveBuildOrbitPreview.render(scene,colours,1.7)));
        assertFalse(Arrays.equals(first,LiveBuildOrbitPreview.render(new LiveBuildOrbitPreview.Surface(128,128,new int[heights.length]),colours,.3)));
        for(int x=0;x<LiveBuildOrbitPreview.WIDTH;x++){assertEquals(0,first[x]);assertEquals(0,first[first.length-1-x]);}
        for(int y=0;y<LiveBuildOrbitPreview.HEIGHT;y++){assertEquals(0,first[y*LiveBuildOrbitPreview.WIDTH]);assertEquals(0,first[(y+1)*LiveBuildOrbitPreview.WIDTH-1]);}
    }
    @Test void emptyTransparentSceneDoesNotInventBlocks(){
        assertTrue(Arrays.stream(LiveBuildOrbitPreview.render(new LiveBuildOrbitPreview.Surface(2,2,new int[4]),new int[4],0)).allMatch(p->p==0));
    }
}
