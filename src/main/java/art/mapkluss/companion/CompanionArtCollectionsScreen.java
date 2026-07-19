package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CompanionArtCollectionsScreen extends Screen {
    private static final int ROWS = 7;
    private static final int LIST_Y = 156;
    private static final int ROW_HEIGHT = 46;
    private static final int BOTTOM_RESERVED = 64;
    private static final int SIDE_RAIL_WIDTH = 128;
    private static final int SIDE_RAIL_GAP = 22;

    private final CompanionArtScreen parent;
    private final String artId;
    private final String fallbackTitle;
    private final List<CompanionCollection> collections = new ArrayList<>();
    private CompanionManifest manifest;
    private TextFieldWidget createInput;
    private TextFieldWidget searchInput;
    private boolean hasLoadedOnce;
    private String status = "";
    private String createDraft = "";
    private String searchQuery = "";
    private int page;
    private ClickableWidget pageButton;

    public CompanionArtCollectionsScreen(CompanionArtScreen parent, String artId, String fallbackTitle, CompanionManifest manifest) {
        super(Text.literal("Коллекции арта"));
        this.parent = parent;
        this.artId = artId;
        this.fallbackTitle = fallbackTitle;
        this.manifest = manifest;
    }

    @Override
    protected void init() {
        clearChildren();
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int left = screenLeft(panelWidth);
        int gap = 4;
        int createButtonWidth = 112;
        int createWidth = Math.max(110, panelWidth - createButtonWidth - gap);
        createInput = new TextFieldWidget(textRenderer, left, 76, createWidth, 20, CompanionI18n.text("Название коллекции"));
        createInput.setPlaceholder(Text.literal("Новая коллекция"));
        createInput.setMaxLength(80);
        createInput.setText(createDraft);
        createInput.setChangedListener(value -> createDraft = value);
        addDrawableChild(createInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Создать +"), button -> createCollection())
            .dimensions(left + createWidth + gap, 76, createButtonWidth, 20).build());

        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new TextFieldWidget(textRenderer, left, 110, searchWidth, 20, CompanionI18n.text("Поиск коллекций"));
        searchInput.setMaxLength(80);
        searchInput.setText(searchQuery);
        searchInput.setChangedListener(value -> searchQuery = value);
        addDrawableChild(searchInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Найти"), button -> applySearch())
            .dimensions(left + searchWidth + gap, 110, searchButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сброс"), button -> clearSearch())
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, 110, searchButtonWidth, 20).build());

        if (sideRailLayout(panelWidth, left)) {
            int railLeft = sideRailLeft(panelWidth, left);
            addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> loadCollections())
                .dimensions(railLeft, 80, SIDE_RAIL_WIDTH, 20).build());
            pageButton = addDrawableChild(MapKlussButton.builder(pageButtonText(), button -> nextPage())
                .dimensions(railLeft, 106, SIDE_RAIL_WIDTH, 20).build());
        } else {
            int bottomButtonWidth = Math.max(48, (panelWidth - gap) / 2);
            addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> loadCollections())
                .dimensions(left, height - 58, bottomButtonWidth, 20).build());
            pageButton = addDrawableChild(MapKlussButton.builder(pageButtonText(), button -> nextPage())
                .dimensions(left + bottomButtonWidth + gap, height - 58, panelWidth - bottomButtonWidth - gap, 20).build());
        }
        addDrawableChild(MapKlussUi.languageButton(this));
        addDrawableChild(MapKlussUi.backButton(this, parent, left));
        rebuildCollectionButtons();
        updatePageButton();
        focusCreateInput();
        if (!hasLoadedOnce) {
            loadCollections();
        }
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
                    clearChildren();
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
                clearChildren();
                init();
            });
        } catch (Exception cacheError) {
            runOnClient(() -> status = CompanionUiErrors.message("sync", reason));
        }
    }

    private void createCollection() {
        String name = createInput == null ? "" : createInput.getText().trim();
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
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), updatedCollections);

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
                    clearChildren();
                    init();
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
                CompanionManifest updatedManifest = withCollectionSelection(manifest, collection.id(), selected);
                runtime.manifestCache().write(runtime.sessionStore().userId(), updatedManifest);
                runtime.libraryCache().updateCollectionItems(runtime.sessionStore().userId(), collection.id(), toLibraryItem(updatedManifest), selected);
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
                runtime.libraryCache().writeCollections(runtime.sessionStore().userId(), updatedCollections);
                runOnClient(() -> {
                    manifest = updatedManifest;
                    collections.clear();
                    collections.addAll(updatedCollections);
                    page = clampPage(page);
                    parent.applyManifestUpdate(updatedManifest, selected ? "Добавлено в " + collection.name() + "." : "Убрано из " + collection.name() + ".");
                    status = selected ? "Добавлено в " + collection.name() + "." : "Убрано из " + collection.name() + ".";
                    clearChildren();
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
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int x = screenLeft(panelWidth);
        int y = LIST_Y;
        int openButtonWidth = panelWidth;
        List<CompanionCollection> visibleCollections = filteredCollections();
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleCollections.size());
        for (int i = start; i < end; i++) {
            CompanionCollection collection = visibleCollections.get(i);
            boolean selected = manifest != null && manifest.collectionIds().contains(collection.id());
            int rowY = y + (i - start) * ROW_HEIGHT;
            String prefix = selected ? "[x] " : "[ ] ";
            String count = collection.itemCount() > 0 ? "  [" + collection.itemCount() + "]" : "";
            addDrawableChild(MapKlussButton.builder(MapKlussUi.clippedText(textRenderer, prefix + collection.name() + count, panelWidth - 8), button -> toggleCollection(collection))
                .selected(selected)
                .dimensions(x, rowY, panelWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Открыть коллекцию"), button ->
                client().setScreen(new CompanionCollectionItemsScreen(this, collection)))
                .dimensions(x, rowY + 23, openButtonWidth, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int left = screenLeft(panelWidth);
        boolean sideRail = sideRailLayout(panelWidth, left);
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 10, MapKlussUi.panelBottom(height));
        if (sideRail) {
            int railLeft = sideRailLeft(panelWidth, left);
            MapKlussUi.drawPanelAt(context, railLeft - 8, railLeft + SIDE_RAIL_WIDTH + 8, 46, MapKlussUi.panelBottom(height));
            drawSideRailSections(context, railLeft);
        }
        MapKlussUi.drawSectionAt(context, textRenderer, null, left, panelWidth, 64, 72);
        MapKlussUi.drawSectionAt(context, textRenderer, "Коллекции", left, panelWidth, LIST_Y - 24, Math.max(42, height - LIST_Y - bottomReserved() + 28));
        if (!sideRail) drawActionGroup(context);
        MapKlussUi.drawHeader(context, textRenderer, title.getString(), "", width, 14);
        MapKlussUi.drawStatusIn(context, textRenderer, status, left + panelWidth / 2, 36, panelWidth - 16);
        MapKlussUi.drawFieldLabel(context, textRenderer, "Название коллекции", left, 76, panelWidth);
        MapKlussUi.drawFieldLabel(context, textRenderer, "Поиск коллекций", left, 110, panelWidth);
        boolean empty = filteredCollections().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                "Коллекций пока нет",
                "Создай коллекцию и добавь в неё этот арт",
                left,
                LIST_Y + 18,
                panelWidth,
                Math.max(40, height - LIST_Y - bottomReserved() - 22)
            );
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawActionGroup(DrawContext context) {
        int panelWidth = MapKlussUi.panelWidth(width, 392);
        int left = screenLeft(panelWidth);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Управление", left, panelWidth, height - 58, 20);
    }

    private void drawSideRailSections(DrawContext context, int railLeft) {
        MapKlussUi.drawSectionAt(context, textRenderer, "Список", railLeft, SIDE_RAIL_WIDTH, 60, 78);
    }

    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getText().trim();
        page = 0;
        clearChildren();
        init();
    }

    private void clearSearch() {
        searchQuery = "";
        page = 0;
        clearChildren();
        init();
    }

    private void nextPage() {
        int totalPages = totalPages();
        if (totalPages <= 1) return;
        page = (page + 1) % totalPages;
        clearChildren();
        init();
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

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
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
            clearChildren();
            init();
        });
    }
}
