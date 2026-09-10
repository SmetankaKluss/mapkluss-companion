package art.mapkluss.companion;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildEvidencePublisherTest {
    @Test void publisherTraversesRealHttpTransportForJoinLeaseAndBatch()throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var requests=new CopyOnWriteArrayList<JsonObject>();
        server.createContext("/",exchange->{
            var input=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            requests.add(input);var value=new JsonObject();
            switch(input.get("action").getAsString()){
                case "join" -> value=group(10);
                case "observe_begin" -> {value.addProperty("lease",LEASE);value.addProperty("valid_ms",30000);}
                case "observe_batch" -> value.addProperty("accepted",true);
                default -> throw new IllegalArgumentException("Unexpected action");
            }
            byte[] data=result(value).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
        });server.start();
        var queue=new LinkedBlockingQueue<Runnable>();var groups=new LiveBuildGroupController(queue::add);var clock=new AtomicLong(100);
        var publisher=new LiveBuildEvidencePublisher(groups,queue::add,clock::get);var progress=progress(10);
        try{
            var client=new LiveBuildApiClient(java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"),"fixture-key","fixture-token");
            groups.configure(SOURCE,new Object(),"fixture-token",client::call);groups.join("f".repeat(32),true);complete(queue);
            var bindings=List.of(new LiveBuildEvidencePublisher.Binding(groups.adopt(groups.placement(0),"minecraft:overworld",true),progress));
            publisher.tick(bindings);clock.set(200);progress.scan(progress.identity(),p->STONE,10,200);publisher.tick(bindings);complete(queue);
            clock.set(1300);publisher.tick(bindings);complete(queue);
            assertEquals(List.of("join","observe_begin","observe_batch"),requests.stream().map(r->r.get("action").getAsString()).toList());
            assertEquals(1,requests.getLast().getAsJsonArray("pages").size());assertFalse(requests.toString().contains("private-world"));
        }finally{publisher.shutdown();groups.shutdown();progress.close();server.stop(0);}
    }
    private static final String ID="11111111-1111-4111-8111-111111111111";
    private static final String LEASE="22222222-2222-4222-8222-222222222222";
    private static final LiveBuildProgress.State STONE=new LiveBuildProgress.State("minecraft:stone",Map.of());
    private static final LiveBuildGroupController.Source SOURCE=new LiveBuildGroupController.Source("a".repeat(64),1,1);
    private static LiveBuildProgress progress(int count){
        var cells=new ArrayList<LiveBuildProgress.Cell>();for(int i=0;i<count;i++)cells.add(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(i,0,0),STONE));
        return new LiveBuildProgress(new LiveBuildProgress.Identity("b".repeat(64),"private-world","minecraft:overworld",new LiveBuildProgress.Position(0,0,0),1),cells);
    }
    private static JsonObject result(JsonObject value){var r=new JsonObject();r.addProperty("api_version",1);r.add("build",value);return r;}
    private static JsonObject group(int count){
        var b=new JsonObject();b.addProperty("id",ID);b.addProperty("role","member");b.addProperty("revision",2);
        b.addProperty("source_sha256",SOURCE.sha256());b.addProperty("grid_wide",1);b.addProperty("grid_tall",1);
        var p=new LiveBuildSharedPlacement(0,1,"b".repeat(64),-1,count,ID,"minecraft:overworld",new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE).input();
        p.addProperty("revision",1);p.addProperty("build_id",ID);var all=new JsonArray();all.add(p);b.add("placements",all);return b;
    }
    private static void complete(LinkedBlockingQueue<Runnable> queue)throws Exception {var reply=queue.poll(3,TimeUnit.SECONDS);assertNotNull(reply);reply.run();}
    private static final class Harness implements AutoCloseable {
        final AtomicLong now=new AtomicLong(100);
        final LinkedBlockingQueue<Runnable> queue=new LinkedBlockingQueue<>();
        final LiveBuildGroupController groups=new LiveBuildGroupController(queue::add);
        final LiveBuildEvidencePublisher publisher=new LiveBuildEvidencePublisher(groups,queue::add,now::get);
        final List<LiveBuildApiClient.Action> calls=new CopyOnWriteArrayList<>();
        final List<JsonObject> bodies=new CopyOnWriteArrayList<>();
        final LiveBuildProgress progress;
        final List<LiveBuildEvidencePublisher.Binding> bindings;
        boolean failUpload;
        Harness(int count)throws Exception {
            progress=progress(count);
            groups.configure(SOURCE,new Object(),"fixture",(action,input)->{
                calls.add(action);bodies.add(input.deepCopy());
                if(action==LiveBuildApiClient.Action.JOIN)return result(group(count));
                if(action==LiveBuildApiClient.Action.BEGIN){var r=new JsonObject();r.addProperty("lease",LEASE);r.addProperty("valid_ms",30000);return result(r);}
                if(failUpload)throw new LiveBuildApiClient.ApiException(503);
                var r=new JsonObject();r.addProperty("accepted",true);return result(r);
            });
            groups.join("f".repeat(32),true);complete(queue);
            bindings=List.of(new LiveBuildEvidencePublisher.Binding(groups.adopt(groups.placement(0),"minecraft:overworld",true),progress));
        }
        void scan(long time){now.set(time);for(int i=0;i<progress.size();i+=4096)progress.scan(progress.identity(),p->STONE,4096,time);}
        void tick(){publisher.tick(bindings);}
        public void close(){publisher.shutdown();groups.shutdown();progress.close();}
    }
    @Test void excludesPreConsentAndPreservesSampleTimeInActualBatchShape()throws Exception {
        try(var h=new Harness(10)){
            h.scan(50);h.now.set(100);h.tick();assertEquals(1,h.calls.size());
            h.scan(200);h.tick();complete(h.queue);assertEquals(LiveBuildApiClient.Action.BEGIN,h.calls.getLast());
            h.now.set(300);h.tick();assertEquals(2,h.calls.size());
            h.now.set(1300);h.tick();complete(h.queue);assertEquals(LiveBuildApiClient.Action.UPLOAD,h.calls.getLast());
            var input=h.bodies.getLast();assertEquals(Set.of("build_id","lease","sequence","pages"),input.keySet());
            var pages=input.getAsJsonArray("pages");assertEquals(1,pages.size());var page=pages.get(0).getAsJsonObject();
            assertEquals(Set.of("tile","page","placement_revision","source_sha256","target_sha256","phase","world_binding","packed"),page.keySet());
            byte[] packed=Base64.getDecoder().decode(page.get("packed").getAsString());assertEquals(20,packed.length);
            int word=(Byte.toUnsignedInt(packed[0])<<8)|Byte.toUnsignedInt(packed[1]);
            assertEquals(1,word&7);assertEquals(1200,word>>>3,"Sample remains200ms, not upload1300ms");
            assertFalse(input.toString().contains("private-world"));
        }
    }
    @Test void staleUnknownAndInactiveBindingsCauseNoTraffic()throws Exception {
        try(var h=new Harness(10)){
            h.tick();h.scan(200);h.now.set(121000);h.tick();assertEquals(1,h.calls.size());
            h.publisher.tick(List.of());assertEquals(1,h.calls.size());
            h.progress.scan(h.progress.identity(),p->null,10,121001);h.now.set(121001);h.tick();assertEquals(1,h.calls.size());
        }
    }
    @Test void batchesAreBoundedAndFailedMutationIsNotImmediatelyRetried()throws Exception {
        try(var h=new Harness(4096*34)){
            h.tick();h.scan(200);h.tick();complete(h.queue);
            for(int i=0;i<25;i++){h.now.set(210+i*20);h.tick();}
            h.failUpload=true;h.now.set(1300);h.tick();complete(h.queue);
            assertEquals(32,h.bodies.getLast().getAsJsonArray("pages").size());int before=h.calls.size();
            h.now.set(1301);h.tick();h.now.set(10000);h.tick();assertEquals(before,h.calls.size());
        }
    }
    @Test void lateLeaseReplyCannotRestartClearedPublisher()throws Exception {
        try(var h=new Harness(10)){
            h.tick();h.scan(200);h.tick();var reply=h.queue.poll(3,TimeUnit.SECONDS);assertNotNull(reply);
            h.publisher.clear();h.groups.clear();reply.run();h.now.set(5000);h.tick();assertEquals(2,h.calls.size());
        }
    }
    @Test void exportedCopyDoesNotFollowLaterWorldChangesAndLastPageIsExact() {
        var p=progress(4097);try{
            p.scan(p.identity(),position->STONE,4096,100);p.scan(p.identity(),position->STONE,1,100);
            var copied=p.exportPage(1,100,99);assertEquals(1,copied.size());assertTrue(copied.hasObservations());
            p.scan(p.identity(),position->null,4096,101);p.scan(p.identity(),position->null,1,101);
            assertFalse(p.exportPage(1,101,99).hasObservations());
            assertEquals(1,Base64.getDecoder().decode(copied.encode(100,101))[1]&7);
            assertFalse(p.exportPage(0,101,102).hasObservations());
        }finally{p.close();}
    }
}
