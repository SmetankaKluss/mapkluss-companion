package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackerSessionScreen extends WorkshopTrackerScreen {
    private static final Map<String, SerialLatestQueue<TrackerMutation>> SESSION_MUTATIONS = new ConcurrentHashMap<>();
    private static final int MATERIAL_ROWS = 12;
    private static final int MATERIAL_ROW_HEIGHT = 24;
    private static final int ACTION_ROWS = 2;
    private static final int ACTION_ROW_HEIGHT = 34;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int ACTION_BOTTOM_MARGIN = 32;
    private static final int MIN_ACTION_TOP = 210;
    private static final int SIDE_RAIL_WIDTH = 120;
    private static final int SIDE_RAIL_GAP = 22;
    private static final int[] STEP_OPTIONS = new int[] {1, 16, 64};

    private final Screen parent;
    private final String sessionId;
    private BuildSessionState session;
    private BuildSessionState previousSession;
    private String status = "";
    private int scrollOffset;
    private int stepIndex = 2;
    private String searchQuery = "";
    private boolean hideCompleted;
    private boolean loadFailed;
    private AbstractWidget artButton;
    private AbstractWidget stepButton;
    private AbstractWidget undoButton;
    private AbstractWidget hideDoneButton;
    private EditBox searchInput;
    private final List<ProgressInput> progressInputs = new ArrayList<>();
    private final ScreenRequestGate requests = new ScreenRequestGate();
    private final SerialLatestQueue<TrackerMutation> mutations;

    public TrackerSessionScreen(Screen parent, String sessionId) {
        super(Component.literal("Трекер MapKluss"));
        this.parent = parent;
        this.sessionId = sessionId;
        this.mutations = SESSION_MUTATIONS.computeIfAbsent(sessionId, ignored ->
            new SerialLatestQueue<>(mutation -> mutation.owner().performMutation(mutation), TrackerMutation::replacementKey)
        );
    }


    private WorkshopTheme workshopTheme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private boolean fixture;
    private boolean fixtureApplied;
    private MapKlussButton trackerButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r,
        java.util.function.BooleanSupplier enabled, boolean selected, Runnable action) {
        var b = MapKlussButton.builder(CompanionI18n.text(label), ignored -> {
            if (enabled.getAsBoolean()) action.run();
        }).action(id).tooltip(CompanionI18n.text(label)).selected(selected)
            .dimensions(r.x(),r.y(),r.width(),r.height()).enabledWhen(enabled);
        return addRenderableWidget(b.build().workshop(workshopTheme,icon));
    }
    private WorkshopLayout.Rect part(WorkshopLayout.Rect r,int i,int n) { return WorkshopTrackerLayout.part(r,i,n); }
    private void workshopNavigation(WorkshopTrackerLayout.Layout s) {
        try { workshopTheme=WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme()); }
        catch(Exception ignored) { }
        int slot=(s.navigation().width()-60)/5;
        WorkshopIcon[] icons={WorkshopIcon.LIBRARY,WorkshopIcon.LENS,WorkshopIcon.SCAN,WorkshopIcon.TRACKER,WorkshopIcon.ACCOUNT};
        String[] labels={"Библиотека","Lens","Скан","Трекер","Аккаунт"};
        for(int i=0;i<5;i++) {
            var d=CompanionUiLayout.Destination.values()[i];
            trackerButton(CompanionActionInventory.navigationAction(d),labels[i],icons[i],
                new WorkshopLayout.Rect(s.navigation().x()+i*slot,s.navigation().y(),slot-4,28),
                ()->true,d==CompanionUiLayout.Destination.TRACKER,()->openDestination(d));
        }
        trackerButton("account.theme","Оформление",WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(s.navigation().right()-56,s.navigation().y(),24,28),()->true,false,
            ()->client().gui.setScreen(new WorkshopAppearanceScreen(this)));
        trackerButton("global.back","Назад",WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.navigation().right()-24,s.navigation().y(),24,28),()->true,false,this::onClose);
        trackerButton("global.language",CompanionI18n.toggleLabel(client()),null,
            new WorkshopLayout.Rect(s.footer().right()-32,s.footer().y(),32,20),()->true,false,
            ()->{try{CompanionI18n.toggle(client()); init();}catch(Exception ignored){}});
    }
    private void text(GuiGraphicsExtractor g,String value,WorkshopLayout.Rect r,String color) {
        if(r.height()<9)return;
        WorkshopDraw.text(g,font,CompanionI18n.translate(value),r.x()+4,r.y()+4,Math.max(0,r.width()-8),workshopTheme.color(color));
    }
    @Override public void onClose() { client().gui.setScreen(parent); }

    private boolean toolsVisible;
    private boolean previewVisible;
    private boolean previewFailed;
    private GatheringArtPreview newGatheringPreview(){return new GatheringArtPreview(value->
        fixture?value.image_preview():TrackerCloudAssets.preview(CompanionRuntime.create(client()).apiClient(),value));}
    private GatheringArtPreview gatheringPreview=newGatheringPreview();
    private final LiveBuildPreviewTexture gatheringTexture=new LiveBuildPreviewTexture();
    private boolean showGatherPreview(){return previewVisible&&session!=null&&!"building".equals(session.mode());}
    private boolean opened;
    private String pendingSearch="";
    private final Map<String,String> manualDrafts=new java.util.HashMap<>();
    private Map<String,Integer> automaticProgress;
    private boolean buildMaterialsRequested;
    void showBuildMaterials(){
        buildMaterialsRequested=true;
        if(session!=null)session=session.withMode("building");
        toolsVisible=false;previewVisible=false;
        client().gui.setScreen(this);
    }
    private Map<String,Integer> currentProgress() {
        return automaticProgress!=null&&session!=null&&"building".equals(session.mode())
            ?automaticProgress:session.currentProgress();
    }
    private boolean automaticBuilding(){return automaticProgress!=null&&session!=null&&"building".equals(session.mode());}
    @Override public void tick(){
        super.tick();
        var next=fixture||session==null?null:LiveBuildClient.instance().scannedMaterials(session.art_id(),session.art_version_id());
        if(next!=null)next=TrackerMaterialCounts.forSession(session.materials(),next);
        if(!java.util.Objects.equals(next,automaticProgress)){
            automaticProgress=next;
            if(session!=null&&"building".equals(session.mode()))rebuildLayout();
        }
    }
    public TrackerSessionScreen(Screen parent,String sessionId,boolean fixture){this(parent,sessionId);this.fixture=fixture;}
    void applyDevelopmentData(BuildSessionState value,String message,boolean failed){
        if(!fixture)return;session=value;status=message;loadFailed=failed;
    }
    @Override protected void init(){
        requests.attach();
        if(fixture&&!fixtureApplied){
            fixtureApplied=true;
            try{Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture").getMethod("applyTrackerSession",Object.class).invoke(null,this);}
            catch(ReflectiveOperationException ignored){}
        }
        rebuildLayout();
        if(!fixture&&!opened){opened=true;load();}
    }
    @Override public void removed(){opened=false;requests.detach();gatheringPreview.close();gatheringPreview=newGatheringPreview();gatheringTexture.close();super.removed();}
    private void rebuildLayout(){
        if(searchInput!=null)pendingSearch=searchInput.getValue();
        clearWidgets();
        searchInput=null;
        progressInputs.clear();
        artButton=stepButton=undoButton=hideDoneButton=null;
        scrollOffset=clampScrollOffset(scrollOffset,session);
        var s=WorkshopTrackerLayout.at(width,height);
        workshopNavigation(s);
        var t=s.title();
        trackerButton("tracker.preview",gatheringPreview.failed()?(CompanionI18n.english(client())?"Retry preview":"Повторить превью"):(CompanionI18n.english(client())?"Art progress":"Прогресс арта"),gatheringPreview.failed()?WorkshopIcon.REFRESH:WorkshopIcon.LIBRARY,
            new WorkshopLayout.Rect(t.right()-52,t.y(),24,20),()->session!=null&&!"building".equals(session.mode()),previewVisible,
            ()->{if(gatheringPreview.failed()){gatheringPreview.retry();previewVisible=true;}else previewVisible=!previewVisible;toolsVisible=false;rebuildLayout();});
        trackerButton("tracker.tools","Поиск и действия",WorkshopIcon.MORE,
            new WorkshopLayout.Rect(t.right()-24,t.y(),24,20),()->true,toolsVisible,()->{toolsVisible=!toolsVisible;rebuildLayout();});
        if(loadFailed){
            trackerButton("tracker.retry","Повторить",WorkshopIcon.REFRESH,part(s.modes(),0,2),()->!fixture,false,this::load);
            trackerButton("tracker.change_session","Изменить UUID",WorkshopIcon.BACK,part(s.modes(),1,2),()->true,false,this::onClose);
            return;
        }
        trackerButton("tracker.status_gathering","Сбор",null,part(s.modes(),0,4),()->session!=null,session!=null&&!"building".equals(session.mode()),()->switchMode("gathering"));
        trackerButton("tracker.build.open","Стройка",WorkshopIcon.TRACKER,part(s.modes(),1,4),()->session!=null,false,
            ()->client().gui.setScreen(new LiveBuildScreen(this,session.art_id(),session.art_version_id())));
        stepButton=trackerButton("tracker.step_cycle","Шаг "+currentStep(),null,part(s.modes(),2,4),()->session!=null,false,this::cycleStep);
        undoButton=trackerButton("tracker.undo","Отмена",WorkshopIcon.BACK,part(s.modes(),3,4),()->previousSession!=null,false,this::undoLastChange);
        if(toolsVisible){addTools(s);return;}
        if(showGatherPreview())return;
        rebuildMaterialButtons();
        trackerButton("tracker.table_previous","Пред.",WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.footer().right()-88,s.footer().y(),24,20),()->scrollOffset>0,false,()->scrollMaterials(visibleMaterialRows()));
        trackerButton("tracker.table_next","След.",WorkshopIcon.MORE,
            new WorkshopLayout.Rect(s.footer().right()-60,s.footer().y(),24,20),()->scrollOffset+visibleMaterialRows()<filteredMaterials().size(),false,()->scrollMaterials(-visibleMaterialRows()));
    }
    private void addTools(WorkshopTrackerLayout.Layout s){
        var a=s.table();
        searchInput=new EditBox(font,a.x(),a.y(),a.width()-84,20,CompanionI18n.text("Поиск материалов"));
        searchInput.setMaxLength(80);searchInput.setValue(pendingSearch);addRenderableWidget(searchInput);
        trackerButton("tracker.search","Найти",WorkshopIcon.SEARCH,new WorkshopLayout.Rect(a.right()-80,a.y(),24,20),()->true,false,this::applySearch);
        trackerButton("tracker.search_clear","Сброс",WorkshopIcon.CLOSE,new WorkshopLayout.Rect(a.right()-52,a.y(),24,20),()->true,false,this::clearSearch);
        hideDoneButton=trackerButton("tracker.hide_completed","Скрыть завершённые",WorkshopIcon.CHECK,new WorkshopLayout.Rect(a.right()-24,a.y(),24,20),()->true,hideCompleted,this::toggleHideCompleted);
        var row=new WorkshopLayout.Rect(a.x(),a.y()+24,a.width(),20);
        trackerButton("tracker.open_site","Сайт",WorkshopIcon.LINK,part(row,0,4),()->!fixture,false,this::openTrackerSite);
        trackerButton("tracker.refresh","Обновить",WorkshopIcon.REFRESH,part(row,1,4),()->!fixture,false,this::load);
        artButton=trackerButton("tracker.open_art","Арт",WorkshopIcon.LIBRARY,part(row,2,4),()->!fixture&&session!=null&&session.art_id()!=null&&!session.art_id().isBlank(),false,this::openRelatedArt);
        trackerButton("tracker.status_building",CompanionI18n.english(client())?"Manual":"Счётчик",WorkshopIcon.TRACKER,
            part(row,3,4),()->session!=null,session!=null&&"building".equals(session.mode()),
            ()->{toolsVisible=false;previewVisible=false;switchMode("building");});
    }
    private String draftKey(BuildSessionMaterial m){return session.mode()+"|"+m.nbtName();}
    private void rebuildMaterialButtons(){
        if(session==null)return;
        var s=WorkshopTrackerLayout.at(width,height);
        var materials=filteredMaterials();
        for(int row=0;row<s.rows()&&row+scrollOffset<materials.size();row++){
            var material=materials.get(row+scrollOffset);
            var r=s.controls(s.row(row));
            int fieldWidth=48,gap=4;
            int bw=(r.width()-fieldWidth-gap*5)/5;
            EditBox input=new EditBox(font,r.x(),r.y(),fieldWidth,18,CompanionI18n.text("Количество"));
            input.setMaxLength(7);
            input.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(materialLabel(material)+" / "+material.count())));
            input.setValue(automaticBuilding()?Integer.toString(currentProgress().getOrDefault(material.nbtName(),0)):
                manualDrafts.getOrDefault(draftKey(material),Integer.toString(currentProgress().getOrDefault(material.nbtName(),0))));
            input.setEditable(!automaticBuilding());
            input.setResponder(value->{ if (!value.matches("\\d{0,7}")) { input.setValue(value.replaceAll("[^0-9]", "")); return; } manualDrafts.put(draftKey(material),value); });
            progressInputs.add(new ProgressInput(material,input));addRenderableWidget(input);
            int x=r.x()+fieldWidth+gap;
            trackerButton("tracker.set_count","Применить",WorkshopIcon.CHECK,new WorkshopLayout.Rect(x,r.y(),bw,18),()->!automaticBuilding(),false,()->applyManualProgress(material,input.getValue()));
            trackerButton("tracker.decrement","-",null,new WorkshopLayout.Rect(x+(bw+gap),r.y(),bw,18),()->!automaticBuilding(),false,()->changeProgress(material,-currentStep()));
            trackerButton("tracker.add_one","+",null,new WorkshopLayout.Rect(x+2*(bw+gap),r.y(),bw,18),()->!automaticBuilding(),false,()->changeProgress(material,currentStep()));
            trackerButton("tracker.clear_count","0",WorkshopIcon.REFRESH,new WorkshopLayout.Rect(x+3*(bw+gap),r.y(),bw,18),()->!automaticBuilding(),false,()->setProgress(material,0));
            trackerButton("tracker.complete_all","Все",WorkshopIcon.CHECK,new WorkshopLayout.Rect(x+4*(bw+gap),r.y(),bw,18),()->!automaticBuilding(),false,()->setProgress(material,material.count()));
        }
    }
    private void load() {
        if (fixture) return;
        loadFailed = false;
        status = "Загрузка трекера...";
        rebuildLayout();
        ScreenRequestGate.Token request = requests.begin("load");
        mutations.submit(TrackerMutation.load(this, request));
    }

    private void switchMode(String mode) {
        buildMaterialsRequested="building".equals(mode);
        if (session == null) return;
        BuildSessionState updated = session.withMode(mode);
        if (updated == session) {
            status = "Этот режим уже выбран.";
            return;
        }
        previousSession = session;
        session = updated;
        status = fixture ? "" : "Переключение режима...";
        rebuildLayout();
        if (!fixture) mutations.submit(TrackerMutation.mode(this, requests.begin("tracker-sync"), mode));
    }

    private void applyManualProgress(BuildSessionMaterial material, String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isBlank()) {
            status = "Введите количество для " + material.displayName() + ".";
            return;
        }
        try {
            setProgress(material, Integer.parseInt(normalized));
        } catch (NumberFormatException e) {
            status = "Некорректное количество: " + normalized;
        }
    }

    private void changeProgress(BuildSessionMaterial material, int delta) {
        if (session != null) manualDrafts.remove(draftKey(material));
        if (session == null) return;
        if(automaticBuilding())return;
        BuildSessionState nextSession = session.withProgress(material.nbtName(), delta);
        if (sameProgress(session, nextSession)) {
            status = "Без изменений: " + material.displayName() + ".";
            rebuildLayout();
            return;
        }
        previousSession = session;
        session = nextSession;
        status = ("building".equals(nextSession.mode()) ? "Постройка" : "Сбор") + " обновлен локально...";
        syncSession(nextSession);
    }

    private void setProgress(BuildSessionMaterial material, int value) {
        if(automaticBuilding())return;
        if (session != null) manualDrafts.remove(draftKey(material));
        if (session == null) return;
        BuildSessionState nextSession = session.withAbsoluteProgress(material.nbtName(), value);
        if (sameProgress(session, nextSession)) {
            status = value <= 0
                ? material.displayName() + " уже сброшен."
                : material.displayName() + " уже заполнен.";
            rebuildLayout();
            return;
        }
        previousSession = session;
        session = nextSession;
        status = value <= 0
            ? "Сброшено локально: " + material.displayName() + "..."
            : "Заполнено локально: " + material.displayName() + "...";
        syncSession(nextSession);
    }

    private void undoLastChange() {
        if(automaticBuilding())return;
        if (previousSession == null) {
            status = "Отменять нечего.";
            return;
        }
        BuildSessionState restore = previousSession;
        BuildSessionState current = session;
        previousSession = current;
        session = restore;
        status = "Отмена последнего изменения...";
        rebuildLayout();
        if (fixture) { status = ""; return; }
        if (!java.util.Objects.equals(current.mode(), restore.mode())) {
            mutations.submit(TrackerMutation.combined(this, requests.begin("tracker-sync"), restore.mode(), restore));
        } else {
            syncSession(restore);
        }
    }

    private void syncSession(BuildSessionState nextSession) {
        rebuildLayout();
        if (fixture) { status = ""; return; }
        mutations.submit(TrackerMutation.progress(this, requests.begin("tracker-sync"), nextSession));
    }

    private void performMutation(TrackerMutation mutation) {
        if (fixture) return;
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            if (!mutation.loadOnly() && mutation.mode() != null) {
                runtime.apiClient().switchTrackerMode(sessionId, mutation.mode());
            }
            if (!mutation.loadOnly() && mutation.session() != null) {
                BuildSessionState snapshot = mutation.session();
                JsonObject gathered = "building".equals(snapshot.mode()) ? null : toJson(snapshot.gathered());
                JsonObject placed = "building".equals(snapshot.mode()) ? toJson(snapshot.placed()) : null;
                runtime.apiClient().updateTracker(sessionId, gathered, placed);
            }
            BuildSessionState loaded = runtime.syncService().tracker(sessionId);
            try {
                rememberTrackerHistory(runtime, loaded);
            } catch (Exception cacheError) {
                MapKlussCompanionClient.LOGGER.debug("Could not cache tracker history.", cacheError);
            }
            runOnClient(mutation.request(), () -> {
                loadFailed = false;
                session = buildMaterialsRequested?loaded.withMode("building"):loaded;
                scrollOffset = clampScrollOffset(scrollOffset, loaded);
                status = mutation.loadOnly()
                    ? ""
                    : mutation.mode() == null ? "Синхронизировано." : "Режим: " + readableMode(loaded.mode());
                rebuildLayout();
            });
        } catch (Exception error) {
            if (CompanionAuthSupport.isAuthFailure(error)) {
                expireSessionLocally(mutation.request(), CompanionAuthSupport.expiredMessage());
            } else {
                runOnClient(mutation.request(), () -> {
                    if (mutation.loadOnly()) {
                        session = null;
                        loadFailed = true;
                    }
                    status = CompanionUiErrors.message(mutation.loadOnly() ? "tracker" : "sync", error);
                    rebuildLayout();
                });
            }
        }
    }


    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta){
        var s=WorkshopTrackerLayout.at(width,height);
        g.fill(0,0,width,height,0x88000000);WorkshopChrome.frame(g::fill,s.frame(),workshopTheme);
        text(g,session==null?(loadFailed?"Сессия не найдена":"Загрузка..."):fallbackTitle(),
            new WorkshopLayout.Rect(s.title().x(),s.title().y(),s.title().width()-60,20),"text-primary");
        if(!toolsVisible&&showGatherPreview()&&!loadFailed){
            gatheringPreview.update(session,CompanionMaterialIcons.mapColours(session));var frame=gatheringPreview.frame();
            if(previewFailed!=gatheringPreview.failed()){previewFailed=gatheringPreview.failed();rebuildLayout();}
            text(g,(CompanionI18n.english(client())?"Gathered ":"Собрано ")+GatheringArtPreview.percent(session)+"%",s.header(),"text-secondary");
            if(frame!=null)WorkshopDraw.image(g,gatheringTexture.get(frame.width(),frame.height(),frame.pixels(),frame.revision()),s.table(),frame.width(),frame.height());
            else text(g,CompanionI18n.english(client())?(gatheringPreview.failed()?"Preview unavailable":"Loading..."):
                (gatheringPreview.failed()?"Превью недоступно":"Загрузка..."),s.table(),"text-secondary");
        }else if(!toolsVisible&&session!=null&&!loadFailed){
            text(g,"Материал",s.header(),"text-secondary");
            var materials=filteredMaterials();
            for(int i=0;i<s.rows()&&i+scrollOffset<materials.size();i++){
                var m=materials.get(i+scrollOffset);var r=s.row(i);var controls=s.controls(r);
                g.fill(r.x(),r.y(),r.right(),r.bottom(),workshopTheme.color(i%2==0?"surface-secondary":"surface-primary"));
                g.item(CompanionMaterialIcons.stackFor(m),r.x()+2,r.y()+1);
                int labelWidth=s.compact()?r.width()-112:controls.x()-r.x()-104;
                text(g,materialLabel(m),new WorkshopLayout.Rect(r.x()+22,r.y(),Math.max(0,labelWidth),18),"text-primary");
                int value=currentProgress().getOrDefault(m.nbtName(),0);
                int countX=s.compact()?r.right()-90:controls.x()-80;
                text(g,value+"/"+m.count(),new WorkshopLayout.Rect(countX,r.y(),76,18),"text-secondary");
                int barWidth=s.compact()?r.width():controls.x()-r.x()-6;
                g.fill(r.x(),r.bottom()-2,r.x()+barWidth,r.bottom(),workshopTheme.color("border"));
                if(m.count()>0)g.fill(r.x(),r.bottom()-2,r.x()+(int)((long)barWidth*Math.min(value,m.count())/m.count()),r.bottom(),workshopTheme.color("accent"));
            }
            if(materials.isEmpty())text(g,"Материалов нет.",s.table(),"text-secondary");
        }
        String summary=status;
        if(summary.isBlank()&&session!=null){
            int done=automaticBuilding()?session.materials().stream().mapToInt(m->Math.min(m.count(),currentProgress().getOrDefault(m.nbtName(),0))).sum():
                "building".equals(session.mode())?session.placedBlocks():session.gatheredBlocks();
            summary=done+" / "+session.totalBlocks();
        }
        text(g,summary,new WorkshopLayout.Rect(s.footer().x(),s.footer().y(),Math.max(0,s.footer().width()-96),20),"text-secondary");
        super.extractRenderState(g,mouseX,mouseY,delta);
    }

    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(!toolsVisible&&isOverMaterialTable(x,y)&&vertical!=0){
            clearTableFocus();return scrollMaterials(vertical);
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override protected boolean submitFocusedInput(){
        if(searchInput!=null&&searchInput.isFocused()){applySearch();return true;}
        for(var input:progressInputs)if(input.widget().isFocused()){
            applyManualProgress(input.material(),input.widget().getValue());return true;
        }
        return false;
    }
    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getValue().trim().toLowerCase();
        toolsVisible = false;
        scrollOffset = 0;
        rebuildLayout();
    }

    private void clearSearch() {
        searchQuery = "";
        pendingSearch = "";
        if (searchInput != null) searchInput.setValue("");
        scrollOffset = 0;
        rebuildLayout();
    }

    private void toggleHideCompleted() {
        hideCompleted = !hideCompleted;
        scrollOffset = 0;
        rebuildLayout();
    }

    private void cycleStep() {
        stepIndex = (stepIndex + 1) % STEP_OPTIONS.length;
        rebuildLayout();
    }

    private void openRelatedArt() {
        if (session == null || session.art_id() == null || session.art_id().isBlank()) {
            status = "Трекер не привязан к арту.";
            return;
        }
        client().gui.setScreen(new CompanionArtScreen(this, session.art_id(), fallbackTitle()));
    }

    private void openTrackerSite() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri("/build/" + sessionId));
            status = "Трекер открыт в браузере.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void updateActionButtons() {
        if (stepButton != null) {
            stepButton.setMessage(stepButtonText());
        }
        if (hideDoneButton != null) {
            hideDoneButton.setMessage(hideDoneButtonText());
        }
        if (undoButton != null) {
            undoButton.active = previousSession != null;
        }
        if (artButton != null) {
            artButton.active = session != null && session.art_id() != null && !session.art_id().isBlank();
        }
    }

    private Component stepButtonText() {
        return Component.literal("Шаг " + currentStep());
    }

    private Component hideDoneButtonText() {
        return Component.literal(hideCompleted ? "Показ." : "Скрыть");
    }

    private int currentStep() {
        return STEP_OPTIONS[stepIndex];
    }

    private int clampScrollOffset(int current, BuildSessionState state) {
        if (state == null) return 0;
        int visible = filteredMaterials(state).size();
        int rows = visibleMaterialRows();
        if (rows <= 0) return 0;
        int max = Math.max(0, visible - rows);
        return Math.max(0, Math.min(current, max));
    }

    private String pageSummary() {
        List<BuildSessionMaterial> visibleMaterials = filteredMaterials(session);
        if (visibleMaterials.isEmpty()) {
            return "Материалов нет.";
        }
        int rows = visibleMaterialRows();
        if (rows <= 0) return "Материалы 0 / " + visibleMaterials.size();
        int start = scrollOffset + 1;
        int end = Math.min(scrollOffset + rows, visibleMaterials.size());
        String filtered = searchQuery.isBlank() && !hideCompleted ? "" : " / фильтр";
        return "Материалы " + start + "-" + end + " / " + visibleMaterials.size() + filtered + " / шаг " + currentStep();
    }


    private int visibleMaterialRows(){return WorkshopTrackerLayout.at(width,height).rows();}
    private boolean isOverMaterialTable(double x,double y){
        var r=WorkshopTrackerLayout.at(width,height).table();
        return x>=r.x()&&x<r.right()&&y>=r.y()&&y<r.bottom();
    }
    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().gui.setScreen(new LensScreen(this, fixture));
            case SCAN -> client().gui.setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(parent, fixture));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this, fixture));
            default -> { }
        }
    }

    private String destinationLabel(CompanionUiLayout.Destination destination) {
        return switch (destination) {
            case LIBRARY -> "Библиотека";
            case LENS -> "Lens";
            case SCAN -> "Скан";
            case TRACKER -> "Трекер";
            case ACCOUNT -> "Аккаунт";
            default -> destination.name();
        };
    }

    private boolean scrollMaterials(double verticalAmount) {
        List<BuildSessionMaterial> visibleMaterials = filteredMaterials(session);
        int rows = visibleMaterialRows();
        if (rows <= 0) return false;
        if (visibleMaterials.size() <= rows) return false;
        int lines = Math.max(1, (int) Math.ceil(Math.abs(verticalAmount)));
        int delta = verticalAmount > 0 ? -lines : lines;
        int next = Math.max(0, Math.min(scrollOffset + delta, visibleMaterials.size() - rows));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        rebuildLayout();
        return true;
    }

    private String fallbackTitle() {
        if (session != null && session.info() != null && session.info().title() != null && !session.info().title().isBlank()) {
            return session.info().title();
        }
        return "Арт трекера";
    }

    private String readableMode(String mode) {
        if ("building".equals(mode)) return "стройка";
        if ("gathering".equals(mode)) return "сбор";
        return mode == null || mode.isBlank() ? "?" : mode;
    }

    private boolean sameProgress(BuildSessionState left, BuildSessionState right) {
        if (left == null || right == null) return false;
        return left.currentProgress().equals(right.currentProgress()) && String.valueOf(left.mode()).equals(String.valueOf(right.mode()));
    }

    private void rememberTrackerHistory(CompanionRuntime runtime, BuildSessionState loaded) {
        try {
            TrackerHistoryStore.load(LitematicaPaths.trackerHistoryPath(client().gameDirectory.toPath())).remember(loaded);
        } catch (Exception ignored) {
        }
    }

    private List<BuildSessionMaterial> filteredMaterials() {
        return filteredMaterials(session);
    }

    private List<BuildSessionMaterial> filteredMaterials(BuildSessionState state) {
        if (state == null || state.materials() == null || state.materials().isEmpty()) return List.of();
        Map<String, Integer> progress = state==session?currentProgress():state.currentProgress();
        List<BuildSessionMaterial> filtered = new ArrayList<>();
        for (BuildSessionMaterial material : state.materials()) {
            if (hideCompleted && progress.getOrDefault(material.nbtName(), 0) >= material.count()) continue;
            if (!searchQuery.isBlank()) {
                String haystack = (materialLabel(material) + " " + material.displayName() + " " + material.nbtName()).toLowerCase();
                if (!haystack.contains(searchQuery)) continue;
            }
            filtered.add(material);
        }
        return filtered;
    }

    private String materialLabel(BuildSessionMaterial material) {
        String label = material.displayName();
        if (label == null || label.isBlank()) return material.nbtName();
        return label
            .replace(" (vertical)", " вертикально")
            .replace("(vertical)", "вертикально")
            .replace("Vertical", "вертикально");
    }

    private void clearTableFocus() {
        if (searchInput != null) searchInput.setFocused(false);
        for (ProgressInput progressInput : progressInputs) {
            progressInput.widget().setFocused(false);
        }
        setFocused(null);
    }

    private static JsonObject toJson(Map<String, Integer> values) {
        JsonObject json = new JsonObject();
        if (values == null) return json;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
    }

    private void runOnClient(ScreenRequestGate.Token request, Runnable task) {
        client().execute(() -> {
            if (requests.isCurrent(request) && client().gui.screen() == this) task.run();
        });
    }

    private void expireSessionLocally(ScreenRequestGate.Token request, String nextStatus) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            CompanionAuthSupport.clearSessionQuietly(runtime);
        } catch (Exception ignored) {
        }
        runOnClient(request, () -> status = nextStatus);
    }

    private record TrackerMutation(
        TrackerSessionScreen owner,
        ScreenRequestGate.Token request,
        String mode,
        BuildSessionState session,
        boolean loadOnly,
        String replacementKey
    ) {
        private static TrackerMutation load(TrackerSessionScreen owner, ScreenRequestGate.Token request) {
            return new TrackerMutation(owner, request, null, null, true, "load");
        }

        private static TrackerMutation mode(TrackerSessionScreen owner, ScreenRequestGate.Token request, String mode) {
            return new TrackerMutation(owner, request, mode, null, false, null);
        }

        private static TrackerMutation progress(
            TrackerSessionScreen owner,
            ScreenRequestGate.Token request,
            BuildSessionState session
        ) {
            return new TrackerMutation(owner, request, null, session, false, "progress");
        }

        private static TrackerMutation combined(
            TrackerSessionScreen owner,
            ScreenRequestGate.Token request,
            String mode,
            BuildSessionState session
        ) {
            return new TrackerMutation(owner, request, mode, session, false, null);
        }
    }

    private record ProgressInput(BuildSessionMaterial material, EditBox widget) {
    }
}
