package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CompanionArtCollectionsScreen extends WorkshopTrackerScreen {
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

    private static final int ROWS = 7;
    private static final int ROW_HEIGHT = 46;
    private static final int BOTTOM_RESERVED = 64;
    private static final int SIDE_RAIL_WIDTH = 128;
    private static final int SIDE_RAIL_GAP = 22;

    private final CompanionArtScreen parent;
    private final String artId;
    private final String fallbackTitle;
    private final List<CompanionCollection> collections = new ArrayList<>();
    private CompanionManifest manifest;
    private EditBox createInput;
    private EditBox searchInput;
    private boolean hasLoadedOnce;
    private String status = "";
    private String createDraft = "";
    private String searchQuery = "";
    private int page;
    private AbstractWidget pageButton;

    public CompanionArtCollectionsScreen(CompanionArtScreen parent, String artId, String fallbackTitle, CompanionManifest manifest) { this(parent, artId, fallbackTitle, manifest,false); }

    CompanionArtCollectionsScreen(CompanionArtScreen parent, String artId, String fallbackTitle, CompanionManifest manifest,boolean fixture) {
        super(Component.literal("Коллекции арта"));
        this.parent = parent;
        this.artId = artId;
        this.fallbackTitle = fallbackTitle;
        this.manifest = manifest;

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

    void applyDevelopmentManifest(CompanionManifest value) {
        if(fixture)manifest=value;
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

        page=clampPage(page);
        updatePageButton();
        focusCreateInput();
    }


    @Override
    protected void init() {
        prepareFixture();
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
        if(!fixture && !requested) { requested=true; loadCollections(); }
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
                    init();
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
                status = cached.isEmpty() ? CompanionUiErrors.message("sync", reason) : "Показан локальный кеш.";
                clearWidgets();
                init();
            });
        } catch (Exception cacheError) {
            runOnClient(() -> status = CompanionUiErrors.message("sync", reason));
        }
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
                CollectionCreateResponse response = runtime.apiClient().createCollection(name);
                CompanionCollection created = response.collection();
                runtime.apiClient().setCollectionItem(created.id(), artId, true);
                CompanionCollection createdWithCount = new CompanionCollection(
                    created.id(),
                    created.name(),
                    created.createdAt(),
                    created.updatedAt(),
                    Math.max(1, created.itemCount())
                );

                List<CompanionCollection> updatedCollections = new ArrayList<>();
                updatedCollections.add(createdWithCount);
                updatedCollections.addAll(collections.stream().filter(existing -> !existing.id().equals(created.id())).toList());
                runtime.libraryCache().upsertCollection(runtime.sessionStore().userId(), createdWithCount);

                CompanionManifest updatedManifest = withCollectionSelection(manifest, created.id(), true);
                runtime.manifestCache().write(runtime.sessionStore().userId(), updatedManifest);
                runtime.libraryCache().updateCollectionItems(runtime.sessionStore().userId(), created.id(), toLibraryItem(updatedManifest), true);

                runOnClient(() -> {
                    manifest = updatedManifest;
                    collections.clear();
                    collections.addAll(updatedCollections);
                    page = clampPage(page);
                    parent.applyManifestUpdate(updatedManifest, "Коллекция создана, арт добавлен.");
                    createDraft = "";
                    status = "Коллекция создана, арт добавлен.";
                    clearWidgets();
                    init();
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

    private void toggleCollection(CompanionCollection collection) {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        boolean selected = !manifest.collectionIds().contains(collection.id());
        status = selected ? "Добавление в коллекцию..." : "Удаление из коллекции...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                runtime.apiClient().setCollectionItem(collection.id(), artId, selected);
                boolean previouslySelected = manifest.collectionIds().contains(collection.id());
                CompanionManifest updatedManifest = withCollectionSelection(manifest, collection.id(), selected);
                runtime.manifestCache().write(runtime.sessionStore().userId(), updatedManifest);
                runtime.libraryCache().setCollectionItemState(
                    runtime.sessionStore().userId(), collection.id(), toLibraryItem(updatedManifest),
                    previouslySelected, selected
                );
                List<CompanionCollection> updatedCollections = new ArrayList<>();
                for (CompanionCollection existing : collections) {
                    if (!existing.id().equals(collection.id())) {
                        updatedCollections.add(existing);
                        continue;
                    }
                    int nextCount = selected
                        ? existing.itemCount() + (manifest.collectionIds().contains(collection.id()) ? 0 : 1)
                        : Math.max(0, existing.itemCount() - 1);
                    updatedCollections.add(new CompanionCollection(
                        existing.id(),
                        existing.name(),
                        existing.createdAt(),
                        existing.updatedAt(),
                        nextCount
                    ));
                }
                runOnClient(() -> {
                    manifest = updatedManifest;
                    collections.clear();
                    collections.addAll(updatedCollections);
                    page = clampPage(page);
                    parent.applyManifestUpdate(updatedManifest, selected ? "Добавлено в " + collection.name() + "." : "Убрано из " + collection.name() + ".");
                    status = selected ? "Добавлено в " + collection.name() + "." : "Убрано из " + collection.name() + ".";
                    clearWidgets();
                    init();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(true, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("sync", e));
                }
            }
        });
    }

    private void rebuildCollectionButtons() {
        var s=WorkshopCollectionLayout.at(width,height);
        var filtered=filteredCollections();
        page=clampPage(page);
        int start=page*s.rows(),end=Math.min(start+s.rows(),filtered.size());
        for(int i=start;i<end;i++) {
            var c=filtered.get(i);
            var r=s.row(i-start);
            boolean selected=manifest!=null && manifest.collectionIds().contains(c.id());
            collectionButton(selected?"collections.remove_art":"collections.add_art",c.name()+"  ["+c.itemCount()+"]",
                selected?WorkshopIcon.CHECK:WorkshopIcon.LAYERS,
                new WorkshopLayout.Rect(r.x(),r.y(),r.width()-32,28),false,()->toggleCollection(c)).setSelected(selected);
            collectionButton("collections.open","Открыть коллекцию",WorkshopIcon.FOLDER,
                new WorkshopLayout.Rect(r.right()-28,r.y(),28,28),true,()->client().gui.setScreen(new CompanionCollectionItemsScreen(this,c,fixture)));
        }
        updatePageButton();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        context.fill(0,0,width,height,0x88000000);
        var s=WorkshopCollectionLayout.at(width,height);
        WorkshopChrome.frame(context::fill,s.frame(),workshopTheme);
        context.fill(s.navigation().x(),s.navigation().bottom()+1,s.navigation().right(),s.navigation().bottom()+2,workshopTheme.color("border-subtle"));
        WorkshopDraw.text(context,font,(fallbackTitle==null?CompanionI18n.translate("Коллекции"):fallbackTitle),s.heading().x()+4,s.heading().y()+6,s.heading().width()-48,workshopTheme.color("text-primary"));
        if(filteredCollections().isEmpty()) WorkshopDraw.text(context,font,CompanionI18n.translate("Пусто"),s.list().x()+6,s.list().y()+10,s.list().width()-12,workshopTheme.color("text-secondary"));
        WorkshopDraw.text(context,font,CompanionI18n.translate(status),s.footer().x()+60,s.footer().y()+6,s.footer().width()-152,workshopTheme.color("text-secondary"));
        super.extractRenderState(context,mouseX,mouseY,delta);
    }



    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getValue().trim();
        page = 0;
        clearWidgets();
        init();
    }

    private void clearSearch() {
        searchQuery = "";
        page = 0;
        clearWidgets();
        init();
    }

    private void nextPage() {
        int totalPages = totalPages();
        if (totalPages <= 1) return;
        page = (page + 1) % totalPages;
        clearWidgets();
        init();
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
        return 30;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return false;
    }

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return collectionsWork(collectionsShell()).x();
    }

    private CompanionUiLayout.Shell collectionsShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect collectionsWork(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 14, content.y() + 12,
            Math.max(1, content.width() - 28), Math.max(1, content.height() - 24));
    }

    private int listY() {
        return collectionsWork(collectionsShell()).y() + 78;
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

    private List<CompanionCollection> filteredCollections() {
        if (searchQuery.isBlank()) return collections;
        List<CompanionCollection> filtered = new ArrayList<>();
        for (CompanionCollection collection : collections) {
            if (collection.name().toLowerCase(java.util.Locale.ROOT).contains(searchQuery.toLowerCase(java.util.Locale.ROOT))) filtered.add(collection);
        }
        return filtered;
    }

    private CompanionLibraryItem toLibraryItem(CompanionManifest source) {
        return new CompanionLibraryItem(
            source.artId(),
            source.versionId(),
            source.title(),
            source.privacy(),
            source.grid(),
            source.mode(),
            source.previewUrl(),
            source.updatedAt(),
            source.isFavorite()
        );
    }

    private static CompanionManifest withCollectionSelection(CompanionManifest manifest, String collectionId, boolean selected) {
        LinkedHashSet<String> collectionIds = new LinkedHashSet<>(manifest.collectionIds());
        if (selected) collectionIds.add(collectionId);
        else collectionIds.remove(collectionId);
        return new CompanionManifest(
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
            List.copyOf(collectionIds),
            manifest.artifacts(),
            manifest.updatedAt()
        );
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
            init();
        });
    }
}
