package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildGeometryResidencyTest {
    private final LiveBuildProgress.State stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
    private final LiveBuildProgress.Identity identity=new LiveBuildProgress.Identity("a".repeat(64),"world","dimension",
        new LiveBuildProgress.Position(0,0,0),1);
    private LiveBuildProgress fresh(){return new LiveBuildProgress(identity,List.of(
        new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone),
        new LiveBuildProgress.Cell(new LiveBuildProgress.Position(1,0,0),stone)));}

    @Test void evictionPreservesTimestampCursorAndExpiryWithoutReadingWorld(){
        var p=fresh();p.scan(identity,pos->stone,1,100);
        var before=p.observation(0,100,120000);
        p.detachGeometry();assertFalse(p.geometryResident());assertEquals(2,p.size());
        assertThrows(IllegalStateException.class,()->p.cell(0));
        assertEquals(0,p.scan(identity,pos->{fail("Dormant geometry cannot scan");return stone;},2,101));
        assertEquals(before,p.observation(0,101,120000));assertEquals(1,p.liveSummary(101).correct());
        p.resumeGeometry(fresh());assertTrue(p.geometryResident());
        p.scan(identity,pos->{assertEquals(1,pos.x(),"Resume retains round-robin cursor");return stone;},1,102);
        assertEquals(2,p.liveSummary(102).correct());
        p.detachGeometry();assertEquals(2,p.liveSummary(200000).stale());
        assertEquals(2,p.summary(200000,120000).stale());
    }
    @Test void closedDormantTargetCannotBecomeFreshOnGeometryReload(){
        var p=fresh();p.scan(identity,pos->stone,2,0);p.detachGeometry();p.close();p.resumeGeometry(fresh());
        assertEquals(0,p.scan(identity,pos->{fail("Closed world must not scan");return stone;},2,1));
        assertEquals(2,p.liveSummary(1).stale());
    }
    @Test void geometryResumeRejectsDifferentIdentityOrAlreadyObservedDonor(){
        var p=fresh();p.detachGeometry();
        var wrong=new LiveBuildProgress(new LiveBuildProgress.Identity("b".repeat(64),"world","dimension",
            new LiveBuildProgress.Position(0,0,0),1),List.of(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone)));
        assertThrows(IllegalArgumentException.class,()->p.resumeGeometry(wrong));
        var observed=fresh();observed.scan(identity,pos->stone,1,0);
        assertThrows(IllegalArgumentException.class,()->p.resumeGeometry(observed));
        assertFalse(p.geometryResident());assertEquals(2,p.liveSummary(0).unknown());
    }
}
