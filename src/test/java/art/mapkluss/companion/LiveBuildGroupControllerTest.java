package art.mapkluss.companion;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildGroupControllerTest {
    @Test void metadataRefreshIsReadOnlyPacedAndInactiveWhenDisconnected()throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);var calls=new ArrayList<LiveBuildApiClient.Action>();
        try{
            c.tick(0);c.configure(SOURCE,new Object(),"fixture",(a,b)->{calls.add(a);return response("member",SOURCE.sha256());});
            c.join("f".repeat(32),true);complete(queue);c.tick(100);complete(queue);
            c.tick(101);c.tick(10099);assertEquals(2,calls.size());
            c.tick(10100);complete(queue);assertEquals(LiveBuildApiClient.Action.READ,calls.getLast());
            c.clear();c.tick(100000);assertEquals(3,calls.size());
        }finally{c.shutdown();}
    }
    @Test void joinWithoutSourceThenExactImportRetainsGroupOnly()throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);
        try{
            c.configure(null,new Object(),"fixture",(a,b)->response("member",SOURCE.sha256()));
            c.create(true);assertEquals(LiveBuildGroupController.Error.MISMATCH,c.error());
            c.join("f".repeat(32),true);complete(queue);assertNotNull(c.group());assertFalse(c.matches());
            assertThrows(IllegalArgumentException.class,()->c.sourceImport("b".repeat(64),2,1));
            var link=c.sourceImport(SOURCE.sha256(),2,1);assertEquals(c.group().id(),link.id());assertTrue(link.placements().isEmpty());
        }finally{c.shutdown();}
    }
    @Test void resumeRequiresFreshExactReadAndNeverCreatesOrJoins() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);
        var calls=new ArrayList<LiveBuildApiClient.Action>();String id="11111111-1111-4111-8111-111111111111";
        try{
            c.configure(SOURCE,new Object(),"fixture",(action,input)->{calls.add(action);assertEquals(id,input.get("build_id").getAsString());return response("member",SOURCE.sha256());});
            c.resume(id);assertNull(c.group());complete(queue);
            assertTrue(c.matches());assertEquals(java.util.List.of(LiveBuildApiClient.Action.READ),calls);
            c.clear();c.configure(SOURCE,new Object(),"fixture",(a,b)->response("member",SOURCE.sha256()));
            c.resume("22222222-2222-4222-8222-222222222222");complete(queue);
            assertNull(c.group());assertEquals(LiveBuildGroupController.Error.UNAVAILABLE,c.error());
        }finally{c.shutdown();}
    }
    private static final LiveBuildGroupController.Source SOURCE=new LiveBuildGroupController.Source("a".repeat(64),2,1);
    private static JsonObject response(String role,String source) {
        JsonObject build=new JsonObject();build.addProperty("id","11111111-1111-4111-8111-111111111111");
        build.addProperty("role",role);build.addProperty("revision",2);build.addProperty("source_sha256",source);
        build.addProperty("grid_wide",2);build.addProperty("grid_tall",1);
        build.add("placements",new com.google.gson.JsonArray());
        JsonObject root=new JsonObject();root.add("build",build);return root;
    }
    private static void complete(LinkedBlockingQueue<Runnable> queue) throws Exception {
        Runnable task=queue.poll(3,TimeUnit.SECONDS);assertNotNull(task);task.run();
    }
    @Test void explicitConsentAndSafeCreatePayload() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);
        var bodies=new ArrayList<JsonObject>();
        try {
            c.configure(SOURCE,new Object(),"fixture",(action,input)->{bodies.add(input);return response("owner",SOURCE.sha256());});
            c.create(false);assertEquals(LiveBuildGroupController.Error.CONSENT,c.error());assertTrue(bodies.isEmpty());
            c.create(true);c.create(true);assertTrue(c.busy());complete(queue);
            assertEquals(1,bodies.size());assertTrue(c.matches());
            assertEquals(java.util.Set.of("source_sha256","grid_wide","grid_tall","consent"),bodies.getFirst().keySet());
            assertEquals("owner",c.group().role());
        } finally {c.shutdown();}
    }
    @Test void contextChangeRejectsCompletedButUndeliveredReply() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);
        try {
            c.configure(SOURCE,new Object(),"fixture",(a,b)->response("owner",SOURCE.sha256()));
            c.create(true);Runnable reply=queue.poll(3,TimeUnit.SECONDS);assertNotNull(reply);
            c.clear();reply.run();assertNull(c.group());assertFalse(c.busy());assertFalse(c.available());
        } finally {c.shutdown();}
    }
    @Test void sourceMismatchNeverMatchesAndMemberCannotInvite() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);var calls=new ArrayList<LiveBuildApiClient.Action>();
        try {
            c.configure(SOURCE,new Object(),"fixture",(a,b)->{calls.add(a);return response("member","b".repeat(64));});
            c.join("invalid",true);assertEquals(LiveBuildGroupController.Error.CODE,c.error());assertTrue(calls.isEmpty());
            c.join("c".repeat(32),true);complete(queue);
            assertEquals(LiveBuildGroupController.Error.MISMATCH,c.error());assertFalse(c.matches());
            c.inviteCode();assertEquals(1,calls.size());
            c.leave();complete(queue);assertEquals(LiveBuildApiClient.Action.LEAVE,calls.getLast());assertNull(c.group());
        } finally {c.shutdown();}
    }
    @Test void ownerInviteAndCloseUseRevisionAndRevokeClearsState() throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var c=new LiveBuildGroupController(queue::add);var bodies=new ArrayList<JsonObject>();
        try {
            Object world=new Object();
            c.configure(SOURCE,world,"fixture",(a,b)->{bodies.add(b);var r=response("owner",SOURCE.sha256());if(a==LiveBuildApiClient.Action.INVITE)r.addProperty("code","f".repeat(32));return r;});
            c.create(true);complete(queue);c.inviteCode();complete(queue);
            assertEquals("f".repeat(32),c.invite());assertEquals(2,bodies.getLast().get("expected_revision").getAsInt());
            c.leave();complete(queue);assertNull(c.group());assertEquals("",c.invite());
            c.create(true);complete(queue);
            c.configure(SOURCE,world,"fixture",(a,b)->{throw new LiveBuildApiClient.ApiException(403);});
            c.refresh();complete(queue);assertNull(c.group());assertEquals(LiveBuildGroupController.Error.DENIED,c.error());
        } finally {c.shutdown();}
    }
}
