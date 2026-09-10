package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class LiveBuildScreen extends Screen {
    private final Screen parent;
    private final LiveBuildClient manager = LiveBuildClient.instance();
    private final LiveBuildPreviewTexture texture = new LiveBuildPreviewTexture();
    private final LiveBuildPreviewTexture orbitTexture=new LiveBuildPreviewTexture();
    private LiveBuildOrbitPreview orbit=new LiveBuildOrbitPreview();
    private boolean threeD;
    private int orbitAngle=1;
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final BlockPos anchor;
    private final Object anchorWorld;
    private String error = "";
    private boolean loading;
    private long request;
    private final String cloudArt,cloudVersion;
    private boolean cloudOpened;
    public LiveBuildScreen(Screen parent) {
        this(parent,null,null);
    }
    public LiveBuildScreen(Screen parent,String artId,String versionId) {
        super(Text.literal("MapKluss Build"));
        this.parent = parent;
        this.cloudArt=artId;this.cloudVersion=versionId;
        var c = MinecraftClient.getInstance();
        anchorWorld = c.world;
        anchor = c.player == null ? null : c.player.getBlockPos().toImmutable();
    }
    private String tr(String ru, String en) { return CompanionI18n.english(MinecraftClient.getInstance()) ? en : ru; }
    private void button(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r, java.util.function.BooleanSupplier enabled, Runnable action) {
        addDrawableChild(MapKlussButton.builder(Text.literal(label), ignored -> {if(enabled.getAsBoolean())action.run();})
            .action(id).tooltip(Text.literal(label)).dimensions(r.x(),r.y(),r.width(),r.height())
            .enabledWhen(enabled).build().workshop(theme,icon));
    }
    @Override protected void init() {
        try { theme=WorkshopTheme.of(CompanionConfig.load(MinecraftClient.getInstance().runDirectory.toPath()).theme()); }catch(Exception ignored){}
        var s=WorkshopTrackerLayout.at(width,height);
        button("tracker.build.view","2D / 3D",WorkshopIcon.LAYERS,new WorkshopLayout.Rect(s.title().right()-104,s.title().y(),52,20),()->manager.top()!=null,()->threeD=!threeD);
        button("tracker.build.rotate_left",tr("Повернуть влево","Rotate left"),WorkshopIcon.BACK,new WorkshopLayout.Rect(s.title().right()-48,s.title().y(),22,20),()->threeD,()->orbitAngle=Math.floorMod(orbitAngle-1,16));
        button("tracker.build.rotate_right",tr("Повернуть вправо","Rotate right"),WorkshopIcon.MORE,new WorkshopLayout.Rect(s.title().right()-22,s.title().y(),22,20),()->threeD,()->orbitAngle=(orbitAngle+1)%16);
        button("tracker.build.load",linked()?tr("Загрузить арт","Load art"):tr("Схема","Schematic"),WorkshopIcon.LIBRARY,WorkshopTrackerLayout.part(s.navigation(),0,4),()->!loading,()->{if(linked())loadCloud();else choose();});
        button("tracker.build.anchor",tr("Закрепить","Anchor"),WorkshopIcon.TRACKER,WorkshopTrackerLayout.part(s.navigation(),1,4),
            ()->!loading&&!manager.phaseBound()&&!manager.preparing()&&manager.selectedMap()!=null&&!manager.selectedMap().empty(),this::start);
        button("tracker.build.stop",tr("Остановить","Stop"),WorkshopIcon.DELETE,WorkshopTrackerLayout.part(s.navigation(),2,4),
            ()->loading||manager.running()||manager.preparing(),()->{request++;loading=false;manager.stop();texture.close();});
        button("tracker.build.group",tr("Друзья","Friends"),WorkshopIcon.ACCOUNT,WorkshopTrackerLayout.part(s.navigation(),3,4),
            ()->!loading&&!manager.preparing(),()->MinecraftClient.getInstance().setScreen(new LiveBuildGroupScreen(this)));
        button("tracker.build.previous",tr("Пред.","Prev"),WorkshopIcon.BACK,new WorkshopLayout.Rect(s.modes().x(),s.modes().y(),64,20),
            ()->!manager.preparing()&&manager.partCount()>1,()->manager.selectPart(-1));
        button("tracker.build.next",tr("След.","Next"),WorkshopIcon.MORE,new WorkshopLayout.Rect(s.modes().x()+68,s.modes().y(),64,20),
            ()->!manager.preparing()&&manager.partCount()>1,()->manager.selectPart(1));
        button("tracker.build.unanchor",tr("Открепить","Unanchor"),WorkshopIcon.CLOSE,new WorkshopLayout.Rect(s.footer().right()-100,s.footer().y(),100,20),
            ()->manager.selectedPlaced()&&!manager.phaseBound()&&!manager.preparing(),manager::removeSelected);
        button("tracker.build.two_layer","Two-layer",WorkshopIcon.TRACKER,
            new WorkshopLayout.Rect(s.footer().x()+72,s.footer().y(),88,20),
            ()->!loading&&!manager.preparing()&&manager.canFollowTwoLayer(MinecraftClient.getInstance()),
            ()->{if(manager.needsTwoLayerBundle(MinecraftClient.getInstance())){choose(true);return;}
                try{manager.followTwoLayer(MinecraftClient.getInstance());error="";}catch(RuntimeException failure){error=tr("Нет активного этапа","No active stage");}});
        button("global.back",parent instanceof TrackerSessionScreen?tr("Блоки","Blocks"):tr("Назад","Back"),WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.footer().x(),s.footer().y(),68,20),()->true,
            ()->{if(parent instanceof TrackerSessionScreen tracker)tracker.showBuildMaterials();else close();});
    }
    private boolean linked(){return cloudArt!=null&&!cloudArt.isBlank();}
    @Override public void tick(){
        super.tick();
        if(linked()&&!cloudOpened){cloudOpened=true;loadCloud();}
    }
    private void loadCloud(){
        var client=MinecraftClient.getInstance();
        if(anchor==null||client.world!=anchorWorld){
            error=tr("Сначала зайдите в мир","Join a world first");return;
        }
        long token=++request;loading=true;error="";
        CompletableFuture.runAsync(()->{
            try{
                var linked=TrackerCloudAssets.linkedSource(CompanionRuntime.create(client).apiClient(),
                    LiveBuildSourceCache.forRunDir(client.runDirectory.toPath()),cloudArt,cloudVersion);
                var loaded=linked.loaded();
                client.execute(()->{
                    if(request!=token||client.world!=anchorWorld){loaded.close();if(request==token)loading=false;return;}
                    loading=false;
                    var active=manager.groupSource();
                    if(active!=null&&active.sha256().equals(loaded.reference().sha256())){manager.bindWeb(linked.artId(),linked.versionId());loaded.close();return;}
                    if(manager.running()||manager.preparing()){
                        loaded.close();error=tr("Сначала остановите другую стройку","Stop the other build first");return;
                    }
                    try{manager.openCached(client,loaded,anchor,null);manager.bindWeb(linked.artId(),linked.versionId());error="";}
                    catch(RuntimeException invalid){loaded.close();error=tr("Схема несовместима","Incompatible schematic");}
                });
            }catch(Exception failure){
                client.execute(()->{if(request==token){loading=false;error=tr("Не удалось загрузить арт. Повторите загрузку","Could not load art. Try again");}});
            }
        });
    }
    private void choose() {
        choose(false);
    }
    public void chooseGroupSource(){choose(false);}
    private void choose(boolean twoLayerOnly) {
        long token=++request;
        loading=true;error="";
        CompletableFuture.runAsync(()->{
            try {
                String selected;
                try(MemoryStack stack=MemoryStack.stackPush()){
                    PointerBuffer filters=stack.mallocPointer(twoLayerOnly?1:2);
                    if(!twoLayerOnly)filters.put(stack.UTF8("*.litematic"));
                    filters.put(stack.UTF8("*.zip")).flip();
                    selected=TinyFileDialogs.tinyfd_openFileDialog(twoLayerOnly?"MapKluss Two-layer ZIP":"MapKluss Litematic",
                        LitematicaPaths.defaultSchematicDir(MinecraftClient.getInstance().runDirectory.toPath()).toString(),
                        filters,"Litematic / Two-layer ZIP",false);
                }
                if(selected==null||selected.isBlank()) { MinecraftClient.getInstance().execute(()->{if(request==token)loading=false;});return; }
                Path path=Path.of(selected);
                var kind=path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")
                    ?LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP:LiveBuildSessionStore.SourceKind.LITEMATIC;
                var loaded=LiveBuildSourceCache.forRunDir(MinecraftClient.getInstance().runDirectory.toPath()).importFile(path,kind);
                if(twoLayerOnly && loaded.bundle()==null){loaded.close();throw new LiveBuildSchematic.PhasePlanRequired();}
                MinecraftClient.getInstance().execute(()->{
                    if(request!=token){loaded.close();return;}
                    loading=false;
                    try {
                        if(MinecraftClient.getInstance().world!=anchorWorld||anchor==null)throw new IllegalStateException("World changed");
                        if(twoLayerOnly){
                            var active=SuppressionManager.instance().trackerSession(MinecraftClient.getInstance());
                            if(active==null || loaded.bundle().catalog().tiles().stream().noneMatch(tile->
                                tile.bundle().planSha256().equals(active.bundle().planSha256())
                                && tile.bundle().litematicSha256().equals(active.bundle().litematicSha256())))
                                throw new IllegalStateException("Bundle does not contain the active source");
                        }
                        manager.openCached(MinecraftClient.getInstance(),loaded,anchor,null);
                        manager.displayName(path.getFileName().toString());
                    }catch(RuntimeException failure){loaded.close();error=tr("Схема несовместима или мир закрыт","Incompatible schematic or unavailable world");}
                });
            }catch(Exception failure){
                MinecraftClient.getInstance().execute(()->{if(request==token){loading=false;error=failure instanceof LiveBuildSchematic.PhasePlanRequired
                    ? tr("Откройте ZIP Two-layer","Open the Two-layer ZIP")
                    : tr("Не удалось открыть схему","Could not open schematic");}});
            }
        });
    }
    private void start() {
        try {
            if (MinecraftClient.getInstance().world != anchorWorld) throw new IllegalStateException("World changed");
            MinecraftClient.getInstance().setScreen(new LiveBuildPlacementScreen(this));error="";
        }catch(Exception failure){error=tr("Схема несовместима или мир закрыт","Incompatible schematic or unavailable world");}
    }
    private void text(DrawContext g,String value,WorkshopLayout.Rect r,String colour) {
        WorkshopDraw.text(g,textRenderer,value,r.x()+4,r.y()+4,Math.max(0,r.width()-8),theme.color(colour));
    }
    @Override public void render(DrawContext g,int mouseX,int mouseY,float delta) {
        var s=WorkshopTrackerLayout.at(width,height);
        g.fill(0,0,width,height,0x88000000);WorkshopChrome.frame(g::fill,s.frame(),theme);
        text(g,loading?tr("Загрузка схемы…","Loading schematic…"):
            manager.displayName().isBlank()?tr("Стройка","Build"):manager.displayName(),new WorkshopLayout.Rect(s.title().x(),s.title().y(),s.title().width()-108,s.title().height()),"text-primary");
        var id=manager.identity();
        String position=id!=null?id.origin().x()+" / "+id.origin().y()+" / "+id.origin().z():anchor==null?"":anchor.getX()+" / "+anchor.getY()+" / "+anchor.getZ();
        text(g,manager.partCount()==0?"":tr("Карта ","Map ")+(manager.selectedPart()+1)+"/"+manager.partCount()+"  "+position,
            new WorkshopLayout.Rect(s.modes().x()+136,s.modes().y(),Math.max(0,s.modes().width()-136),20),"text-secondary");
        var summary=manager.summary();
        if(summary!=null) {
            var selected=manager.selectedSummary();
            text(g,tr("Всего ","Total ")+String.format(java.util.Locale.ROOT,"%.1f%%",manager.wholeCompletion()*100)
                +"  "+tr("Карта ","Map ")+(selected==null?"—":String.format(java.util.Locale.ROOT,"%.1f%%",selected.completion()*100))
                ,new WorkshopLayout.Rect(s.header().x(),s.header().y(),s.header().width(),16),"text-secondary");
            text(g,tr("Ошибки: ","Wrong: ")+summary.wrong()+"  "+tr("Не проверено: ","Unknown: ")+(summary.unknown()+summary.stale()),
                new WorkshopLayout.Rect(s.header().x(),s.header().y()+16,s.header().width(),16),"text-secondary");
            var preview = new WorkshopLayout.Rect(s.table().x(),s.header().y()+32,s.table().width(),
                Math.max(0,s.table().bottom()-s.header().y()-32));
            if(threeD){
                orbit.update(manager.orbitSurface(),manager.pixels(),manager.viewRevision(),orbitAngle);
                var frame=orbit.frame();
                if(frame!=null)WorkshopDraw.image(g,orbitTexture.get(LiveBuildOrbitPreview.WIDTH,LiveBuildOrbitPreview.HEIGHT,frame.pixels(),frame.revision()),preview,LiveBuildOrbitPreview.WIDTH,LiveBuildOrbitPreview.HEIGHT);
                else text(g,tr("Подготовка 3D...","Preparing 3D..."),preview,"text-secondary");
            }else{
            var top=manager.top();
            WorkshopDraw.image(g,texture.get(top.width(),top.height(),manager.pixels(),manager.viewRevision()),preview,top.width(),top.height());
            var area=WorkshopLayout.contain(preview,top.width(),top.height());
            var part=manager.selectedMap();var bounds=manager.artworkBounds();
            int left=area.x()+part.column()*128*area.width()/bounds.width();
            int upper=area.y()+part.row()*128*area.height()/bounds.depth();
            int right=area.x()+(part.column()*128+part.width())*area.width()/bounds.width();
            int lower=area.y()+(part.row()*128+part.depth())*area.height()/bounds.depth();
            int outline=theme.color("text-primary");
            g.fill(left,upper,right,upper+1,outline);g.fill(left,lower-1,right,lower,outline);
            g.fill(left,upper,left+1,lower,outline);g.fill(right-1,upper,right,lower,outline);
            }
        }else text(g,!error.isBlank()?error:loading?tr("Загрузка арта...","Loading art..."):manager.preparing()?tr("Подготовка…","Preparing…"):
            manager.preparationFailed()?tr("Схема несовместима","Incompatible schematic"):
            tr("Выберите схему","Choose a schematic"),s.table(),"text-secondary");
        String notice=!error.isBlank()?error:manager.preparing()?tr("Подготовка…","Preparing…"):
            manager.preparationFailed()?tr("Не удалось закрепить","Could not anchor"):
            manager.saveFailed()?tr("Сохранение недоступно","Save unavailable"):
            manager.webSyncFailed()?tr("Нет связи с сайтом","Website sync unavailable"):"";
        if(!notice.isBlank())text(g,notice,new WorkshopLayout.Rect(s.footer().x()+164,s.footer().y(),Math.max(0,s.footer().width()-268),20),"text-secondary");
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public void removed(){request++;loading=false;texture.close();orbitTexture.close();orbit.close();orbit=new LiveBuildOrbitPreview();super.removed();}
    @Override public void close(){MinecraftClient.getInstance().setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
