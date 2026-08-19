package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompanionCollectionItemsScreen extends Screen {
    private static final int SECTION_WIDTH = 1120;
    private static final int ROWS = 8;
    private static final int ROW_HEIGHT = 46;
    private static final int BOTTOM_RESERVED = 40;
    private static final int SIDE_RAIL_WIDTH = 128;
    private static final int SIDE_RAIL_GAP = 22;

    private final Screen parent;
    private CompanionCollection collection;
    private final List<CompanionLibraryItem> items = new ArrayList<>();
    private TextFieldWidget nameInput;
    private TextFieldWidget searchInput;
    private MapKlussButton deleteCollectionButton;
    private final CompanionConfirmation deleteCollectionConfirmation = new CompanionConfirmation();
    private String status = "";
    private String nameDraft;
    private String searchQuery = "";
    private int page;
    private ClickableWidget pageButton;

    public CompanionCollectionItemsScreen(Screen parent, CompanionCollection collection) {
        super(Text.literal(collection.name()));
        this.parent = parent;
        this.collection = collection;
        this.nameDraft = collection.name();
    }

    @Override
    protected void init() {
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
        loadItems();
    }

    private void rebuildControls() {
        CompanionUiLayout.Shell shell = collectionShell();
        CompanionUiLayout.Rect work = collectionWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        int gap = 6;
        int metadataButtonWidth = 84;
        int nameWidth = Math.max(100, panelWidth - metadataButtonWidth * 2 - gap * 2);
        nameInput = new TextFieldWidget(textRenderer, left, work.y(), nameWidth, 22, CompanionI18n.text("Название коллекции"));
        nameInput.setText(nameDraft);
        nameInput.setMaxLength(80);
        nameInput.setChangedListener(value -> nameDraft = value);
        addDrawableChild(nameInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Сохранить"), button -> saveCollectionName()).action("collections.rename")
            .selected(true).dimensions(left + nameWidth + gap, work.y(), metadataButtonWidth, 22).build());
        deleteCollectionButton = addDrawableChild(MapKlussButton.builder(Text.literal(deleteCollectionConfirmation.armed() ? "Подтвердить удаление" : "Удалить"), button -> requestDeleteCollection()).danger()
            .action("collections.delete")
            .tooltip(CompanionI18n.text("Удалить коллекцию"))
            .navigationOrder(1000)
            .dimensions(left + nameWidth + metadataButtonWidth + gap * 2, work.y(), metadataButtonWidth, 22).build());

        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new TextFieldWidget(textRenderer, left, work.y() + 30, searchWidth, 22, CompanionI18n.text("Поиск артов"));
        searchInput.setMaxLength(80);
        searchInput.setText(searchQuery);
        searchInput.setChangedListener(value -> searchQuery = value);
        addDrawableChild(searchInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Найти"), button -> applySearch()).action("collections.search")
            .dimensions(left + searchWidth + gap, work.y() + 30, searchButtonWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сброс"), button -> clearSearch()).action("collections.search_clear")
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, work.y() + 30, searchButtonWidth, 22).build());

        int bottomButtonWidth = Math.max(48, (panelWidth - gap * 2) / 3);
        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> loadItems())
            .action("collections.refresh").technical()
            .dimensions(left, work.bottom() - 22, bottomButtonWidth, 22).build());
        pageButton = addDrawableChild(MapKlussButton.builder(pageButtonText(), button -> nextPage())
            .action("collections.page_next")
            .dimensions(left + bottomButtonWidth + gap, work.bottom() - 22, bottomButtonWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт"), button -> openCollectionSite())
            .action("collections.open_site").technical()
            .dimensions(left + (bottomButtonWidth + gap) * 2, work.bottom() - 22,
                panelWidth - (bottomButtonWidth + gap) * 2, 22).build());
        addNavigationControls(shell);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
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
                    collection = refreshedCollection;
                    nameDraft = refreshedCollection.name();
                    items.clear();
                    items.addAll(loadedItems);
                    page = clampPage(page);
                    if (parent instanceof CompanionCollectionsScreen collectionsScreen) {
                        collectionsScreen.applyUpdatedCollection(refreshedCollection);
                    }
                    status = "";
                    clearChildren();
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
                clearChildren();
                rebuildControls();
                rebuildArtButtons();
            });
        } catch (Exception cacheError) {
            runOnClient(() -> status = CompanionUiErrors.message("sync", reason));
        }
    }

    private void rebuildArtButtons() {
        CompanionUiLayout.Rect work = collectionWork(collectionShell());
        int panelWidth = work.width();
        int x = work.x();
        int y = listY();
        int gap = 4;
        int actionWidth = Math.max(46, (panelWidth - gap * 3) / 4);
        List<CompanionLibraryItem> visibleItems = filteredItems();
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleItems.size());
        for (int i = start; i < end; i++) {
            CompanionLibraryItem item = visibleItems.get(i);
            int rowY = y + (i - start) * ROW_HEIGHT;
            int actionY = rowY + 23;
        addDrawableChild(MapKlussButton.builder(MapKlussUi.clippedText(textRenderer, itemLabel(item), panelWidth - 8), button ->
                client().setScreen(new CompanionArtScreen(this, item.artId(), item.title()))).action("collections.open_art")
                .dimensions(x, rowY, panelWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Убрать"), button -> removeFromCollection(item)).action("collections.remove_art").danger()
                .dimensions(x, actionY, actionWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> quickOpenTracker(item)).action("collections.open_tracker")
                .dimensions(x + actionWidth + gap, actionY, actionWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт"), button -> openArtSite(item.artId())).action("collections.open_site")
                .dimensions(x + (actionWidth + gap) * 2, actionY, actionWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openEditor(item.artId())).action("collections.open_editor")
                .dimensions(x + (actionWidth + gap) * 3, actionY, actionWidth, 20).build());
        }
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
        clearChildren();
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
                    clearChildren();
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
        String nextName = nameInput == null ? "" : nameInput.getText().trim();
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
                    nameDraft = updatedWithCount.name();
                    page = clampPage(page);
                    if (parent instanceof CompanionCollectionsScreen collectionsScreen) {
                        collectionsScreen.applyUpdatedCollection(updatedWithCount);
                    }
                    status = "Название коллекции сохранено.";
                    clearChildren();
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
                    client().setScreen(parent);
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
            Util.getOperatingSystem().open(runtime.config().siteUri("/collection/" + collection.id()));
            status = "Коллекция открыта на сайте.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openArtSite(String artId) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getOperatingSystem().open(runtime.config().siteUri("/art/" + artId));
            status = "Арт открыт на сайте.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openEditor(String artId) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getOperatingSystem().open(runtime.config().siteUri("/?art=" + artId));
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
                runOnClient(() -> client().setScreen(new TrackerSessionScreen(this, session.id())));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.LIBRARY, CompanionI18n.translate("Коллекции"),
                java.util.List.of(collection.name()), status), false
        );
        CompanionUiLayout.Rect work = collectionWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        MapKlussUi.drawLeft(context, textRenderer, "Арты", left, listY() - 18, panelWidth, MapKlussUi.MUTED);
        boolean empty = filteredItems().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                "В коллекции пока нет артов",
                "Добавь арт через экран арта или сайт",
                left,
                listY() + 18,
                panelWidth,
                Math.max(40, work.bottom() - listY() - 30)
            );
        }
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.LIBRARY);
    }

    private void drawActionGroup(DrawContext context) {
        int panelWidth = collectionWork(collectionShell()).width();
        int left = screenLeft(panelWidth);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Управление", left, panelWidth, height - 58, 20);
    }

    private void drawSideRailSections(DrawContext context, int railLeft) {
        MapKlussUi.drawSectionAt(context, textRenderer, "Список", railLeft, SIDE_RAIL_WIDTH, 60, 78);
        MapKlussUi.drawSectionAt(context, textRenderer, "Сайт", railLeft, SIDE_RAIL_WIDTH, 146, 54);
    }

    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getText().trim();
        page = 0;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private void clearSearch() {
        searchQuery = "";
        page = 0;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private void nextPage() {
        int totalPages = totalPages();
        if (totalPages <= 1) return;
        page = (page + 1) % totalPages;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private void updatePageButton() {
        if (pageButton == null) return;
        pageButton.setMessage(pageButtonText());
        pageButton.active = totalPages() > 1;
    }

    private Text pageButtonText() {
        return Text.literal("Стр " + (page + 1) + "/" + totalPages());
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

    private int visibleRows() {
        CompanionUiLayout.Rect work = collectionWork(collectionShell());
        return Math.max(0, Math.min(ROWS, (work.bottom() - 30 - listY()) / ROW_HEIGHT));
    }

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
            addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destination.name()))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().setScreen(new LensScreen(this));
            case SCAN -> client().setScreen(new ScanScreen(this));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> client().setScreen(new CompanionAccountScreen(this));
            default -> { }
        }
    }

    private List<CompanionLibraryItem> filteredItems() {
        if (searchQuery.isBlank()) return items;
        List<CompanionLibraryItem> filtered = new ArrayList<>();
        for (CompanionLibraryItem item : items) {
            String haystack = (item.title() + " " + item.gridLabel() + " " + item.mode() + " " + item.modeLabel() + " " + item.privacy() + " " + item.privacyLabel()).toLowerCase();
            if (haystack.contains(searchQuery)) filtered.add(item);
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

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
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
            clearChildren();
            rebuildControls();
            rebuildArtButtons();
        });
    }
}
