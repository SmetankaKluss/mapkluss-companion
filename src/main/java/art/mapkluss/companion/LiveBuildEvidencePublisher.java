package art.mapkluss.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** Owner-thread collection, one bounded network worker. Mutation failures drop the batch, never replay it. */
public final class LiveBuildEvidencePublisher {
    public record Binding(LiveBuildGroupController.Adoption adoption,LiveBuildProgress progress) { }
    private record Page(LiveBuildGroupController.Adoption adoption,int index,LiveBuildLocalEvidence evidence) { }
    private final LiveBuildGroupController groups;
    private final Executor callback;
    private final LongSupplier clock;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),
        task->{var t=new Thread(task,"MapKluss-build-evidence");t.setDaemon(true);return t;});
    private final LiveBuildPublishingLease lease=new LiveBuildPublishingLease();
    private final Map<LiveBuildGroupController.Adoption,Long> firstSeen=new HashMap<>();
    private final List<Page> queued=new ArrayList<>();
    private LiveBuildGroupController.Transport channel;
    private Future<?> pending;
    private boolean busy,leased,failed;
    public boolean failed(){return failed;}
    private long generation,nextCall,leaseExpires;
    private int bindingCursor,pageCursor;

    public LiveBuildEvidencePublisher(LiveBuildGroupController groups,Executor callback,LongSupplier clock){this.groups=groups;this.callback=callback;this.clock=clock;}
    public void tick(List<Binding> bindings){
        long now=clock.getAsLong();var current=groups.transport();
        if(channel!=null&&!groups.current(channel))clear();
        if(current==null||bindings.isEmpty()){if(channel!=null)clear();return;}
        if(channel==null)channel=current;
        var accepted=new HashSet<LiveBuildGroupController.Adoption>();
        for(var binding:bindings)if(groups.valid(binding.adoption()))accepted.add(binding.adoption());
        firstSeen.keySet().retainAll(accepted);
        queued.removeIf(page->!accepted.contains(page.adoption()));
        // At most two 4096-cell copies per native tick, at most32 pages awaiting the worker.
        for(int copied=0,visited=0;copied<2&&queued.size()<32&&visited<bindings.size()+2;visited++){
            bindingCursor=Math.floorMod(bindingCursor,bindings.size());var binding=bindings.get(bindingCursor);
            if(!accepted.contains(binding.adoption())){bindingCursor++;pageCursor=0;continue;}
            long since=firstSeen.computeIfAbsent(binding.adoption(),ignored->Math.addExact(now,1));
            var progress=binding.progress();int count=(progress.size()+4095)/4096;
            if(pageCursor>=count){bindingCursor++;pageCursor=0;continue;}
            int page=pageCursor++;copied++;
            if(queued.stream().anyMatch(p->p.adoption().equals(binding.adoption())&&p.index()==page))continue;
            var evidence=progress.exportPage(page,now,since);
            if(evidence.hasObservations())queued.add(new Page(binding.adoption(),page,evidence));
        }
        if(busy||now<nextCall||queued.isEmpty())return;
        if(!leased||now>=leaseExpires){begin(now);return;}
        LiveBuildPublishingLease.Ticket ticket;
        try{ticket=lease.next(now);}catch(IllegalStateException expired){leased=false;begin(now);return;}
        var pages=List.copyOf(queued);queued.clear();var input=new JsonObject();input.addProperty("build_id",channel.group().id());
        input.addProperty("lease",ticket.nonce());input.addProperty("sequence",ticket.sequence());
        dispatch(LiveBuildApiClient.Action.UPLOAD,input,pages,ticket,now);
    }
    private void begin(long now){
        var input=new JsonObject();input.addProperty("build_id",channel.group().id());input.addProperty("consent",true);
        dispatch(LiveBuildApiClient.Action.BEGIN,input,List.of(),null,now);
    }
    private void dispatch(LiveBuildApiClient.Action action,JsonObject input,List<Page> pages,LiveBuildPublishingLease.Ticket ticket,long started){
        busy=true;nextCall=started+1100;long epoch=generation;var transport=channel;
        pending=worker.submit(()->{
            JsonObject result=null;Exception failure=null;
            try{
                if(!groups.current(transport))throw new CancellationException();
                long now=clock.getAsLong();
                if(action==LiveBuildApiClient.Action.UPLOAD){
                    if(now>=leaseExpires)throw new CancellationException();
                    var payload=new JsonArray();
                    for(var page:pages){
                        if(!groups.valid(page.adoption()))throw new CancellationException();
                        var remote=page.adoption().placement();var value=new JsonObject();
                        value.addProperty("tile",remote.tile());value.addProperty("page",page.index());
                        value.addProperty("placement_revision",remote.revision());value.addProperty("source_sha256",page.adoption().source().sha256());
                        value.addProperty("target_sha256",remote.targetSha256());value.addProperty("phase",remote.phase());
                        value.addProperty("world_binding",remote.worldBinding());value.addProperty("packed",page.evidence().encode(ticket.sampleOrigin(),now));payload.add(value);
                    }
                    input.add("pages",payload);
                }
                if(!groups.current(transport))throw new CancellationException();
                result=transport.api().call(action,input);
            }catch(Exception caught){failure=caught;}
            var response=result;var problem=failure;
            callback.execute(()->complete(epoch,transport,action,response,problem,started));
        });
    }
    private void complete(long epoch,LiveBuildGroupController.Transport transport,LiveBuildApiClient.Action action,JsonObject response,Exception failure,long started){
        if(epoch!=generation)return;
        busy=false;pending=null;
        if(!groups.current(transport)){clear();return;}
        long now=clock.getAsLong();
        if(failure!=null){
            failed=true;
            leased=false;lease.clear();queued.clear();
            int status=failure instanceof LiveBuildApiClient.ApiException e?e.status():0;
            nextCall=Math.max(nextCall,now+(status==429?30_000:10_000));
            groups.denyTransport(transport,status);
            if(status==409)groups.refresh();
            return;
        }
        try{
            failed=false;
            var value=response.getAsJsonObject("build");
            if(action==LiveBuildApiClient.Action.BEGIN){
                if(LiveBuildSharedPlacement.integer(value,"valid_ms")!=30_000)throw new IllegalArgumentException("Invalid lease duration");
                lease.accept(value.get("lease").getAsString(),started,now);leaseExpires=started+29_000;leased=true;
            }else if(!value.has("accepted")||!value.get("accepted").getAsJsonPrimitive().isBoolean()||!value.get("accepted").getAsBoolean())
                throw new IllegalArgumentException("Upload not acknowledged");
        }catch(RuntimeException invalid){failed=true;leased=false;lease.clear();queued.clear();nextCall=Math.max(nextCall,now+10_000);}
    }
    public void clear(){
        generation++;if(pending!=null)pending.cancel(true);pending=null;worker.purge();busy=false;leased=false;lease.clear();channel=null;
        queued.clear();firstSeen.clear();bindingCursor=0;pageCursor=0;failed=false;
    }
    public void shutdown(){clear();worker.shutdownNow();}
}
