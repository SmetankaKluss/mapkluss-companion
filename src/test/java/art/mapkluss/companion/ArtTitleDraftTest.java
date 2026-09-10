package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArtTitleDraftTest {
    @Test void remoteRefreshPreservesRename() {
        var draft=new ArtTitleDraft("Old");
        draft.edit("New");
        draft.receive("Remote");
        assertEquals("New",draft.value());
    }
    @Test void unchangedDraftFollowsServer() {
        var draft=new ArtTitleDraft("Old");
        draft.receive("Remote");
        assertEquals("Remote",draft.value());
    }
    @Test void saveAcknowledgementDoesNotDiscardNewerTyping() {
        var draft=new ArtTitleDraft("Old");
        draft.edit("Second");
        draft.saved("First","First");
        assertEquals("Second",draft.value());
        draft.saved("Second","Second");
        draft.receive("Third");
        assertEquals("Third",draft.value());
    }
    @Test void supportedArtGeometryFits() {
        for(int[] size:new int[][]{{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var l=WorkshopArtLayout.at(size[0],size[1]);
            assertTrue(l.controls().height()>=68);
            for(var r:new WorkshopLayout.Rect[]{l.navigation(),l.title(),l.preview(),l.tabs(),l.controls(),l.primary(),l.footer()})
                assertTrue(l.frame().contains(r));
            assertFalse(l.controls().intersects(l.primary()));
            if(l.split()) assertFalse(l.preview().intersects(l.controls()));
        }
    }
}
