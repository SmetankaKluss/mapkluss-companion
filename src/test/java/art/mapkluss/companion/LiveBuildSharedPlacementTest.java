package art.mapkluss.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildSharedPlacementTest {
    @Test void worldConsentTicketsExpireOnReplacementAndContextChange() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);Object world=new Object();
        try{
            c.configure(SOURCE,world,"fixture",(a,b)->{var value=build("member",3);value.getAsJsonArray("placements").add(wire(1));return response(value);});
            c.join("c".repeat(32),true);complete(queue);var remote=c.placement(1);
            assertNull(c.adopt(remote,remote.dimension(),false));assertNull(c.adopt(remote,"minecraft:the_nether",true));
            var ticket=c.adopt(remote,remote.dimension(),true);assertNotNull(ticket);assertTrue(c.valid(ticket));
            c.configure(SOURCE,world,"fixture",(a,b)->{var value=build("member",4);var p=wire(1);p.addProperty("origin_x",1000);p.addProperty("revision",4);value.getAsJsonArray("placements").add(p);return response(value);});
            c.refresh();complete(queue);assertFalse(c.valid(ticket));
            var fresh=c.adopt(c.placement(1),remote.dimension(),true);assertTrue(c.valid(fresh));
            c.configure(SOURCE,new Object(),"fixture",(a,b)->response(build("member",4)));assertFalse(c.valid(fresh));
        }finally{c.shutdown();}
    }
    private static final String ID="11111111-1111-4111-8111-111111111111";
    private static final String BINDING="22222222-2222-4222-8222-222222222222";
    private static final LiveBuildGroupController.Source SOURCE=new LiveBuildGroupController.Source("a".repeat(64),2,1);
    private static final LiveBuildGroupController.Group GROUP=new LiveBuildGroupController.Group(ID,"owner",3,SOURCE);
    private static LiveBuildSharedPlacement placement(int tile) {
        return new LiveBuildSharedPlacement(tile,3,"b".repeat(64),2,16384,BINDING,"minecraft:overworld",
            new LiveBuildProgress.Position(50,64,100),new LiveBuildTransform(1,true));
    }
    private static JsonObject build(String role,long revision) {
        var b=new JsonObject();b.addProperty("id",ID);b.addProperty("role",role);b.addProperty("revision",revision);
        b.addProperty("source_sha256",SOURCE.sha256());b.addProperty("grid_wide",2);b.addProperty("grid_tall",1);
        b.add("placements",new JsonArray());return b;
    }
    private static JsonObject wire(int tile) {
        var p=placement(tile).input();p.addProperty("revision",3);p.addProperty("build_id",ID);return p;
    }
    private static JsonObject response(JsonObject b) {var r=new JsonObject();r.add("build",b);return r;}
    private static void complete(LinkedBlockingQueue<Runnable> queue)throws Exception {
        var task=queue.poll(3,TimeUnit.SECONDS);assertNotNull(task);task.run();
    }
    @Test void fullGridKeepsOnlyExplicitIndependentPlacements() {
        var b=build("owner",3);b.getAsJsonArray("placements").add(wire(1));
        var decoded=LiveBuildSharedPlacement.decode(b,GROUP);
        assertEquals(java.util.List.of(placement(1)),decoded);assertEquals(1,decoded.size());
        assertThrows(UnsupportedOperationException.class,()->decoded.clear());
        b.getAsJsonArray("placements").add(wire(1));
        assertThrows(IllegalArgumentException.class,()->LiveBuildSharedPlacement.decode(b,GROUP));
    }
    @Test void malformedOrForeignPlacementsNeverPartiallyApply() {
        for(String field:java.util.List.of("tile","revision","phase","cell_count","origin_x","rotation")){
            var b=build("owner",3);var p=wire(1);p.addProperty(field,1.25);b.getAsJsonArray("placements").add(p);
            assertThrows(RuntimeException.class,()->LiveBuildSharedPlacement.decode(b,GROUP),field);
        }
        for(var change:java.util.List.<java.util.function.Consumer<JsonObject>>of(
            p->p.addProperty("tile",2),p->p.addProperty("revision",4),p->p.addProperty("revision",0),
            p->p.addProperty("build_id",BINDING),p->p.addProperty("mirrored","true"),
            p->p.addProperty("world_binding","not-a-world"),p->p.addProperty("origin_y",2049),
            p->p.addProperty("target_sha256","bad"))){
            var b=build("owner",3);var p=wire(1);change.accept(p);b.getAsJsonArray("placements").add(p);
            assertThrows(RuntimeException.class,()->LiveBuildSharedPlacement.decode(b,GROUP));
        }
    }
    @Test void publishRequiresConsentAndOmitsLocalWorldIdentity() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);
        var calls=new ArrayList<LiveBuildApiClient.Action>();var inputs=new ArrayList<JsonObject>();
        try{
            c.configure(SOURCE,new Object(),"fixture",(action,input)->{
                calls.add(action);inputs.add(input.deepCopy());var b=build("owner",action==LiveBuildApiClient.Action.CREATE?2:3);
                if(action==LiveBuildApiClient.Action.PLACE){var p=input.deepCopy();p.remove("consent");p.remove("expected_revision");p.addProperty("revision",3);b.getAsJsonArray("placements").add(p);}
                return response(b);
            });
            c.create(true);complete(queue);
            var local=new LiveBuildProgress.Identity("b".repeat(64),"PRIVATE-WORLD-NEVER-SEND","minecraft:overworld",
                new LiveBuildProgress.Position(50,64,100),41,new LiveBuildTransform(1,true));
            var publication=new LiveBuildGroupController.Publication(SOURCE,1,2,16384,local);
            c.publish(publication,false);assertEquals(1,calls.size());assertEquals(LiveBuildGroupController.Error.CONSENT,c.error());
            c.publish(publication,true);complete(queue);assertEquals(LiveBuildApiClient.Action.PLACE,calls.getLast());
            var body=inputs.getLast();assertFalse(body.toString().contains(local.worldKey()));
            assertEquals(2,body.get("expected_revision").getAsInt());assertEquals(1,body.get("tile").getAsInt());
            assertEquals(2,body.get("phase").getAsInt());assertTrue(body.get("mirrored").getAsBoolean());
            assertEquals(java.util.Set.of("tile","target_sha256","phase","cell_count","world_binding","dimension",
                "origin_x","origin_y","origin_z","rotation","mirrored","build_id","expected_revision","consent"),body.keySet());
            assertEquals(1,c.placements().size());assertEquals(1,c.placements().getFirst().tile());
            assertNotNull(c.publishedPlacement(1));assertNull(c.publishedPlacement(0));
            c.unpublish(0);assertEquals(2,calls.size());c.unpublish(1);complete(queue);
            assertEquals(LiveBuildApiClient.Action.UNPLACE,calls.getLast());assertTrue(c.placements().isEmpty());
            assertNull(c.publishedPlacement(1));
            assertEquals(3,inputs.getLast().get("expected_revision").getAsInt());
        }finally{c.shutdown();}
    }
    @Test void membersAndMismatchedSourcesCannotPublishAndRevocationClearsPlacements() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);Object world=new Object();
        try{
            c.configure(SOURCE,world,"fixture",(a,b)->{var value=build("member",3);value.getAsJsonArray("placements").add(wire(1));return response(value);});
            c.join("c".repeat(32),true);complete(queue);assertFalse(c.canPublish());assertEquals(1,c.placements().size());
            c.publish(null,true);assertFalse(c.busy());c.unpublish(1);assertFalse(c.busy());
            c.configure(SOURCE,world,"fixture",(a,b)->{throw new LiveBuildApiClient.ApiException(403);});
            c.refresh();complete(queue);assertTrue(c.placements().isEmpty());assertNull(c.group());
        }finally{c.shutdown();}
    }
    @Test void revisionRollbackClearsUntrustedRemoteSnapshot() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);Object world=new Object();
        try{
            c.configure(SOURCE,world,"fixture",(a,b)->response(build("owner",3)));c.create(true);complete(queue);
            c.configure(SOURCE,world,"fixture",(a,b)->response(build("owner",2)));c.refresh();complete(queue);
            assertNull(c.group());assertTrue(c.placements().isEmpty());assertEquals(LiveBuildGroupController.Error.UNAVAILABLE,c.error());
        }finally{c.shutdown();}
    }
    @Test void sourceMismatchAndConflictCannotSilentlyRepublish() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);Object world=new Object();
        var calls=new ArrayList<LiveBuildApiClient.Action>();
        try{
            c.configure(SOURCE,world,"fixture",(a,b)->{calls.add(a);if(a==LiveBuildApiClient.Action.PLACE)throw new LiveBuildApiClient.ApiException(409);return response(build("owner",3));});
            c.create(true);complete(queue);
            var identity=new LiveBuildProgress.Identity("b".repeat(64),"private","minecraft:overworld",new LiveBuildProgress.Position(0,64,0),1);
            c.publish(new LiveBuildGroupController.Publication(new LiveBuildGroupController.Source("f".repeat(64),2,1),1,-1,5,identity),true);
            assertEquals(1,calls.size());assertEquals(LiveBuildGroupController.Error.MISMATCH,c.error());
            var valid=new LiveBuildGroupController.Publication(SOURCE,1,-1,5,identity);
            c.publish(valid,true);complete(queue);assertEquals(2,calls.size());assertFalse(c.canPublish());
            c.publish(valid,true);assertEquals(2,calls.size());
            c.refresh();complete(queue);assertTrue(c.canPublish());assertEquals(3,calls.size());
        }finally{c.shutdown();}
    }
}
