package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildScanTargetsTest {
    @Test void roundRobinTracksAttachReplaceRemoveAndClose() {
        var state=new LiveBuildProgress.State("minecraft:stone",Map.of());
        var cells=List.of(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),state),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(128,0,0),state));
        var bounds=new LiveBuildSchematic.Bounds(0,0,0,255,0,127);
        var parts=new LiveBuildParts(new LiveBuildSchematic("a".repeat(64),cells,bounds,bounds));
        var assembly=new LiveBuildAssembly(parts);
        for(int i=0;i<2;i++){
            var part=parts.part(i);
            assembly.attach(i,new LiveBuildProgress(new LiveBuildProgress.Identity(part.targetSha256(),"world","dim",
                new LiveBuildProgress.Position(i*100,0,0),1),part.cells()));
        }
        var seen=new java.util.ArrayList<Integer>();
        LiveBuildProgress.WorldReader reader=p->{seen.add(p.x());return state;};
        for(int i=0;i<4;i++)assembly.scan(reader,1,0);
        assertEquals(List.of(0,100,0,100),seen);
        var old=assembly.placement(0);var part=parts.part(0);
        assembly.attach(0,new LiveBuildProgress(new LiveBuildProgress.Identity(part.targetSha256(),"world","dim",
            new LiveBuildProgress.Position(200,0,0),2),part.cells()));
        seen.clear();assembly.scan(reader,1,1);assertEquals(List.of(200),seen);
        assertEquals(0,old.scan(old.identity(),reader,1,1));
        assembly.remove(1);seen.clear();assembly.scan(reader,1,2);assertEquals(List.of(200),seen);
        assertEquals(1,assembly.summary(2).correct());
        assembly.close();seen.clear();assertEquals(0,assembly.scan(reader,1,3));assertTrue(seen.isEmpty());
    }
}
