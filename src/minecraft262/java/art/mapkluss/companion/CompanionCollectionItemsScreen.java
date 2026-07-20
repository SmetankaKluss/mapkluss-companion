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

public final class CompanionCollectionItemsScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int SECTION_WIDTH = 500;
    private static final int ROWS = 8;
    private static final int LIST_Y = 156;
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

    public CompanionCollectionItemsScreen(Screen parent, CompanionCollection collection) {
        super(Component.literal(collection.name()));
        this.parent = parent;
        this.collection = collection;
        this.nameDraft = collection.name();
    }

    @Override
    protected void init() {
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
        loadItems();
    }

    private void rebuildControls() {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int gap = 4;
        int metadataButtonWidth = 84;
        int nameWidth = Math.max(100, panelWidth - metadataButtonWidth * 2 - gap * 2);
        nameInput = new EditBox(font, left, 76, nameWidth, 20, CompanionI18n.text("Название коллекции"));
        nameInput.setValue(nameDraft);
        nameInput.setMaxLength(80);
        nameInput.setResponder(value -> nameDraft = value);
        addRenderableWidget(nameInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Сохранить"), button -> saveCollectionName())
            .dimensions(left + nameWidth + gap, 76, metadataButtonWidth, 20).build());
        deleteCollectionButton = addRenderableWidget(MapKlussButton.builder(Component.literal(deleteCollectionConfirmation.armed() ? "Подтвердить удаление" : "Удалить"), button -> requestDeleteCollection()).danger()
            .tooltip(CompanionI18n.text("Удалить коллекцию"))
            .navigationOrder(1000)
            .dimensions(left + nameWidth + metadataButtonWidth + gap * 2, 76, metadataButtonWidth, 20).build());

        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new EditBox(font, left, 110, searchWidth, 20, CompanionI18n.text("Поиск артов"));
        searchInput.setMaxLength(80);
        searchInput.setValue(searchQuery);
        searchInput.setResponder(value -> searchQuery = value);
        addRenderableWidget(searchInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Найти"), button -> applySearch())
            .dimensions(left + searchWidth + gap, 110, searchButtonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Сброс"), button -> clearSearch())
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, 110, searchButtonWidth, 20).build());

        if (sideRailLayout(panelWidth, left)) {
            int railLeft = sideRailLeft(panelWidth, left);
            addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> loadItems())
                .dimensions(railLeft, 80, SIDE_RAIL_WIDTH, 20).build());
            pageButton = addRenderableWidget(MapKlussButton.builder(pageButtonText(), button -> nextPage())
                .dimensions(railLeft, 106, SIDE_RAIL_WIDTH, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт коллекции"), button -> openCollectionSite())
                .dimensions(railLeft, 164, SIDE_RAIL_WIDTH, 20).build());
        } else {
            int bottomButtonWidth = Math.max(48, (panelWidth - gap * 2) / 3);
            addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> loadItems())
                .dimensions(left, height - 58, bottomButtonWidth, 20).build());
            pageButton = addRenderableWidget(MapKlussButton.builder(pageButtonText(), button -> nextPage())
                .dimensions(left + bottomButtonWidth + gap, height - 58, bottomButtonWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт"), button -> openCollectionSite())
                .dimensions(left + (bottomButtonWidth + gap) * 2, height - 58, panelWidth - (bottomButtonWidth + gap) * 2, 20).build());
        }
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left));
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
                List<CompanionCollection> currentCollections = new ArrayList<>(runtime.libraryCache().readCollections(runtime.sessionStore().userId()).items());
                List<CompanionCollection> updatedCollections = new ArrayList<>();
                boolean found = false;
                for (CompanionCollection existing : currentCollections) {
                    if (existing.id().equals(refreshedCollection.id())) {
                        updatedCollections.add(refreshedCollection);
                        found = true;
                    } else {
                        updatedCollections.add(existing);
                    }
                }
                if (!found) updatedCollections.add(0, refreshedCollection);
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), updatedCollections);
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
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int x = screenLeft(panelWidth);
        int y = LIST_Y;
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
            addRenderableWidget(MapKlussButton.builder(MapKlussUi.clippedText(font, itemLabel(item), panelWidth - 8), button ->
                client().gui.setScreen(new CompanionArtScreen(this, item.artId(), item.title())))
                .dimensions(x, rowY, panelWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Убрать"), button -> removeFromCollection(item)).danger()
                .dimensions(x, actionY, actionWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Трекер"), button -> quickOpenTracker(item))
                .dimensions(x + actionWidth + gap, actionY, actionWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт"), button -> openArtSite(item.artId()))
                .dimensions(x + (actionWidth + gap) * 2, actionY, actionWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Редактор"), button -> openEditor(item.artId()))
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
                    items.removeIf(existing -> existing.artId().equals(item.artId()));
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
                List<CompanionCollection> current = new ArrayList<>(runtime.libraryCache().readCollections(runtime.sessionStore().userId()).items());
                List<CompanionCollection> replaced = new ArrayList<>();
                boolean found = false;
                for (CompanionCollection existing : current) {
                    if (existing.id().equals(updatedWithCount.id())) {
                        replaced.add(updatedWithCount);
                        found = true;
                    } else {
                        replaced.add(existing);
                    }
                }
                if (!found) replaced.add(0, updatedWithCount);
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), replaced);
                runOnClient(() -> {
                    collection = updatedWithCount;
                    nameDraft = updatedWithCount.name();
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
                List<CompanionCollection> current = new ArrayList<>(runtime.libraryCache().readCollections(runtime.sessionStore().userId()).items());
                current.removeIf(existing -> existing.id().equals(collection.id()));
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), current);
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
                BuildSessionState session = runtime.syncService().trackerForArt(item.artId());
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
        runtime.libraryCache().updateCollectionItems(userId, collection.id(), item, false);

        List<CompanionCollection> current = new ArrayList<>(runtime.libraryCache().readCollections(userId).items());
        List<CompanionCollection> updatedCollections = new ArrayList<>();
        for (CompanionCollection existing : current) {
            if (existing.id().equals(collection.id())) {
                updatedCollections.add(new CompanionCollection(
                    existing.id(),
                    existing.name(),
                    existing.createdAt(),
                    existing.updatedAt(),
                    Math.max(0, existing.itemCount() - 1)
                ));
            } else {
                updatedCollections.add(existing);
            }
        }
        runtime.libraryCache().writeCollections(userId, updatedCollections);

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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        boolean sideRail = sideRailLayout(panelWidth, left);
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 10, MapKlussUi.panelBottom(height));
        if (sideRail) {
            int railLeft = sideRailLeft(panelWidth, left);
            MapKlussUi.drawPanelAt(context, railLeft - 8, railLeft + SIDE_RAIL_WIDTH + 8, 46, MapKlussUi.panelBottom(height));
            drawSideRailSections(context, railLeft);
        }
        MapKlussUi.drawSectionAt(context, font, null, left, panelWidth, 64, 72);
        MapKlussUi.drawSectionAt(context, font, "Арты", left, panelWidth, LIST_Y - 24, Math.max(42, height - LIST_Y - bottomReserved() + 28));
        if (!sideRail) drawActionGroup(context);
        MapKlussUi.drawHeader(context, font, collection.name(), "", width, 14);
        MapKlussUi.drawStatusIn(context, font, status, left + panelWidth / 2, 36, panelWidth - 16);
        MapKlussUi.drawFieldLabel(context, font, "Название коллекции", left, 76, panelWidth);
        MapKlussUi.drawFieldLabel(context, font, "Поиск артов", left, 110, panelWidth);
        boolean empty = filteredItems().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                font,
                "В коллекции пока нет артов",
                "Добавь арт через экран арта или сайт",
                left,
                LIST_Y + 18,
                panelWidth,
                Math.max(40, height - LIST_Y - bottomReserved() - 22)
            );
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawActionGroup(GuiGraphicsExtractor context) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        MapKlussUi.drawActionGroupLabel(context, font, "Управление", left, panelWidth, height - 58, 20);
    }

    private void drawSideRailSections(GuiGraphicsExtractor context, int railLeft) {
        MapKlussUi.drawSectionAt(context, font, "Список", railLeft, SIDE_RAIL_WIDTH, 60, 78);
        MapKlussUi.drawSectionAt(context, font, "Сайт", railLeft, SIDE_RAIL_WIDTH, 146, 54);
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

    private int visibleRows() {
        return MapKlussUi.visibleRows(height, LIST_Y, bottomReserved(), ROW_HEIGHT, ROWS);
    }

    private int bottomReserved() {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return sideRailLayout(panelWidth, left) ? 34 : 66;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return height >= 360 && MapKlussUi.rightRailFits(width, panelWidth, SIDE_RAIL_WIDTH, SIDE_RAIL_GAP);
    }

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return MapKlussUi.leftWithRightRail(width, panelWidth, SIDE_RAIL_WIDTH, SIDE_RAIL_GAP);
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
