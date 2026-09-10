package art.mapkluss.companion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildTransformTest {
    private final LiveBuildProgress.State north = new LiveBuildProgress.State("minecraft:oak_stairs",Map.of("facing","north"));
    private final LiveBuildProgress.State east = new LiveBuildProgress.State("minecraft:oak_stairs",Map.of("facing","east"));
    private LiveBuildParts.Part part() {
        var cells=List.of(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(1,2,-1),north),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(3,4,2),north));
        return new LiveBuildParts(new LiveBuildSchematic("a".repeat(64),cells,
            new LiveBuildSchematic.Bounds(0,0,-1,127,4,127),new LiveBuildSchematic.Bounds(0,0,0,127,4,127))).part(0);
    }
    private LiveBuildProgress.Identity identity(LiveBuildTransform transform) {
        return new LiveBuildProgress.Identity(part().targetSha256(),"test","minecraft:overworld",
            new LiveBuildProgress.Position(100,64,200),1,transform);
    }
    @Test void transformsCoordinatesIncludingNegativeReferenceWithoutCropping() {
        var p=new LiveBuildProgress.Position(2,7,-1);
        assertEquals(new LiveBuildProgress.Position(1,7,2),new LiveBuildTransform(1,false).apply(p));
        assertEquals(new LiveBuildProgress.Position(-2,7,1),new LiveBuildTransform(2,false).apply(p));
        assertEquals(new LiveBuildProgress.Position(-1,7,-2),new LiveBuildTransform(3,false).apply(p));
        assertEquals(new LiveBuildProgress.Position(1,7,-2),new LiveBuildTransform(1,true).apply(p));
        var rotated=p;
        for(int i=0;i<4;i++)rotated=new LiveBuildTransform(1,false).apply(rotated);
        assertEquals(p,rotated);
        var mirror=new LiveBuildTransform(0,true);
        assertEquals(p,mirror.apply(mirror.apply(p)));
    }
    @Test void validatesRotationAndRejectsCoordinateOverflow() {
        assertThrows(IllegalArgumentException.class,()->new LiveBuildTransform(4,false));
        assertThrows(IllegalArgumentException.class,()->new LiveBuildTransform(-1,false));
        assertThrows(ArithmeticException.class,()->new LiveBuildTransform(0,true)
            .apply(new LiveBuildProgress.Position(Integer.MIN_VALUE,0,0)));
    }
    @Test void orientationIsPartOfIdentityAndRejectsOldScan() {
        var original=identity(LiveBuildTransform.NONE);
        var rotated=identity(new LiveBuildTransform(1,false));
        assertNotEquals(original,rotated);
        var progress=new LiveBuildProgress(original,part().cells());
        assertEquals(0,progress.scan(rotated,p->{fail("Stale orientation");return north;},2,0));
        assertEquals(LiveBuildTransform.NONE,new LiveBuildProgress.Identity(original.schematicSha256(),"test",
            "minecraft:overworld",original.origin(),1).transform());
    }
    @Test void boundedPreparationPreservesCellIndexesAndUsesNativeStateResolverOnCaller() {
        assertTimeoutPreemptively(Duration.ofSeconds(5),()->{
            var thread=Thread.currentThread();
            var transform=new LiveBuildTransform(1,true);
            var calls=new AtomicInteger();
            try(var job=new LiveBuildPlacementPreparation(part(),identity(transform))){
                LiveBuildProgress result=null;
                while(result==null){
                    int before=calls.get();
                    result=job.advance((state,t)->{
                        assertSame(thread,Thread.currentThread());assertEquals(transform,t);
                        assertEquals(north,state);calls.incrementAndGet();return east;
                    },1);
                    assertTrue(calls.get()-before<=1);
                    Thread.sleep(1);
                }
                assertEquals(1,calls.get());
                for(int i=0;i<part().cells().size();i++){
                    assertEquals(transform.apply(part().cells().get(i).relativePosition()),result.cell(i).relativePosition());
                    assertEquals(east,result.cell(i).expected());
                }
                result.scan(result.identity(),p->east,2,0);
                assertEquals(1,result.liveSummary(0).completion());
            }
        });
    }
    @Test void cancelledPlacementCannotPublishOrAccessRegistry() {
        var job=new LiveBuildPlacementPreparation(part(),identity(LiveBuildTransform.NONE));
        job.close();
        assertThrows(CancellationException.class,()->job.advance((s,t)->{fail("Cancelled resolver");return s;},1));
    }
    @Test void rejectsInvalidBudgetAndDifferentPart() {
        var wrong=new LiveBuildProgress.Identity("b".repeat(64),"test","minecraft:overworld",
            new LiveBuildProgress.Position(0,0,0),1);
        assertThrows(IllegalArgumentException.class,()->new LiveBuildPlacementPreparation(part(),wrong));
        try(var job=new LiveBuildPlacementPreparation(part(),identity(LiveBuildTransform.NONE))){
            assertThrows(IllegalArgumentException.class,()->job.advance((s,t)->s,0));
            assertThrows(IllegalArgumentException.class,()->job.advance((s,t)->s,65));
        }
    }
}
