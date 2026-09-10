package art.mapkluss.companion;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildEvidenceReceiverTest {
    @Test void actualControllerBindingReadsPagesWithoutPollutingLocalScanAndClearsOnExit()throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var clock=new AtomicLong(1000);
        var groups=new LiveBuildGroupController(queue::add);var receiver=new LiveBuildEvidenceReceiver(groups,queue::add,clock::get);
        String id="11111111-1111-4111-8111-111111111111";
        var source=new LiveBuildGroupController.Source("a".repeat(64),1,1);
        var origin=new LiveBuildProgress.Position(0,0,0);
        var progress=new LiveBuildProgress(new LiveBuildProgress.Identity("b".repeat(64),"private-world","minecraft:overworld",origin,1),
            List.of(new LiveBuildProgress.Cell(origin,new LiveBuildProgress.State("minecraft:stone",Map.of()))));
        var calls=new CopyOnWriteArrayList<LiveBuildApiClient.Action>();
        try{
            groups.configure(source,new Object(),"fixture",(action,input)->{
                calls.add(action);var build=new JsonObject();
                if(action==LiveBuildApiClient.Action.JOIN){
                    build.addProperty("id",id);build.addProperty("role","member");build.addProperty("revision",2);
                    build.addProperty("source_sha256",source.sha256());build.addProperty("grid_wide",1);build.addProperty("grid_tall",1);
                    var placement=new LiveBuildSharedPlacement(0,1,"b".repeat(64),-1,1,id,"minecraft:overworld",origin,LiveBuildTransform.NONE).input();
                    placement.addProperty("revision",1);placement.addProperty("build_id",id);
                    var entries=new JsonArray();entries.add(placement);build.add("placements",entries);
                }else{
                    assertEquals(LiveBuildApiClient.Action.DOWNLOAD,action);
                    assertFalse(input.toString().contains("private-world"));
                    var pages=new JsonArray();
                    for(var item:input.getAsJsonArray("pages")){
                        var page=item.getAsJsonObject().deepCopy();page.addProperty("revision",1);page.addProperty("membership_revision",2);
                        if(page.has("known_revision"))page.addProperty("unchanged",true);else page.addProperty("packed","AAE=");
                        pages.add(page);
                    }
                    build.add("pages",pages);
                }
                var result=new JsonObject();result.add("build",build);return result;
            });
            groups.join("f".repeat(32),true);complete(queue);
            var adoption=groups.adopt(groups.placement(0),"minecraft:overworld",true);
            var bindings=List.of(new LiveBuildEvidencePublisher.Binding(adoption,progress));
            receiver.tick(bindings);complete(queue);
            receiver.tick(bindings);
            assertEquals(LiveBuildProgress.Status.CORRECT,progress.displayObservation(0,1000,120000).status());
            assertEquals(1,progress.liveSummary(1000).correct());
            assertFalse(progress.exportPage(0,1000,0).hasObservations());
            clock.set(1500);receiver.tick(bindings);assertEquals(2,calls.size());
            clock.set(2100);receiver.tick(bindings);complete(queue);
            assertEquals(1000,progress.displayObservation(0,2100,120000).observedAt());
            progress.scan(progress.identity(),p->new LiveBuildProgress.State("minecraft:air",Map.of()),1,2200);
            assertEquals(0,progress.liveSummary(2200).correct());
            assertEquals(1,progress.liveSummary(2200).missing());
            assertEquals(0,progress.liveSummary(122201).checked());
            clock.set(123000);receiver.tick(bindings);
            var late=queue.poll(3,TimeUnit.SECONDS);assertNotNull(late);
            receiver.clear();late.run();
            assertEquals(LiveBuildProgress.Status.STALE,progress.displayObservation(0,123000,120000).status());
            groups.clear();receiver.tick(List.of());assertFalse(receiver.failed());
        }finally{receiver.shutdown();groups.shutdown();progress.close();}
    }
    private static void complete(LinkedBlockingQueue<Runnable> queue)throws Exception {
        var action=queue.poll(3,TimeUnit.SECONDS);assertNotNull(action);action.run();
    }
}
