package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopOverlayTest {
    @Test void updateActionsFitWithoutOverlap() {
        for (int[] size : new int[][]{{240,180},{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var frame=WorkshopOverlayLayout.update(size[0],size[1]);
            assertTrue(frame.x()>=0 && frame.y()>=0 && frame.right()<=size[0] && frame.bottom()<=size[1]);
            int end=frame.x();
            for(int i=0;i<3;i++){
                var action=WorkshopOverlayLayout.action(frame,i);
                assertTrue(action.x()>=end && action.right()<=frame.right());
                assertTrue(action.y()>frame.y()+56 && action.bottom()<frame.bottom());
                assertTrue(action.width()>=60);
                end=action.right();
            }
            var hud=WorkshopOverlayLayout.lens(size[0],4000);
            assertTrue(hud.right()<=size[0] && hud.width()>=0);
        }
    }

    @Test void hudThemeTracksSelectionWithoutConfigurationReads() {
        String previous=WorkshopHudTheme.current().id();
        try {
            for(String id:WorkshopTheme.IDS){
                WorkshopHudTheme.select(id);
                assertEquals(id,WorkshopHudTheme.current().id());
            }
        } finally { WorkshopHudTheme.select(previous); }
    }

    @Test void updateAndHudUseWorkshopAndRetainActions() throws Exception {
        for(String family:new String[]{"minecraftLegacy","minecraft262"}){
            String base="src/"+family+"/java/art/mapkluss/companion/";
            String source=Files.readString(Path.of(base+"CompanionUpdateScreen.java"));
            assertTrue(source.contains("WorkshopChrome.frame("));
            assertFalse(source.contains("MapKlussUi.draw"));
            assertFalse(source.contains("parent.render("));
            assertFalse(source.contains("parent.extractRenderState("));
            assertFalse(source.contains("parent.init("));
            assertFalse(source.contains("parent.resize("));
            assertTrue(source.contains("setScreen(parent)"));
            for(String action:new String[]{"update.telegram","update.download","update.dismiss"})
                assertTrue(source.contains(".action(\""+action+"\")"));
            assertTrue(source.contains("if (fixture) return;"));
            String account=Files.readString(Path.of(base+"CompanionAccountScreen.java"));
            assertTrue(account.contains("request != updateRequest"));
            assertTrue(account.contains("public void removed()"));
            assertTrue(account.contains("whenComplete((release, error)"));
        }
        for(String family:new String[]{"compat1214","compat1218","compat12111","compat262"}){
            String source=Files.readString(Path.of("src/"+family+"/java/art/mapkluss/companion/LensRenderBridge.java"));
            assertFalse(source.contains("WorkshopHudDraw.lens("));
            assertFalse(source.contains("realtimeWakeupCount()"));
            assertFalse(source.contains("entityCutout"));
            assertFalse(source.contains("getEntityCutout"));
            assertTrue(source.contains("getText(snapshot.atlas())") || source.contains("text(snapshot.atlas())"));
            assertFalse(source.contains("OverlayTexture"));
            assertTrue(source.contains("WorkshopHudDraw.twoLayer("));
        }
    }
}
