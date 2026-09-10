package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompanionCollectionItemsScreen extends WorkshopTrackerScreen {
    @Override
    protected boolean submitFocusedInput() {
        if (searchInput != null && searchInput.isFocused()) {
            applySearch();
            return true;
        }
        if (nameInput != null && nameInput.isFocused()) {
            if (!fixture) saveCollectionName();
            return true;
        }
        return false;
    }

    private static final int SECTION_WIDTH = 1120;
    private static final int ROWS = 8;
    private static final int ROW_HEIGHT = 46;
    private static final int BOTTOM_RESERVED = 40;
    private static final int SIDE_RAIL_WIDTH = 128;
    private static final int SIDE_RAIL_GAP = 22;

    private final Screen parent;
    private CompanionCollection collection;
    private final List<CompanionLibraryItem> items = new ArrayList<>();
    private EditBox nameInput;
    private EditBox searchInput;
    private MapKlussButton deleteCollectionButton;
    private final CompanionConfirmation deleteCollectionConfirmation = new CompanionConfirmation();
    private String status = "";
    private String nameDraft;
    private String searchQuery = "";
    private int page;
    private AbstractWidget pageButton;

    public CompanionCollectionItemsScreen(Screen parent, CompanionCollection collection) { this(parent, collection,false); }

    CompanionCollectionItemsScreen(Screen parent, CompanionCollection collection,boolean fixture) {
        super(Component.literal(collection.name()));
        this.parent = parent;
        this.collection = collection;
        this.nameDraft = collection.name();

        this.fixture=fixture;
    }

    private WorkshopTheme workshopTheme=WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final boolean fixture;
    private boolean fixtureApplied;
    private boolean requested;

    @Override
    public void onClose() {
        deleteCollectionConfirmation.reset();
        client().gui.setScreen(parent);
    }

    void applyDevelopmentData(List<CompanionLibraryItem> data,String nextStatus) {
        if(!fixture)return;
        items.clear();
        items.addAll(data);
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
        rebuildArtButtons();
        if(!fixture && !requested) { requested=true; loadItems(); }
    }

    private void rebuildControls() {
        workshopNavigation();
        workshopSearch();
        var s=WorkshopCollectionLayout.at(width,height);
        var r=s.manage();
        nameInput=new EditBox(font,r.x(),r.y(),r.width()-56,24,CompanionI18n.text("Название коллекции"));
        nameInput.setMaxLength(80);
        nameInput.setValue(nameDraft);
        nameInput.setResponder(value->nameDraft=value);
        addRenderableWidget(nameInput);
        collectionButton("collections.rename","Сохранить",WorkshopIcon.CHECK,new WorkshopLayout.Rect(r.right()-52,r.y(),24,24),false,this::saveCollectionName);
        deleteCollectionButton=collectionButton("collections.delete",deleteCollectionConfirmation.armed()?"Подтвердить удаление":"Удалить",WorkshopIcon.DELETE,
            new WorkshopLayout.Rect(r.right()-24,r.y(),24,24),false,this::requestDeleteCollection);
        collectionButton("collections.refresh","Обновить",WorkshopIcon.REFRESH,new WorkshopLayout.Rect(s.footer().x(),s.footer().y(),24,20),false,this::loadItems);
        collectionButton("collections.open_site","Сайт",WorkshopIcon.LINK,new WorkshopLayout.Rect(s.footer().x()+28,s.footer().y(),24,20),false,this::openCollectionSite);
        pageButton=collectionButton("collections.page_next",pageButtonText().getString(),null,new WorkshopLayout.Rect(s.footer().right()-88,s.footer().y(),88,20),true,this::nextPage);
        page=clampPage(page);
        updatePageButton();
        focusNameInput();
    }

    private void loadItems() {
        status = "Загрузка коллекции...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                ItemListResponse<CompanionLibraryItem> response = runtime.apiClient().collectionItems(collection.id());
                List<CompanionLibraryItem> loadedItems = response.items() == null ? List.of() : response.items();
                runtime.libraryCache().writeCollectionItems(runtime.sessionStore().userId(), collection.id(), loadedItems);
                CompanionCollection refreshedCollection = new CompanionCollection(
                    collection.id(),
                    collection.name(),
                    collection.createdAt(),
                    collection.updatedAt(),
                    loadedItems.size()
                );
                runtime.libraryCache().upsertCollection(runtime.sessionStore().userId(), refreshedCollection);
                runOnClient(() -> {
                    if (java.util.Objects.equals(nameDraft, collection.name())) nameDraft = refreshedCollection.name();
                    collection = refreshedCollection;
                    items.clear();
                    items.addAll(loadedItems);
                    page = clampPage(page);
                    if (parent instanceof CompanionCollectionsScreen collectionsScreen) {
                        collectionsScreen.applyUpdatedCollection(refreshedCollection);
                    }
                    status = "";
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    loadCachedItems(e.getMessage());
                }
            }
        });
    }

    private void loadCachedItems(String reason) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            LibraryCache.CachedLibrary cached = runtime.libraryCache().readCollectionItems(runtime.sessionStore().userId(), collection.id());
            runOnClient(() -> {
                items.clear();
                if (!cached.isEmpty()) items.addAll(cached.items());
                page = clampPage(page);
                status = cached.isEmpty()
                    ? CompanionUiErrors.message("sync", reason)
                    : "Показан локальный кеш.";
                clearWidgets();
                rebuildControls();
                rebuildArtButtons();
            });
        } catch (Exception cacheError) {
            runOnClient(() -> status = CompanionUiErrors.message("sync", reason));
        }
    }

    private void rebuildArtButtons() {
        var s=WorkshopCollectionLayout.at(width,height);
        var filtered=filteredItems();
        page=clampPage(page);
        int start=page*s.rows(),end=Math.min(start+s.rows(),filtered.size());
        for(int i=start;i<end;i++) {
            var item=filtered.get(i);
            var r=s.row(i-start);
            int tail=r.right()-128;
            collectionButton("collections.open_art",item.title(),WorkshopIcon.LIBRARY,
                new WorkshopLayout.Rect(r.x(),r.y(),r.width()-132,28),true,
                ()->client().gui.setScreen(new CompanionArtScreen(this,item.artId(),item.title(),fixture)));
            collectionButton("collections.open_tracker","Трекер",WorkshopIcon.TRACKER,new WorkshopLayout.Rect(tail,r.y(),28,28),false,()->quickOpenTracker(item));
            collectionButton("collections.open_editor","Редактор",WorkshopIcon.EDIT,new WorkshopLayout.Rect(tail+32,r.y(),28,28),false,()->openEditor(item.artId()));
            collectionButton("collections.open_site","Сайт",WorkshopIcon.LINK,new WorkshopLayout.Rect(tail+64,r.y(),28,28),false,()->openArtSite(item.artId()));
            collectionButton("collections.remove_art","Убрать",WorkshopIcon.DELETE,new WorkshopLayout.Rect(tail+96,r.y(),28,28),false,()->removeFromCollection(item));
        }
        updatePageButton();
    }

    void applyDeletedArt(String artId, String title) {
        boolean removed = items.removeIf(item -> item.artId().equals(artId));
        if (!removed) return;
        collection = new CompanionCollection(
            collection.id(),
            collection.name(),
            collection.createdAt(),
            collection.updatedAt(),
            Math.max(0, collection.itemCount() - 1)
        );
        page = clampPage(page);
        status = "Арт удален: " + title;
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
    }

    private void removeFromCollection(CompanionLibraryItem item) {
        MapKlussCompanionClient.LOGGER.info(
            "Removing art {} ({}) from collection {} ({}).",
            item.title(),
            item.artId(),
            collection.name(),
            collection.id()
        );
        status = "Удаление из коллекции...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                runtime.apiClient().setCollectionItem(collection.id(), item.artId(), false);
                syncAfterRemoval(runtime, item);
                runOnClient(() -> {
                    boolean removed = items.removeIf(existing -> existing.artId().equals(item.artId()));
                    if (!removed) return;
                    collection = new CompanionCollection(
                        collection.id(),
                        collection.name(),
                        collection.createdAt(),
                        collection.updatedAt(),
                        Math.max(0, collection.itemCount() - 1)
                    );
                    page = clampPage(page);
                    if (parent instanceof CompanionCollectionsScreen collectionsScreen) {
                        collectionsScreen.applyUpdatedCollection(collection);
                    }
                    status = "Убрано из " + collection.name() + ": " + item.title();
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                MapKlussCompanionClient.LOGGER.error(
                    "Failed removing art {} ({}) from collection {} ({}).",
                    item.title(),
                    item.artId(),
                    collection.name(),
                    collection.id(),
                    e
                );
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("delete", e));
                }
            }
        });
    }

    private void saveCollectionName() {
        String nextName = nameInput == null ? "" : nameInput.getValue().trim();
        if (nextName.isEmpty()) {
            status = "Введите название коллекции.";
            return;
        }
        if (nextName.equals(collection.name())) {
            status = "Название коллекции не изменилось.";
            return;
        }
        status = "Сохранение коллекции...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                CompanionCollection updated = runtime.apiClient().updateCollection(collection.id(), nextName).collection();
                CompanionCollection updatedWithCount = new CompanionCollection(
                    updated.id(),
                    updated.name(),
                    updated.createdAt(),
                    updated.updatedAt(),
                    collection.itemCount()
                );
                runtime.libraryCache().upsertCollection(runtime.sessionStore().userId(), updatedWithCount);
                runOnClient(() -> {
                    collection = updatedWithCount;
                    if (java.util.Objects.equals(nameDraft.trim(), nextName)) nameDraft = updatedWithCount.name();
                    page = clampPage(page);
                    if (parent instanceof CompanionCollectionsScreen collectionsScreen) {
                        collectionsScreen.applyUpdatedCollection(updatedWithCount);
                    }
                    status = "Название коллекции сохранено.";
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("save", e));
                }
            }
        });
    }

    private void deleteCollection() {
        status = "Удаление коллекции...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                runtime.apiClient().deleteCollection(collection.id());
                runtime.libraryCache().removeCollection(runtime.sessionStore().userId(), collection.id());
                runtime.libraryCache().writeCollectionItems(runtime.sessionStore().userId(), collection.id(), List.of());
                runOnClient(() -> {
                    if (parent instanceof CompanionCollectionsScreen collectionsScreen) {
                        collectionsScreen.applyDeletedCollection(collection.id(), collection.name());
                    }
                    client().gui.setScreen(parent);
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("delete", e));
                }
            }
        });
    }

    private void requestDeleteCollection() {
        if (!deleteCollectionConfirmation.confirmOrArm()) {
            status = "Нажмите ещё раз для подтверждения";
            if (deleteCollectionButton != null) deleteCollectionButton.setMessage(CompanionI18n.text("Подтвердить удаление"));
            return;
        }
        deleteCollection();
    }

    private void openCollectionSite() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri("/collection/" + collection.id()));
            status = "Коллекция открыта на сайте.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openArtSite(String artId) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri("/art/" + artId));
            status = "Арт открыт на сайте.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openEditor(String artId) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri("/?art=" + artId));
            status = "Арт открыт в редакторе.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void quickOpenTracker(CompanionLibraryItem item) {
        status = "Открываю трекер...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                TrackerForArtResult tracker = runtime.syncService().trackerForArtResult(item.artId());
                BuildSessionState session = tracker.session();
                if (tracker.created()) CompanionTelemetryManager.record(CompanionTelemetryEvent.TRACKER_CREATED);
                runOnClient(() -> client().gui.setScreen(new TrackerSessionScreen(this, session.id())));
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("tracker", e));
                }
            }
        });
    }

    private void syncAfterRemoval(CompanionRuntime runtime, CompanionLibraryItem item) throws Exception {
        String userId = runtime.sessionStore().userId();
        runtime.libraryCache().setCollectionItemState(userId, collection.id(), item, true, false);

        Optional<ManifestCache.CachedManifest> cachedManifest = runtime.manifestCache().read(userId, item.artId());
        if (cachedManifest.isPresent()) {
            CompanionManifest manifest = cachedManifest.get().manifest();
            LinkedHashSet<String> updatedIds = new LinkedHashSet<>(manifest.collectionIds());
            updatedIds.remove(collection.id());
            runtime.manifestCache().write(userId, new CompanionManifest(
                manifest.artId(),
                manifest.versionId(),
                manifest.ownerId(),
                manifest.title(),
                manifest.privacy(),
                manifest.grid(),
                manifest.mode(),
                manifest.minecraftVersion(),
                manifest.buildTechnique(),
                manifest.previewUrl(),
                manifest.isFavorite(),
                List.copyOf(updatedIds),
                manifest.artifacts(),
                manifest.updatedAt()
            ));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        context.fill(0,0,width,height,0x88000000);
        var s=WorkshopCollectionLayout.at(width,height);
        WorkshopChrome.frame(context::fill,s.frame(),workshopTheme);
        context.fill(s.navigation().x(),s.navigation().bottom()+1,s.navigation().right(),s.navigation().bottom()+2,workshopTheme.color("border-subtle"));
        WorkshopDraw.text(context,font,collection.name(),s.heading().x()+4,s.heading().y()+6,s.heading().width()-48,workshopTheme.color("text-primary"));
        if(filteredItems().isEmpty()) WorkshopDraw.text(context,font,CompanionI18n.translate("Пусто"),s.list().x()+6,s.list().y()+10,s.list().width()-12,workshopTheme.color("text-secondary"));
        WorkshopDraw.text(context,font,CompanionI18n.translate(status),s.footer().x()+60,s.footer().y()+6,s.footer().width()-152,workshopTheme.color("text-secondary"));
        super.extractRenderState(context,mouseX,mouseY,delta);
    }



    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getValue().trim();
        page = 0;
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
    }

    private void clearSearch() {
        searchQuery = "";
        page = 0;
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
    }

    private void nextPage() {
        int totalPages = totalPages();
        if (totalPages <= 1) return;
        page = (page + 1) % totalPages;
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
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
        return Math.max(1, (filteredItems().size() + rows - 1) / rows);
    }

    private int clampPage(int current) {
        return Math.max(0, Math.min(current, totalPages() - 1));
    }

    private String pageSummary() {
        List<CompanionLibraryItem> visibleItems = filteredItems();
        if (visibleItems.isEmpty()) return "Арты 0/0";
        int rows = visibleRows();
        if (rows <= 0) return "Арты 0 / " + visibleItems.size();
        int start = page * rows + 1;
        int end = Math.min((page + 1) * rows, visibleItems.size());
        String suffix = searchQuery.isBlank() ? "" : " / фильтр";
        return "Арты " + start + "-" + end + " / " + visibleItems.size() + suffix;
    }

    private int visibleRows() { return WorkshopCollectionLayout.at(width,height).rows(); }

    private int bottomReserved() {
        return 30;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return false;
    }

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return collectionWork(collectionShell()).x();
    }

    private CompanionUiLayout.Shell collectionShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect collectionWork(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 14, content.y() + 12,
            Math.max(1, content.width() - 28), Math.max(1, content.height() - 24));
    }

    private int listY() {
        return collectionWork(collectionShell()).y() + 78;
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destination.name()))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().gui.setScreen(new LensScreen(this, fixture));
            case SCAN -> client().gui.setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this, fixture));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this, fixture));
            default -> { }
        }
    }

    private List<CompanionLibraryItem> filteredItems() {
        if (searchQuery.isBlank()) return items;
        List<CompanionLibraryItem> filtered = new ArrayList<>();
        for (CompanionLibraryItem item : items) {
            String haystack = (item.title() + " " + item.gridLabel() + " " + item.mode() + " " + item.modeLabel() + " " + item.privacy() + " " + item.privacyLabel()).toLowerCase(java.util.Locale.ROOT);
            if (haystack.contains(searchQuery.toLowerCase(java.util.Locale.ROOT))) filtered.add(item);
        }
        return filtered;
    }

    private String itemLabel(CompanionLibraryItem item) {
        StringBuilder label = new StringBuilder();
        if (item.isFavorite()) label.append("★ ");
        label.append(item.title()).append("  ").append(item.gridLabel());
        label.append(" / ").append(item.modeLabel());
        label.append(" / ").append(item.privacyLabel());
        return label.toString();
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }

    private void focusNameInput() {
        if (searchInput != null) searchInput.setFocused(false);
        if (nameInput == null) return;
        setFocused(null);
        nameInput.setFocused(false);
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
                items.clear();
                page = 0;
            }
            status = nextStatus;
            clearWidgets();
            rebuildControls();
            rebuildArtButtons();
        });
    }
}
