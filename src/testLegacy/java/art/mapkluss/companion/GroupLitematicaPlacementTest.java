package art.mapkluss.companion;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GroupLitematicaPlacementTest {
    public static class Placement {
        final Object schematic; final BlockPos origin; final UUID id;
        Placement(Object schematic,BlockPos origin,UUID id){this.schematic=schematic;this.origin=origin;this.id=id;}
        public static Placement createFor(Object schematic,BlockPos origin,String name,boolean enabled,boolean render,UUID id){
            assertTrue(enabled);assertTrue(render);return new Placement(schematic,origin,id);
        }
        public UUID getHashId(){return id;}
        public BlockPos getOrigin(){return origin;}
    }
    public static class Manager {
        final List<Placement> entries=new ArrayList<>();
        Placement selected; int failAdd; boolean refuseRemoval,copyOnAdd;
        public List<Placement> getAllSchematicsPlacements(){return List.copyOf(entries);}
        public void addSchematicPlacement(Placement p,boolean rebuild){
            assertTrue(rebuild);
            if(failAdd-->0)throw new IllegalStateException("Simulated add failure");
            entries.add(copyOnAdd?new Placement(p.schematic,p.origin,p.id):p);
        }
        public void removeSchematicPlacement(Placement p){if(!refuseRemoval)entries.remove(p);}
        public void setSelectedSchematicPlacement(Placement p){assertTrue(entries.contains(p));selected=p;}
    }
    @Test void repeatedAcceptanceMovesOnlyOwnedTileAndReplacesItsSource(){
        var manager=new Manager();var id=UUID.randomUUID();var old=new Placement("old",new BlockPos(0,60,0),id);
        var unrelated=new Placement("user schematic",old.origin,UUID.randomUUID());manager.entries.add(old);manager.entries.add(unrelated);
        var origin=new BlockPos(-192,75,-64);var source=new Object();
        for(int repeat=0;repeat<3;repeat++){
            var result=OptionalLitematicaAdapter.replaceGroupPlacement(manager,source,Placement.class,origin,id,3);
            assertTrue(result.placed());assertEquals(2,manager.entries.size());assertTrue(manager.entries.contains(unrelated));
            assertEquals(origin,manager.selected.origin);assertSame(source,manager.selected.schematic);assertEquals(id,manager.selected.id);
        }
    }
    @Test void refusedAdditionRestoresOldGhostWithoutChangingOtherPlacements(){
        var manager=new Manager();var id=UUID.randomUUID();var old=new Placement("old",new BlockPos(0,60,0),id);
        var other=new Placement("other",old.origin,UUID.randomUUID());manager.entries.add(old);manager.entries.add(other);manager.failAdd=1;
        assertFalse(OptionalLitematicaAdapter.replaceGroupPlacement(manager,"new",Placement.class,new BlockPos(128,70,0),id,0).placed());
        assertEquals(2,manager.entries.size());assertTrue(manager.entries.contains(old));assertTrue(manager.entries.contains(other));
    }
    @Test void refusedRemovalDoesNotAddConflictingGhost(){
        var manager=new Manager();var id=UUID.randomUUID();var old=new Placement("old",new BlockPos(0,60,0),id);
        manager.entries.add(old);manager.refuseRemoval=true;
        assertFalse(OptionalLitematicaAdapter.replaceGroupPlacement(manager,"new",Placement.class,new BlockPos(128,70,0),id,0).placed());
        assertEquals(List.of(old),manager.entries);
    }
    @Test void registeredEquivalentInstanceIsSelected(){
        var manager=new Manager();manager.copyOnAdd=true;var origin=new BlockPos(128,60,128);
        assertTrue(OptionalLitematicaAdapter.replaceGroupPlacement(manager,"new",Placement.class,origin,UUID.randomUUID(),0).placed());
        assertEquals(1,manager.entries.size());assertSame(manager.entries.getFirst(),manager.selected);assertEquals(origin,manager.selected.origin);
    }
}
