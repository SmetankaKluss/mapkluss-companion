package art.mapkluss.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** One bounded read worker, separately paced from the publisher's 1100ms budget. */
public final class LiveBuildEvidenceReceiver {
    private final LiveBuildGroupController groups;
    private final Executor callback;
    private final LongSupplier clock;
    private final LiveBuildRemoteEvidence cache=new LiveBuildRemoteEvidence();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
        task->{var thread=new Thread(task,"MapKluss-build-receive");thread.setDaemon(true);return thread;});
    private final Map<LiveBuildProgress,LiveBuildGroupController.Adoption> attached=new IdentityHashMap<>();
    private final Map<LiveBuildGroupController.Adoption,LiveBuildDisplayProgress> displays=new HashMap<>();
    private final LinkedHashSet<LiveBuildRemoteEvidence.Key> recount=new LinkedHashSet<>();
    private LiveBuildGroupController.Transport channel;
    private Future<?> pending;
    private long generation,nextCall;
    private int cursor;
    private boolean busy,failed;
    public LiveBuildEvidenceReceiver(LiveBuildGroupController groups,Executor callback,LongSupplier clock){
        this.groups=groups;this.callback=callback;this.clock=clock;
    }
    public boolean failed(){return failed;}
    public void tick(List<LiveBuildEvidencePublisher.Binding> bindings){
        var transport=groups.transport();
        if(channel!=null&&!groups.current(channel))clear();
        if(transport==null||bindings.isEmpty()){if(channel!=null)clear();return;}
        channel=transport;
        var accepted=new HashSet<LiveBuildGroupController.Adoption>();
        var sources=Collections.newSetFromMap(new IdentityHashMap<LiveBuildProgress,Boolean>());
        for(var binding:bindings)if(groups.valid(binding.adoption())){
            accepted.add(binding.adoption());sources.add(binding.progress());
            var adoption=binding.adoption();var progress=binding.progress();
            if(!adoption.equals(attached.get(progress))){
                attached.put(progress,adoption);
                var display=new LiveBuildDisplayProgress(progress,cache,adoption,()->groups.valid(adoption));
                displays.put(adoption,display);progress.displayEvidence(display);
                queue(adoption);
            }
        }
        attached.entrySet().removeIf(entry->{if(sources.contains(entry.getKey()))return false;entry.getKey().displayEvidence(null);return true;});
        cache.retain(accepted);
        displays.keySet().retainAll(accepted);recount.removeIf(key->!accepted.contains(key.adoption()));
        long displayNow=clock.getAsLong();
        for(int i=0;i<2&&!recount.isEmpty();i++){
            var iterator=recount.iterator();var key=iterator.next();iterator.remove();
            var display=displays.get(key.adoption());if(display!=null)display.page(key.page(),displayNow);
        }
        long now=clock.getAsLong();if(busy||now<nextCall||accepted.isEmpty())return;
        var all=new ArrayList<LiveBuildRemoteEvidence.Key>();
        for(var binding:bindings)if(accepted.contains(binding.adoption())){
            int count=(binding.adoption().placement().cellCount()+4095)/4096;
            for(int page=0;page<count;page++)all.add(new LiveBuildRemoteEvidence.Key(binding.adoption(),page));
        }
        var keys=new ArrayList<LiveBuildRemoteEvidence.Key>();var payload=new JsonArray();
        for(int i=0;i<Math.min(32,all.size());i++){
            var key=all.get(Math.floorMod(cursor++,all.size()));keys.add(key);payload.add(cache.request(key));
        }
        var input=new JsonObject();input.addProperty("build_id",transport.group().id());input.add("pages",payload);
        busy=true;nextCall=now+1100;long epoch=generation;
        pending=worker.submit(()->{
            JsonObject response=null;Exception failure=null;
            try{
                if(!groups.current(transport))throw new CancellationException();
                for(var key:keys)if(!groups.valid(key.adoption()))throw new CancellationException();
                response=transport.api().call(LiveBuildApiClient.Action.DOWNLOAD,input);
            }catch(Exception caught){failure=caught;}
            var result=response;var problem=failure;
            callback.execute(()->{
                if(epoch!=generation)return;
                busy=false;pending=null;
                if(!groups.current(transport)){clear();return;}
                for(var key:keys)if(!groups.valid(key.adoption())){clear();return;}
                if(problem!=null){
                    int status=problem instanceof LiveBuildApiClient.ApiException e?e.status():0;
                    discard();failed=true;nextCall=Math.max(nextCall,clock.getAsLong()+(status==429?30_000:10_000));
                    groups.denyTransport(transport,status);if(status==409)groups.refresh();return;
                }
                try{
                    long previous=cache.membership();cache.accept(keys,result,now);failed=false;
                    if(previous!=cache.membership())resetDisplays();
                    recount.addAll(keys);
                }
                catch(RuntimeException invalid){discard();failed=true;nextCall=Math.max(nextCall,clock.getAsLong()+10_000);}
            });
        });
    }
    private void queue(LiveBuildGroupController.Adoption adoption){
        for(int page=0;page<(adoption.placement().cellCount()+4095)/4096;page++)recount.add(new LiveBuildRemoteEvidence.Key(adoption,page));
    }
    private void resetDisplays(){for(var entry:displays.entrySet()){entry.getValue().reset();queue(entry.getKey());}}
    private void discard(){cache.clear();resetDisplays();}
    public void clear(){
        generation++;if(pending!=null)pending.cancel(true);pending=null;worker.purge();busy=false;failed=false;
        channel=null;cursor=0;cache.clear();
        for(var progress:attached.keySet())progress.displayEvidence(null);
        attached.clear();displays.clear();recount.clear();
    }
    public void shutdown(){clear();worker.shutdownNow();}
}
