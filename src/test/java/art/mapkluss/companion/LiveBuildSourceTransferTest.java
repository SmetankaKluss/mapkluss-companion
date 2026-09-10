package art.mapkluss.companion;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildSourceTransferTest {
    @TempDir Path root;
    private static final class Server implements LiveBuildGroupController.Api {
        JsonObject manifest;final Map<Integer,byte[]> chunks=new HashMap<>();boolean corrupt;int calls;
        @Override public JsonObject call(LiveBuildApiClient.Action action,JsonObject input){
            calls++;
            if(action==LiveBuildApiClient.Action.SOURCE_BEGIN){manifest=input.deepCopy();manifest.addProperty("ready",false);}
            var reply=new JsonObject();reply.add("build",manifest.deepCopy());return reply;
        }
        @Override public byte[] source(String mode,String id,int part,byte[] bytes){
            calls++;
            if(mode.equals("put")){chunks.put(part,bytes.clone());return new byte[0];}
            if(mode.equals("get")){var value=chunks.get(part).clone();if(corrupt)value[0]^=1;return value;}
            manifest.addProperty("ready",true);var reply=new JsonObject();reply.add("source",manifest.deepCopy());
            return reply.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private LiveBuildGroupController.Transport transport(Server api,String hash){
        return new LiveBuildGroupController.Transport(1,new LiveBuildGroupController.Group("11111111-1111-4111-8111-111111111111","owner",1,
            new LiveBuildGroupController.Source(hash,2,1)),api);
    }
    @Test void validatedBundleTransfersAndImportsWholeArtWithoutPlacements()throws Exception{
        byte[] bytes=SuppressionTestFixtures.multiZipBytes();var cache=LiveBuildSourceCache.forRunDir(root.resolve("owner"));
        try(var original=cache.importBytes(bytes,LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP)){
            var api=new Server();
            var t=transport(api,original.reference().sha256());
            // Service derives these immutable fields from the group rather than the upload request.
            LiveBuildGroupController.Api service=new LiveBuildGroupController.Api(){
                public JsonObject call(LiveBuildApiClient.Action a,JsonObject input){
                    var out=api.call(a,input);enrich(api,t);out.add("build",api.manifest.deepCopy());return out;
                }
                public byte[] source(String m,String id,int part,byte[] data){return api.source(m,id,part,data);}
            };
            final var active=new LiveBuildGroupController.Transport(t.generation(),t.group(),service);
            LiveBuildSourceTransfer.upload(active,cache,original.reference(),()->true,p->{});
            var download=LiveBuildSourceCache.forRunDir(root.resolve("member"));
            try(var loaded=LiveBuildSourceTransfer.download(active,download,()->true,p->{})){
                assertEquals(original.reference(),loaded.reference());assertEquals(256,loaded.bundle().width());assertEquals(128,loaded.bundle().height());
                assertNull(loaded.bundle().assembly(0));assertArrayEquals(bytes,download.exportBytes(loaded.reference()));
            }
            api.corrupt=true;
            assertThrows(java.io.IOException.class,()->LiveBuildSourceTransfer.download(active,download,()->true,p->{}));
            int before=api.calls;
            assertThrows(InterruptedException.class,()->LiveBuildSourceTransfer.download(active,download,()->false,p->{}));
            assertEquals(before,api.calls);
        }
    }
    private static void enrich(Server api,LiveBuildGroupController.Transport t){
        api.manifest.addProperty("available",true);api.manifest.addProperty("source_sha256",t.group().source().sha256());
        api.manifest.addProperty("grid_wide",2);api.manifest.addProperty("grid_tall",1);api.manifest.addProperty("part_bytes",LiveBuildSourceTransfer.PART_BYTES);
    }
}
