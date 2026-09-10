package art.mapkluss.companion;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildPhaseTargetTest {
    @Test void removalRequirementSurvivesNormalizationPartitionAndRotation() throws Exception {
        byte[] source=SuppressionTestFixtures.litematicBytes();
        var target=LiveBuildPhaseTarget.read(SuppressionTestFixtures.planBytes(source),source,0);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),()->{
            var origin=new LiveBuildProgress.Position(0,0,0);
            var initialId=new LiveBuildProgress.Identity(target.sha256(),"test","minecraft:overworld",origin,0);
            LiveBuildAssembly assembly=null;
            try(var preparation=new LiveBuildPreparation(target,initialId)){
                while(assembly==null){assembly=preparation.advance(s->s,1);Thread.sleep(1);}
            }
            var part=assembly.parts().part(0);
            assertEquals(256,part.cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
            var transform=new LiveBuildTransform(1,true);
            var id=new LiveBuildProgress.Identity(part.targetSha256(),"test","minecraft:overworld",origin,1,transform);
            LiveBuildProgress progress=null;
            try(var preparation=new LiveBuildPlacementPreparation(part,id)){
                while(progress==null){progress=preparation.advance((s,t)->s,1);Thread.sleep(1);}
            }
            int air=0;
            for(int i=0;i<progress.size();i++){
                assertEquals(part.cells().get(i).requiresAir(),progress.cell(i).requiresAir());
                assertEquals(transform.apply(part.cells().get(i).relativePosition()),progress.cell(i).relativePosition());
                if(progress.cell(i).requiresAir())air++;
            }
            assertEquals(256,air);
        });
    }
    @Test void phasesRetainRequiredAirAndHaveIndependentIdentities() throws Exception {
        byte[] source=SuppressionTestFixtures.litematicBytes();
        byte[] plan=SuppressionTestFixtures.planBytes(source);
        var initial=LiveBuildPhaseTarget.read(plan,source,-1);
        var first=LiveBuildPhaseTarget.read(plan,source,0);
        var second=LiveBuildPhaseTarget.read(plan,source,1);
        assertEquals(initial.cells().size(),first.cells().size());
        assertEquals(0,initial.cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
        assertEquals(256,first.cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
        assertEquals(512,second.cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
        assertNotEquals(initial.sha256(),first.sha256());assertNotEquals(first.sha256(),second.sha256());
        assertEquals(second,LiveBuildPhaseTarget.read(plan,source,1));
        assertEquals(1,new LiveBuildParts(first).parts().size());
        var top=new LiveBuildTopView(first.cells(),first.artBounds());
        assertTrue(top.index(0)>=0);
        assertFalse(first.cells().get(top.index(0)).requiresAir());
        assertEquals(0,first.cells().get(top.index(0)).relativePosition().y());
    }
    @Test void removalMustBeObservedAndCanBecomeWrongAgain() throws Exception {
        byte[] source=SuppressionTestFixtures.litematicBytes();
        var target=LiveBuildPhaseTarget.read(SuppressionTestFixtures.planBytes(source),source,0);
        var cell=target.cells().stream().filter(LiveBuildProgress.Cell::requiresAir).findFirst().orElseThrow();
        var id=new LiveBuildProgress.Identity(target.sha256(),"test","minecraft:overworld",new LiveBuildProgress.Position(0,0,0),1);
        var progress=new LiveBuildProgress(id,java.util.List.of(cell));
        progress.scan(id,p->cell.expected(),1,0);assertEquals(1,progress.liveSummary(0).wrong());
        progress.scan(id,p->null,1,1);assertEquals(1,progress.liveSummary(1).stale());
        progress.scan(id,p->new LiveBuildProgress.State("minecraft:cave_air",Map.of()),1,2);
        assertEquals(1,progress.liveSummary(2).correct());
        progress.scan(id,p->cell.expected(),1,3);assertEquals(1,progress.liveSummary(3).wrong());
    }
    @Test void rejectsWrongSourceAndPhase() throws Exception {
        byte[] source=SuppressionTestFixtures.litematicBytes(),plan=SuppressionTestFixtures.planBytes(source);
        assertThrows(java.io.IOException.class,()->LiveBuildPhaseTarget.read(plan,source,-2));
        assertThrows(java.io.IOException.class,()->LiveBuildPhaseTarget.read(plan,source,64));
        source[source.length-1]^=1;
        assertThrows(java.io.IOException.class,()->LiveBuildPhaseTarget.read(plan,source,0));
    }
}
