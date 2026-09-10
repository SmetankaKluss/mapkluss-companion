package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Worker-only transfer; all imports validate exact source and dimensions before native attachment. */
public final class LiveBuildSourceTransfer {
    static final int PART_BYTES=2097152;
    private static JsonObject input(String id){var j=new JsonObject();j.addProperty("build_id",id);return j;}
    private static void current(BooleanSupplier valid)throws InterruptedException{
        if(Thread.currentThread().isInterrupted()||!valid.getAsBoolean())throw new InterruptedException("Source context changed");
    }
    public static void upload(LiveBuildGroupController.Transport t,LiveBuildSourceCache cache,LiveBuildSourceCache.Reference ref,
                              BooleanSupplier valid,IntConsumer percent)throws Exception{
        current(valid);
        if(!t.group().role().equals("owner")||!ref.sha256().equals(t.group().source().sha256()))throw new IOException("Different source");
        byte[] bytes=cache.exportBytes(ref);current(valid);
        var manifest=input(t.group().id());var hashes=new JsonArray();
        for(int start=0;start<bytes.length;start+=PART_BYTES){current(valid);hashes.add(SuppressionHashes.sha256(Arrays.copyOfRange(bytes,start,Math.min(bytes.length,start+PART_BYTES))));}
        manifest.addProperty("kind",ref.kind()==LiveBuildSessionStore.SourceKind.LITEMATIC?"litematic":"two_layer_zip");
        manifest.addProperty("size_bytes",bytes.length);manifest.add("part_sha256",hashes);manifest.addProperty("consent",true);
        var result=t.api().call(LiveBuildApiClient.Action.SOURCE_BEGIN,manifest).getAsJsonObject("build");current(valid);
        if(result.has("ready")&&result.get("ready").getAsBoolean()){percent.accept(100);return;}
        for(int part=0;part<hashes.size();part++){
            current(valid);int start=part*PART_BYTES;
            t.api().source("put",t.group().id(),part,Arrays.copyOfRange(bytes,start,Math.min(bytes.length,start+PART_BYTES)));
            current(valid);percent.accept((part+1)*90/hashes.size());
        }
        current(valid);byte[] reply=t.api().source("finalize",t.group().id(),0,new byte[0]);current(valid);
        var done=com.google.gson.JsonParser.parseString(new String(reply,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("source");
        if(done==null||!done.get("ready").getAsBoolean()||!done.get("source_sha256").getAsString().equals(ref.sha256())
            ||integer(done,"size_bytes")!=bytes.length)throw new IOException("Source publication unconfirmed");
        percent.accept(100);
    }
    public static LiveBuildSourceCache.Loaded download(LiveBuildGroupController.Transport t,LiveBuildSourceCache cache,
                               BooleanSupplier valid,IntConsumer percent)throws Exception{
        current(valid);
        var m=t.api().call(LiveBuildApiClient.Action.SOURCE_READ,input(t.group().id())).getAsJsonObject("build");current(valid);
        if(m==null||!m.has("available")||!m.get("available").getAsBoolean()||!m.has("ready")||!m.get("ready").getAsBoolean())throw new IOException("Source not ready");
        String kind=m.get("kind").getAsString();
        var type=switch(kind){case "litematic"->LiveBuildSessionStore.SourceKind.LITEMATIC;case "two_layer_zip"->LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP;default->throw new IOException("Invalid source type");};
        int size=integer(m,"size_bytes"),limit=type==LiveBuildSessionStore.SourceKind.LITEMATIC?LiveBuildSchematic.MAX_FILE_BYTES:SuppressionPlanParser.MAX_BUNDLE_BYTES;
        if(size<1||size>limit||integer(m,"part_bytes")!=PART_BYTES||integer(m,"grid_wide")!=t.group().source().wide()
            ||integer(m,"grid_tall")!=t.group().source().tall()||!m.get("source_sha256").getAsString().equals(t.group().source().sha256()))throw new IOException("Different source manifest");
        var hashes=m.getAsJsonArray("part_sha256");
        if(hashes==null||hashes.size()!=(size+PART_BYTES-1)/PART_BYTES)throw new IOException("Invalid parts");
        for(var h:hashes)if(!h.isJsonPrimitive()||!h.getAsJsonPrimitive().isString()||!h.getAsString().matches("[a-f0-9]{64}"))throw new IOException("Invalid part hash");
        byte[] bytes=new byte[size];
        for(int part=0;part<hashes.size();part++){
            current(valid);byte[] chunk=t.api().source("get",t.group().id(),part,new byte[0]);current(valid);
            int start=part*PART_BYTES;
            if(chunk.length!=Math.min(PART_BYTES,size-start)||!SuppressionHashes.sha256(chunk).equals(hashes.get(part).getAsString()))throw new IOException("Source integrity failed");
            System.arraycopy(chunk,0,bytes,start,chunk.length);percent.accept((part+1)*90/hashes.size());
        }
        if(!SuppressionHashes.sha256(bytes).equals(t.group().source().sha256()))throw new IOException("Source integrity failed");
        current(valid);var loaded=cache.importOwnedBytes(bytes,type);
        try{
            current(valid);
            int wide=loaded.bundle()!=null?loaded.bundle().width()/128:(loaded.schematic().artBounds().width()+127)/128;
            int tall=loaded.bundle()!=null?loaded.bundle().height()/128:(loaded.schematic().artBounds().depth()+127)/128;
            if(wide!=t.group().source().wide()||tall!=t.group().source().tall())throw new IOException("Source grid mismatch");
            percent.accept(100);return loaded;
        }catch(Exception failure){loaded.close();throw failure;}
    }
    private static int integer(JsonObject j,String key)throws IOException{
        try{return new java.math.BigDecimal(j.get(key).getAsString()).intValueExact();}catch(RuntimeException failure){throw new IOException("Invalid source number");}
    }
}
