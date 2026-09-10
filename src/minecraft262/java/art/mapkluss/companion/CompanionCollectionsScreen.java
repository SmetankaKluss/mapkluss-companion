package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CompanionCollectionsScreen extends WorkshopTrackerScreen {
    @Override
    protected boolean submitFocusedInput() {
        if (searchInput != null && searchInput.isFocused()) {
            applySearch();
            return true;
        }
        if (createInput != null && createInput.isFocused()) {
            if (!fixture) createCollection();
            return true;
        }
        return false;
    }

    private static final int PANEL_WIDTH = 1120;
    private static final int ROWS = 8;
    private static final int LIST_Y = 156;
    private static final int ROW_HEIGHT = 24;
    private static final int BOTTOM_RESERVED = 40;
    private static final int SIDE_RAIL_WIDTH = 128;
    private static final int SIDE_RAIL_GAP = 22;

    private final Screen parent;
    private final List<CompanionCollection> collections = new ArrayList<>();
    private String status = "";
    private String createDraft = "";
    private String searchQuery = "";
    private EditBox createInput;
    private boolean hasLoadedOnce;
    private int page;
    private AbstractWidget pageButton;
    private EditBox searchInput;

    public CompanionCollectionsScreen(Screen parent) { this(parent,false); }

    CompanionCollectionsScreen(Screen parent,boolean fixture) {
        super(Component.literal("Коллекции MapKluss"));
        this.parent = parent;

        this.fixture=fixture;
    }

    private WorkshopTheme workshopTheme=WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final boolean fixture;
    private boolean fixtureApplied;
    private boolean requested;

    @Override
    public void onClose() {

        client().gui.setScreen(parent);
    }

    void applyDevelopmentData(List<CompanionCollection> data,String nextStatus) {
        if(!fixture)return;
        collections.clear();
        collections.addAll(data);
        status=nextStatus;
    }

    private void prepareFixture() {
        if(!fixture || fixtureApplied)return;
        fixtureApplied=true;
        try {
            Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture")
                .getMethod("applyCollections",Object.class).invoke(null,this);
        } catch(ReflectiveOperationException ignored) { }
    }

    private MapKlussButton collectionButton(String id,String label,WorkshopIcon icon,WorkshopLayout.Rect r,boolean local,Runnable action) {
        var b=MapKlussButton.builder(CompanionI18n.text(label),button->{if(!fixture||local)action.run();}).action(id)
            .enabledWhen(()->!fixture||local).tooltip(CompanionI18n.text(label)).dimensions(r.x(),r.y(),r.width(),r.height());
        if(id.equals("collections.delete")||id.equals("collections.remove_art"))b.danger();
        if(id.equals("collections.create")||id.equals("collections.rename"))b.gold();
        return addRenderableWidget(b.build().workshop(workshopTheme,icon));
    }

    private void workshopNavigation() {
        try {workshopTheme=WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme());}
        catch(Exception ignored) {}
        var s=WorkshopCollectionLayout.at(width,height);
        var nav=s.navigation();
        int slot=(nav.width()-28)/5;
        WorkshopIcon[] icons={WorkshopIcon.LIBRARY,WorkshopIcon.LENS,WorkshopIcon.SCAN,WorkshopIcon.TRACKER,WorkshopIcon.ACCOUNT};
        String[] labels={"Библиотека","Lens","Скан","Трекер","Аккаунт"};
        for(int i=0;i<5;i++) {
            var d=CompanionUiLayout.Destination.values()[i];
            collectionButton(CompanionActionInventory.navigationAction(d),labels[i],icons[i],
                new WorkshopLayout.Rect(nav.x()+i*slot,nav.y(),slot-4,28),true,()->openDestination(d));
        }
        collectionButton("global.back","Назад",WorkshopIcon.BACK,new WorkshopLayout.Rect(nav.right()-24,nav.y(),24,28),true,this::onClose);
        collectionButton("global.language",CompanionI18n.toggleLabel(client()),null,
            new WorkshopLayout.Rect(s.heading().right()-40,s.heading().y(),40,20),true,()->{
                try {CompanionI18n.toggle(client());init();}catch(Exception e){status="Не удалось сохранить выбор.";}
            });
    }

    private void workshopSearch() {
        var r=WorkshopCollectionLayout.at(width,height).search();
        searchInput=new EditBox(font,r.x(),r.y(),r.width()-56,24,CompanionI18n.text("Поиск"));
        searchInput.setHint(CompanionI18n.text("Поиск"));
        searchInput.setMaxLength(80);
        searchInput.setValue(searchQuery);
        searchInput.setResponder(value->searchQuery=value);
        addRenderableWidget(searchInput);
        collectionButton("collections.search","Найти",WorkshopIcon.SEARCH,new WorkshopLayout.Rect(r.right()-52,r.y(),24,24),true,this::applySearch);
        collectionButton("collections.search_clear","Сброс",WorkshopIcon.CLOSE,new WorkshopLayout.Rect(r.right()-24,r.y(),24,24),true,this::clearSearch);
    }


    @Override
    protected void init() {
        prepareFixture();
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
        if(!fixture && !requested) { requested=true; loadCollections(); }
    }

    private void rebuildControls() {
        workshopNavigation();
        workshopSearch();
        var s=WorkshopCollectionLayout.at(width,height);
        var r=s.manage();
        createInput=new EditBox(font,r.x(),r.y(),r.width()-84,24,CompanionI18n.text("Новая коллекция"));
        createInput.setHint(CompanionI18n.text("Новая коллекция"));
        createInput.setMaxLength(80);
        createInput.setValue(createDraft);
        createInput.setResponder(value->createDraft=value);
        addRenderableWidget(createInput);
        collectionButton("collections.create","Создать",WorkshopIcon.CHECK,new WorkshopLayout.Rect(r.right()-80,r.y(),80,24),false,this::createCollection);
        collectionButton("collections.refresh","Обновить",WorkshopIcon.REFRESH,new WorkshopLayout.Rect(s.footer().x(),s.footer().y(),24,20),false,this::loadCollections);
        pageButton=collectionButton("collections.page_next",pageButtonText().getString(),null,new WorkshopLayout.Rect(s.footer().right()-88,s.footer().y(),88,20),true,this::nextPage);
        collectionButton("collections.open_site","Облако",WorkshopIcon.LINK,new WorkshopLayout.Rect(s.footer().x()+28,s.footer().y(),24,20),false,this::openCollectionsSite);
        page=clampPage(page);
        updatePageButton();
        focusCreateInput();
    }

    private void loadCollections() {
        status = "Загрузка коллекций...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                ItemListResponse<CompanionCollection> response = runtime.apiClient().collections();
                List<CompanionCollection> loadedCollections = response.items() == null ? List.of() : response.items();
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), loadedCollections);
                runOnClient(() -> {
                    hasLoadedOnce = true;
                    collections.clear();
                    collections.addAll(loadedCollections);
                    page = clampPage(page);
                    status = "";
                    clearWidgets();
                    rebuildControls();
                    rebuildCollectionButtons();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    loadCachedCollections(e.getMessage());
                }
            }
        });
    }

    private void loadCachedCollections(String reason) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            LibraryCache.CachedCollections cached = runtime.libraryCache().readCollections(runtime.sessionStore().userId());
            runOnClient(() -> {
                hasLoadedOnce = true;
                collections.clear();
                if (!cached.isEmpty()) collections.addAll(cached.items());
                page = clampPage(page);
                status = cached.isEmpty()
                    ? CompanionUiErrors.message("sync", reason)
                    : "Показан локальный кеш.";
                clearWidgets();
                rebuildControls();
                rebuildCollectionButtons();
            });
        } catch (Exception cacheError) {
            runOnClient(() -> status = CompanionUiErrors.message("sync", reason));
        }
    }

    private void rebuildCollectionButtons() {
        var s=WorkshopCollectionLayout.at(width,height);
        var filtered=filteredCollections();
        page=clampPage(page);
        int start=page*s.rows(),end=Math.min(start+s.rows(),filtered.size());
        for(int i=start;i<end;i++) {
            var c=filtered.get(i);
            var r=s.row(i-start);
            collectionButton("collections.open",c.name()+"  ["+c.itemCount()+"]",WorkshopIcon.FOLDER,r,true,
                ()->client().gui.setScreen(new CompanionCollectionItemsScreen(this,c,fixture)));
        }
        updatePageButton();
    }

    void applyUpdatedCollection(CompanionCollection updatedCollection) {
        boolean found = false;
        for (int i = 0; i < collections.size(); i++) {
            if (collections.get(i).id().equals(updatedCollection.id())) {
                collections.set(i, updatedCollection);
                found = true;
                break;
            }
        }
        if (!found) collections.add(0, updatedCollection);
        page = clampPage(page);
        status = "Коллекция обновлена: " + updatedCollection.name();
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
    }

    void applyDeletedCollection(String collectionId, String collectionName) {
        collections.removeIf(existing -> existing.id().equals(collectionId));
        page = clampPage(page);
        status = "Коллекция удалена: " + collectionName;
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        context.fill(0,0,width,height,0x88000000);
        var s=WorkshopCollectionLayout.at(width,height);
        WorkshopChrome.frame(context::fill,s.frame(),workshopTheme);
        context.fill(s.navigation().x(),s.navigation().bottom()+1,s.navigation().right(),s.navigation().bottom()+2,workshopTheme.color("border-subtle"));
        WorkshopDraw.text(context,font,CompanionI18n.translate("Коллекции"),s.heading().x()+4,s.heading().y()+6,s.heading().width()-48,workshopTheme.color("text-primary"));
        if(filteredCollections().isEmpty()) WorkshopDraw.text(context,font,CompanionI18n.translate("Пусто"),s.list().x()+6,s.list().y()+10,s.list().width()-12,workshopTheme.color("text-secondary"));
        WorkshopDraw.text(context,font,CompanionI18n.translate(status),s.footer().x()+60,s.footer().y()+6,s.footer().width()-152,workshopTheme.color("text-secondary"));
        super.extractRenderState(context,mouseX,mouseY,delta);
    }

    private final java.util.concurrent.atomic.AtomicBoolean creatingCollection = new java.util.concurrent.atomic.AtomicBoolean();

    private void createCollection() {
        String name = createInput == null ? "" : createInput.getValue().trim();
        if (name.isEmpty()) {
            status = "Введите название коллекции.";
            return;
        }
        if (!creatingCollection.compareAndSet(false, true)) return;
        createDraft = name;
        status = "Создание коллекции...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                CompanionCollection created = runtime.apiClient().createCollection(name).collection();
                List<CompanionCollection> updated = new ArrayList<>();
                updated.add(created);
                for (CompanionCollection existing : collections) {
                    if (!existing.id().equals(created.id())) updated.add(existing);
                }
                runtime.libraryCache().upsertCollection(runtime.sessionStore().userId(), created);
                runOnClient(() -> {
                    collections.clear();
                    collections.addAll(updated);
                    page = clampPage(page);
                    createDraft = "";
                    status = "Коллекция создана: " + created.name();
                    clearWidgets();
                    rebuildControls();
                    rebuildCollectionButtons();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("save", e));
                }
            }
        }).whenComplete((ignored, error) -> creatingCollection.set(false));
    }

    private void openCollectionsSite() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri("/cloud"));
            status = "Коллекции открыты на сайте.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getValue().trim();
        page = 0;
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
    }

    private void clearSearch() {
        searchQuery = "";
        page = 0;
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
    }

    private void nextPage() {
        int totalPages = totalPages();
        if (totalPages <= 1) return;
        page = (page + 1) % totalPages;
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
    }

    private void updatePageButton() {
        if (pageButton == null) return;
        pageButton.setMessage(pageButtonText());
        pageButton.active = totalPages() > 1;
    }

    private Component pageButtonText() {
        return Component.literal("Стр " + (page + 1) + "/" + totalPages());
    }

    private int totalPages() {
        int rows = CompanionLayout.pageSize(visibleRows());
        return Math.max(1, (filteredCollections().size() + rows - 1) / rows);
    }

    private int clampPage(int current) {
        return Math.max(0, Math.min(current, totalPages() - 1));
    }

    private String pageSummary() {
        List<CompanionCollection> visibleCollections = filteredCollections();
        if (visibleCollections.isEmpty()) return "Коллекции 0/0";
        int rows = visibleRows();
        if (rows <= 0) return "Коллекции 0 / " + visibleCollections.size();
        int start = page * rows + 1;
        int end = Math.min((page + 1) * rows, visibleCollections.size());
        String suffix = searchQuery.isBlank() ? "" : " / фильтр";
        return "Коллекции " + start + "-" + end + " / " + visibleCollections.size() + suffix;
    }

    private int visibleRows() { return WorkshopCollectionLayout.at(width,height).rows(); }

    private int bottomReserved() {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return sideRailLayout(panelWidth, left) ? 34 : 66;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return false;
    }

    private CompanionUiLayout.Shell collectionsShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private int listTop() {
        return collectionsShell().content().y() + 86;
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destinationLabel(destination)))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(parent));
            case LENS -> client().gui.setScreen(new LensScreen(this, fixture));
            case SCAN -> client().gui.setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this, fixture));
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

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return MapKlussUi.centeredLeft(width, panelWidth);
    }

    private List<CompanionCollection> filteredCollections() {
        if (searchQuery.isBlank()) return collections;
        List<CompanionCollection> filtered = new ArrayList<>();
        for (CompanionCollection collection : collections) {
            if (collection.name().toLowerCase(java.util.Locale.ROOT).contains(searchQuery.toLowerCase(java.util.Locale.ROOT))) filtered.add(collection);
        }
        return filtered;
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }

    private void focusCreateInput() {
        if (searchInput != null) searchInput.setFocused(false);
        if (createInput == null) return;
        setFocused(null);
        createInput.setFocused(false);
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
    }

    private void expireSessionLocally(boolean keepCachedContent, String nextStatus) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            CompanionAuthSupport.clearSessionQuietly(runtime);
        } catch (Exception ignored) {
        }
        runOnClient(() -> {
            if (!keepCachedContent) {
                collections.clear();
                page = 0;
            }
            status = nextStatus;
            clearWidgets();
            rebuildControls();
            rebuildCollectionButtons();
        });
    }
}
