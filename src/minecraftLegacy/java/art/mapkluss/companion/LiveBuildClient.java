package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;

/** Main-thread owner of local verification; never writes to the world. */
public final class LiveBuildClient {
    private static final long CLOCK_START = System.nanoTime();
    private static final LiveBuildClient INSTANCE = new LiveBuildClient();
    private final TrackerWebPublisher webPublisher = new TrackerWebPublisher(task -> MinecraftClient.getInstance().execute(task));
    private TrackerWebSnapshot webSnapshot;
    private String webArt, webVersion;
    private long webConfigureAt;
    public void bindWeb(String art, String version) {
        if (art == null || version == null) return;
        java.util.UUID.fromString(art); java.util.UUID.fromString(version);
        if (!java.util.Objects.equals(webArt, art) || !java.util.Objects.equals(webVersion, version)) {
            webPublisher.clear(); webSnapshot = null; webConfigureAt = 0;
        }
        webArt = art; webVersion = version;
    }
    public boolean webSyncFailed() { return webPublisher.failed(); }
    private TrackerWebSnapshot materialSnapshot;
    private Map<String,Integer> scannedMaterials;
    private long nextMaterials;
    public Map<String,Integer> scannedMaterials(String art,String version) {
        return running()&&!preparing()&&java.util.Objects.equals(webArt,art)&&webArt!=null
            &&java.util.Objects.equals(webVersion,version)?scannedMaterials:null;
    }
    private void tickMaterials(MinecraftClient client) {
        if(!running()||preparing()||client.world!=world||client.player==null) {
            materialSnapshot=null;scannedMaterials=null;nextMaterials=0;return;
        }
        long time=now();
        if(time<nextMaterials)return;
        var targets=new java.util.ArrayList<LiveBuildProgress>();
        for(int i=0;i<partCount();i++)targets.add(bundleRuntime!=null?bundleRuntime.observationSource(i):progress.placement(i));
        if(targets.stream().allMatch(java.util.Objects::isNull)) {
            materialSnapshot=null;scannedMaterials=null;return;
        }
        if(materialSnapshot==null||!materialSnapshot.matches(targets)) {
            materialSnapshot=new TrackerWebSnapshot(targets);
        }
        if(!materialSnapshot.advance(time,8192))return;
        scannedMaterials=materialSnapshot.materialCounts();
        materialSnapshot=null;nextMaterials=time+1000;
    }
    private void tickWeb(MinecraftClient client) {
        tickMaterials(client);
        if (webArt == null || !running() || preparing() || client.world != world || client.player == null) return;
        long time = now();
        if (time >= webConfigureAt) {
            webConfigureAt = time + 5000;
            try {
                var runtime = CompanionRuntime.create(client);
                var token = runtime.sessionStore().accessToken();
                var source = groupSource();
                if (source == null || token == null || token.isBlank()) { webPublisher.clear(); webSnapshot = null; return; }
                webPublisher.configure(new LiveBuildApiClient(runtime.config(),token),token,webArt,webVersion,source.sha256());
            } catch (Exception failure) { webPublisher.clear(); webSnapshot = null; return; }
        }
        if (!webPublisher.ready(time)) return;
        var targets = new java.util.ArrayList<LiveBuildProgress>();
        for(int i=0;i<partCount();i++)targets.add(bundleRuntime!=null?bundleRuntime.observationSource(i):progress.placement(i));
        if(webSnapshot==null||!webSnapshot.matches(targets))webSnapshot=new TrackerWebSnapshot(targets);
        if(!webSnapshot.advance(time,8192))return;
        var image=pixels();var total=summary();
        int w=bundleRuntime!=null?bundleRuntime.width():top==null?0:top.width();
        int h=bundleRuntime!=null?bundleRuntime.height():top==null?0:top.height();
        if(image==null||total==null||w==0||h==0)return;
        try { webPublisher.publish(webSnapshot.freeze(w,h,image,total,time),groups.matches()?groups.group():null,time); }
        catch(IllegalArgumentException invalid) { /* An unfinished overview will be retried on the next tick. */ }
        webSnapshot=null;
    }
    private final LiveBuildGroupController groups = new LiveBuildGroupController(this::groupCallback);
    private Object groupWorld;
    private final LiveBuildEvidencePublisher publisher=new LiveBuildEvidencePublisher(groups,this::groupCallback,LiveBuildClient::now);
    private final LiveBuildEvidenceReceiver receiver=new LiveBuildEvidenceReceiver(groups,this::groupCallback,LiveBuildClient::now);
    public boolean groupSyncFailed(){return publisher.failed()||receiver.failed();}
    private void publishGroupEvidence(){
        var bindings=new java.util.ArrayList<LiveBuildEvidencePublisher.Binding>();
        for(int tile=0;tile<partCount();tile++){
            var adoption=activeGroupPlacement(tile);
            if(adoption==null)continue;
            var observed=bundleRuntime!=null?bundleRuntime.observationSource(tile):progress==null?null:progress.placement(tile);
            if(observed!=null)bindings.add(new LiveBuildEvidencePublisher.Binding(adoption,observed));
        }
        publisher.tick(bindings);
        receiver.tick(bindings);
    }
    private final Map<Integer,LiveBuildGroupController.Adoption> groupAdoptions=new HashMap<>();
    private LiveBuildGroupController.Adoption pendingGroupAdoption;
    public boolean canAdoptGroupPlacement() {
        return groups.available()&&groups.matches()&&!groups.busy()&&!preparing()&&(!selectedPlaced()||canChangeGroupPhase())&&!phaseBound()
            &&groups.placement(selectedPart())!=null;
    }
    private boolean canChangeGroupPhase(){
        var previous=groupAdoptions.get(selectedPart());var current=groups.transport();
        return bundleRuntime!=null&&previous!=null&&current!=null&&previous.generation()==current.generation()
            &&previous.buildId().equals(current.group().id())&&previous.source().equals(current.group().source())
            &&bundleRuntime.canChangeGroupPhase(groups.placement(selectedPart()));
    }
    public void adoptGroupPlacement(MinecraftClient client,LiveBuildSharedPlacement remote) {
        if(client.world!=world||client.player==null||!canAdoptGroupPlacement()||remote==null||remote.tile()!=selectedPart())return;
        var ticket=groups.adopt(remote,LensWorldIdentity.dimensionId(client),true);
        if(ticket==null)return;
        try {
            if(bundleRuntime!=null){
                if(selectedPlaced())bundleRuntime.changeGroupPhase(remote,()->groups.valid(ticket));
                else bundleRuntime.adoptGroupPlacement(remote,()->groups.valid(ticket));
            }
            else {
                var part=selectedMap();
                if(part==null||!remote.targetSha256().equals(part.targetSha256())||remote.phase()!=-1||remote.cellCount()!=part.cells().size())
                    throw new IllegalArgumentException("Different local target");
                anchor(client,new BlockPos(remote.origin().x(),remote.origin().y(),remote.origin().z()),remote.transform());
                pendingGroupAdoption=ticket;
            }
            groupAdoptions.put(remote.tile(),ticket);
        }catch(RuntimeException invalid){preparationFailed=true;}
    }
    public LiveBuildGroupController.Adoption activeGroupPlacement(int tile) {
        var ticket=groupAdoptions.get(tile);
        if(!groups.valid(ticket))ticket=groups.publishedPlacement(tile);
        if(!groups.valid(ticket))return null;
        var remote=ticket.placement();
        if(bundleRuntime!=null)return bundleRuntime.matchesGroupPlacement(remote)?ticket:null;
        var placed=progress==null?null:progress.placement(tile);
        return placed!=null&&remote.matchesLocal(placed.identity(),-1,placed.size())?ticket:null;
    }
    private void updateGroupAdoptions() {
        var transport=groups.transport();
        groupAdoptions.values().removeIf(ticket->transport==null||ticket.generation()!=transport.generation()
            ||!ticket.buildId().equals(transport.group().id())||groups.placement(ticket.placement().tile())==null);
        if(pendingGroupAdoption==null)return;
        if(!groups.valid(pendingGroupAdoption)&&placementJob!=null){
            placementJob.close();placementJob=null;preparationFailed=true;nextView=0;
        }
        if(placementJob==null)pendingGroupAdoption=null;
    }
    private void groupCallback(Runnable task) {
        var client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (groupWorld == null || client.world != groupWorld || client.player == null) { groups.clear(); return; }
            task.run();
        });
    }
    public LiveBuildGroupController groups() { return groups; }
    public LiveBuildGroupController.Publication groupPublication() {
        var source=groupSource();var identity=identity();var summary=selectedSummary();
        if(source==null||identity==null||summary==null||preparing())return null;
        int phase=bundleRuntime!=null?bundleRuntime.selectedPhase():phaseSession==null?-1:phaseSession.phase();
        return new LiveBuildGroupController.Publication(source,selectedPart(),phase,summary.total(),identity);
    }
    public void publishGroupPlacement(LiveBuildGroupController.Publication confirmed) {
        // A phase/selection/anchor change while the confirmation is open invalidates that consent.
        if(confirmed!=null&&confirmed.equals(groupPublication()))groups.publish(confirmed,true);
    }
    public LiveBuildGroupController.Source groupSource() {
        var bounds = artworkBounds();
        if (bounds == null || persistence == null || persistence.source() == null) return null;
        return new LiveBuildGroupController.Source(persistence.source().sha256(), (bounds.width()+127)/128, (bounds.depth()+127)/128);
    }
    public void configureGroup(MinecraftClient client) {
        groupWorld=client.world;
        try {
            var runtime = CompanionRuntime.create(client);
            var token = runtime.sessionStore().accessToken();
            groups.configure(groupSource(),client.world,token,token==null||token.isBlank()?null:new LiveBuildApiClient(runtime.config(),token));
        } catch (Exception failure) { groups.clear(); }
    }
    private LiveBuildAssembly progress;
    private final java.util.concurrent.ExecutorService sourceWorker=java.util.concurrent.Executors.newSingleThreadExecutor(task->{
        var thread=new Thread(task,"MapKluss-build-source");thread.setDaemon(true);return thread;
    });
    private java.util.concurrent.Future<?> sourceTask;
    private long sourceGeneration;
    private boolean sourcePending;
    private volatile int sourcePercent;
    private boolean sourceFailed,sourceComplete;
    public boolean sourceBusy(){return sourcePending;}
    public int sourcePercent(){return sourcePercent;}
    public boolean sourceFailed(){return sourceFailed;}
    public boolean sourceComplete(){return sourceComplete;}
    public boolean canTransferSource(boolean upload){
        var t=groups.sourceTransport();
        return t!=null&&!sourceBusy()&&!groups.busy()&&!preparing()&&(!upload||
            t.group().role().equals("owner")&&groups.matches()&&persistence!=null&&persistence.source()!=null);
    }
    public void transferSource(MinecraftClient client,boolean upload){
        if(client.player==null||!canTransferSource(upload))return;
        var t=groups.sourceTransport();var expectedWorld=client.world;
        var cache=LiveBuildSourceCache.forRunDir(client.runDirectory.toPath());
        var ref=persistence==null?null:persistence.source();
        sourcePercent=0;sourceFailed=false;sourceComplete=false;
        long generation=++sourceGeneration;sourcePending=true;
        sourceTask=sourceWorker.submit(()->{
            try{
                LiveBuildSourceCache.Loaded loaded;
                if(upload){
                    LiveBuildSourceTransfer.upload(t,cache,ref,()->groups.sourceCurrent(t),value->sourcePercent=value);loaded=null;
                }else loaded=LiveBuildSourceTransfer.download(t,cache,()->groups.sourceCurrent(t),value->sourcePercent=value);
                client.execute(()->{
                    if(generation!=sourceGeneration){if(loaded!=null)loaded.close();return;}
                    sourcePending=false;
                    if(client.world!=expectedWorld||client.player==null||!groups.sourceCurrent(t)){if(loaded!=null)loaded.close();return;}
                    try{
                        if(loaded!=null)openCached(client,loaded,client.player.getBlockPos(),null);
                        sourceComplete=true;
                    }catch(RuntimeException failed){if(loaded!=null)loaded.close();sourceFailed=true;}
                });
            }catch(Exception failure){
                client.execute(()->{
                    if(generation!=sourceGeneration)return;
                    sourcePending=false;
                    if(client.world!=expectedWorld||!groups.sourceCurrent(t))return;
                    sourceFailed=true;
                    if(failure instanceof LiveBuildApiClient.ApiException apiFailure&&(apiFailure.status()==401||apiFailure.status()==403))groups.clear();
                });
            }
        });
    }
    public LiveBuildOrbitPreview.Surface orbitSurface(){
        if(bundleRuntime!=null)return bundleRuntime.orbitSurface();
        if(progress==null||top==null)return null;
        int[] heights=new int[top.width()*top.height()];
        for(int pixel=0;pixel<heights.length;pixel++){int cell=top.index(pixel);if(cell>=0)heights[pixel]=progress.cell(cell).relativePosition().y();}
        double spacing=Math.max(1,(Math.max(artworkBounds().width(),artworkBounds().depth())+127)/128);
        return new LiveBuildOrbitPreview.Surface(top.width(),top.height(),heights,spacing);
    }
    private LiveBuildPersistence persistence;
    private LiveBuildSessionStore.Snapshot restoring;
    private LiveBuildSessionStore.GroupLink resumeGroup;
    private long nextGroupResume;
    private void resumeGroup(MinecraftClient client){
        if(resumeGroup==null||world==null||client.world!=world||client.player==null||preparing())return;
        if(groups.group()==null){
            if(groups.busy()||now()<nextGroupResume)return;
            if(groups.error()==LiveBuildGroupController.Error.DENIED){resumeGroup=null;return;}
            nextGroupResume=now()+10000;configureGroup(client);
            if(groups.available())groups.resume(resumeGroup.id());
            return;
        }
        if(!groups.matches()||!groups.group().id().equals(resumeGroup.id())||groups.group().revision()<resumeGroup.revision()){
            resumeGroup=null;return;
        }
        for(var saved:resumeGroup.placements()){
            if(!saved.equals(groups.placement(saved.tile())))continue;
            var local=bundleRuntime!=null?bundleRuntime.observationSource(saved.tile()):progress==null?null:progress.placement(saved.tile());
            boolean same=bundleRuntime!=null?bundleRuntime.matchesGroupPlacement(saved):local!=null&&saved.matchesLocal(local.identity(),-1,local.size());
            if(same){var ticket=groups.adopt(saved,sourceDimension,true);if(ticket!=null)groupAdoptions.put(saved.tile(),ticket);}
        }
        resumeGroup=null;
    }
    private final java.util.ArrayDeque<LiveBuildSessionStore.Placement> restoreParts=new java.util.ArrayDeque<>();
    private LiveBuildSessionStore.Placement restoringPart;
    public boolean saveFailed(){return persistence!=null&&persistence.failed();}
    private void ensurePersistence(MinecraftClient client){if(persistence==null)persistence=new LiveBuildPersistence(client.runDirectory.toPath());}
    public void openCached(MinecraftClient client,LiveBuildSourceCache.Loaded loaded,BlockPos origin,LiveBuildSessionStore.Snapshot saved){
        ensurePersistence(client);
        int wide=loaded.bundle()!=null?(loaded.bundle().width()+127)/128:(loaded.schematic().artBounds().width()+127)/128;
        int tall=loaded.bundle()!=null?(loaded.bundle().height()+127)/128:(loaded.schematic().artBounds().depth()+127)/128;
        var importedGroup=saved==null?groups.sourceImport(loaded.reference().sha256(),wide,tall):null;
        if(saved!=null&&(!saved.worldHash().equals(LensWorldIdentity.serverHash(client))
            ||!saved.dimension().equals(LensWorldIdentity.dimensionId(client))))throw new IllegalArgumentException("Stored world changed");
        if(loaded.bundle()!=null){
            loadBundle(client,loaded.bundle(),origin);
            if(saved!=null)bundleRuntime.restore(saved);
        }else{
            load(client,loaded.schematic(),loaded.projection(),origin);
            if(saved!=null){
                if(!saved.sourceSha256().equals(loaded.schematic().sha256())
                    ||saved.gridWide()!=(loaded.schematic().artBounds().width()+127)/128
                    ||saved.gridTall()!=(loaded.schematic().artBounds().depth()+127)/128)throw new IllegalArgumentException("Stored source dimensions changed");
                restoring=saved;restoreParts.addAll(saved.placements());
            }
        }
        persistence.activate(loaded.reference());
        resumeGroup=saved==null?importedGroup:saved.group();
        if(saved!=null&&saved.cloud()!=null)bindWeb(saved.cloud().art(),saved.cloud().version());
    }
    private LiveBuildSessionStore.Snapshot capture(){
        var localSnapshot=captureLocal();if(localSnapshot==null)return null;
        var saved=webArt==null?localSnapshot:localSnapshot.withCloud(new LiveBuildSessionStore.CloudLink(webArt,webVersion));
        var group=groups.group();
        if(group==null){
            if(resumeGroup==null)return saved;
            var links=resumeGroup.placements().stream().filter(remote->saved.placements().stream().anyMatch(
                local->local.tile()==remote.tile()&&remote.matchesLocal(local.identity(),local.phase(),local.states().length))).toList();
            return saved.withGroup(new LiveBuildSessionStore.GroupLink(resumeGroup.id(),resumeGroup.revision(),links));
        }
        if(!groups.matches())return saved;
        var links=new java.util.ArrayList<LiveBuildSharedPlacement>();
        for(var local:saved.placements()){var ticket=activeGroupPlacement(local.tile());if(ticket!=null)links.add(ticket.placement());}
        return saved.withGroup(new LiveBuildSessionStore.GroupLink(group.id(),group.revision(),links));
    }
    private LiveBuildSessionStore.Snapshot captureLocal(){
        if(persistence==null||persistence.source()==null||preparing())return null;
        if(bundleRuntime!=null)return bundleRuntime.snapshot(System.currentTimeMillis());
        if(preparationFailed())return null;
        if(progress==null||phaseSession!=null)return null;
        var source=persistence.source();var parts=progress.parts();var placed=new java.util.ArrayList<LiveBuildSessionStore.Placement>();
        for(int i=0;i<parts.parts().size();i++){var p=progress.placement(i);if(p!=null)placed.add(new LiveBuildSessionStore.Placement(i,-1,false,p.identity(),p.savedStates()));}
        return new LiveBuildSessionStore.Snapshot(source.kind(),source.sha256(),sourceWorld,sourceDimension,
            (parts.source().artBounds().width()+127)/128,(parts.source().artBounds().depth()+127)/128,selectedPart,System.currentTimeMillis(),placed);
    }
    private String sourceWorld,sourceDimension;
    private void persist(boolean suspend){
        if(persistence==null)return;
        try{var saved=capture();if(suspend)persistence.suspend(saved);else persistence.save(saved,now());}
        catch(RuntimeException failure){persistence.failureAt(now());}
    }
    private void restoreNext(){
        if(restoring==null||placementJob!=null)return;
        restoringPart=restoreParts.poll();
        if(restoringPart==null){selectedPart=restoring.selected();restoring=null;nextView=0;return;}
        pendingPart=restoringPart.tile();
        var part=progress.parts().part(pendingPart);
        if(!part.targetSha256().equals(restoringPart.identity().schematicSha256()))throw new IllegalArgumentException("Stored map changed");
        placementJob=new LiveBuildPlacementPreparation(part,restoringPart.identity());
    }
    private LiveBuildBundleRuntime bundleRuntime;
    private long catalogRequest;
    private boolean catalogLoading;
    private LiveBuildCatalogLink pendingCatalogFollow;
    private long observedBundleRevision=-1;
    public boolean bundleLoaded(){return bundleRuntime!=null;}
    public void loadBundle(MinecraftClient client,LiveBuildBundleWorkspace workspace,BlockPos origin){
        if(client.world==null||client.player==null)throw new IllegalStateException("World unavailable");
        reset();world=client.world;pendingOrigin=origin;
        sourceWorld=LensWorldIdentity.serverHash(client);sourceDimension=LensWorldIdentity.dimensionId(client);
        bundleRuntime=new LiveBuildBundleRuntime(workspace,LensWorldIdentity.serverHash(client),LensWorldIdentity.dimensionId(client),net.minecraft.block.MapColor::getRenderColor);
        top=LiveBuildTopView.overview(workspace.width(),workspace.height());displayName=workspace.catalog().title();
    }
    public double wholeCompletion(){return bundleRuntime!=null?bundleRuntime.completion(now()):summary==null?0:summary.completion();}
    private LiveBuildPreviewCheck previewCheck;
    private long previewEpoch=-1;
    private LiveBuildPhaseSession phaseSession;
    private java.util.concurrent.CompletableFuture<LiveBuildSchematic> phaseFuture;
    public boolean phaseBound(){return bundleRuntime!=null?bundleRuntime.selectedBound():phaseSession!=null;}
    public boolean canFollowTwoLayer(MinecraftClient client){
        if(catalogLoading||pendingCatalogFollow!=null)return false;
        var session=SuppressionManager.instance().trackerSession(client);
        return session!=null&&(bundleRuntime==null||bundleRuntime.canFollow(session));
    }
    public void followTwoLayer(MinecraftClient client){
        var session=SuppressionManager.instance().trackerSession(client);
        if(session==null)throw new IllegalStateException("Two-layer placement unavailable");
        if(bundleRuntime!=null){bundleRuntime.follow(session);return;}
        var link=SuppressionManager.instance().trackerSource();
        if(link!=null){openTwoLayerCatalog(client,session,link);return;}
        throw new IllegalStateException("Import the full Two-layer ZIP first");
    }
    public boolean needsTwoLayerBundle(MinecraftClient client){
        return bundleRuntime==null && SuppressionManager.instance().trackerSession(client)!=null
            && SuppressionManager.instance().trackerSource()==null;
    }
    private LiveBuildPlacementPreparation placementJob;
    private void openTwoLayerCatalog(MinecraftClient client,LiveBuildPhaseSession session,LiveBuildCatalogLink link){
        ensurePersistence(client);
        persist(true);reset();world=client.world;catalogLoading=true;
        long request=catalogRequest;
        var cache=LiveBuildSourceCache.forRunDir(client.runDirectory.toPath());
        java.util.concurrent.CompletableFuture.supplyAsync(()->{
            try{
                var loaded=cache.load(link.reference());
                try{link.validate(loaded,session.bundle());return loaded;}
                catch(RuntimeException failure){loaded.close();throw failure;}
            }catch(java.io.IOException failure){throw new java.util.concurrent.CompletionException(failure);}
        }).whenComplete((loaded,error)->client.execute(()->{
            if(request!=catalogRequest){if(loaded!=null)loaded.close();return;}
            var current=SuppressionManager.instance().trackerSession(client);
            if(error!=null||client.world!=world||client.player==null||current==null
                ||!session.worldKey().equals(current.worldKey())||!session.dimension().equals(current.dimension())
                ||!link.equals(SuppressionManager.instance().trackerSource())){
                if(loaded!=null)loaded.close();reset();preparationFailed=true;return;
            }
            try{
                link.validate(loaded,current.bundle());
                var p=current.origin();
                openCached(client,loaded,new BlockPos(p.x(),p.y(),p.z()),null);
                pendingCatalogFollow=link;
            }catch(RuntimeException failure){loaded.close();reset();preparationFailed=true;}
        }));
    }
    private final Map<Integer, LiveBuildProgress.Summary> partSummaries = new HashMap<>();
    private int pendingPart;
    private int selectedPart;
    private String displayName = "";
    public String displayName() { return displayName; }
    private long placementRevision;
    public int selectedPart() { return bundleRuntime!=null?bundleRuntime.selected():selectedPart; }
    public int partCount() { return bundleRuntime!=null?bundleRuntime.count():progress==null?0:progress.parts().parts().size(); }
    public LiveBuildParts.Part selectedMap() { return bundleRuntime!=null?bundleRuntime.selectedMap():progress==null?null:progress.parts().part(selectedPart); }
    public LiveBuildSchematic.Bounds artworkBounds() { return bundleRuntime!=null?new LiveBuildSchematic.Bounds(0,0,0,bundleRuntime.width()-1,0,bundleRuntime.height()-1):progress==null?null:progress.parts().source().artBounds(); }
    public void selectPart(int delta) { if(bundleRuntime!=null){bundleRuntime.select(bundleRuntime.selected()+delta);return;} if(partCount()>0){selectedPart=Math.floorMod(selectedPart+delta,partCount());refreshView(now());} }
    public boolean canAnchor() { return progress!=null&&!preparing()&&!progress.parts().part(selectedPart).empty()&&progress.placement(selectedPart)==null; }
    public boolean selectedPlaced() { return bundleRuntime!=null?bundleRuntime.identity()!=null:progress!=null&&progress.placement(selectedPart)!=null; }
    public void removeSelected() { if(bundleRuntime!=null){bundleRuntime.unanchor();return;} if(progress!=null){progress.remove(selectedPart);refreshView(now());} }
    public LiveBuildProgress.Summary selectedSummary() { return bundleRuntime!=null?bundleRuntime.selectedSummary(now()):partSummaries.get(selectedPart); }
    private LiveBuildPreparation preparation;
    private BlockPos pendingOrigin;
    private boolean preparationFailed;
    public boolean preparing() { return catalogLoading||pendingCatalogFollow!=null||(bundleRuntime!=null?bundleRuntime.interactivePreparing():preparation != null || placementJob != null || phaseFuture != null || restoring!=null); }
    public boolean preparationFailed() { return bundleRuntime!=null?bundleRuntime.failed():preparationFailed; }
    private LiveBuildTopView top;
    private Object world;
    private boolean registered;
    private final Map<BlockState, LiveBuildProgress.State> observed = new HashMap<>();
    private final Map<LiveBuildProgress.State, Integer> colours = new HashMap<>();
    private LiveBuildProgress.Summary summary;
    private int[] pixels;
    private long nextView;
    private long viewRevision;
    public long viewRevision() {
        if(bundleRuntime!=null&&observedBundleRevision!=bundleRuntime.revision()){
            observedBundleRevision=bundleRuntime.revision();viewRevision++;
        }
        return viewRevision;
    }
    public static LiveBuildClient instance() { return INSTANCE; }
    public static void register() {
        if (INSTANCE.registered) return;
        INSTANCE.registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tick);
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::tickWeb);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client->{
            INSTANCE.persist(true);
            if(INSTANCE.persistence!=null)INSTANCE.persistence.finishWrites();
            INSTANCE.reset();
        });
    }
    public boolean running() { return bundleRuntime!=null || progress != null; }
    public LiveBuildProgress.Summary summary() { return bundleRuntime!=null?bundleRuntime.summary(now()):summary; }
    public LiveBuildTopView top() { return top; }
    public int[] pixels() { return bundleRuntime!=null?bundleRuntime.pixels():pixels; }
    public LiveBuildProgress.Identity identity() { if(bundleRuntime!=null)return bundleRuntime.identity();var p=progress==null?null:progress.placement(selectedPart);return p==null?null:p.identity(); }
    public void stop() {
        if(persistence!=null)persistence.stop();
        reset();
    }
    private void reset() {
        webPublisher.clear(); webSnapshot=null; webArt=webVersion=null; webConfigureAt=0;
        materialSnapshot=null;scannedMaterials=null;nextMaterials=0;
        sourceGeneration++;sourcePending=false;
        if(sourceTask!=null)sourceTask.cancel(true);sourceTask=null;sourceFailed=false;sourceComplete=false;
        groups.clear();
        publisher.clear();
        receiver.clear();
        groupWorld=null;
        resumeGroup=null;nextGroupResume=0;
        groupAdoptions.clear();pendingGroupAdoption=null;
        catalogRequest++;catalogLoading=false;pendingCatalogFollow=null;
        restoring=null;restoringPart=null;restoreParts.clear();
        if(bundleRuntime!=null)bundleRuntime.close();bundleRuntime=null;observedBundleRevision=-1;viewRevision++;
        previewCheck=null;
        previewEpoch=-1;
        phaseSession=null;
        if(phaseFuture!=null)phaseFuture.cancel(false);
        phaseFuture=null;
        if(placementJob!=null)placementJob.close();
        placementJob=null;partSummaries.clear();
        if (preparation != null) preparation.close();
        preparation = null; pendingOrigin = null; preparationFailed = false;
        if (progress != null) progress.close();
        progress = null; top = null; world = null; summary = null; pixels = null; displayName = "";
        observed.clear(); colours.clear();
    }
    public void load(MinecraftClient client, LiveBuildSchematic schematic, LiveBuildTopView view, BlockPos origin) {
        if (client.world == null || client.player == null) throw new IllegalStateException("Enter a world first");
        var identity = new LiveBuildProgress.Identity(schematic.sha256(), LensWorldIdentity.serverHash(client),
            LensWorldIdentity.dimensionId(client), new LiveBuildProgress.Position(origin.getX(), origin.getY(), origin.getZ()), 0);
        reset();
        sourceWorld=LensWorldIdentity.serverHash(client);sourceDimension=LensWorldIdentity.dimensionId(client);
        selectedPart=0;
        displayName = "";
        world = client.world; top = view; pendingOrigin = origin;
        preparation = new LiveBuildPreparation(schematic, identity);
    }
    public LiveBuildProgress.Position schematicOrigin() {
        var id=identity();
        if(id==null)return null;
        return bundleRuntime!=null?bundleRuntime.schematicOrigin():progress.parts().toSchematicOrigin(selectedPart,id.origin(),id.transform());
    }
    public void anchorSchematic(MinecraftClient client, BlockPos origin, LiveBuildTransform transform) {
        var p=new LiveBuildProgress.Position(origin.getX(),origin.getY(),origin.getZ());
        if(bundleRuntime!=null){
            if(client.world!=world||client.player==null)throw new IllegalStateException("World changed");
            bundleRuntime.anchorSchematic(p,transform);return;
        }
        if(bundleRuntime==null&&progress!=null)p=progress.parts().fromSchematicOrigin(selectedPart,p,transform);
        anchor(client,new BlockPos(p.x(),p.y(),p.z()),transform);
    }
    public void anchor(MinecraftClient client) {
        if(client.player==null)throw new IllegalStateException("Placement unavailable");
        anchor(client, client.player.getBlockPos().toImmutable(), LiveBuildTransform.NONE);
    }
    public void anchor(MinecraftClient client, BlockPos origin, LiveBuildTransform transform) {
        if(bundleRuntime!=null){
            if(client.world!=world||client.player==null)throw new IllegalStateException("World changed");
            bundleRuntime.anchor(new LiveBuildProgress.Position(origin.getX(),origin.getY(),origin.getZ()),transform);return;
        }
        if(progress==null||preparing()||client.world!=world||client.player==null||selectedMap().empty())
            throw new IllegalStateException("Placement unavailable");
        var part=progress.parts().part(selectedPart);
        pendingPart=selectedPart;
        var identity=new LiveBuildProgress.Identity(part.targetSha256(),LensWorldIdentity.serverHash(client),
            LensWorldIdentity.dimensionId(client),new LiveBuildProgress.Position(origin.getX(),origin.getY(),origin.getZ()),++placementRevision,transform);
        var job=new LiveBuildPlacementPreparation(part,identity);
        progress.remove(selectedPart);
        placementJob=job;preparationFailed=false;
        refreshView(now());
    }
    public void displayName(String name) { displayName = name == null ? "" : name; }
    private LiveBuildProgress.State resolve(MinecraftClient client, LiveBuildProgress.State expected) {
        Identifier id = Identifier.of(expected.block());
        if (!Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("Unknown block");
        BlockState state = Registries.BLOCK.get(id).getDefaultState();
        for (var property : expected.properties().entrySet()) {
            Property<?> key = state.getBlock().getStateManager().getProperty(property.getKey());
            if (key == null) throw new IllegalArgumentException("Unknown block property");
            state = apply(state, key, property.getValue());
        }
        var value = describe(state);
        colours.put(value, 0xff000000 | state.getMapColor(client.world, pendingOrigin).color);
        return value;
    }
    private void prepare(MinecraftClient client) {
        try {
            long deadline = System.nanoTime() + 2_000_000L;
            for (int i = 0; i < 64 && System.nanoTime() < deadline; i++) {
                var ready = preparation.advance(expected -> resolve(client, expected), 1);
                if (ready == null) continue;
                progress = ready;
                preparation.close(); preparation = null;
                pixels = new int[top.width() * top.height()]; nextView = 0;
                refreshView(now());
                if(phaseSession!=null)anchor(client,pendingOrigin,LiveBuildTransform.NONE);
                restoreNext();
                return;
            }
        } catch (RuntimeException failure) {
            reset(); preparationFailed = true;
        }
    }
    private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
        return state.with(property, property.parse(value).orElseThrow(() -> new IllegalArgumentException("Invalid block property value")));
    }
    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) { return property.name(state.get(property)); }
    private static LiveBuildProgress.State describe(BlockState state) {
        var properties = new HashMap<String, String>();
        for (Property<?> property : state.getProperties()) properties.put(property.getName(), value(state, property));
        return new LiveBuildProgress.State(Registries.BLOCK.getId(state.getBlock()).toString(), properties);
    }
    private LiveBuildProgress.State transformState(LiveBuildProgress.State expected, LiveBuildTransform transform) {
        BlockState state = Registries.BLOCK.get(Identifier.of(expected.block())).getDefaultState();
        for(var property:expected.properties().entrySet()){
            Property<?> key=state.getBlock().getStateManager().getProperty(property.getKey());
            if(key==null)throw new IllegalArgumentException("Unknown block property");
            state=apply(state,key,property.getValue());
        }
        if(transform.mirrorX())state=state.mirror(net.minecraft.util.BlockMirror.FRONT_BACK);
        var rotation=switch(transform.quarterTurns()){
            case 1 -> net.minecraft.util.BlockRotation.CLOCKWISE_90;
            case 2 -> net.minecraft.util.BlockRotation.CLOCKWISE_180;
            case 3 -> net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.util.BlockRotation.NONE;
        };
        return describe(state.rotate(rotation));
    }
    private static long now() { return (System.nanoTime() - CLOCK_START) / 1_000_000L; }
    private LiveBuildProgress.State readBundleBlock(MinecraftClient client,BlockPos pos){
        if(!client.world.getChunkManager().isChunkLoaded(pos.getX()>>4,pos.getZ()>>4))return null;
        return observed.computeIfAbsent(client.world.getBlockState(pos),LiveBuildClient::describe);
    }
    private long freshness() { return LiveBuildProgress.LIVE_FRESHNESS_MS; }
    private void tick(MinecraftClient client) {
        if(world==null&&groupWorld!=null&&(client.world!=groupWorld||client.player==null)){groups.clear();publisher.clear();receiver.clear();groupWorld=null;}
        groups.tick(now());
        updateGroupAdoptions();
        ensurePersistence(client);
        if(world!=null&&(client.world!=world||client.player==null)){persist(true);reset();return;}
        if(world==null&&client.world!=null&&client.player!=null){
            var saved=persistence.poll(LensWorldIdentity.serverHash(client),LensWorldIdentity.dimensionId(client));
            if(saved!=null){
                try{openCached(client,saved.source(),client.player.getBlockPos().toImmutable(),saved.snapshot());}
                catch(RuntimeException failure){saved.source().close();reset();preparationFailed=true;}
                return;
            }
        }
        publishGroupEvidence();
        resumeGroup(client);
        if(persistence.due(now())&&!preparing())persist(false);
        if(bundleRuntime!=null){
            if(client.world!=world||client.world==null||client.player==null){stop();return;}
            bundleRuntime.updateSession(SuppressionManager.instance().trackerSession(client));
            long deadline=System.nanoTime()+2_000_000L;
            for(int i=0;i<64&&bundleRuntime.preparing()&&System.nanoTime()<deadline;i++)
                bundleRuntime.prepare(state->resolve(client,state),this::transformState);
            if(pendingCatalogFollow!=null&&!bundleRuntime.preparing()){
                var current=SuppressionManager.instance().trackerSession(client);
                if(current==null)return;
                if(!pendingCatalogFollow.equals(SuppressionManager.instance().trackerSource())){
                    reset();preparationFailed=true;return;
                }
                try{
                    if(bundleRuntime.followTile(pendingCatalogFollow.tile(),current))pendingCatalogFollow=null;
                }catch(RuntimeException failure){reset();preparationFailed=true;}
                return;
            }
            long time=now();deadline=System.nanoTime()+2_000_000L;
            for(int i=0;i<64&&System.nanoTime()<deadline;i++)bundleRuntime.scan(position->{
                var pos=new BlockPos(position.x(),position.y(),position.z());
                return readBundleBlock(client,pos);
            },32,time);
            deadline=System.nanoTime()+2_000_000L;
            for(int i=0;i<128&&System.nanoTime()<deadline;i++)bundleRuntime.preview(32,time);
            return;
        }
        if(phaseSession!=null){
            if(client.world!=world||client.player==null){stop();return;}
            var current=SuppressionManager.instance().trackerSession(client);
            if(current==null){stop();return;}
            if(!phaseSession.equals(current)){
                // Finish at most one validation job before accepting the newest phase.
                if(phaseFuture!=null&&!phaseFuture.isDone())return;
                followTwoLayer(client);return;
            }
            if(phaseFuture!=null){
                if(!phaseFuture.isDone())return;
                try{
                    var session=phaseSession;var target=phaseFuture.join();
                    var origin=session.origin();
                    load(client,target,new LiveBuildTopView(target.cells(),target.artBounds()),new BlockPos(origin.x(),origin.y(),origin.z()));
                    phaseSession=session;displayName=session.bundle().title()+" · Two-layer "+(session.phase()+1);
                }catch(RuntimeException failure){stop();preparationFailed=true;}
                return;
            }
        }
        if (progress == null && preparation == null) return;
        if (client.world != world || client.world == null || client.player == null) { stop(); return; }
        if (preparation != null) { prepare(client); return; }
        if(placementJob!=null){
            try{
                long deadline=System.nanoTime()+2_000_000L;
                for(int i=0;i<64&&System.nanoTime()<deadline;i++){
                    var ready=placementJob.advance(this::transformState,1);
                    if(ready==null)continue;
                    if(restoringPart!=null){
                        ready.restoreStates(restoringPart.identity(),restoringPart.states());
                        placementRevision=Math.max(placementRevision,ready.identity().placementRevision());restoringPart=null;
                    }
                    progress.attach(pendingPart,ready);
                    placementJob.close();placementJob=null;nextView=0;break;
                }
            }catch(RuntimeException failure){
                if(restoring!=null){reset();preparationFailed=true;return;}
                placementJob.close();placementJob=null;preparationFailed=true;nextView=0;
            }
        }
        long now = now(), deadline = System.nanoTime() + 2_000_000L;
        if(restoring!=null){
            try{restoreNext();}catch(RuntimeException failure){reset();preparationFailed=true;}
            return;
        }
        int budget = Math.min(2048, progress.size());
        while (budget > 0 && System.nanoTime() < deadline) {
            int count = Math.min(32, budget);
            progress.scan(position -> {
                var pos = new BlockPos(position.x(), position.y(), position.z());
                if (!client.world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return null;
                BlockState actual = client.world.getBlockState(pos);
                return observed.computeIfAbsent(actual, LiveBuildClient::describe);
            }, count, now);
            budget -= count;
        }
        if (now >= nextView) { refreshView(now); nextView = now + 1000; }
        if(previewCheck!=null){
            long previewDeadline=System.nanoTime()+2_000_000L;
            try{
                for(int i=0;i<128&&System.nanoTime()<previewDeadline;i++){
                    var ready=previewCheck.advance(32,now);
                    if(ready!=null){paintPreview(ready);previewCheck=null;viewRevision++;break;}
                }
            }catch(java.util.concurrent.CancellationException changed){previewCheck=null;nextView=0;}
        }
    }
    private void refreshView(long now) {
        viewRevision++;
        summary = progress.summary(now);
        partSummaries.clear();
        for(int part=0;part<partCount();part++){
            var placed=progress.placement(part);
            if(placed!=null)partSummaries.put(part,placed.liveSummary(now));
        }
        if(previewEpoch!=progress.placementEpoch()){
            previewEpoch=progress.placementEpoch();previewCheck=null;
            var unknown=new LiveBuildProgress.Status[pixels.length];
            java.util.Arrays.fill(unknown,LiveBuildProgress.Status.UNKNOWN);
            paintPreview(unknown);
        }
        if(previewCheck==null)previewCheck=new LiveBuildPreviewCheck(progress,top);
    }
    private void paintPreview(LiveBuildProgress.Status[] statuses){
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            int index = top.index(pixel);
            if (index < 0) { pixels[pixel] = 0; continue; }
            var status = statuses[pixel];
            int colour = colours.getOrDefault(progress.cell(index).expected(), 0xff888888);
            int grey = (((colour >> 16) & 255) * 3 + ((colour >> 8) & 255) * 6 + (colour & 255)) / 10;
            pixels[pixel] = switch (status) {
                case CORRECT -> colour;
                case WRONG -> 0xffc74e59;
                case STALE -> 0xff776e4b;
                case MISSING -> 0xff000000 | (grey / 3) * 0x010101;
                case UNKNOWN -> 0xff000000 | (grey / 2) * 0x010101;
            };
        }
    }
}
