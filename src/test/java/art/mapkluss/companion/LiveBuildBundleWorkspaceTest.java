package art.mapkluss.companion;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildBundleWorkspaceTest {
    @Test void workerPreparesProjectionForEveryPhaseWithoutLosingCellIndices()throws Exception{
        try(var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes())){
            for(int phase:new int[]{-1,0,63}){
                var prepared=workspace.prepare(workspace.begin(0,phase));
                var expected=new LiveBuildTopView(prepared.target().cells(),prepared.target().artBounds());
                assertEquals(128,prepared.projection().width());assertEquals(128,prepared.projection().height());
                for(int z=0;z<128;z++)for(int x=0;x<128;x++)
                    assertEquals(expected.pixelAt(x,z),prepared.projection().pixelAt(x,z));
            }
        }
    }
    private LiveBuildAssembly assembly(LiveBuildBundleWorkspace.Prepared prepared){
        var assembly=new LiveBuildAssembly(new LiveBuildParts(prepared.target()));
        var part=assembly.parts().part(0);
        var progress=new LiveBuildProgress(new LiveBuildProgress.Identity(part.targetSha256(),"test","overworld",
            new LiveBuildProgress.Position(0,0,0),1),part.cells());
        assembly.attach(0,progress);
        for(int i=0;i<progress.size();i+=4096)assembly.scan(p->new LiveBuildProgress.State("minecraft:stone",Map.of()),4096,0);
        return assembly;
    }
    @Test void keepsFullArtworkAndScopesIdenticalTilePayloadsIndependently()throws Exception{
        try(var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes())){
            assertEquals(256,workspace.width());assertEquals(128,workspace.height());
            assertEquals(32768,workspace.mapColours().length);assertEquals(0,workspace.completion(0));
            var first=workspace.prepare(workspace.begin(0,-1));
            var second=workspace.prepare(workspace.begin(1,-1));
            assertNotEquals(first.target().sha256(),second.target().sha256());
            workspace.publish(first,assembly(first));assertEquals(.5,workspace.completion(0));
            var original=workspace.assembly(0);
            workspace.publish(second,assembly(second));assertEquals(1,workspace.completion(0));
            assertSame(original,workspace.assembly(0));
            workspace.begin(1,0);assertEquals(.5,workspace.completion(0));assertSame(original,workspace.assembly(0));
            assertEquals(32768,workspace.mapColours().length);
        }
    }
    @Test void staleTileWorkCannotReplaceNewPhaseOrSurviveClose()throws Exception{
        var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
        var issued=workspace.begin(0,-1);
        assertThrows(java.util.concurrent.CancellationException.class,()->workspace.prepare(
            new LiveBuildBundleWorkspace.Request(issued.tile(),0,issued.revision())));
        var old=workspace.prepare(workspace.begin(0,-1));workspace.begin(0,0);
        assertThrows(java.util.concurrent.CancellationException.class,()->workspace.publish(old,assembly(old)));
        var pending=workspace.begin(1,-1);workspace.close();
        assertThrows(java.util.concurrent.CancellationException.class,()->workspace.prepare(pending));
    }
    @Test void wrongTileCannotBePublishedAndPreviewIsDefensiveCopy()throws Exception{
        try(var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes())){
            var first=workspace.prepare(workspace.begin(0,-1));var second=workspace.prepare(workspace.begin(1,-1));
            assertThrows(IllegalArgumentException.class,()->workspace.publish(first,assembly(second)));
            var colours=workspace.mapColours();byte before=colours[0];colours[0]^=1;
            assertEquals(before,workspace.mapColours()[0]);
        }
    }
}
