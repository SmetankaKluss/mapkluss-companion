package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class TrackerWebSnapshotTest {
    @Test void automaticCountsFollowPlacedAndBrokenBlocksButExcludeRemovalTargets() {
        var id=new LiveBuildProgress.Identity("c".repeat(64),"world","minecraft:overworld",new LiveBuildProgress.Position(0,0,0),0);
        var stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
        var air=new LiveBuildProgress.State("minecraft:air",Map.of());
        var p=new LiveBuildProgress(id,List.of(
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(1,0,0),stone,true)));
        p.scan(id,pos->pos.x()==0?stone:air,2,10);
        var first=new TrackerWebSnapshot(Arrays.asList(p,null));
        while(!first.advance(10,100)) { }
        assertEquals(Map.of("minecraft:stone",1),first.materialCounts());
        p.scan(id,pos->air,2,20);
        var second=new TrackerWebSnapshot(List.of(p));
        while(!second.advance(20,100)) { }
        assertTrue(second.materialCounts().isEmpty());
    }
    @Test void nativeColoursAndDormantMaterialsStayExact() {
        var identity=new LiveBuildProgress.Identity("a".repeat(64),"world","minecraft:overworld",new LiveBuildProgress.Position(0,0,0),0);
        var stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
        var progress=new LiveBuildProgress(identity,List.of(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone)));
        progress.scan(identity,p->stone,1,10);
        progress.detachGeometry();
        var snapshot=new TrackerWebSnapshot(Arrays.asList(progress,null));
        for(int i=0;i<100&&!snapshot.advance(10,100);i++) { }
        int[] pixels={0xff102030,0,0xffc74e59};
        var result=snapshot.capture(3,1,pixels,progress.liveSummary(10),10);
        assertEquals(1,result.getAsJsonObject("materials").get("minecraft:stone").getAsInt());
        var bytes=Base64.getDecoder().decode(result.get("pixels").getAsString());
        for(int i=0;i<pixels.length;i++)assertEquals(pixels[i],result.getAsJsonArray("palette").get((bytes[i*2]&255)|((bytes[i*2+1]&255)<<8)).getAsInt());
        assertTrue(result.getAsJsonArray("parts").get(1).isJsonNull());
    }
    @Test void expiredEvidenceDoesNotCountAsPlaced() {
        var id=new LiveBuildProgress.Identity("b".repeat(64),"world","minecraft:overworld",new LiveBuildProgress.Position(0,0,0),0);
        var stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
        var p=new LiveBuildProgress(id,List.of(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone)));
        p.scan(id,pos->stone,1,10);
        var snapshot=new TrackerWebSnapshot(List.of(p));
        while(!snapshot.advance(200000,100)) { }
        assertEquals(0,snapshot.capture(1,1,new int[]{0},p.liveSummary(200000),200000).getAsJsonObject("materials").size());
    }
    @Test void cloudIdentitySurvivesRestartWithoutCredentials() throws Exception {
        var cloud=new LiveBuildSessionStore.CloudLink("11111111-1111-4111-8111-111111111111","22222222-2222-4222-8222-222222222222");
        var session=new LiveBuildSessionStore.Snapshot(LiveBuildSessionStore.SourceKind.LITEMATIC,"a".repeat(64),"b".repeat(64),"minecraft:overworld",1,1,0,123,List.of()).withCloud(cloud);
        assertEquals(session,LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(session)));
    }
}
