package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildGroupSchematicTest {
    @TempDir Path directory;
    private static final String GROUP="11111111-1111-4111-8111-111111111111";
    private static final LiveBuildProgress.State STONE=new LiveBuildProgress.State("minecraft:cobblestone",Map.of());
    private static final LiveBuildProgress.State SAND=new LiveBuildProgress.State("minecraft:sand",Map.of());
    private static LiveBuildProgress.Cell cell(int x,int y,int z,LiveBuildProgress.State state){
        return new LiveBuildProgress.Cell(new LiveBuildProgress.Position(x,y,z),state);
    }
    private static Map<LiveBuildProgress.State,LiveBuildProgress.State> palette(){return Map.of(STONE,STONE,SAND,SAND);}
    private static LiveBuildSharedPlacement remote(LiveBuildParts.Part part,LiveBuildTransform transform){
        return new LiveBuildSharedPlacement(part.index(),4,part.targetSha256(),-1,part.cells().size(),GROUP,
            "minecraft:overworld",new LiveBuildProgress.Position(1344,-60,576),transform);
    }
    @Test void shadowRowAndThreeDimensionalHeightsMatchTrackerForEveryTransform()throws Exception{
        var cells=List.of(cell(0,0,-1,STONE),cell(0,0,0,SAND),cell(1,5,2,SAND),cell(127,2,127,STONE));
        for(int rotation=0;rotation<4;rotation++)for(boolean mirrored:List.of(false,true)){
            var transform=new LiveBuildTransform(rotation,mirrored);
            var decoded=LiveBuildSchematic.read(LiveBuildGroupSchematic.encode(cells,transform,palette(),3839));
            assertEquals(cells.size(),decoded.cells().size());
            for(var original:cells)assertTrue(decoded.cells().contains(new LiveBuildProgress.Cell(transform.apply(original.relativePosition()),original.expected())));
            var root=SuppressionNbt.compound(SuppressionNbt.readCompressed(LiveBuildGroupSchematic.encode(cells,transform,palette(),3839)).root(),"root");
            assertEquals(3839,SuppressionNbt.intValue(root.get("MinecraftDataVersion"),"version"));
        }
    }
    @Test void detachedSouthernMapIncludesItsOwnReferenceNotTheWholeArt()throws Exception{
        var source=new LiveBuildSchematic("a".repeat(64),List.of(cell(0,5,0,STONE),cell(0,3,1,SAND),cell(0,6,128,STONE),cell(0,4,129,SAND)),
            new LiveBuildSchematic.Bounds(0,0,0,127,6,256),new LiveBuildSchematic.Bounds(0,0,1,127,6,256));
        var part=new LiveBuildParts(source).part(1);
        var file=LiveBuildGroupSchematic.install(directory,part,remote(part,LiveBuildTransform.NONE),palette(),3839);
        var decoded=LiveBuildSchematic.read(file.path());
        assertEquals(2,decoded.cells().size());
        assertTrue(decoded.cells().contains(cell(0,6,-1,STONE)));
        assertTrue(decoded.cells().contains(cell(0,4,0,SAND)));
        assertFalse(decoded.cells().contains(cell(0,3,0,SAND)));
        assertEquals(file,LiveBuildGroupSchematic.install(directory,part,remote(part,LiveBuildTransform.NONE),palette(),3839));
    }
    @Test void twoLayerRemovedBlocksStayAirAndDirectionalPaletteIsPreserved()throws Exception{
        var log=new LiveBuildProgress.State("minecraft:oak_log",Map.of("axis","x"));
        var turned=new LiveBuildProgress.State("minecraft:oak_log",Map.of("axis","z"));
        var cells=List.of(cell(0,0,0,log),new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,1,0),SAND,true),cell(1,0,0,STONE));
        var transform=new LiveBuildTransform(1,true);
        var decoded=LiveBuildSchematic.read(LiveBuildGroupSchematic.encode(cells,transform,Map.of(log,turned,STONE,STONE),3839));
        assertEquals(2,decoded.cells().size());
        assertTrue(decoded.cells().contains(cell(0,0,0,turned)));
        assertTrue(decoded.cells().contains(cell(0,0,-1,STONE)));
        assertTrue(decoded.cells().stream().noneMatch(c->c.relativePosition().y()==1));
    }
    @Test void changedTargetOrUnresolvedPaletteCannotProduceAGhost()throws Exception{
        var part=new LiveBuildParts.Part(0,0,0,128,128,List.of(cell(0,0,0,SAND)),"a".repeat(64));
        var wrong=new LiveBuildParts.Part(0,0,0,128,128,part.cells(),"b".repeat(64));
        assertThrows(java.io.IOException.class,()->LiveBuildGroupSchematic.install(directory,wrong,remote(part,LiveBuildTransform.NONE),palette(),3839));
        assertThrows(java.io.IOException.class,()->LiveBuildGroupSchematic.encode(part.cells(),LiveBuildTransform.NONE,Map.of(),3839));
        assertThrows(java.io.IOException.class,()->LiveBuildGroupSchematic.encode(part.cells(),LiveBuildTransform.NONE,palette(),0));
    }
    @Test void replacementIdentityIsStablePerWorldGroupAndTile(){
        var id=LiveBuildGroupSchematic.placementId("server:overworld",GROUP,0);
        assertEquals(id,LiveBuildGroupSchematic.placementId("server:overworld",GROUP,0));
        assertNotEquals(id,LiveBuildGroupSchematic.placementId("server:nether",GROUP,0));
        assertNotEquals(id,LiveBuildGroupSchematic.placementId("other:overworld",GROUP,0));
        assertNotEquals(id,LiveBuildGroupSchematic.placementId("server:overworld",GROUP,1));
        assertNotEquals(id,LiveBuildGroupSchematic.placementId("server:overworld","22222222-2222-4222-8222-222222222222",0));
    }
}
