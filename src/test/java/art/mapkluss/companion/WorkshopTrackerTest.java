package art.mapkluss.companion;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopTrackerTest {
    @Test void tableAndControlsFitWithoutShrinkingText() {
        for(int[] size:new int[][]{{240,180},{320,240},{480,270},{640,360},{960,540},{1280,720}}){
            var s=WorkshopTrackerLayout.at(size[0],size[1]);
            var bands=List.of(s.navigation(),s.title(),s.modes(),s.header(),s.table(),s.footer());
            for(var r:bands)assertTrue(s.frame().contains(r));
            for(int i=0;i<bands.size();i++)for(int j=i+1;j<bands.size();j++)assertFalse(bands.get(i).intersects(bands.get(j)));
            assertTrue(s.rows()>=1,"At least one editable material: "+size[0]+"x"+size[1]);
            assertTrue(s.table().height()>=44,"Tools require two 20px rows");
            for(int i=0;i<s.rows();i++){
                assertTrue(s.table().contains(s.row(i)));
                assertTrue(s.row(i).contains(s.controls(s.row(i))));
            }
        }
    }
    @Test void actualKeyboardAdaptersForwardUnhandledKeys() throws Exception {
        for(String v:List.of("compat1214","compat1218","compat12111","compat262")){
            String s=java.nio.file.Files.readString(java.nio.file.Path.of("src",v,"java/art/mapkluss/companion/WorkshopTrackerScreen.java"));
            assertTrue(s.contains("@Override public boolean keyPressed("));
            assertTrue(s.contains("submitFocusedInput()"));
            assertTrue(s.contains("return super.keyPressed("));
        }
    }
    @Test void queueAndFixtureBoundariesRemainExplicit() throws Exception {
        for(String v:List.of("minecraftLegacy","minecraft262")){
            String s=java.nio.file.Files.readString(java.nio.file.Path.of("src",v,"java/art/mapkluss/companion/TrackerSessionScreen.java"));
            assertTrue(s.contains("if (!fixture) mutations.submit(TrackerMutation.mode"));
            assertTrue(s.contains("if (fixture) { status = \"\"; return; }"));
            assertTrue(s.contains("if(!fixture&&!opened){opened=true;load();}"));
            assertTrue(s.contains("manualDrafts.getOrDefault(draftKey(material)"));
            assertTrue(s.contains("requests.isCurrent(request)"));
            assertFalse(s.contains("MapKlussUi.drawShell"));
        }
    }
}
