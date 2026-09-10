package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildPhaseSessionTest {
    @Test void initialCaptureNeverRequestsRemoval() {
        for(var stage:new SuppressionStage[]{SuppressionStage.BUILDING,SuppressionStage.INITIAL_MOVE,
            SuppressionStage.INITIAL_EQUIP,SuppressionStage.INITIAL_DWELL,SuppressionStage.INITIAL_STOW,
            SuppressionStage.INITIAL_VERIFY})assertEquals(-1,LiveBuildPhaseSession.targetPhase(stage,0,64));
    }
    @Test void removalCaptureAndCompletionUseCumulativeTarget() {
        for(var stage:new SuppressionStage[]{SuppressionStage.REMOVE,SuppressionStage.MOVE,
            SuppressionStage.EQUIP,SuppressionStage.DWELL,SuppressionStage.STOW,SuppressionStage.VERIFY,
            SuppressionStage.READY_NEXT})assertEquals(7,LiveBuildPhaseSession.targetPhase(stage,7,64));
        assertEquals(63,LiveBuildPhaseSession.targetPhase(SuppressionStage.COMPLETE,63,64));
    }
    @Test void unboundPausedAndInvalidStagesCannotClaimTarget() {
        for(var stage:new SuppressionStage[]{null,SuppressionStage.WAITING_ANCHOR,SuppressionStage.ANCHOR_CONFIRM,SuppressionStage.PAUSED})
            assertEquals(-2,LiveBuildPhaseSession.targetPhase(stage,0,64));
        assertEquals(-2,LiveBuildPhaseSession.targetPhase(SuppressionStage.REMOVE,64,64));
        assertEquals(-2,LiveBuildPhaseSession.targetPhase(SuppressionStage.REMOVE,-1,64));
    }
    @Test void phaseWorldOriginAndSourceInvalidateSnapshot() throws Exception {
        var source=SuppressionTestFixtures.litematicBytes();
        var bytes=SuppressionTestFixtures.planBytes(source);
        var bundle=new SuppressionBundle(SuppressionPlanParser.parse(bytes),bytes,source,
            SuppressionHashes.sha256(bytes),SuppressionHashes.sha256(source),null,null,"Test","test");
        var origin=new LiveBuildProgress.Position(1,2,3);
        var first=new LiveBuildPhaseSession(bundle,0,"world","dimension",origin);
        assertEquals(first,new LiveBuildPhaseSession(bundle,0,"world","dimension",origin));
        assertNotEquals(first,new LiveBuildPhaseSession(bundle,1,"world","dimension",origin));
        assertNotEquals(first,new LiveBuildPhaseSession(bundle,0,"other","dimension",origin));
        assertNotEquals(first,new LiveBuildPhaseSession(bundle,0,"world","other",origin));
        assertNotEquals(first,new LiveBuildPhaseSession(bundle,0,"world","dimension",new LiveBuildProgress.Position(2,2,3)));
        assertEquals(256,first.prepare().cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
    }
}
