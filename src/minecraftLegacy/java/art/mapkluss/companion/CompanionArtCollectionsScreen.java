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
        CompanionUiLayout.Shell shell = collectionsShell();
        CompanionUiLayout.Rect work = collectionsWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        int gap = 6;
        int createButtonWidth = 112;
        int createWidth = Math.max(110, panelWidth - createButtonWidth - gap);
        createInput = new TextFieldWidget(textRenderer, left, work.y(), createWidth, 22, CompanionI18n.text("Название коллекции"));
        createInput.setPlaceholder(Text.literal("Новая коллекция"));
        createInput.setMaxLength(80);
        createInput.setText(createDraft);
        createInput.setChangedListener(value -> createDraft = value);
        addDrawableChild(createInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Создать +"), button -> createCollection()).action("collections.create")
            .selected(true).dimensions(left + createWidth + gap, work.y(), createButtonWidth, 22).build());

        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new TextFieldWidget(textRenderer, left, work.y() + 30, searchWidth, 22, CompanionI18n.text("Поиск коллекций"));
        searchInput.setMaxLength(80);
        searchInput.setText(searchQuery);
        searchInput.setChangedListener(value -> searchQuery = value);
        addDrawableChild(searchInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Найти"), button -> applySearch()).action("collections.search")
            .dimensions(left + searchWidth + gap, work.y() + 30, searchButtonWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сброс"), button -> clearSearch()).action("collections.search_clear")
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, work.y() + 30, searchButtonWidth, 22).build());

        int bottomButtonWidth = Math.max(48, (panelWidth - gap) / 2);
        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> loadCollections()).action("collections.refresh")
            .technical().dimensions(left, work.bottom() - 22, bottomButtonWidth, 22).build());
        pageButton = addDrawableChild(MapKlussButton.builder(pageButtonText(), button -> nextPage()).action("collections.page_next")
            .dimensions(left + bottomButtonWidth + gap, work.bottom() - 22, panelWidth - bottomButtonWidth - gap, 22).build());
        addNavigationControls(shell);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
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
        CompanionUiLayout.Rect work = collectionsWork(collectionsShell());
        int panelWidth = work.width();
        int x = work.x();
        int y = listY();
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
                .action(selected ? "collections.remove_art" : "collections.add_art")
                .selected(selected)
                .dimensions(x, rowY, panelWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Открыть коллекцию"), button ->
                client().setScreen(new CompanionCollectionItemsScreen(this, collection)))
                .action("collections.open")
                .dimensions(x, rowY + 23, openButtonWidth, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.ART, CompanionI18n.translate("Коллекции"),
                java.util.List.of(fallbackTitle == null ? "Арт" : fallbackTitle), status), false, 52
        );
        CompanionUiLayout.Rect work = collectionsWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        MapKlussUi.drawLeft(context, textRenderer, "Коллекции", left, listY() - 18, panelWidth, MapKlussUi.MUTED);
        boolean empty = filteredCollections().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                "Коллекций пока нет",
                "Создай коллекцию и добавь в неё этот арт",
                left,
                listY() + 18,
                panelWidth,
                Math.max(40, work.bottom() - listY() - 30)
            );
        }
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.ART);
    }

    private void drawActionGroup(DrawContext context) {
        int panelWidth = collectionsWork(collectionsShell()).width();
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
        CompanionUiLayout.Rect work = collectionsWork(collectionsShell());
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
        return collectionsWork(collectionsShell()).x();
    }

    private CompanionUiLayout.Shell collectionsShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect collectionsWork(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 14, content.y() + 12, Math.max(1, content.width() - 28), Math.max(1, content.height() - 24));
    }

    private int listY() {
        return collectionsWork(collectionsShell()).y() + 78;
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destination.name())).dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
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
