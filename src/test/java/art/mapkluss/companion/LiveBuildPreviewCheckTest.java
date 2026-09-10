package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildPreviewCheckTest {
    @Test void absenceOfRequirementsIsNotProofOfAColouredPixel(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0,false)),
            new LiveBuildSchematic.Bounds(0,0,0,1,0,0));
        var assembly=new LiveBuildAssembly(new LiveBuildParts(source));var placed=place(assembly,0);
        placed.scan(placed.identity(),p->stone,1,0);
        var result=complete(new LiveBuildPreviewCheck(assembly,new LiveBuildTopView(source.cells(),source.artBounds())),0);
        assertEquals(LiveBuildProgress.Status.CORRECT,result[0]);
        assertEquals(LiveBuildProgress.Status.UNKNOWN,result[1]);
    }
    private final LiveBuildProgress.State stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
    private final LiveBuildProgress.State air=new LiveBuildProgress.State("minecraft:air",Map.of());
    private LiveBuildProgress.Cell cell(int x,int y,int z,boolean remove){
        return new LiveBuildProgress.Cell(new LiveBuildProgress.Position(x,y,z),stone,remove);
    }
    private LiveBuildProgress place(LiveBuildAssembly assembly,int part){
        var target=assembly.parts().part(part);
        var progress=new LiveBuildProgress(new LiveBuildProgress.Identity(target.targetSha256(),"test","overworld",
            new LiveBuildProgress.Position(0,0,0),1),target.cells());
        assembly.attach(part,progress);return progress;
    }
    private LiveBuildProgress.Status[] complete(LiveBuildPreviewCheck check,long now){
        for(int i=0;i<100;i++){var result=check.advance(1,now);if(result!=null)return result;}
        throw new AssertionError("Reduction did not finish");
    }
    @Test void visibleRetainedBlockDependsOnSupportAndRequiredRemoval(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0,false),cell(0,1,0,false),cell(0,2,0,true)));
        var assembly=new LiveBuildAssembly(new LiveBuildParts(source));
        var view=new LiveBuildTopView(source.cells(),source.artBounds());
        var placed=place(assembly,0);
        placed.scan(placed.identity(),p->stone,3,0);
        assertEquals(LiveBuildProgress.Status.WRONG,complete(new LiveBuildPreviewCheck(assembly,view),0)[0]);
        placed.scan(placed.identity(),p->p.y()==2?air:stone,3,1);
        assertEquals(LiveBuildProgress.Status.CORRECT,complete(new LiveBuildPreviewCheck(assembly,view),1)[0]);
        placed.scan(placed.identity(),p->p.y()==1?stone:air,3,2);
        assertEquals(LiveBuildProgress.Status.MISSING,complete(new LiveBuildPreviewCheck(assembly,view),2)[0]);
        placed.scan(placed.identity(),p->null,3,3);
        assertEquals(LiveBuildProgress.Status.STALE,complete(new LiveBuildPreviewCheck(assembly,view),3)[0]);
    }
    @Test void copiedReferenceConstrainsSelectedMapNotUnplacedNeighbour(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,128,false),cell(0,1,129,false)),
            new LiveBuildSchematic.Bounds(0,0,0,127,1,256),new LiveBuildSchematic.Bounds(0,0,1,127,1,256));
        var assembly=new LiveBuildAssembly(new LiveBuildParts(source));var placed=place(assembly,1);
        var view=new LiveBuildTopView(source.cells(),source.artBounds());
        placed.scan(placed.identity(),p->p.z()<0?air:stone,3,0);
        var result=complete(new LiveBuildPreviewCheck(assembly,view),0);
        assertEquals(LiveBuildProgress.Status.MISSING,result[view.pixelAt(0,129)]);
        assertEquals(LiveBuildProgress.Status.UNKNOWN,result[view.pixelAt(0,128)]);
    }
    @Test void respectsBudgetAndRejectsChangedPlacement(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0,false),cell(0,1,0,false)));
        var assembly=new LiveBuildAssembly(new LiveBuildParts(source));
        var view=new LiveBuildTopView(source.cells(),source.artBounds());
        var check=new LiveBuildPreviewCheck(assembly,view);
        assertNull(check.advance(1,0));
        place(assembly,0);
        assertThrows(java.util.concurrent.CancellationException.class,()->check.advance(1,0));
        var next=new LiveBuildPreviewCheck(assembly,view);
        assertThrows(IllegalArgumentException.class,()->next.advance(0,0));
        assertThrows(IllegalArgumentException.class,()->next.advance(4097,0));
        assembly.close();assertThrows(java.util.concurrent.CancellationException.class,()->next.advance(1,0));
    }
}
