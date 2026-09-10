package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopConsentLayoutTest {
    @Test void wrappedCopyAndBothChoicesFitSmallAndLargeGui() {
        for(int width:new int[]{320,480,640,960})for(int height:new int[]{240,360,540})
            for(int body:new int[]{32,44,56,68}) {
                var frame=WorkshopOverlayLayout.consent(width,height,body);
                assertTrue(frame.x()>=8);
                assertTrue(frame.right()<=width-8);
                assertTrue(frame.y()>=8);
                assertTrue(frame.bottom()<=height-8);
                int bodyEnd=frame.y()+36+body;
                int firstButton=frame.y()+body+46;
                int secondButtonEnd=firstButton+28+24;
                assertTrue(firstButton>bodyEnd);
                assertTrue(secondButtonEnd<frame.bottom()-18);
            }
    }
}
