package art.mapkluss.companion;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildBundleRuntimeTest {
    private final byte[] groupFixture=SuppressionTestFixtures.multiZipBytes();
    LiveBuildBundleRuntimeTest() throws Exception { }
    @Test void schematicOriginRoundTripsAndChecksOriginalSourceCoordinates() throws Exception {
        try(var w=LiveBuildBundleWorkspace.read(groupFixture)){
            var bundle=w.catalog().tiles().get(0).bundle();
            var source=LiveBuildPhaseTarget.read(bundle.planBytes(),bundle.litematicBytes(),-1);
            var blocks=new java.util.HashMap<LiveBuildProgress.Position,LiveBuildProgress.State>();
            var origin=new LiveBuildProgress.Position(100,64,200);
            for(var c:source.cells())blocks.put(c.relativePosition().plus(origin),c.expected());
            try(var runtime=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(groupFixture),"test","overworld",i->0xff909090)){
                ready(runtime);runtime.anchorSchematic(origin,LiveBuildTransform.NONE);ready(runtime);
                assertEquals(origin,runtime.schematicOrigin());
                for(int i=0;i<600;i++)runtime.scan(p->blocks.getOrDefault(p,new LiveBuildProgress.State("minecraft:air",Map.of())),128,10);
                assertEquals(runtime.selectedSummary(10).total(),runtime.selectedSummary(10).correct());
            }
        }
    }
    private LiveBuildSharedPlacement remoteMap(int tile,int phase) throws Exception {
        try(var w=LiveBuildBundleWorkspace.read(groupFixture)){
            var prepared=w.prepare(w.begin(tile,phase));var p=new LiveBuildParts(prepared.target()).part(0);
            return new LiveBuildSharedPlacement(tile,4,p.targetSha256(),phase,p.cells().size(),
                "11111111-1111-4111-8111-111111111111","minecraft:overworld",
                new LiveBuildProgress.Position(500,64,900),new LiveBuildTransform(1,true));
        }
    }
    @Test void sharedPhaseAdoptionPreservesNeighbourAndStartsUnknown() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            try(var r=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(groupFixture),
                "a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(r);r.anchor(new LiveBuildProgress.Position(0,64,0),LiveBuildTransform.NONE);ready(r);
                var neighbour=r.identity();r.select(1);ready(r);
                var remote=remoteMap(1,0);r.adoptGroupPlacement(remote,()->true);ready(r);
                assertFalse(r.failed());assertEquals(0,r.selectedPhase());assertTrue(r.matchesGroupPlacement(remote));
                assertEquals("a".repeat(64),r.identity().worldKey());assertEquals(remote.origin(),r.identity().origin());
                assertEquals(remote.transform(),r.identity().transform());assertEquals(0,r.selectedSummary(0).correct());
                assertEquals(remote.cellCount(),r.selectedSummary(0).unknown());assertEquals(2,r.snapshot(1).placements().size());
                assertEquals(256*128,r.pixels().length);r.select(0);ready(r);assertEquals(neighbour,r.identity());
            }
        });
    }
    @Test void explicitSharedPhaseChangeKeepsNeighbourAndRejectsInvalidTarget() throws Exception {
        try(var r=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(groupFixture),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
            ready(r);r.anchor(new LiveBuildProgress.Position(0,64,0),LiveBuildTransform.NONE);ready(r);
            var neighbour=r.identity();r.select(1);ready(r);r.adoptGroupPlacement(remoteMap(1,-1),()->true);ready(r);
            var before=r.identity();var next=remoteMap(1,0);
            assertTrue(r.canChangeGroupPhase(next));
            assertThrows(IllegalStateException.class,()->r.changeGroupPhase(next,()->false));assertEquals(before,r.identity());
            r.changeGroupPhase(next,()->true);ready(r);assertFalse(r.failed());assertTrue(r.matchesGroupPlacement(next));
            assertEquals(0,r.selectedSummary(0).correct());r.select(0);ready(r);assertEquals(neighbour,r.identity());
        }
    }
    @Test void revokedPendingAdoptionCannotAttachAndRetainsNeighbour() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            try(var r=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(groupFixture),
                "a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(r);r.anchor(new LiveBuildProgress.Position(0,64,0),LiveBuildTransform.NONE);ready(r);
                var neighbour=r.identity();r.select(1);ready(r);
                var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
                r.adoptGroupPlacement(remoteMap(1,0),allowed::get);allowed.set(false);
                while(r.preparing()){r.prepare(s->s,(s,t)->s);Thread.sleep(1);}
                assertNull(r.identity());assertTrue(r.failed());assertEquals(1,r.snapshot(1).placements().size());
                r.select(0);ready(r);assertEquals(neighbour,r.identity());
            }
        });
    }
    @Test void failedSharedPhaseReplacementKeepsCommittedMapAndObservations() throws Exception {
        for(int failureMode=0;failureMode<3;failureMode++){
            final int mode=failureMode;
            try(var r=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(groupFixture),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(r);r.adoptGroupPlacement(remoteMap(0,-1),()->true);ready(r);
                for(int i=0;i<600;i++)r.scan(p->stone,32,0);
                var identity=r.identity();var source=r.observationSource(0);var states=source.savedStates();
                var next=remoteMap(0,0);var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
                if(mode==0)next=new LiveBuildSharedPlacement(next.tile(),next.revision(),"f".repeat(64),next.phase(),next.cellCount(),
                    next.worldBinding(),next.dimension(),next.origin(),next.transform());
                r.changeGroupPhase(next,allowed::get);
                assertSame(source,r.observationSource(0));assertEquals(-1,r.selectedPhase());
                int ticks=0;
                while(r.preparing()){
                    assertTrue(ticks++<10000);
                    r.prepare(s->s,(s,t)->{
                        if(mode==1)throw new IllegalArgumentException("Unresolvable replacement");
                        if(mode==2)allowed.set(false);
                        return s;
                    });
                    Thread.sleep(1);
                }
                assertTrue(r.failed());assertEquals(identity,r.identity());assertSame(source,r.observationSource(0));
                assertArrayEquals(states,source.savedStates());assertEquals(-1,r.selectedPhase());
                assertEquals(identity,r.snapshot(1).placements().getFirst().identity());
                r.changeGroupPhase(remoteMap(0,0),()->true);ready(r);
                assertEquals(0,r.selectedPhase());assertNotEquals(identity,r.identity());
            }
        }
    }
    @Test void revocationAfterPhaseDecodeButBeforePlacementCommitStillCancels() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var w=LiveBuildBundleWorkspace.read(groupFixture);
            try(var r=new LiveBuildBundleRuntime(w,"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(r);r.select(1);ready(r);var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
                r.adoptGroupPlacement(remoteMap(1,0),allowed::get);
                while(w.assembly(1)==null){r.prepare(s->s,(s,t)->s);Thread.sleep(1);}
                assertTrue(r.preparing());assertNull(r.identity());allowed.set(false);
                r.prepare(s->s,(s,t)->s);
                assertFalse(r.preparing());assertNull(r.identity());assertNull(w.assembly(1));assertTrue(r.failed());
            }
        });
    }
    @Test void sharedWrongTargetFailsWithoutPlacementAndExistingLocalMapCannotBeOverwritten() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            try(var r=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(groupFixture),
                "a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(r);r.anchor(new LiveBuildProgress.Position(0,64,0),LiveBuildTransform.NONE);ready(r);
                var existing=r.identity();assertThrows(IllegalStateException.class,()->r.adoptGroupPlacement(remoteMap(0,0),()->true));
                assertEquals(existing,r.identity());r.select(1);ready(r);var valid=remoteMap(1,0);
                var wrong=new LiveBuildSharedPlacement(1,4,"f".repeat(64),0,valid.cellCount(),valid.worldBinding(),valid.dimension(),valid.origin(),valid.transform());
                r.adoptGroupPlacement(wrong,()->true);
                while(r.preparing()){r.prepare(s->s,(s,t)->s);Thread.sleep(1);}
                assertTrue(r.failed());assertNull(r.identity());
                assertEquals(1,r.snapshot(1).placements().size());r.select(0);ready(r);assertEquals(existing,r.identity());
            }
        });
    }
    @Test void dormantBoundPhaseIsInvalidatedWithoutLosingItsNeighbour() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes(),20000);
            try(var runtime=new LiveBuildBundleRuntime(workspace,"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(runtime);var bundle=workspace.catalog().tiles().get(0).bundle();
                var session=new LiveBuildPhaseSession(bundle,-1,"a".repeat(64),"minecraft:overworld",new LiveBuildProgress.Position(0,0,0));
                runtime.follow(session);ready(runtime);var old=runtime.identity();
                for(int i=0;i<600;i++)runtime.scan(p->stone,32,0);
                runtime.select(1);ready(runtime);assertNull(workspace.assembly(0));
                runtime.anchor(new LiveBuildProgress.Position(1000,0,0),LiveBuildTransform.NONE);ready(runtime);
                var neighbour=runtime.identity();
                runtime.updateSession(new LiveBuildPhaseSession(bundle,0,session.worldKey(),session.dimension(),session.origin()));ready(runtime);
                assertEquals(neighbour,runtime.identity());
                runtime.select(0);ready(runtime);assertTrue(runtime.selectedBound());assertNotEquals(old,runtime.identity());
                assertEquals(0,runtime.selectedSummary(0).correct());
                runtime.updateSession(null);assertNull(runtime.identity());
                assertEquals(1,runtime.snapshot(1).placements().size());
                runtime.select(1);ready(runtime);assertEquals(neighbour,runtime.identity());
            }
        });
    }
    @Test void compactDependenciesMatchOriginalStructuralPreviewIncludingRemovalAndExpiry() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            try(var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes())){
                var prepared=workspace.prepare(workspace.begin(0,0));
                var assembly=new LiveBuildAssembly(new LiveBuildParts(prepared.target()));var part=assembly.parts().part(0);
                var identity=new LiveBuildProgress.Identity(part.targetSha256(),"world","dimension",new LiveBuildProgress.Position(0,0,0),0);
                var progress=new LiveBuildProgress(identity,part.cells());assembly.attach(0,progress);
                var air=new LiveBuildProgress.State("minecraft:air",Map.of());
                for(int i=0;i<10;i++)progress.scan(identity,p->p.x()%3==0?null:p.x()%3==1?stone:air,4096,0);
                for(long now:new long[]{0,200000}){
                    var original=new LiveBuildPreviewCheck(assembly,prepared.projection());
                    var compact=new LiveBuildPreviewCheck(progress,prepared.pixelDependencies());
                    LiveBuildProgress.Status[] a=null,b=null;
                    while(a==null)a=original.advance(32,now);
                    while(b==null)b=compact.advance(32,now);
                    assertArrayEquals(a,b);
                }
            }
        });
    }
    @Test void backgroundPagingRevisitsDormantMapsWithoutChangingSelection() {
        assertTimeoutPreemptively(Duration.ofSeconds(20),()->{
            var source=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes()).catalog();
            var tiles=new java.util.ArrayList<SuppressionBundleCatalog.Tile>();
            for(int i=0;i<3;i++)tiles.add(new SuppressionBundleCatalog.Tile(String.format(java.util.Locale.ROOT,"tile_%03d",i+1),i+1,i,0,source.tiles().get(0).bundle()));
            byte[] zip=LiveBuildCatalogSource.encode(new SuppressionBundleCatalog(null,null,"fixture","fixture",null,3,1,tiles));
            LiveBuildSessionStore.Snapshot saved;
            var workspace=LiveBuildBundleWorkspace.read(zip,40000);
            try(var runtime=new LiveBuildBundleRuntime(workspace,"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(runtime);
                for(int i=0;i<3;i++){
                    runtime.select(i);ready(runtime);runtime.anchor(new LiveBuildProgress.Position(i*1000,0,0),LiveBuildTransform.NONE);ready(runtime);
                }
                boolean[] observed=new boolean[3];
                boolean preempted=false;
                for(int tick=0;tick<30;tick++){
                    for(int i=0;i<20&&!runtime.preparing();i++)runtime.scan(p->{observed[p.x()/1000]=true;return stone;},4096,tick*1000L);
                    if(runtime.preparing()){
                        assertFalse(runtime.interactivePreparing(),"Background load must not disable ordinary controls");
                        assertEquals(3,runtime.snapshot(1).placements().size(),"World exit during background load must preserve all observations");
                        if(!preempted){
                            while(workspace.assembly(0)==null&&runtime.preparing()){
                                runtime.prepare(s->s,(s,t)->s);Thread.sleep(1);
                            }
                            assertNotNull(workspace.assembly(0));assertTrue(runtime.preparing());
                            var before=runtime.snapshot(1);runtime.select(2);preempted=true;
                            assertFalse(runtime.preparing());
                            assertNull(workspace.assembly(0),"Cancelled partial geometry must be released");
                            var after=runtime.snapshot(1);
                            for(int part=0;part<3;part++){
                                assertEquals(before.placements().get(part).identity(),after.placements().get(part).identity());
                                assertArrayEquals(before.placements().get(part).states(),after.placements().get(part).states());
                            }
                        }
                    }
                    ready(runtime);assertEquals(2,runtime.selected());
                    assertNotNull(workspace.assembly(2),"Background paging must keep selected geometry resident");
                    assertTrue(workspace.activeCells()<=40000);
                }
                assertTrue(observed[0]&&observed[1]&&observed[2]);assertEquals(1,runtime.completion(30000));
                assertTrue(preempted);
                saved=runtime.snapshot(1);assertEquals(3,saved.placements().size());
                runtime.anchor(new LiveBuildProgress.Position(2222,0,0),LiveBuildTransform.NONE);
                assertTrue(runtime.interactivePreparing(),"Explicit placement must still block until prepared");ready(runtime);
            }
            try(var restored=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(zip,40000),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                restored.restore(saved);ready(restored);assertEquals(2,restored.selected());
                assertEquals(3,restored.snapshot(2).placements().size());assertEquals(0,restored.completion(0));
                assertEquals(restored.summary(0).total(),restored.summary(0).stale());
                for(int i=0;i<3;i++){
                    restored.select(i);ready(restored);
                    assertEquals(saved.placements().get(i).identity(),restored.identity());
                }
            }
        });
    }
    @Test void partialRestoreFailureCannotOverwriteCompleteSavedSession() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            byte[] source=SuppressionTestFixtures.multiZipBytes();LiveBuildSessionStore.Snapshot saved;
            try(var first=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(first);first.anchor(new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE);ready(first);
                first.select(1);ready(first);first.anchor(new LiveBuildProgress.Position(128,0,0),LiveBuildTransform.NONE);ready(first);
                saved=first.snapshot(1);
            }
            var placements=new java.util.ArrayList<>(saved.placements());
            var old=placements.get(1);var id=old.identity();
            placements.set(1,new LiveBuildSessionStore.Placement(1,old.phase(),false,
                new LiveBuildProgress.Identity("f".repeat(64),id.worldKey(),id.dimension(),id.origin(),id.placementRevision(),id.transform()),old.states()));
            saved=new LiveBuildSessionStore.Snapshot(saved.kind(),saved.sourceSha256(),saved.worldHash(),saved.dimension(),
                saved.gridWide(),saved.gridTall(),saved.selected(),saved.savedAt(),placements);
            try(var second=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source,20000),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                second.restore(saved);
                while(second.preparing()){second.prepare(s->s,(s,t)->s);Thread.sleep(1);}
                assertTrue(second.failed());assertNull(second.snapshot(2));
                second.select(0);ready(second);assertNull(second.snapshot(3));
                assertEquals(2,saved.placements().size());
            }
        });
    }
    @Test void evictionKeepsIndependentObservationsAndReloadsExactPlacement() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes(),20000);
            try(var runtime=new LiveBuildBundleRuntime(workspace,"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(runtime);runtime.anchor(new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE);ready(runtime);
                for(int i=0;i<600;i++)runtime.scan(p->stone,32,0);
                var placed=runtime.identity();runtime.select(1);ready(runtime);
                assertFalse(runtime.failed());assertNull(workspace.assembly(0));assertNotNull(workspace.assembly(1));
                var saved=LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(runtime.snapshot(1)));
                assertEquals(1,saved.placements().size());assertEquals(placed,saved.placements().getFirst().identity());
                assertEquals(16385,runtime.summary(0).correct());
                assertEquals(.5,runtime.completion(0));
                for(int i=0;i<1200;i++)runtime.preview(32,0);
                assertEquals(0xff90c040,runtime.pixels()[0],"Dormant geometry must still render verified colours");
                runtime.select(0);ready(runtime);assertFalse(runtime.failed());assertEquals(placed,runtime.identity());
                assertEquals(16385,runtime.summary(0).correct());
                for(int i=0;i<600;i++)runtime.scan(p->null,32,1);
                assertEquals(0,runtime.summary(1).correct());assertEquals(16385,runtime.summary(1).stale());
            }
        });
    }
    @Test void restoredBindingWaitsForUnplacedSelectionPreparation() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            byte[] source=SuppressionTestFixtures.multiZipBytes();
            var workspace=LiveBuildBundleWorkspace.read(source);
            var session=new LiveBuildPhaseSession(workspace.catalog().tiles().get(0).bundle(),-1,
                "a".repeat(64),"minecraft:overworld",new LiveBuildProgress.Position(0,0,0));
            LiveBuildSessionStore.Snapshot saved;
            try(var first=new LiveBuildBundleRuntime(workspace,session.worldKey(),session.dimension(),i->0xff90c040)) {
                ready(first);first.follow(session);ready(first);first.select(1);ready(first);saved=first.snapshot(1);
            }
            var tasks=new java.util.ArrayDeque<Runnable>();
            try(var second=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source),session.worldKey(),session.dimension(),i->0xff90c040,tasks::add)) {
                second.restore(saved);
                for(int tick=0;tick<2000;tick++) {
                    second.updateSession(session);
                    assertTrue(tasks.size()<=1,"Rebinding must not overwrite another tile's pending preparation");
                    if(!tasks.isEmpty())tasks.remove().run();
                    for(int i=0;i<64&&second.preparing();i++)second.prepare(s->s,(s,t)->s);
                    if(tick>5&&!second.preparing()&&!second.selectedMap().empty())break;
                    Thread.sleep(1);
                }
                assertFalse(second.failed());assertEquals(1,second.selected());
                assertFalse(second.selectedMap().empty());assertNull(second.identity());
                second.select(0);ready(second);assertTrue(second.selectedBound());assertNotNull(second.identity());
            }
        });
    }
    @Test void secondInitialPhaseRestoresWhileLiveSessionTicks() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            byte[] source=SuppressionTestFixtures.multiZipBytes();
            var workspace=LiveBuildBundleWorkspace.read(source);
            var session=new LiveBuildPhaseSession(workspace.catalog().tiles().get(1).bundle(),-1,
                "a".repeat(64),"minecraft:overworld",new LiveBuildProgress.Position(0,0,0));
            LiveBuildSessionStore.Snapshot saved;
            try(var first=new LiveBuildBundleRuntime(workspace,session.worldKey(),session.dimension(),i->0xff90c040)) {
                ready(first);first.select(1);ready(first);first.follow(session);ready(first);
                for(int i=0;i<1200;i++)first.scan(p->stone,32,0);
                saved=LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(first.snapshot(123)));
            }
            try(var second=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source),session.worldKey(),session.dimension(),i->0xff90c040)) {
                second.restore(saved);
                int[] reads={0};
                for(int tick=0;tick<2000&&reads[0]==0;tick++) {
                    second.updateSession(session);
                    for(int i=0;i<64&&second.preparing();i++)second.prepare(s->s,(s,t)->s);
                    second.scan(p->{reads[0]++;return stone;},32,tick);
                    Thread.sleep(1);
                }
                assertFalse(second.failed());assertEquals(1,second.selected());
                assertTrue(second.selectedBound());assertTrue(reads[0]>0,"Bound restart must resume observations");
                assertEquals(second.selectedSummary(2000).total(),second.summary(2000).total(),
                    "Unplaced startup tile must not inflate restored verification counters");
                assertEquals(32768,second.pixels().length,"Cleanup must preserve the whole artwork");
            }
        });
    }
    @Test void directFollowSelectsExactSecondTileAfterInitialPreparation() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            var session=new LiveBuildPhaseSession(workspace.catalog().tiles().get(1).bundle(),-1,
                "a".repeat(64),"minecraft:overworld",new LiveBuildProgress.Position(0,0,0));
            try(var runtime=new LiveBuildBundleRuntime(workspace,session.worldKey(),session.dimension(),i->0xff90c040)) {
                assertFalse(runtime.followTile(1,session));
                ready(runtime);
                assertFalse(runtime.followTile(1,session));
                ready(runtime);
                assertTrue(runtime.followTile(1,session));
                ready(runtime);
                assertEquals(1,runtime.selected());assertTrue(runtime.selectedBound());
                assertEquals(1,runtime.snapshot(123).placements().getFirst().tile());
                assertEquals(32768,runtime.pixels().length);
                runtime.select(0);ready(runtime);assertNull(runtime.identity());
            }
        });
    }
    @Test void storedWholeBundleRestoresIndependentTargetsAsStale() {
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            byte[] source=SuppressionTestFixtures.multiZipBytes();LiveBuildSessionStore.Snapshot saved;
            try(var first=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                ready(first);first.anchor(new LiveBuildProgress.Position(100,64,100),LiveBuildTransform.NONE);ready(first);
                first.select(1);ready(first);first.anchor(new LiveBuildProgress.Position(800,64,800),new LiveBuildTransform(1,true));ready(first);
                for(int i=0;i<1200;i++)first.scan(p->stone,32,10000);
                assertEquals(1,first.completion(10000));saved=LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(first.snapshot(123)));
            }
            try(var second=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source),"a".repeat(64),"minecraft:overworld",i->0xff90c040)){
                second.restore(saved);ready(second);assertEquals(1,second.selected());
                assertEquals(saved.placements().get(1).identity(),second.identity());
                assertEquals(0,second.completion(0));assertEquals(second.summary(0).total(),second.summary(0).stale());
                second.select(0);assertEquals(saved.placements().get(0).identity(),second.identity());
                for(int i=0;i<1200;i++)second.scan(p->stone,32,0);
                assertEquals(1,second.completion(0));
            }
        });
    }
    @Test void restoredBoundTileWaitsForMatchingLiveTwoLayerSession(){
        assertTimeoutPreemptively(Duration.ofSeconds(15),()->{
            byte[] source=SuppressionTestFixtures.multiZipBytes();LiveBuildSessionStore.Snapshot saved;
            var workspace=LiveBuildBundleWorkspace.read(source);
            var session=new LiveBuildPhaseSession(workspace.catalog().tiles().get(0).bundle(),0,"a".repeat(64),"minecraft:overworld",new LiveBuildProgress.Position(0,0,0));
            try(var first=new LiveBuildBundleRuntime(workspace,session.worldKey(),session.dimension(),i->0xff90c040)){
                ready(first);first.follow(session);ready(first);saved=first.snapshot(123);
            }
            try(var second=new LiveBuildBundleRuntime(LiveBuildBundleWorkspace.read(source),session.worldKey(),session.dimension(),i->0xff90c040)){
                second.restore(saved);ready(second);second.updateSession(null);
                assertTrue(second.selectedBound());assertNotNull(second.identity());
                second.scan(p->{fail("Restored bound phase cannot read until active session matches");return stone;},32,0);
                assertNotNull(second.snapshot(124));
                second.updateSession(session);ready(second);
                int[] reads={0};second.scan(p->{reads[0]++;return stone;},32,0);assertEquals(32,reads[0]);
            }
        });
    }
    @Test void browsingReleasesUnplacedGeometryButRetainsPlacedProgress(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            try(var runtime=new LiveBuildBundleRuntime(workspace,"test","overworld",i->0xff90c040)){
                ready(runtime);
                for(int cycle=0;cycle<4;cycle++){
                    int previous=runtime.selected();runtime.select(1-previous);ready(runtime);
                    assertNull(workspace.assembly(previous));
                    assertNotNull(workspace.assembly(runtime.selected()));
                    assertEquals(32768,runtime.pixels().length);
                    runtime.scan(p->{fail("Browsing cannot scan world");return stone;},32,0);
                }
                runtime.anchor(new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE);ready(runtime);
                for(int i=0;i<600;i++)runtime.scan(p->stone,32,0);
                var retained=workspace.assembly(0);runtime.select(1);ready(runtime);
                assertSame(retained,workspace.assembly(0));assertEquals(.5,runtime.completion(0));
                runtime.select(0);assertNull(workspace.assembly(1));
                assertFalse(runtime.preparing());assertEquals(1,runtime.selectedSummary(0).completion());
                runtime.unanchor();runtime.select(1);ready(runtime);
                assertNull(workspace.assembly(0));assertEquals(0,runtime.completion(0));
            }
        });
    }
    @Test void unchangedPreviewDoesNotTriggerTextureUploadButStalenessDoes(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            try(var runtime=new LiveBuildBundleRuntime(workspace,"test","overworld",i->0xff90c040)){
                ready(runtime);runtime.anchor(new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE);ready(runtime);
                for(int i=0;i<600;i++)runtime.scan(p->stone,32,0);
                for(int i=0;i<600;i++)runtime.preview(32,0);
                long stable=runtime.revision();int[] expected=runtime.pixels().clone();
                for(int cycle=1;cycle<=20;cycle++)for(int i=0;i<600;i++)runtime.preview(32,cycle*1000L);
                assertEquals(stable,runtime.revision());assertArrayEquals(expected,runtime.pixels());
                for(int i=0;i<600;i++)runtime.preview(32,300000);
                assertTrue(runtime.revision()>stable);assertFalse(java.util.Arrays.equals(expected,runtime.pixels()));
                runtime.unanchor();long detached=runtime.revision();runtime.unanchor();
                assertEquals(detached,runtime.revision());
            }
        });
    }
    @Test void activePhaseBindsExplicitTileAndPreservesNeighbour(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            try(var runtime=new LiveBuildBundleRuntime(workspace,"test","overworld",i->0xff90c040)){
                ready(runtime);runtime.anchor(new LiveBuildProgress.Position(100,64,100),LiveBuildTransform.NONE);ready(runtime);
                var first=runtime.identity();runtime.select(1);ready(runtime);
                var bundle=workspace.catalog().tiles().get(0).bundle();
                var session=new LiveBuildPhaseSession(bundle,0,"test","overworld",new LiveBuildProgress.Position(700,64,700));
                assertTrue(runtime.canFollow(session));runtime.follow(session);ready(runtime);
                assertTrue(runtime.selectedBound());assertEquals(session.origin(),runtime.schematicOrigin());
                assertEquals(256,workspace.assembly(1).parts().source().cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
                assertEquals(first,workspace.assembly(0).placement(0).identity());
                var previous=runtime.identity();
                var next=new LiveBuildPhaseSession(bundle,1,"test","overworld",new LiveBuildProgress.Position(800,64,800));
                runtime.updateSession(next);ready(runtime);
                assertNotEquals(previous,runtime.identity());assertEquals(next.origin(),runtime.schematicOrigin());
                assertEquals(512,workspace.assembly(1).parts().source().cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
                runtime.select(0);assertFalse(runtime.selectedBound());assertEquals(first,runtime.identity());
                runtime.updateSession(null);assertNotNull(workspace.assembly(0));assertNull(workspace.assembly(1));
                assertEquals(32768,runtime.pixels().length);
            }
        });
    }
    @Test void coalescesInFlightPhaseChangesAndRejectsOtherWorld(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            var tasks=new java.util.ArrayDeque<Runnable>();
            try(var runtime=new LiveBuildBundleRuntime(workspace,"test","overworld",i->0xff90c040,tasks::add)){
                tasks.remove().run();ready(runtime);var bundle=workspace.catalog().tiles().get(0).bundle();
                var origin=new LiveBuildProgress.Position(0,0,0);
                var first=new LiveBuildPhaseSession(bundle,0,"test","overworld",origin);
                assertFalse(runtime.canFollow(new LiveBuildPhaseSession(bundle,0,"other","overworld",origin)));
                runtime.follow(first);runtime.updateSession(null);
                runtime.updateSession(first);assertEquals(1,tasks.size());
                tasks.remove().run();runtime.updateSession(first);
                assertEquals(1,tasks.size());tasks.remove().run();ready(runtime);
                assertFalse(runtime.failed());assertNotNull(runtime.identity());
                assertThrows(IllegalStateException.class,runtime::unanchor);
                assertThrows(IllegalStateException.class,()->runtime.anchor(origin,LiveBuildTransform.NONE));
            }
        });
    }
    private final LiveBuildProgress.State stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
    private void ready(LiveBuildBundleRuntime runtime)throws Exception{
        while(runtime.preparing()){runtime.prepare(s->s,(s,t)->s);Thread.sleep(1);}
        assertFalse(runtime.failed());
    }
    @Test void fullOverviewAndIndependentPlacementSurviveTileSwitching(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            try(var runtime=new LiveBuildBundleRuntime(workspace,"test","overworld",i->0xff90c040)){
                assertEquals(256,runtime.width());assertEquals(128,runtime.height());
                assertEquals(32768,runtime.pixels().length);ready(runtime);
                assertEquals(0,runtime.selected());assertNull(runtime.identity());
                runtime.anchor(new LiveBuildProgress.Position(100,64,100),LiveBuildTransform.NONE);ready(runtime);
                for(int i=0;i<600;i++)runtime.scan(p->{assertTrue(p.x()>=100);return stone;},32,0);
                assertEquals(.5,runtime.completion(0));
                var first=runtime.identity();runtime.select(1);ready(runtime);
                assertEquals(1,runtime.selectedMap().column());assertNull(runtime.identity());
                runtime.anchor(new LiveBuildProgress.Position(700,64,700),LiveBuildTransform.NONE);ready(runtime);
                boolean[] seen=new boolean[2];
                for(int i=0;i<1200;i++)runtime.scan(p->{seen[p.x()>=700?1:0]=true;return stone;},32,0);
                assertTrue(seen[0]&&seen[1]);assertEquals(1,runtime.completion(0));
                runtime.select(0);assertEquals(first,runtime.identity());assertFalse(runtime.preparing());
                runtime.select(1);runtime.unanchor();assertEquals(.5,runtime.completion(0));
                assertEquals(32768,runtime.pixels().length);
                runtime.select(0);assertEquals(first,runtime.identity());
            }
        });
    }
    @Test void previewAndScanAreBoundedAndDoNotReadUnplacedParts(){
        assertTimeoutPreemptively(Duration.ofSeconds(10),()->{
            var workspace=LiveBuildBundleWorkspace.read(SuppressionTestFixtures.multiZipBytes());
            try(var runtime=new LiveBuildBundleRuntime(workspace,"test","overworld",i->0xff90c040)){
                ready(runtime);runtime.scan(p->{fail("Unplaced world read");return stone;},32,0);
                runtime.anchor(new LiveBuildProgress.Position(0,0,0),LiveBuildTransform.NONE);ready(runtime);
                for(int i=0;i<600;i++){
                    int[] calls={0};runtime.scan(p->{calls[0]++;return stone;},32,0);assertTrue(calls[0]<=32);
                }
                long before=runtime.revision();runtime.preview(32,0);assertEquals(before,runtime.revision());
                for(int i=0;i<600;i++)runtime.preview(32,0);
                assertTrue(runtime.revision()>before);
                assertEquals(0,runtime.selected());assertEquals(.5,runtime.completion(0));
            }
        });
    }
}
