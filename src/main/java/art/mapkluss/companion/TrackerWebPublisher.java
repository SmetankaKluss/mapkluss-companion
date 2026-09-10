package art.mapkluss.companion;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/** One in-flight upload, no mutation fallback, no personal metadata in snapshots. */
public final class TrackerWebPublisher {
    private final Executor callback;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{var t=new Thread(task,"MapKluss-tracker-web");t.setDaemon(true);return t;});
    private LiveBuildApiClient api;
    private String art,version,source,credential,publisher,session,lease;
    private long generation,sequence,next;
    private boolean busy,failed,denied;
    public TrackerWebPublisher(Executor callback){this.callback=callback;}
    public boolean failed(){return failed;}
    public void configure(LiveBuildApiClient api,String credential,String art,String version,String source){
        if(!Objects.equals(this.art,art)||!Objects.equals(this.version,version)||!Objects.equals(this.source,source)||!Objects.equals(this.credential,credential))clear();
        this.api=api;this.credential=credential;this.art=art;this.version=version;this.source=source;
    }
    public boolean ready(long now){return api!=null&&art!=null&&version!=null&&!busy&&!denied&&now>=next;}
    private JsonObject identity(){var input=new JsonObject();input.addProperty("publisher",publisher);input.addProperty("source_sha256",source);
        input.addProperty("session_id",session);input.addProperty("lease",lease);return input;}
    public void clear(){
        if(api!=null&&session!=null&&lease!=null){var old=api;var input=identity();worker.execute(()->{try{old.call(LiveBuildApiClient.Action.WEB_STOP,input);}catch(Exception ignored){}});}
        generation++;api=null;art=version=source=credential=session=lease=null;publisher=UUID.randomUUID().toString();
        sequence=next=0;busy=failed=denied=false;
    }
    public void publish(java.util.function.Supplier<JsonObject> snapshot,LiveBuildGroupController.Group group,long now){
        if(!ready(now))return;
        if(publisher==null)publisher=UUID.randomUUID().toString();
        busy=true;next=now+5000;long epoch=generation;long seq=++sequence;long captured=System.nanoTime();
        var client=api;var input=identity();String artId=art,versionId=version;
        worker.execute(()->{
            try{
                if(!input.has("lease")||input.get("lease").isJsonNull()){
                    var begin=new JsonObject();begin.addProperty("art_id",artId);begin.addProperty("art_version_id",versionId);
                    begin.add("publisher",input.get("publisher"));begin.add("source_sha256",input.get("source_sha256"));
                    var response=client.call(LiveBuildApiClient.Action.WEB_BEGIN,begin).getAsJsonObject("build");
                    input.add("session_id",response.get("session_id"));input.add("lease",response.get("lease"));
                }
                input.addProperty("sequence",seq);input.add("snapshot",snapshot.get());
                if(group!=null){input.addProperty("group_id",group.id());input.addProperty("group_revision",group.revision());}
                long age=(System.nanoTime()-captured)/1_000_000;
                if(age>10000)throw new java.io.IOException("Snapshot expired");
                input.addProperty("age_ms",age);client.call(LiveBuildApiClient.Action.WEB_PUBLISH,input);
                callback.execute(()->{if(epoch!=generation)return;session=input.get("session_id").getAsString();lease=input.get("lease").getAsString();busy=false;failed=false;});
            }catch(Exception failure){callback.execute(()->{if(epoch!=generation)return;busy=false;failed=true;next=now+15000;
                if(failure instanceof LiveBuildApiClient.ApiException e&&(e.status()==401||e.status()==403||e.status()==409))denied=true;
            });}
        });
    }
}
