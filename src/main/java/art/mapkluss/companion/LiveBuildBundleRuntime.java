package art.mapkluss.companion;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** Client-thread coordinator for a whole bundle; background work only prepares immutable geometry. */
public final class LiveBuildBundleRuntime implements AutoCloseable {
    private final LiveBuildBundleWorkspace workspace;
    private final String world,dimension;
    private final java.util.concurrent.Executor executor;
    private final LiveBuildParts.Part[] displayParts;
    private final LiveBuildTopView[] projections;
    private final LiveBuildProgress[] dormant;
    private final int[] dormantPhases;
    private final short[][] dependencies;
    private int evictionCursor;
    private int dormantCount;
    private int residencyCursor;
    private int priorityTile=-1,scanShare;
    private final int[] scannedSinceLoad;
    private long nextResidency;
    private boolean backgroundPreparation;
    private final int[] original,pixels;
    private int selected,scanTile,previewTile,preparingTile;
    private long revision,placementRevision,nextPreview;
    private CompletableFuture<LiveBuildBundleWorkspace.Prepared> future;
    private LiveBuildBundleWorkspace.Prepared prepared;
    private LiveBuildPreparation preparation;
    private LiveBuildPlacementPreparation placement;
    private LiveBuildPreviewCheck preview;
    private boolean failed;
    private CompletableFuture<int[]> orbitHeights;
    private volatile boolean orbitClosed;
    private long orbitRevision=-1;
    private LiveBuildOrbitPreview.Surface orbitSurface;
    private int[][] orbitOverrides;
    public LiveBuildOrbitPreview.Surface orbitSurface(){
        if(orbitClosed)return null;
        if(orbitHeights==null){
            orbitHeights=CompletableFuture.supplyAsync(()->{
                int[] heights=new int[width()*height()];
                for(var tile:workspace.catalog().tiles()){
                    if(orbitClosed)throw new java.util.concurrent.CancellationException();
                    try{
                        var bundle=tile.bundle();var raw=LiveBuildPhaseTarget.read(bundle.planBytes(),bundle.litematicBytes(),-1);
                        var projection=new LiveBuildTopView(raw.cells(),raw.artBounds());
                        for(int pixel=0;pixel<16384;pixel++){int cell=projection.index(pixel);if(cell>=0)heights[(tile.row()*128+pixel/128)*width()+tile.column()*128+pixel%128]=raw.cells().get(cell).relativePosition().y();}
                    }catch(java.io.IOException failure){throw new java.util.concurrent.CompletionException(failure);}
                }
                return heights;
            },executor);return null;
        }
        if(!orbitHeights.isDone()||orbitHeights.isCompletedExceptionally())return null;
        if(orbitSurface!=null&&orbitRevision==revision)return orbitSurface;
        int[] heights=orbitHeights.join().clone();
        for(int tile=0;tile<count();tile++)if(orbitOverrides[tile]!=null){
            var part=displayParts[tile];
            for(int pixel=0;pixel<16384;pixel++)heights[(part.row()*128+pixel/128)*width()+part.column()*128+pixel%128]=orbitOverrides[tile][pixel];
        }
        orbitRevision=revision;return orbitSurface=new LiveBuildOrbitPreview.Surface(width(),height(),heights);
    }
    private boolean incompleteRestore;
    private LiveBuildPhaseSession boundSession;
    private int boundTile=-1;
    private boolean phaseInvalidated;
    private java.util.ArrayDeque<LiveBuildSessionStore.Placement> restoreQueue;
    private LiveBuildSessionStore.Placement restoringPlacement;
    private int restoreSelected;
    private boolean restoredBindingWaiting;
    private LiveBuildSharedPlacement groupPlacement;
    private java.util.function.BooleanSupplier groupValidity;
    private boolean replacingGroupPhase;
    private LiveBuildAssembly stagedGroupAssembly;
    public boolean canChangeGroupPhase(LiveBuildSharedPlacement remote){
        var local=identity();
        return remote!=null&&remote.tile()==selected&&!preparing()&&!selectedBound()&&local!=null
            &&remote.phase()!=selectedPhase()&&remote.dimension().equals(dimension)
            &&remote.origin().equals(local.origin())&&remote.transform().equals(local.transform())
            &&workspace.supportsPhase(remote);
    }
    public void changeGroupPhase(LiveBuildSharedPlacement remote,java.util.function.BooleanSupplier valid){
        if(!canChangeGroupPhase(remote)||!valid.getAsBoolean())throw new IllegalStateException("Phase changed");
        var request=workspace.stage(selected,remote.phase());
        preparingTile=selected;groupPlacement=remote;groupValidity=valid;failed=false;replacingGroupPhase=true;
        future=CompletableFuture.supplyAsync(()->{
            try{return workspace.prepare(request);}catch(java.io.IOException failure){throw new java.util.concurrent.CompletionException(failure);}
        },executor);
    }
    public void adoptGroupPlacement(LiveBuildSharedPlacement remote,java.util.function.BooleanSupplier stillValid) {
        cancelBackgroundPreparation();
        if(remote==null||remote.tile()!=selected||preparing()||identity()!=null||selectedBound()
            ||!dimension.equals(remote.dimension())||!stillValid.getAsBoolean())throw new IllegalStateException("Map cannot be adopted");
        var request=workspace.begin(selected,remote.phase());
        preparingTile=selected;groupPlacement=remote;groupValidity=stillValid;failed=false;grey(selected);
        future=CompletableFuture.supplyAsync(()->{
            try{return workspace.prepare(request);}catch(java.io.IOException failure){throw new java.util.concurrent.CompletionException(failure);}
        },executor);
    }
    private void cancelGroupPreparation() {
        if(future!=null)future.cancel(false);if(preparation!=null)preparation.close();if(placement!=null)placement.close();
        future=null;preparation=null;placement=null;prepared=null;
        if(replacingGroupPhase){
            workspace.cancelStage(preparingTile);
            if(stagedGroupAssembly!=null)stagedGroupAssembly.close();stagedGroupAssembly=null;
            replacingGroupPhase=false;groupPlacement=null;groupValidity=null;failed=true;return;
        }
        workspace.invalidate(preparingTile);projections[preparingTile]=null;dependencies[preparingTile]=null;
        var tile=workspace.catalog().tiles().get(preparingTile);
        displayParts[preparingTile]=new LiveBuildParts.Part(preparingTile,tile.column(),tile.row(),128,128,java.util.List.of(),"0".repeat(64));
        groupPlacement=null;groupValidity=null;failed=true;
    }
    public LiveBuildSessionStore.Snapshot snapshot(long time){
        if(interactivePreparing()||incompleteRestore)return null;
        var placed=new java.util.ArrayList<LiveBuildSessionStore.Placement>();
        for(int i=0;i<count();i++){
            var p=progress(i);
            if(p!=null)placed.add(new LiveBuildSessionStore.Placement(i,dormant[i]!=null?dormantPhases[i]:workspace.phase(i),i==boundTile,p.identity(),p.savedStates()));
        }
        return new LiveBuildSessionStore.Snapshot(LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP,
            workspace.catalog().bundleSha256(),world,dimension,width()/128,height()/128,selected,time,placed);
    }
    public void restore(LiveBuildSessionStore.Snapshot saved){
        if(restoreQueue!=null||saved.kind()!=LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP
            ||!workspace.catalog().bundleSha256().equals(saved.sourceSha256())||!world.equals(saved.worldHash())
            ||!dimension.equals(saved.dimension())||saved.gridWide()*128!=width()||saved.gridTall()*128!=height())
            throw new IllegalArgumentException("Stored bundle identity mismatch");
        for(int i=0;i<count();i++)if(progress(i)!=null)throw new IllegalStateException("Bundle already placed");
        restoreQueue=new java.util.ArrayDeque<>(saved.placements());restoreSelected=saved.selected();
    }
    public boolean selectedBound(){return boundSession!=null&&boundTile==selected;}
    public int selectedPhase(){return dormant[selected]!=null?dormantPhases[selected]:workspace.phase(selected);}
    public boolean canFollow(LiveBuildPhaseSession session){return matches(selected,session);}
    private boolean matches(int tile,LiveBuildPhaseSession session){
        if(session==null||!world.equals(session.worldKey())||!dimension.equals(session.dimension()))return false;
        var source=workspace.catalog().tiles().get(tile).bundle();
        if(session.phase() < -1 || session.phase()>=source.parsed().plan().phases().size())return false;
        return source.planSha256().equals(session.bundle().planSha256())&&source.litematicSha256().equals(session.bundle().litematicSha256());
    }
    public void follow(LiveBuildPhaseSession session){
        cancelBackgroundPreparation();
        if(preparing()||!canFollow(session))throw new IllegalStateException("Selected tile does not match session");
        boundTile=selected;startPhase(session);
    }
    /** Retry on the owner tick: selection cannot be applied during initial tile preparation. */
    public boolean followTile(int tile, LiveBuildPhaseSession session) {
        if (tile < 0 || tile >= count()) throw new IllegalArgumentException("Invalid bound tile");
        if (preparing()) return false;
        if (selected != tile) { select(tile); return false; }
        follow(session);
        return true;
    }
    private void startPhase(LiveBuildPhaseSession session){
        backgroundPreparation=false;
        forgetDormant(boundTile);
        boundSession=session;phaseInvalidated=false;failed=false;preparingTile=boundTile;
        grey(boundTile);preview=null;nextPreview=0;
        var request=workspace.begin(boundTile,session.phase());
        future=CompletableFuture.supplyAsync(()->{
            try{return workspace.prepare(request);}catch(java.io.IOException e){throw new java.util.concurrent.CompletionException(e);}
        },executor);
    }
    /** Main thread, before preparation/scanning: invalidate only bound tile; coalesce pending worker results. */
    public void updateSession(LiveBuildPhaseSession current){
        if(restoreQueue!=null)return;
        if(restoredBindingWaiting){
            if(preparing())return;
            if(!matches(boundTile,current))return;
            restoredBindingWaiting=false;startPhase(current);return;
        }
        if(boundSession==null||(!phaseInvalidated&&boundSession.equals(current)))return;
        if(!phaseInvalidated){
            forgetDormant(boundTile);workspace.invalidate(boundTile);grey(boundTile);preview=null;phaseInvalidated=true;
            var tile=workspace.catalog().tiles().get(boundTile);
            displayParts[boundTile]=new LiveBuildParts.Part(boundTile,tile.column(),tile.row(),128,128,java.util.List.of(),"0".repeat(64));
        }
        if(preparing()&&preparingTile!=boundTile)return;
        if(future!=null&&!future.isDone())return;
        if(preparation!=null)preparation.close();if(placement!=null)placement.close();
        future=null;preparation=null;placement=null;prepared=null;
        if(!matches(boundTile,current)){boundSession=null;boundTile=-1;phaseInvalidated=false;return;}
        startPhase(current);
    }
    public LiveBuildBundleRuntime(LiveBuildBundleWorkspace workspace,String world,String dimension,IntUnaryOperator colour){
        this(workspace,world,dimension,colour,java.util.concurrent.ForkJoinPool.commonPool());
    }
    LiveBuildBundleRuntime(LiveBuildBundleWorkspace workspace,String world,String dimension,IntUnaryOperator colour,
                           java.util.concurrent.Executor executor){
        this.executor=java.util.Objects.requireNonNull(executor);
        this.workspace=workspace;this.world=world;this.dimension=dimension;
        original=SuppressionPreviewPixels.assemble(workspace.catalog(),colour);pixels=original.clone();
        displayParts=new LiveBuildParts.Part[workspace.tileCount()];projections=new LiveBuildTopView[displayParts.length];
        orbitOverrides=new int[displayParts.length][];
        dormant=new LiveBuildProgress[displayParts.length];dormantPhases=new int[displayParts.length];
        dependencies=new short[displayParts.length][];
        scannedSinceLoad=new int[displayParts.length];
        for(int i=0;i<displayParts.length;i++){
            var tile=workspace.catalog().tiles().get(i);
            displayParts[i]=new LiveBuildParts.Part(i,tile.column(),tile.row(),128,128,java.util.List.of(),"0".repeat(64));
            grey(i);
        }
        select(0);
    }
    public int selected(){return selected;}
    public int count(){return displayParts.length;}
    public int width(){return workspace.width();}
    public int height(){return workspace.height();}
    public int[] pixels(){return pixels;}
    public long revision(){return revision;}
    public boolean failed(){return failed;}
    public boolean preparing(){return future!=null||preparation!=null||placement!=null||restoreQueue!=null;}
    public boolean interactivePreparing(){return preparing()&&!backgroundPreparation;}
    public LiveBuildParts.Part selectedMap(){return displayParts[selected];}
    private LiveBuildProgress progress(int tile){var a=workspace.assembly(tile);return dormant[tile]!=null?dormant[tile]:a==null?null:a.placement(0);}
    public LiveBuildProgress observationSource(int tile){return tile<0||tile>=count()?null:progress(tile);}
    public LiveBuildProgress.Identity identity(){var p=progress(selected);return p==null?null:p.identity();}
    public boolean matchesGroupPlacement(LiveBuildSharedPlacement remote) {
        if(remote==null||remote.tile()<0||remote.tile()>=count())return false;
        int tile=remote.tile();var p=progress(tile);
        return p!=null&&remote.matchesLocal(p.identity(),dormant[tile]!=null?dormantPhases[tile]:workspace.phase(tile),p.size());
    }
    public double completion(long now){
        double result=0;for(int i=0;i<count();i++){var s=tileSummary(i,now);if(s!=null)result+=s.completion();}
        return result/count();
    }
    private LiveBuildProgress.Summary tileSummary(int tile,long now){
        if(dormant[tile]!=null)return dormant[tile].liveSummary(now);
        var a=workspace.assembly(tile);return a==null?null:a.summary(now);
    }
    public LiveBuildProgress.Summary selectedSummary(long now){return tileSummary(selected,now);}
    public LiveBuildProgress.Summary summary(long now){
        int total=0,correct=0,missing=0,wrong=0,unknown=0,stale=0;
        for(int i=0;i<count();i++){
            var s=tileSummary(i,now);if(s==null)continue;
            total+=s.total();correct+=s.correct();missing+=s.missing();wrong+=s.wrong();unknown+=s.unknown();stale+=s.stale();
        }
        return new LiveBuildProgress.Summary(total,correct,missing,wrong,unknown,stale);
    }
    public void select(int index){
        cancelBackgroundPreparation();
        if(preparing())return;
        int next=Math.floorMod(index,count());
        if(next!=selected){
            var previous=workspace.assembly(selected);
            // Browsing must not retain every decoded map. Placed/bound maps keep their progress.
            if(previous!=null&&previous.placement(0)==null&&selected!=boundTile){
                workspace.invalidate(selected);projections[selected]=null;dependencies[selected]=null;
                var tile=workspace.catalog().tiles().get(selected);
                displayParts[selected]=new LiveBuildParts.Part(selected,tile.column(),tile.row(),128,128,java.util.List.of(),"0".repeat(64));
            }
        }
        selected=next;failed=false;
        backgroundPreparation=false;
        if(workspace.assembly(selected)!=null)return;
        if(dormant[selected]==null&&boundSession!=null&&selected==boundTile){startPhase(boundSession);return;}
        preparingTile=selected;
        var request=workspace.begin(selected,dormant[selected]==null?-1:dormantPhases[selected]);
        future=CompletableFuture.supplyAsync(()->{
            try{return workspace.prepare(request);}catch(java.io.IOException e){throw new java.util.concurrent.CompletionException(e);}
        },executor);
    }
    public void anchor(LiveBuildProgress.Position origin,LiveBuildTransform transform){
        cancelBackgroundPreparation();
        if(selectedBound())throw new IllegalStateException("Placement follows Two-layer session");
        anchorTile(selected,origin,transform);
    }
    public LiveBuildProgress.Position schematicOrigin(){
        var id=identity();var a=workspace.assembly(selected);
        if(id==null)return null;
        if(a==null)throw new IllegalStateException("Tile geometry unavailable");
        return a.parts().toSchematicOrigin(0,id.origin(),id.transform());
    }
    public void anchorSchematic(LiveBuildProgress.Position origin,LiveBuildTransform transform){
        var a=workspace.assembly(selected);
        if(a==null)throw new IllegalStateException("Tile geometry unavailable");
        anchor(a.parts().fromSchematicOrigin(0,origin,transform),transform);
    }
    private void anchorTile(int tile,LiveBuildProgress.Position origin,LiveBuildTransform transform){
        if(preparing()||workspace.assembly(tile)==null)throw new IllegalStateException("Tile unavailable");
        preparingTile=tile;var a=workspace.assembly(tile);var part=a.parts().part(0);
        int observations=part.cells().size();
        for(int i=0;i<count();i++)if(i!=tile&&progress(i)!=null)observations=Math.addExact(observations,progress(i).size());
        if(observations>LiveBuildSessionStore.MAX_BUNDLE_OBSERVATIONS)throw new IllegalStateException("Observation budget exceeded");
        placement=new LiveBuildPlacementPreparation(part,new LiveBuildProgress.Identity(part.targetSha256(),world,dimension,origin,++placementRevision,transform));
        a.remove(0);grey(tile);preview=null;nextPreview=0;
    }
    public void unanchor(){cancelBackgroundPreparation();if(preparing())throw new IllegalStateException("Tile is preparing");if(selectedBound())throw new IllegalStateException("Placement follows Two-layer session");forgetDormant(selected);var a=workspace.assembly(selected);if(a!=null)a.remove(0);grey(selected);preview=null;nextPreview=0;}
    private void cancelBackgroundPreparation(){
        if(!backgroundPreparation)return;
        backgroundPreparation=false;
        if(!preparing())return;
        if(future!=null)future.cancel(false);
        if(preparation!=null)preparation.close();
        if(placement!=null)placement.close();
        // Dormant observations own the placement until the new geometry is fully attached.
        workspace.invalidate(preparingTile);
        projections[preparingTile]=null;
        var tile=workspace.catalog().tiles().get(preparingTile);
        displayParts[preparingTile]=new LiveBuildParts.Part(preparingTile,tile.column(),tile.row(),128,128,java.util.List.of(),"0".repeat(64));
        future=null;preparation=null;placement=null;prepared=null;
    }
    private void forgetDormant(int tile){if(dormant[tile]!=null){dormant[tile].close();dormant[tile]=null;dormantCount--;}}
    private void makeRoom(int incoming){
        if(incoming>workspace.activeCellBudget())throw new IllegalArgumentException("Tile exceeds active geometry budget");
        while(workspace.activeCells()+incoming>workspace.activeCellBudget()){
            int victim=-1;
            for(int i=0;i<count();i++){
                int tile=(evictionCursor+i)%count();
                if(tile!=preparingTile&&(!backgroundPreparation||tile!=selected)&&workspace.assembly(tile)!=null){victim=tile;break;}
            }
            if(victim<0)throw new IllegalStateException("No geometry eviction candidate");
            var a=workspace.assembly(victim);var p=a.detach(0);
            if(p!=null){dormant[victim]=p;dormantCount++;dormantPhases[victim]=workspace.phase(victim);}
            else dependencies[victim]=null;
            workspace.invalidate(victim);projections[victim]=null;
            scannedSinceLoad[victim]=0;
            var tile=workspace.catalog().tiles().get(victim);
            displayParts[victim]=new LiveBuildParts.Part(victim,tile.column(),tile.row(),128,128,java.util.List.of(),"0".repeat(64));
            evictionCursor=(victim+1)%count();
        }
    }
    public void prepare(Function<LiveBuildProgress.State,LiveBuildProgress.State> resolver,
                        BiFunction<LiveBuildProgress.State,LiveBuildTransform,LiveBuildProgress.State> transformer){
        if(groupPlacement!=null&&!groupValidity.getAsBoolean()){cancelGroupPreparation();return;}
        if(phaseInvalidated&&preparingTile==boundTile)return;
        try{
            if(restoreQueue!=null&&future==null&&preparation==null&&placement==null){
                restoringPlacement=restoreQueue.poll();
                if(restoringPlacement==null){
                    restoreQueue=null;
                    // Apply normal browsing cleanup to the initially prepared, unplaced tile.
                    select(restoreSelected);
                    return;
                }
                preparingTile=restoringPlacement.tile();
                var request=workspace.begin(preparingTile,restoringPlacement.phase());
                future=CompletableFuture.supplyAsync(()->{
                    try{return workspace.prepare(request);}catch(java.io.IOException e){throw new java.util.concurrent.CompletionException(e);}
                },executor);
            }
            if(future!=null){
                if(!future.isDone())return;
                prepared=future.join();future=null;
                var source=prepared.target();
                preparation=new LiveBuildPreparation(source,new LiveBuildProgress.Identity(source.sha256(),world,dimension,new LiveBuildProgress.Position(0,0,0),0));
            }
            if(preparation!=null){
                var a=preparation.advance(resolver,1);if(a==null)return;
                if(replacingGroupPhase){
                    stagedGroupAssembly=a;
                    var part=a.parts().part(0);
                    if(!part.targetSha256().equals(groupPlacement.targetSha256())||part.cells().size()!=groupPlacement.cellCount())
                        throw new IllegalArgumentException("Shared map target changed");
                    int observations=part.cells().size();
                    for(int i=0;i<count();i++)if(i!=preparingTile&&progress(i)!=null)observations=Math.addExact(observations,progress(i).size());
                    if(observations>LiveBuildSessionStore.MAX_BUNDLE_OBSERVATIONS)throw new IllegalStateException("Observation budget exceeded");
                    placement=new LiveBuildPlacementPreparation(part,new LiveBuildProgress.Identity(part.targetSha256(),world,dimension,
                        groupPlacement.origin(),++placementRevision,groupPlacement.transform()));
                    preparation.close();preparation=null;
                    return;
                }
                makeRoom(a.parts().requiredCells());
                workspace.publish(prepared,a);preparation.close();preparation=null;
                scannedSinceLoad[preparingTile]=0;
                var p=a.parts().part(0);var tile=workspace.catalog().tiles().get(preparingTile);
                displayParts[preparingTile]=new LiveBuildParts.Part(preparingTile,tile.column(),tile.row(),128,128,p.cells(),p.targetSha256());
                projections[preparingTile]=prepared.projection();
                int[] heights=new int[16384];
                for(int pixel=0;pixel<16384;pixel++){int cell=prepared.projection().index(pixel);if(cell>=0)heights[pixel]=p.cells().get(cell).relativePosition().y();}
                orbitOverrides[preparingTile]=heights;orbitRevision=-1;
                dependencies[preparingTile]=prepared.pixelDependencies();
                prepared=null;nextPreview=0;
                if(groupPlacement!=null){
                    if(!p.targetSha256().equals(groupPlacement.targetSha256())||p.cells().size()!=groupPlacement.cellCount())
                        throw new IllegalArgumentException("Shared map target changed");
                    anchorTile(preparingTile,groupPlacement.origin(),groupPlacement.transform());
                }else if(restoringPlacement!=null){
                    if(!p.targetSha256().equals(restoringPlacement.identity().schematicSha256()))throw new IllegalArgumentException("Stored target changed");
                    placement=new LiveBuildPlacementPreparation(p,restoringPlacement.identity());
                }else if(dormant[preparingTile]!=null){
                    placement=new LiveBuildPlacementPreparation(p,dormant[preparingTile].identity());
                }else if(boundSession!=null&&preparingTile==boundTile)anchorTile(boundTile,
                    a.parts().fromSchematicOrigin(0,boundSession.origin(),LiveBuildTransform.NONE),LiveBuildTransform.NONE);
            }
            if(placement!=null){
                var p=placement.advance(transformer,1);if(p==null)return;
                if(replacingGroupPhase){
                    if(!groupValidity.getAsBoolean()){p.close();cancelGroupPreparation();return;}
                    var old=workspace.assembly(preparingTile);
                    makeRoom(Math.max(0,stagedGroupAssembly.parts().requiredCells()-(old==null?0:old.parts().requiredCells())));
                    stagedGroupAssembly.attach(0,p);
                    workspace.publish(prepared,stagedGroupAssembly);
                    var part=stagedGroupAssembly.parts().part(0);var tile=workspace.catalog().tiles().get(preparingTile);
                    displayParts[preparingTile]=new LiveBuildParts.Part(preparingTile,tile.column(),tile.row(),128,128,part.cells(),part.targetSha256());
                    projections[preparingTile]=prepared.projection();dependencies[preparingTile]=prepared.pixelDependencies();
                    int[] heights=new int[16384];
                    for(int pixel=0;pixel<16384;pixel++){int cell=prepared.projection().index(pixel);if(cell>=0)heights[pixel]=part.cells().get(cell).relativePosition().y();}
                    orbitOverrides[preparingTile]=heights;orbitRevision=-1;revision++;
                    forgetDormant(preparingTile);scannedSinceLoad[preparingTile]=0;grey(preparingTile);preview=null;nextPreview=0;
                    stagedGroupAssembly=null;prepared=null;replacingGroupPhase=false;
                    placement.close();placement=null;groupPlacement=null;groupValidity=null;
                    return;
                }
                if(restoringPlacement!=null){
                    p.restoreStates(restoringPlacement.identity(),restoringPlacement.states());
                    placementRevision=Math.max(placementRevision,p.identity().placementRevision());
                    if(restoringPlacement.followsTwoLayer()){
                        boundTile=preparingTile;restoredBindingWaiting=true;
                        boundSession=new LiveBuildPhaseSession(workspace.catalog().tiles().get(boundTile).bundle(),
                            restoringPlacement.phase(),world,dimension,workspace.assembly(preparingTile).parts().toSchematicOrigin(
                                0,p.identity().origin(),p.identity().transform()));
                        p.close();
                    }
                    restoringPlacement=null;
                }
                if(dormant[preparingTile]!=null){
                    var retained=dormant[preparingTile];retained.resumeGeometry(p);p=retained;dormant[preparingTile]=null;dormantCount--;
                }
                workspace.assembly(preparingTile).attach(0,p);placement.close();placement=null;nextPreview=0;
                groupPlacement=null;groupValidity=null;
                if(backgroundPreparation)priorityTile=preparingTile;
            }
        }catch(RuntimeException|java.io.IOException failure){
            if(groupPlacement!=null){cancelGroupPreparation();return;}
            // Never replace a saved multi-map session with a partially restored subset.
            if(restoreQueue!=null)incompleteRestore=true;
            if(preparation!=null)preparation.close();if(placement!=null)placement.close();
            future=null;preparation=null;placement=null;prepared=null;failed=true;
            restoreQueue=null;restoringPlacement=null;
        }
    }
    public void scan(LiveBuildProgress.WorldReader reader,int budget,long now){
        if(budget<0||budget>LiveBuildProgress.MAX_SCAN_BUDGET||now<0)throw new IllegalArgumentException("Invalid scan budget or clock");
        if(restoreQueue!=null)return;
        var previousPriority=priorityTile<0?null:workspace.assembly(priorityTile);
        if(previousPriority==null||previousPriority.placement(0)==null
            ||scannedSinceLoad[priorityTile]>=previousPriority.placement(0).size())priorityTile=-1;
        if(priorityTile<0){
            // First residency must also finish maps in order, so eviction follows observation age.
            for(int i=0;i<count();i++){
                int tile=(scanTile+i)%count();var a=workspace.assembly(tile);var p=a==null?null:a.placement(0);
                if(p!=null&&!(tile==boundTile&&restoredBindingWaiting)&&scannedSinceLoad[tile]<p.size()){
                    priorityTile=tile;scanTile=(tile+1)%count();break;
                }
            }
        }
        var priority=priorityTile<0?null:workspace.assembly(priorityTile);
        if(priority!=null&&priority.placement(0)!=null&&scannedSinceLoad[priorityTile]<priority.placement(0).size()){
            // Finish the incoming page promptly; reserve one eighth of reads for the selected map.
            int tile=(scanShare++&7)==0&&workspace.assembly(selected)!=null
                &&workspace.assembly(selected).placement(0)!=null?selected:priorityTile;
            scanAssembly(tile,reader,budget,now);pageDormant(now);return;
        }
        priorityTile=-1;
        for(int i=0;i<count();i++){
            int tile=scanTile;scanTile=(scanTile+1)%count();var a=workspace.assembly(tile);
            if(a!=null&&a.placement(0)!=null){
                scanAssembly(tile,reader,budget,now);
                pageDormant(now);return;
            }
        }
        pageDormant(now);
    }
    private void scanAssembly(int tile,LiveBuildProgress.WorldReader reader,int budget,long now){
        var a=workspace.assembly(tile);int reads=a.scan(reader,budget,now);
        scannedSinceLoad[tile]=Math.min(a.placement(0).size(),scannedSinceLoad[tile]+reads);
    }
    private void pageDormant(long now){
        if(dormantCount==0||preparing()||failed||incompleteRestore||now<nextResidency)return;
        // Never evict a freshly loaded scan target before it has had one complete bounded scan pass.
        for(int i=0;i<count();i++){
            var a=workspace.assembly(i);var p=a==null?null:a.placement(0);
            if(p!=null&&!(i==boundTile&&restoredBindingWaiting)&&scannedSinceLoad[i]<p.size())return;
        }
        var selectedAssembly=workspace.assembly(selected);
        int pinned=selectedAssembly==null?0:selectedAssembly.parts().requiredCells();
        for(int i=0;i<count();i++){
            int tile=(residencyCursor+i)%count();var p=dormant[tile];
            if(p==null||(tile==boundTile&&restoredBindingWaiting)||pinned+p.size()>workspace.activeCellBudget())continue;
            preparingTile=tile;backgroundPreparation=true;residencyCursor=(tile+1)%count();nextResidency=now+1000;
            var request=workspace.begin(tile,dormantPhases[tile]);
            future=CompletableFuture.supplyAsync(()->{
                try{return workspace.prepare(request);}catch(java.io.IOException e){throw new java.util.concurrent.CompletionException(e);}
            },executor);
            return;
        }
    }
    public void preview(int budget,long now){
        if(preview==null){
            if(now<nextPreview)return;
            for(int i=0;i<count();i++){
                previewTile=(previewTile+1)%count();var p=progress(previewTile);
                if(p!=null&&dependencies[previewTile]!=null){preview=new LiveBuildPreviewCheck(p,dependencies[previewTile]);break;}
            }
            if(preview==null){nextPreview=now+1000;return;}
        }
        try{
            var result=preview.advance(budget,now);if(result==null)return;
            var tile=workspace.catalog().tiles().get(previewTile);
            boolean changed=false;
            for(int p=0;p<16384;p++){
                int dest=(tile.row()*128+p/128)*width()+tile.column()*128+p%128;
                int rgb=original[dest];int gray=gray(rgb);
                int next=rgb==0?0:switch(result[p]){
                    case CORRECT->rgb;case WRONG->0xffc74e59;case STALE->0xff776e4b;
                    case MISSING->0xff000000|(gray/3)*0x010101;case UNKNOWN->0xff000000|(gray/2)*0x010101;
                };
                if(pixels[dest]!=next){pixels[dest]=next;changed=true;}
            }
            if(changed)revision++;
            preview=null;nextPreview=now+Math.max(10,1000/count());
        }catch(java.util.concurrent.CancellationException changed){preview=null;nextPreview=0;}
    }
    private static int gray(int rgb){return (((rgb>>16)&255)*3+((rgb>>8)&255)*6+(rgb&255))/10;}
    private void grey(int index){
        var tile=workspace.catalog().tiles().get(index);
        boolean changed=false;
        for(int p=0;p<16384;p++){
            int dest=(tile.row()*128+p/128)*width()+tile.column()*128+p%128;
            int next=original[dest]==0?0:0xff000000|(gray(original[dest])/2)*0x010101;
            if(pixels[dest]!=next){pixels[dest]=next;changed=true;}
        }
        if(changed)revision++;
    }
    @Override public void close(){
        if(stagedGroupAssembly!=null)stagedGroupAssembly.close();stagedGroupAssembly=null;
        orbitClosed=true;if(orbitHeights!=null)orbitHeights.cancel(false);orbitSurface=null;
        groupPlacement=null;groupValidity=null;
        if(future!=null)future.cancel(false);if(preparation!=null)preparation.close();if(placement!=null)placement.close();
        future=null;preparation=null;placement=null;workspace.close();
        for(int i=0;i<count();i++){forgetDormant(i);dependencies[i]=null;projections[i]=null;displayParts[i]=null;}
        restoreQueue=null;restoringPlacement=null;
    }
}
