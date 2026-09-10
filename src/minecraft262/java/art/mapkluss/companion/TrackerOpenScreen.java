package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TrackerOpenScreen extends WorkshopTrackerScreen {
    private static final int HISTORY_ROW_HEIGHT = 23;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final List<TrackerHistoryEntry> history = new ArrayList<>();
    private EditBox sessionInput;
    private String status = "";
    private int historyPage;

    public TrackerOpenScreen(Screen parent) {
        super(Component.literal("Трекер сборки MapKluss"));
        this.parent = parent;
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

    private String pendingSession="";
    private boolean historyLoaded;
    public TrackerOpenScreen(Screen parent,boolean fixture) { this(parent); this.fixture=fixture; }
    void applyDevelopmentData(List<TrackerHistoryEntry> entries,String message) {
        if(!fixture)return;
        history.clear(); history.addAll(entries); status=message;
    }
    @Override protected void init() {
        if(sessionInput!=null)pendingSession=sessionInput.getValue();
        if(fixture&&!fixtureApplied) {
            fixtureApplied=true;
            try { Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture").getMethod("applyTrackerOpen",Object.class).invoke(null,this); }
            catch(ReflectiveOperationException ignored) { }
        }
        if(!fixture&&!historyLoaded){loadHistory();historyLoaded=true;}
        clearWidgets();
        var s=WorkshopTrackerLayout.at(width,height);
        workshopNavigation(s);
        trackerButton("tracker.build.open", CompanionI18n.english(client()) ? "Build" : "Стройка", WorkshopIcon.TRACKER,
            new WorkshopLayout.Rect(s.modes().x(), s.modes().y(), 100, 20), ()->!fixture, false,
            ()->client().gui.setScreen(new LiveBuildScreen(this)));
        var t=s.title();
        sessionInput=new EditBox(font,t.x(),t.y(),t.width()-60,20,CompanionI18n.text("UUID сборки"));
        sessionInput.setHint(CompanionI18n.text("UUID сборки"));
        sessionInput.setMaxLength(64);
        sessionInput.setValue(pendingSession);
        addRenderableWidget(sessionInput);
        trackerButton("tracker.open_session","Открыть",WorkshopIcon.TRACKER,
            new WorkshopLayout.Rect(t.right()-56,t.y(),56,20),()->!sessionInput.getValue().isBlank(),false,
            ()->openSession(sessionInput.getValue().trim()));
        int rows=historyRowCapacity();
        int pageSize=Math.max(1,rows);
        historyPage=Math.max(0,Math.min(historyPage,totalPages(pageSize)-1));
        for(int i=0;i<rows&&historyPage*pageSize+i<history.size();i++){
            var entry=history.get(historyPage*pageSize+i);
            var r=new WorkshopLayout.Rect(s.table().x(),s.modes().bottom()+4+i*28,s.table().width(),24);
            trackerButton("tracker.open_session",historyLabel(entry),WorkshopIcon.TRACKER,
                new WorkshopLayout.Rect(r.x(),r.y(),r.width()-32,24),()->true,false,()->openSession(entry.sessionId()));
            trackerButton("tracker.open_history_art","Арт",WorkshopIcon.LIBRARY,
                new WorkshopLayout.Rect(r.right()-28,r.y(),28,24),
                ()->entry.artId()!=null&&!entry.artId().isBlank()&&!fixture,false,()->openHistoryArt(entry));
        }
        trackerButton("tracker.history_page","Стр "+(historyPage+1)+"/"+totalPages(pageSize),null,
            new WorkshopLayout.Rect(s.modes().right()-94,s.modes().y(),94,20),()->totalPages(pageSize)>1,false,
            ()->{historyPage=(historyPage+1)%totalPages(pageSize);init();});
        setFocused(null);
    }
    @Override public void removed(){historyLoaded=false;super.removed();}
    private int historyRowCapacity() {
        var s=WorkshopTrackerLayout.at(width,height);
        return Math.max(0,(s.footer().y()-4-s.modes().bottom()-4)/28);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta){
        var s=WorkshopTrackerLayout.at(width,height);
        context.fill(0,0,width,height,0x88000000);
        WorkshopChrome.frame(context::fill,s.frame(),workshopTheme);
        if(history.isEmpty())text(context,"Недавних сессий пока нет",s.table(),"text-secondary");
        text(context,status,new WorkshopLayout.Rect(s.footer().x(),s.footer().y(),s.footer().width()-40,20),"text-secondary");
        super.extractRenderState(context,mouseX,mouseY,delta);
    }
    @Override protected boolean submitFocusedInput() {
        if (sessionInput == null || !sessionInput.isFocused()) return false;
        openSession(sessionInput.getValue()); return true;
    }

    private void openSession(String sessionId) {
        String trimmed = sessionId == null ? "" : sessionId.trim();
        if (trimmed.isEmpty()) {
            status = "Сначала вставьте UUID трекера.";
            return;
        }
        client().gui.setScreen(new TrackerSessionScreen(this, trimmed, fixture));
    }

    private void loadHistory() {
        try {
            TrackerHistoryStore store = TrackerHistoryStore.load(LitematicaPaths.trackerHistoryPath(client().gameDirectory.toPath()));
            history.clear();
            history.addAll(store.entries());
        } catch (Exception error) {
            history.clear();
            status = CompanionUiErrors.message("tracker", error);
        }
    }

    private int totalPages(int pageSize) {
        return Math.max(1, (history.size() + pageSize - 1) / pageSize);
    }

    private String historyLabel(TrackerHistoryEntry entry) {
        String entryTitle = entry.title() == null || entry.title().isBlank() ? entry.sessionId() : entry.title();
        return entryTitle + " / " + readableMode(entry.mode()) + " / " + formatInstant(entry.openedAt());
    }

    private String readableMode(String mode) {
        if ("building".equals(mode)) return CompanionI18n.translate("Стройка");
        if ("gathering".equals(mode)) return CompanionI18n.translate("Сбор");
        return CompanionI18n.translate("Трекер");
    }

    private void openHistoryArt(TrackerHistoryEntry entry) {
        if (entry.artId() == null || entry.artId().isBlank()) return;
        client().gui.setScreen(new CompanionArtScreen(this, entry.artId(), entry.title()));
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().gui.setScreen(new LensScreen(this, fixture));
            case SCAN -> client().gui.setScreen(new ScanScreen(this, fixture));
            case TRACKER -> { }
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

    private String formatInstant(String value) {
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return value == null ? CompanionI18n.translate("неизвестно") : value;
        }
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }
}
