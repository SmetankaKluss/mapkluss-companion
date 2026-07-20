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

public final class CompanionCollectionsScreen extends Screen {
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

    public CompanionCollectionsScreen(Screen parent) {
        super(Component.literal("Коллекции MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        rebuildControls();
        rebuildCollectionButtons();
        if (!hasLoadedOnce) {
            loadCollections();
        }
    }

    private void rebuildControls() {
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int left = screenLeft(panelWidth);
        int gap = 4;
        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new EditBox(font, left, 76, searchWidth, 20, CompanionI18n.text("Поиск коллекций"));
        searchInput.setMaxLength(80);
        searchInput.setValue(searchQuery);
        searchInput.setResponder(value -> searchQuery = value);
        addRenderableWidget(searchInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Найти"), button -> applySearch())
            .dimensions(left + searchWidth + gap, 76, searchButtonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Сброс"), button -> clearSearch())
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, 76, searchButtonWidth, 20).build());

        int createButtonWidth = 92;
        int createWidth = Math.max(120, panelWidth - createButtonWidth - gap);
        createInput = new EditBox(font, left, 110, createWidth, 20, CompanionI18n.text("Новая коллекция"));
        createInput.setMaxLength(80);
        createInput.setValue(createDraft);
        createInput.setResponder(value -> createDraft = value);
        addRenderableWidget(createInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Создать"), button -> createCollection())
            .dimensions(left + createWidth + gap, 110, createButtonWidth, 20).build());

        if (sideRailLayout(panelWidth, left)) {
            int railLeft = sideRailLeft(panelWidth, left);
            addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> loadCollections())
                .dimensions(railLeft, 80, SIDE_RAIL_WIDTH, 20).build());
            pageButton = addRenderableWidget(MapKlussButton.builder(pageButtonText(), button -> nextPage())
                .dimensions(railLeft, 106, SIDE_RAIL_WIDTH, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт облака"), button -> openCollectionsSite())
                .dimensions(railLeft, 164, SIDE_RAIL_WIDTH, 20).build());
        } else {
            int buttonWidth = Math.max(48, (panelWidth - gap * 2) / 3);
            addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> loadCollections())
                .dimensions(left, height - 58, buttonWidth, 20).build());
            pageButton = addRenderableWidget(MapKlussButton.builder(pageButtonText(), button -> nextPage())
                .dimensions(left + buttonWidth + gap, height - 58, buttonWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Облако"), button -> openCollectionsSite())
                .dimensions(left + (buttonWidth + gap) * 2, height - 58, buttonWidth, 20).build());
        }
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left));
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
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int x = screenLeft(panelWidth);
        int y = LIST_Y;
        int w = panelWidth;
        List<CompanionCollection> visibleCollections = filteredCollections();
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleCollections.size());
        for (int i = start; i < end; i++) {
            CompanionCollection collection = visibleCollections.get(i);
            int rowY = y + (i - start) * ROW_HEIGHT;
            String label = collection.itemCount() <= 0
                ? collection.name()
                : collection.name() + "  [" + collection.itemCount() + "]";
            addRenderableWidget(MapKlussButton.builder(MapKlussUi.clippedText(font, label, w - 8), button -> {
                MapKlussCompanionClient.LOGGER.info("Opening collection {} ({}) from collections screen.", collection.name(), collection.id());
                client().gui.setScreen(new CompanionCollectionItemsScreen(this, collection));
            })
                .dimensions(x, rowY, w, 20).build());
        }
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int left = screenLeft(panelWidth);
        boolean sideRail = sideRailLayout(panelWidth, left);
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 10, MapKlussUi.panelBottom(height));
        if (sideRail) {
            int railLeft = sideRailLeft(panelWidth, left);
            MapKlussUi.drawPanelAt(context, railLeft - 8, railLeft + SIDE_RAIL_WIDTH + 8, 46, MapKlussUi.panelBottom(height));
            drawSideRailSections(context, railLeft);
        }
        MapKlussUi.drawSectionAt(context, font, null, left, panelWidth, 64, 72);
        MapKlussUi.drawSectionAt(context, font, "Коллекции", left, panelWidth, LIST_Y - 24, Math.max(42, height - LIST_Y - bottomReserved() + 28));
        if (!sideRail) drawActionGroup(context);
        MapKlussUi.drawHeader(context, font, title.getString(), "", width, 16);
        MapKlussUi.drawStatusIn(context, font, status, left + panelWidth / 2, 38, panelWidth - 16);
        MapKlussUi.drawFieldLabel(context, font, "Поиск коллекций", left, 76, panelWidth);
        MapKlussUi.drawFieldLabel(context, font, "Новая коллекция", left, 110, panelWidth);
        boolean empty = filteredCollections().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                font,
                "Коллекций пока нет",
                "Создай первую коллекцию или добавь арт позже",
                left,
                LIST_Y + 18,
                panelWidth,
                Math.max(40, height - LIST_Y - bottomReserved() - 22)
            );
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawActionGroup(GuiGraphicsExtractor context) {
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int left = screenLeft(panelWidth);
        MapKlussUi.drawActionGroupLabel(context, font, "Управление", left, panelWidth, height - 58, 20);
    }

    private void drawSideRailSections(GuiGraphicsExtractor context, int railLeft) {
        MapKlussUi.drawSectionAt(context, font, "Список", railLeft, SIDE_RAIL_WIDTH, 60, 78);
        MapKlussUi.drawSectionAt(context, font, "Сайт", railLeft, SIDE_RAIL_WIDTH, 146, 54);
    }

    private void createCollection() {
        String name = createInput == null ? "" : createInput.getValue().trim();
        if (name.isEmpty()) {
            status = "Введите название коллекции.";
            return;
        }
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
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), updated);
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
        });
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

    private int visibleRows() {
        return MapKlussUi.visibleRows(height, LIST_Y, bottomReserved(), ROW_HEIGHT, ROWS);
    }

    private int bottomReserved() {
        int panelWidth = MapKlussUi.panelWidth(width, 392);
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

    private List<CompanionCollection> filteredCollections() {
        if (searchQuery.isBlank()) return collections;
        List<CompanionCollection> filtered = new ArrayList<>();
        for (CompanionCollection collection : collections) {
            if (collection.name().toLowerCase().contains(searchQuery)) filtered.add(collection);
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
