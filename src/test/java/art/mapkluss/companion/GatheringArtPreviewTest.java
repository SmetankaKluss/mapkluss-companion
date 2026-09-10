package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GatheringArtPreviewTest {
    private BuildSessionState session(String preview,int gathered,int placed){
        return new BuildSessionState("fixture",null,preview,List.of(new BuildSessionMaterial("minecraft:stone","Stone",100)),
            Map.of("minecraft:stone",gathered),Map.of("minecraft:stone",placed),"gathering",null,null,null);
    }
    @Test void gatheringNeverUsesPlacedCountsAndRestoresExactOriginal(){
        assertEquals(0,GatheringArtPreview.percent(session(null,0,100)));
        assertEquals(50,GatheringArtPreview.percent(session(null,50,100)));
        assertEquals(100,GatheringArtPreview.percent(session(null,999,0)));
        assertEquals(0,GatheringArtPreview.percent(session(null,-1,100)));
        int[] original={0x12ff0000,0xff00ff00,0x000000ff};
        var palette=Map.of("minecraft:stone",0xff0000);
        var reveal=new GatheringPixelReveal(original,palette);
        assertArrayEquals(original,reveal.render(session(null,100,0),palette));
        var grey=reveal.render(session(null,0,0),palette);
        for(int i=0;i<grey.length;i++){
            assertEquals(original[i]&0xff000000,grey[i]&0xff000000);
            if((original[i]>>>24)!=0){assertEquals(grey[i]&255,(grey[i]>>>8)&255);assertEquals(grey[i]&255,(grey[i]>>>16)&255);}
        }
    }
    @Test void inlineImageLoadsOffThreadAndUpdatesWithoutChangingSource()throws Exception {
        var png=new java.awt.image.BufferedImage(2,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0,0,0xffff0000);png.setRGB(1,0,0xff00ff00);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(png,"png",bytes);
        String source="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        try(var preview=new GatheringArtPreview()){
            var palette=Map.of("minecraft:stone",0xff0000);
            preview.update(session(source,0,100),palette);waitFor(preview,1);var grey=preview.frame().pixels().clone();
            preview.update(session(source,100,0),palette);waitFor(preview,2);
            assertEquals(0xffff0000,preview.frame().pixels()[0]);assertNotEquals(grey[0],preview.frame().pixels()[0]);
            assertFalse(preview.failed());
        }
    }
    @Test void coloursRevealIndependentlyWithExactPartialCountAndMonotonicOrder(){
        int iron=0xffcbbdba,sand=0xffe9d889;
        int[] pixels={iron,sand,iron,sand,iron,sand,iron,sand,0x00123456};
        var palette=Map.of("iron",iron&0xffffff,"sand",sand&0xffffff);
        var reveal=new GatheringPixelReveal(pixels,palette);
        var half=reveal.render(twoMaterials(4,2),palette);
        int visibleSand=0;
        for(int i=0;i<8;i++){
            if(i%2==0)assertEquals(iron,half[i]);
            else if(half[i]==sand)visibleSand++;
        }
        assertEquals(2,visibleSand);assertEquals(pixels[8],half[8]);
        var more=reveal.render(twoMaterials(4,3),palette);
        for(int i=0;i<8;i++)if(half[i]==pixels[i])assertEquals(pixels[i],more[i]);
        assertArrayEquals(pixels,reveal.render(twoMaterials(4,4),palette));
        assertArrayEquals(half,reveal.render(twoMaterials(4,2),palette));
        assertFalse(Arrays.equals(reveal.render(twoMaterials(4,0),palette),reveal.render(twoMaterials(0,4),palette)));
    }
    private BuildSessionState twoMaterials(int iron,int sand){
        return new BuildSessionState("fixture",null,null,List.of(new BuildSessionMaterial("iron","Iron",4),new BuildSessionMaterial("sand","Sand",4)),
            Map.of("iron",iron,"sand",sand),Map.of(),"gathering",null,null,null);
    }
    private void waitFor(GatheringArtPreview preview,long revision)throws Exception {
        long end=System.nanoTime()+3_000_000_000L;
        while((preview.frame()==null||preview.frame().revision()<revision)&&!preview.failed()&&System.nanoTime()<end)Thread.sleep(10);
        assertNotNull(preview.frame());assertTrue(preview.frame().revision()>=revision);
    }
    @Test void transientLoadFailureCanRetryWithoutChangingSession()throws Exception{
        var png=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        png.setRGB(0,0,0xffff0000);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(png,"png",bytes);
        String fresh="data:image/png;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        var attempts=new java.util.concurrent.atomic.AtomicInteger();
        try(var preview=new GatheringArtPreview(s->{if(attempts.incrementAndGet()==1)throw new java.io.IOException("expired");return fresh;})){
            var s=session("old-invalid-link",100,0);var colours=Map.of("minecraft:stone",0xff0000);
            preview.update(s,colours);
            long end=System.nanoTime()+3_000_000_000L;
            while(!preview.failed()&&System.nanoTime()<end)Thread.sleep(10);
            assertTrue(preview.failed());
            preview.retry();preview.update(s,colours);waitFor(preview,1);
            assertEquals(0xffff0000,preview.frame().pixels()[0]);assertEquals(2,attempts.get());
        }
    }
}
