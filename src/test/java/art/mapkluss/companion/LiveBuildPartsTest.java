package art.mapkluss.companion;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildPartsTest {
    @Test void schematicOriginIncludesNorthernReferenceAndAllMapOffsets() {
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0),cell(0,0,1),cell(128,4,129)),
            new LiveBuildSchematic.Bounds(0,0,0,255,4,256),new LiveBuildSchematic.Bounds(0,0,1,255,4,256));
        var parts=new LiveBuildParts(source);
        var origin=new LiveBuildProgress.Position(100,60,200);
        assertEquals(new LiveBuildProgress.Position(100,60,201),parts.fromSchematicOrigin(0,origin,LiveBuildTransform.NONE));
        assertEquals(new LiveBuildProgress.Position(228,60,329),parts.fromSchematicOrigin(3,origin,LiveBuildTransform.NONE));
        for(int turns=0;turns<4;turns++)for(boolean mirror:new boolean[]{false,true}) {
            var transform=new LiveBuildTransform(turns,mirror);
            for(int i=0;i<source.cells().size();i++) {
                int index=parts.partOfCell(i);
                var anchor=parts.fromSchematicOrigin(index,origin,transform);
                assertEquals(origin,parts.toSchematicOrigin(index,anchor,transform));
                var local=transform.apply(parts.part(index).cells().get(parts.localCellIndex(i)).relativePosition());
                var original=transform.apply(source.cells().get(i).relativePosition());
                assertEquals(new LiveBuildProgress.Position(origin.x()+original.x(),origin.y()+original.y(),origin.z()+original.z()),
                    new LiveBuildProgress.Position(anchor.x()+local.x(),anchor.y()+local.y(),anchor.z()+local.z()));
            }
        }
    }
    @Test void schematicOriginPreservesNegativeBoundsAndUnpaddedSources() {
        var origin=new LiveBuildProgress.Position(1,2,3);
        var parts=parts();
        var transform=new LiveBuildTransform(1,true);
        assertEquals(origin,parts.toSchematicOrigin(1,parts.fromSchematicOrigin(1,origin,transform),transform));
        var zero=new LiveBuildParts(new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0)),
            new LiveBuildSchematic.Bounds(0,0,0,127,0,127)));
        assertEquals(origin,zero.fromSchematicOrigin(0,origin,transform));
    }
    @Test void detachedSouthernMapIncludesExactNorthernStructureWithoutColouringPreviousMap(){
        var directional=new LiveBuildProgress.State("minecraft:oak_stairs",Map.of("facing","east","half","top"));
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,4,128),directional),
            cell(0,0,128),cell(0,3,129)),
            new LiveBuildSchematic.Bounds(0,0,0,127,4,256),new LiveBuildSchematic.Bounds(0,0,1,127,4,256));
        var parts=new LiveBuildParts(source);
        assertEquals(2,parts.rows());assertEquals(6,parts.requiredCells());
        assertEquals(3,parts.part(1).cells().size());
        assertEquals(new LiveBuildProgress.Position(0,4,-1),parts.part(1).cells().get(1).relativePosition());
        assertEquals(directional,parts.part(1).cells().get(1).expected());
        assertEquals(0,parts.localCellIndex(3));
        var assembly=new LiveBuildAssembly(parts);assembly.attach(1,placed(parts.part(1),500));
        assembly.scan(p->p.z()==-1?new LiveBuildProgress.State("minecraft:air",Map.of()):stone,32,10);
        assertEquals(1,assembly.summary(10).correct());assertEquals(2,assembly.summary(10).missing());
        assertEquals(3,assembly.summary(10).unknown());
        assertEquals(LiveBuildProgress.Status.UNKNOWN,assembly.status(1,10));
        assembly.scan(p->p.y()==68?directional:stone,32,20);
        assertEquals(3,assembly.summary(20).correct());assertEquals(.5,assembly.summary(20).completion());
        assertEquals(LiveBuildProgress.Status.UNKNOWN,assembly.status(1,20));
    }
    @Test void ordinarySchematicDoesNotAcquireGuessedNorthReferences(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,127),cell(0,0,128)),
            new LiveBuildSchematic.Bounds(0,0,0,127,0,255));
        var parts=new LiveBuildParts(source);
        assertEquals(2,parts.requiredCells());assertEquals(1,parts.part(1).cells().size());
    }
    @Test void northReferencesDoNotTurnAnEmptyMapIntoABuildTask(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,128)),
            new LiveBuildSchematic.Bounds(0,0,0,127,0,256),new LiveBuildSchematic.Bounds(0,0,1,127,0,256));
        var parts=new LiveBuildParts(source);
        assertTrue(parts.part(1).empty());assertEquals(1,parts.requiredCells());
    }
    @Test void northReferenceIsStructuralButNotAnExtraMapOrPreviewRow(){
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,0,0),cell(0,0,1),cell(0,0,128)),
            new LiveBuildSchematic.Bounds(0,0,0,127,0,128),new LiveBuildSchematic.Bounds(0,0,1,127,0,128));
        var parts=new LiveBuildParts(source);
        assertEquals(1,parts.rows());assertEquals(3,parts.part(0).cells().size());
        assertEquals(-1,parts.part(0).cells().getFirst().relativePosition().z());
        var view=new LiveBuildTopView(source.cells(),source.artBounds());
        assertEquals(128,view.height());assertEquals(1,view.index(0));assertEquals(2,view.index(127*128));
    }
    private final LiveBuildProgress.State stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
    private LiveBuildProgress.Cell cell(int x,int y,int z){return new LiveBuildProgress.Cell(new LiveBuildProgress.Position(x,y,z),stone);}
    private LiveBuildParts parts(){
        return new LiveBuildParts(new LiveBuildSchematic("a".repeat(64),List.of(cell(-7,3,0),cell(121,4,0)),
            new LiveBuildSchematic.Bounds(-10,2,-5,245,6,122)));
    }
    @Test void partitionsFromDeclaredBoundsNotOccupiedPixels(){
        var parts=parts();
        assertEquals(2,parts.columns());assertEquals(1,parts.rows());
        assertEquals(new LiveBuildProgress.Position(3,1,5),parts.part(0).cells().getFirst().relativePosition());
        assertEquals(new LiveBuildProgress.Position(3,2,5),parts.part(1).cells().getFirst().relativePosition());
        assertEquals(0,parts.partOfCell(0));assertEquals(1,parts.partOfCell(1));
        assertEquals(0,parts.localCellIndex(1));
        assertNotEquals(parts.part(0).targetSha256(),parts.part(1).targetSha256());
        assertEquals(parts.part(0).targetSha256(),parts().part(0).targetSha256());
        var view=new LiveBuildTopView(parts.source().cells(),parts.source().bounds());
        assertEquals(128,view.width());assertEquals(64,view.height());
    }
    @Test void emptyAndPartialMapsRemainInWholeArtwork(){
        var parts=new LiveBuildParts(new LiveBuildSchematic("a".repeat(64),List.of(cell(256,0,0)),
            new LiveBuildSchematic.Bounds(0,0,0,256,0,127)));
        assertEquals(3,parts.columns());assertTrue(parts.part(0).empty());
        assertEquals(1,parts.part(2).width());
        assertEquals(2,parts.partOfCell(0));
    }
    private LiveBuildProgress placed(LiveBuildParts.Part part,int x){
        return new LiveBuildProgress(new LiveBuildProgress.Identity(part.targetSha256(),"test","minecraft:overworld",
            new LiveBuildProgress.Position(x,64,0),1),part.cells());
    }
    @Test void onlyPlacedMapsAreScannedAndColouredAtIndependentOrigins(){
        var parts=parts();var assembly=new LiveBuildAssembly(parts);
        assembly.attach(1,placed(parts.part(1),500));
        assembly.scan(pos->{assertEquals(503,pos.x());assertEquals(66,pos.y());return stone;},32,50);
        assertEquals(1,assembly.summary(50).correct());assertEquals(1,assembly.summary(50).unknown());
        assertEquals(LiveBuildProgress.Status.UNKNOWN,assembly.status(0,50));
        assertEquals(LiveBuildProgress.Status.CORRECT,assembly.status(1,50));
        assembly.attach(0,placed(parts.part(0),900));
        assembly.scan(pos->stone,32,100);assembly.scan(pos->stone,32,100);
        assertEquals(1,assembly.summary(100).completion());
        assembly.remove(1);
        assertEquals(1,assembly.summary(150).correct());assertEquals(1,assembly.summary(150).unknown());
        assertEquals(LiveBuildProgress.Status.CORRECT,assembly.status(0,150));
        assembly.scan(pos->new LiveBuildProgress.State("minecraft:air",Map.of()),32,200);
        assertEquals(0,assembly.summary(200).correct());
        assembly.close();assertEquals(0,assembly.scan(pos->{fail("Closed read");return stone;},32,250));
    }
    @Test void wrongPartCannotBeAttached(){
        var parts=parts();var assembly=new LiveBuildAssembly(parts);
        assertThrows(IllegalArgumentException.class,()->assembly.attach(0,placed(parts.part(1),0)));
    }
}
