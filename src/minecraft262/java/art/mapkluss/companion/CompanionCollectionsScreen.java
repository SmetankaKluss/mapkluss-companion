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
        CompanionUiLayout.Shell shell = collectionsShell();
        CompanionUiLayout.Rect content = shell.content();
        int left = content.x() + 14;
        int panelWidth = Math.max(120, content.width() - 28);
        int gap = 6;
        int searchY = content.y() + 14;
        int createY = searchY + 30;
        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new EditBox(font, left, searchY, searchWidth, 22, CompanionI18n.text("Поиск коллекций"));
        searchInput.setMaxLength(80);
        searchInput.setValue(searchQuery);
        searchInput.setResponder(value -> searchQuery = value);
        addRenderableWidget(searchInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Найти"), button -> applySearch()).action("collections.search")
            .dimensions(left + searchWidth + gap, searchY, searchButtonWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Сброс"), button -> clearSearch()).action("collections.search_clear")
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, searchY, searchButtonWidth, 22).build());

        int createButtonWidth = 92;
        int createWidth = Math.max(120, panelWidth - createButtonWidth - gap);
        createInput = new EditBox(font, left, createY, createWidth, 22, CompanionI18n.text("Новая коллекция"));
        createInput.setMaxLength(80);
        createInput.setValue(createDraft);
        createInput.setResponder(value -> createDraft = value);
        addRenderableWidget(createInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Создать"), button -> createCollection()).action("collections.create")
            .selected(true).dimensions(left + createWidth + gap, createY, createButtonWidth, 22).build());

        int actionsY = content.bottom() - 28;
        int buttonWidth = Math.max(54, (panelWidth - gap * 2) / 3);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> loadCollections()).action("collections.refresh")
            .technical().dimensions(left, actionsY, buttonWidth, 22).build());
        pageButton = addRenderableWidget(MapKlussButton.builder(pageButtonText(), button -> nextPage()).action("collections.page_next")
            .dimensions(left + buttonWidth + gap, actionsY, buttonWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Облако"), button -> openCollectionsSite()).action("collections.open_site")
            .technical().dimensions(left + (buttonWidth + gap) * 2, actionsY, panelWidth - (buttonWidth + gap) * 2, 22).build());
        addNavigationControls(shell);
        addRenderableWidget(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
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
        CompanionUiLayout.Rect content = collectionsShell().content();
        int x = content.x() + 14;
        int panelWidth = Math.max(120, content.width() - 28);
        int y = listTop();
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
            }).action("collections.open")
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
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, font, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.LIBRARY, CompanionI18n.translate("Библиотека"),
                java.util.List.of(CompanionI18n.translate("Коллекции")), status), false
        );
        CompanionUiLayout.Rect content = shell.content();
        int left = content.x() + 14;
        int panelWidth = Math.max(120, content.width() - 28);
        int listY = listTop();
        MapKlussUi.drawLeft(context, font, "Коллекции", left, listY - 18, panelWidth, MapKlussUi.MUTED);
        boolean empty = filteredCollections().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                font,
                "Коллекций пока нет",
                "Создай первую коллекцию или добавь арт позже",
                left,
                listY + 18,
                panelWidth,
                Math.max(40, content.bottom() - listY - 64)
            );
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.LIBRARY);
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
        return MapKlussUi.visibleRows(height, listTop(), height - collectionsShell().content().bottom() + 40, ROW_HEIGHT, ROWS);
    }

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
            case LENS -> client().gui.setScreen(new LensScreen(this));
            case SCAN -> client().gui.setScreen(new ScanScreen(this));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this));
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
