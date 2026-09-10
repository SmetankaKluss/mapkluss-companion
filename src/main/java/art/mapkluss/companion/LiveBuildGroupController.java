package art.mapkluss.companion;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/** One bounded worker; callbacks cannot reconnect a group after its local context changed. */
public final class LiveBuildGroupController {
    public record Source(String sha256, int wide, int tall) {
        public Source {
            if (sha256 == null || !sha256.matches("[a-f0-9]{64}") || wide < 1 || wide > 10 || tall < 1 || tall > 10)
                throw new IllegalArgumentException("Invalid build source");
        }
    }
    public record Group(String id, String role, long revision, Source source) { }
    public record Publication(Source source,int tile,int phase,int cellCount,LiveBuildProgress.Identity identity) { }
    public record Adoption(long generation,String buildId,Source source,LiveBuildSharedPlacement placement) { }
    public record Transport(long generation,Group group,Api api) { }
    public enum Error { NONE, LOGIN, UNAVAILABLE, CONSENT, CODE, MISMATCH, DIMENSION, DENIED, CONFLICT, LIMIT }
    @FunctionalInterface public interface Api {
        JsonObject call(LiveBuildApiClient.Action action, JsonObject input) throws Exception;
        default byte[] source(String mode,String build,int part,byte[] bytes)throws Exception{throw new java.io.IOException("Source transfer unavailable");}
    }
    private final Executor callback;
    private final ThreadPoolExecutor worker;
    private Api api;
    private String credential;
    private Object world;
    private Source source;
    private Group group;
    private java.util.List<LiveBuildSharedPlacement> placements=java.util.List.of();
    private String worldBinding=UUID.randomUUID().toString();
    private String invite = "";
    private Error error = Error.NONE;
    private Future<?> pending;
    private long epoch;
    private boolean busy;
    private Publication publishing;
    private String resuming;
    private long nextRefresh;
    public synchronized void tick(long now){
        if(now<0)throw new IllegalArgumentException("Invalid group clock");
        if(group==null||!available()||busy||now<nextRefresh)return;
        nextRefresh=now+(error==Error.NONE?10000:30000);refresh();
    }
    public synchronized void resume(String id){
        if(!available()||busy||group!=null)return;
        if(id==null||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid build");
        resuming=id;var input=new JsonObject();input.addProperty("build_id",id);submit(LiveBuildApiClient.Action.READ,input);
    }
    private final java.util.Map<Integer,Adoption> acknowledged=new java.util.HashMap<>();
    public synchronized Transport transport(){return available()&&matches()?new Transport(epoch,group,api):null;}
    public synchronized Transport sourceTransport(){return available()&&group!=null?new Transport(epoch,group,api):null;}
    public synchronized boolean sourceCurrent(Transport t){return t!=null&&available()&&group!=null&&epoch==t.generation()
        &&group.id().equals(t.group().id())&&group.source().equals(t.group().source());}
    public synchronized boolean current(Transport transport){return transport!=null&&epoch==transport.generation()&&available()&&Objects.equals(group,transport.group());}
    public synchronized Adoption publishedPlacement(int tile){var ticket=acknowledged.get(tile);return valid(ticket)?ticket:null;}
    public synchronized void denyTransport(Transport transport,int status){
        if(!current(transport))return;
        if(status==401||status==403){clear();error=status==401?Error.LOGIN:Error.DENIED;}
    }

    public LiveBuildGroupController(Executor callback) {
        this.callback = callback;
        worker = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),task->{
            Thread thread=new Thread(task,"MapKluss-build-group");thread.setDaemon(true);return thread;
        });
    }
    public synchronized void configure(Source source, Object world, String credential, Api api) {
        if (!Objects.equals(this.source,source) || this.world != world || !Objects.equals(this.credential,credential)) clear();
        this.source=source; this.world=world; this.credential=credential; this.api=api;
    }
    public synchronized boolean available() { return api!=null && world!=null && credential!=null && !credential.isBlank(); }
    public synchronized boolean busy() { return busy; }
    public synchronized Group group() { return group; }
    public synchronized String invite() { return invite; }
    public synchronized Error error() { return error; }
    public synchronized boolean matches() { return group!=null && group.source().equals(source); }
    public synchronized java.util.List<LiveBuildSharedPlacement> placements() { return placements; }
    public synchronized LiveBuildSharedPlacement placement(int tile) {
        return placements.stream().filter(p->p.tile()==tile).findFirst().orElse(null);
    }
    public synchronized Adoption adopt(LiveBuildSharedPlacement placement,String localDimension,boolean consent) {
        if(!consent){error=Error.CONSENT;return null;}
        if(!available()||busy||!matches()||placement==null||!placements.contains(placement)){error=Error.MISMATCH;return null;}
        if(!placement.dimension().equals(localDimension)){error=Error.DIMENSION;return null;}
        return new Adoption(epoch,group.id(),source,placement);
    }
    public synchronized boolean valid(Adoption adoption) {
        return adoption!=null&&adoption.generation()==epoch&&available()&&matches()
            &&group.id().equals(adoption.buildId())&&source.equals(adoption.source())&&placements.contains(adoption.placement());
    }
    public synchronized boolean canPublish() { return available()&&!busy&&error!=Error.CONFLICT&&matches()&&group.role().equals("owner"); }
    public synchronized void publish(Publication publication,boolean consent) {
        if(!consent){error=Error.CONSENT;return;}
        if(!canPublish())return;
        if(publication==null||!source.equals(publication.source())||publication.identity()==null
            ||publication.tile()<0||publication.tile()>=source.wide()*source.tall()){error=Error.MISMATCH;return;}
        try {
            var local=publication.identity();
            var placement=new LiveBuildSharedPlacement(publication.tile(),0,local.schematicSha256(),publication.phase(),
                publication.cellCount(),worldBinding,local.dimension(),local.origin(),local.transform());
            var input=placement.input();input.addProperty("build_id",group.id());input.addProperty("expected_revision",group.revision());
            input.addProperty("consent",true);publishing=publication;submit(LiveBuildApiClient.Action.PLACE,input);
        }catch(IllegalArgumentException invalid){error=Error.MISMATCH;}
    }
    public synchronized void unpublish(int tile) {
        if(!canPublish()||placements.stream().noneMatch(p->p.tile()==tile))return;
        var input=identity(true);input.addProperty("tile",tile);submit(LiveBuildApiClient.Action.UNPLACE,input);
    }
    public synchronized void create(boolean consent) {
        if (!consent) { error=Error.CONSENT; return; }
        if (!available()) {error=Error.LOGIN;return;}
        if(source==null){error=Error.MISMATCH;return;}
        if (group!=null || busy) return;
        JsonObject input=new JsonObject();input.addProperty("source_sha256",source.sha256());
        input.addProperty("grid_wide",source.wide());input.addProperty("grid_tall",source.tall());input.addProperty("consent",true);
        submit(LiveBuildApiClient.Action.CREATE,input);
    }
    public synchronized void join(String code, boolean consent) {
        if (!consent) {error=Error.CONSENT;return;}
        if (code==null || !code.trim().matches("[a-fA-F0-9]{32}")) {error=Error.CODE;return;}
        if (group!=null || busy) return;
        JsonObject input=new JsonObject();input.addProperty("code",code.trim().toLowerCase(java.util.Locale.ROOT));
        input.addProperty("consent",true);submit(LiveBuildApiClient.Action.JOIN,input);
    }
    public synchronized void refresh() { if(group!=null)submit(LiveBuildApiClient.Action.READ,identity(false)); }
    public synchronized LiveBuildSessionStore.GroupLink sourceImport(String sha256,int wide,int tall){
        if(group==null)return null;
        if(!group.source().equals(new Source(sha256,wide,tall)))throw new IllegalArgumentException("Different group source");
        return new LiveBuildSessionStore.GroupLink(group.id(),group.revision(),java.util.List.of());
    }
    public synchronized void inviteCode() {
        if(group!=null && group.role().equals("owner"))submit(LiveBuildApiClient.Action.INVITE,identity(true));
    }
    public synchronized void leave() {
        if(group!=null)submit(group.role().equals("owner")?LiveBuildApiClient.Action.CLOSE:LiveBuildApiClient.Action.LEAVE,
            identity(group.role().equals("owner")));
    }
    private JsonObject identity(boolean revision) {
        JsonObject input=new JsonObject();input.addProperty("build_id",group.id());
        if(revision)input.addProperty("expected_revision",group.revision());return input;
    }
    private void submit(LiveBuildApiClient.Action action, JsonObject input) {
        if(busy)return;
        if(!available()){error=Error.LOGIN;return;}
        busy=true;error=Error.NONE;long generation=epoch;Api selected=api;
        pending=worker.submit(()->{
            JsonObject response=null;Exception failure=null;
            try{response=selected.call(action,input);}catch(Exception caught){failure=caught;}
            JsonObject result=response;Exception problem=failure;
            callback.execute(()->complete(generation,action,result,problem));
        });
    }
    private synchronized void complete(long generation,LiveBuildApiClient.Action action,JsonObject response,Exception failure) {
        if(generation!=epoch)return;
        busy=false;pending=null;
        var completedPublication=publishing;publishing=null;
        var expectedResume=resuming;resuming=null;
        if(failure!=null){
            int status=failure instanceof LiveBuildApiClient.ApiException apiFailure?apiFailure.status():0;
            error=switch(status){case 401->Error.LOGIN;case 403->Error.DENIED;case 409->Error.CONFLICT;case 429->Error.LIMIT;default->Error.UNAVAILABLE;};
            if(status==401||status==403){group=null;invite="";placements=java.util.List.of();}
            if(status==409)placements=java.util.List.of();
            return;
        }
        if(action==LiveBuildApiClient.Action.CLOSE||action==LiveBuildApiClient.Action.LEAVE){group=null;invite="";placements=java.util.List.of();worldBinding=UUID.randomUUID().toString();return;}
        try{
            JsonObject value=response.getAsJsonObject("build");
            String id=value.get("id").getAsString();String role=value.get("role").getAsString();
            if(expectedResume!=null&&!expectedResume.equals(id))throw new IllegalArgumentException("Different resumed group");
            if(!UUID.fromString(id).toString().equals(id)||!(role.equals("owner")||role.equals("member")))throw new IllegalArgumentException();
            long revision=LiveBuildSharedPlacement.integer(value,"revision");if(revision<1)throw new IllegalArgumentException();
            Group next=new Group(id,role,revision,new Source(value.get("source_sha256").getAsString(),Math.toIntExact(LiveBuildSharedPlacement.integer(value,"grid_wide")),Math.toIntExact(LiveBuildSharedPlacement.integer(value,"grid_tall"))));
            if(group!=null && !group.id().equals(next.id()))throw new IllegalArgumentException();
            if(group!=null && (next.revision()<group.revision()||!group.source().equals(next.source())))throw new IllegalArgumentException();
            var decoded=LiveBuildSharedPlacement.decode(value,next);
            if(group==null)worldBinding=UUID.randomUUID().toString();
            group=next;
            placements=decoded;
            if(action==LiveBuildApiClient.Action.PLACE&&completedPublication!=null){
                var p=placement(completedPublication.tile());
                if(p==null||!p.worldBinding().equals(worldBinding)||!p.matchesLocal(completedPublication.identity(),completedPublication.phase(),completedPublication.cellCount()))
                    throw new IllegalArgumentException("Publication acknowledgement mismatch");
                acknowledged.put(p.tile(),new Adoption(epoch,group.id(),source,p));
            }
            acknowledged.values().removeIf(ticket->!valid(ticket));
            if(response.has("code")){
                String code=response.get("code").getAsString();if(!code.matches("[a-f0-9]{32}"))throw new IllegalArgumentException();invite=code;
            }
            error=matches()?Error.NONE:Error.MISMATCH;
        }catch(RuntimeException invalid){group=null;invite="";placements=java.util.List.of();error=Error.UNAVAILABLE;}
    }
    public synchronized void clear() {
        epoch++;if(pending!=null)pending.cancel(true);pending=null;worker.purge();
        publishing=null;resuming=null;acknowledged.clear();
        busy=false;group=null;invite="";placements=java.util.List.of();worldBinding=UUID.randomUUID().toString();api=null;credential=null;source=null;world=null;error=Error.NONE;
    }
    public synchronized void shutdown() { clear();worker.shutdownNow(); }
}
