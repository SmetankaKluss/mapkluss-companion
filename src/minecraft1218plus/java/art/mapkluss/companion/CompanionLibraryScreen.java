package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompanionLibraryScreen extends WorkshopTrackerScreen {
    private static final int PANEL_WIDTH = 1120;
    private static final int SECTION_WIDTH = 1120;
    private static final int SIDE_RAIL_WIDTH = 134;
    private static final int SIDE_RAIL_GAP = 22;
    private static final int ROWS = 8;
    private static final int LIST_Y = 144;
    private static final int ROW_HEIGHT = 54;
    private static final int THUMB_SIZE = 44;
    private static final int THUMB_GAP = 8;
    private static final int BOTTOM_RESERVED = 92;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final boolean syncOnOpen;
    private CompanionRuntime runtime;
    private final List<CompanionLibraryItem> items = new ArrayList<>();
    private LibraryView view = LibraryView.MY_ARTS;
    private boolean signedIn;
    private String status = "";
    private String sessionStatus = "Сессия: вход не выполнен";
    private String searchQuery = "";
    private String searchDraft = "";
    private String selectedArtId = "";
    private String placementSelectionKey = "";
    private int placementPart;

    private int placementPartCount() {
        var item=selectedItem();
        if(item==null)return 1;
        String key=item.artId()+":"+item.currentVersionId();
        if(!key.equals(placementSelectionKey)){placementSelectionKey=key;placementPart=0;}
        try{return LibraryPlacementSource.count(item.grid());}catch(IllegalArgumentException invalid){return 1;}
    }
    private void changePlacementPart(int step) {
        int count=placementPartCount();
        placementPart=Math.floorMod(placementPart+step,count);
        libraryRefresh.begin();
        rebuildWorkshop();
    }
    private WorkshopLayout.Rect placementPreview(WorkshopLayout.Shell shell) {
        var p=shell.preview();
        return placementPartCount()>1?new WorkshopLayout.Rect(p.x(),p.y(),p.width(),Math.max(0,p.height()-28)):p;
    }
    private int page;
    private boolean secondaryActions;
    private boolean telemetryRecorded;
    private boolean developmentFixtureActive;
    private boolean compactDetail;
    private boolean searchOpen;
    private WorkshopTheme workshopTheme = WorkshopTheme.of("amethyst");
    private final LibraryRefreshEpoch libraryRefresh = new LibraryRefreshEpoch();
    private final LibraryOpenPolicy openPolicy = new LibraryOpenPolicy();
    private ClickableWidget pageButton;
    private TextFieldWidget searchInput;
    private MapKlussButton accountButton;
    private final CompanionConfirmation logoutConfirmation = new CompanionConfirmation();

    public CompanionLibraryScreen(Screen parent) {
        this(parent, false);
    }

    CompanionLibraryScreen(Screen parent, boolean syncOnOpen) {
        super(Text.literal("Библиотека MapKluss"));
        this.parent = parent;
        this.syncOnOpen = syncOnOpen;
    }

    @Override
    protected void init() {
        clearChildren();
        if (developmentFixtureActive || applyDevelopmentFixtureIfEnabled()) {
            rebuildControls();
            rebuildArtButtons();
            return;
        }
        if (!telemetryRecorded) {
            telemetryRecorded = true;
            CompanionTelemetryManager.record(CompanionTelemetryEvent.LIBRARY_OPENED);
        }
        var load = openPolicy.enter(syncOnOpen);
        if (load != LibraryOpenPolicy.Load.NONE) refreshSignedInState();
        rebuildControls();
        rebuildArtButtons();
        if (load == LibraryOpenPolicy.Load.SYNC) updateAll();
        else if (load == LibraryOpenPolicy.Load.REFRESH) loadLibrary();
    }

    @Override
    public void removed() {
        openPolicy.leave();
        libraryRefresh.begin();
        super.removed();
    }

    private void refreshSignedInState() {
        String previousUser = runtime == null ? null : runtime.sessionStore().userId();
        try {
            runtime = CompanionRuntime.create(client());
            signedIn = runtime.sessionStore().hasAccessToken();
            sessionStatus = sessionSummary(runtime.sessionInfo());
        } catch (Exception e) {
            signedIn = false;
            sessionStatus = "Сессия: недоступна";
        }
        if (!signedIn || !java.util.Objects.equals(previousUser, runtime.sessionStore().userId())) {
            libraryRefresh.begin();
            items.clear();
            selectedArtId = "";
            page = 0;
            compactDetail = false;
        }
    }

    boolean applyDevelopmentFixture(List<CompanionLibraryItem> fixtureItems, String fixtureStatus, String fixtureSessionStatus) {
        developmentFixtureActive = true;
        signedIn = true;
        status = fixtureStatus;
        sessionStatus = fixtureSessionStatus;
        view = LibraryView.MY_ARTS;
        searchQuery = "";
        selectedArtId = "";
        page = 0;
        items.clear();
        items.addAll(fixtureItems);
        return true;
    }

    private boolean applyDevelopmentFixtureIfEnabled() {
        try {
            return Boolean.TRUE.equals(Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture")
                .getMethod("applyIfConfigured", CompanionLibraryScreen.class)
                .invoke(null, this));
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void disableDevelopmentFixtureControls() {
        for (var child : children()) {
            if (child instanceof ClickableWidget widget) widget.active = false;
        }
    }

    private void rebuildControls() {
        ensureSelectedItem(filteredItems());
        try { workshopTheme = WorkshopTheme.of(CompanionConfig.load(client().runDirectory.toPath()).theme()); }
        catch (IOException ignored) { workshopTheme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID); }
        var shell = WorkshopLayout.library(width, height);
        var nav = shell.navigation();
        int iconWidth = 24;
        int brandWidth = nav.width() >= 548 ? 98 : 0;
        int navWidth = (nav.width() - brandWidth - 2 * (iconWidth + 4)) / 4;
        for (int i = 0; i < 4; i++) {
            var destination = CompanionUiLayout.Destination.values()[i];
            var icon = new WorkshopIcon[]{WorkshopIcon.LIBRARY, WorkshopIcon.LENS, WorkshopIcon.SCAN, WorkshopIcon.TRACKER}[i];
            workshopButton(CompanionActionInventory.navigationAction(destination), destinationLabel(destination), icon,
                new WorkshopLayout.Rect(nav.x() + brandWidth + i * navWidth, nav.y(), navWidth - 4, nav.height()),
                true, i == 0, false, () -> openDestination(destination));
        }
        workshopButton("nav.account", "Аккаунт", WorkshopIcon.ACCOUNT,
            new WorkshopLayout.Rect(nav.right() - 52, nav.y(), 24, nav.height()), true, false, true,
            () -> openDestination(CompanionUiLayout.Destination.ACCOUNT));
        workshopButton("global.back", "Закрыть", WorkshopIcon.CLOSE,
            new WorkshopLayout.Rect(nav.right() - 24, nav.y(), 24, nav.height()), true, false, true, this::close);

        var tabs = shell.tabs();
        if (searchOpen) {
            searchInput = new TextFieldWidget(textRenderer, tabs.x() + 4, tabs.y() + 3, tabs.width() - 88, 18, CompanionI18n.text("Поиск артов"));
            searchInput.setMaxLength(80);
            searchInput.setText(searchDraft);
            searchInput.setChangedListener(value -> searchDraft = value);
            addDrawableChild(searchInput);
            workshopButton("library.search", "Поиск", WorkshopIcon.SEARCH, new WorkshopLayout.Rect(tabs.right() - 80, tabs.y(), 24, 24), true, false, true, this::applySearch);
            workshopButton("library.search_clear", "Очистить поиск", WorkshopIcon.DELETE, new WorkshopLayout.Rect(tabs.right() - 52, tabs.y(), 24, 24), true, false, true, this::clearSearch);
            workshopButton("library.search_toggle", "Назад", WorkshopIcon.BACK, new WorkshopLayout.Rect(tabs.right() - 24, tabs.y(), 24, 24), true, false, true, () -> { searchOpen = false; rebuildWorkshop(); });
        } else {
            searchInput = null;
            String[] labels = {"Мои арты", "Избранное", "Недавние", "Коллекции"};
            String[] ids = {"library.my_arts", "library.favorites", "library.recent", "library.collections"};
            WorkshopIcon[] icons = {WorkshopIcon.LIBRARY, WorkshopIcon.FAVORITE, WorkshopIcon.REFRESH, WorkshopIcon.FOLDER};
            int tabWidth = (tabs.width() - 56) / 4;
            for (int i = 0; i < 4; i++) {
                final int tab = i;
                workshopButton(ids[i], labels[i], icons[i], new WorkshopLayout.Rect(tabs.x() + i * tabWidth, tabs.y(), tabWidth - 4, 24),
                    true, i < 3 && view.ordinal() == i, true,
                    () -> { if (tab == 3) client().setScreen(new CompanionCollectionsScreen(this,developmentFixtureActive)); else switchView(LibraryView.values()[tab]); });
            }
            workshopButton("library.search_toggle", "Поиск", WorkshopIcon.SEARCH, new WorkshopLayout.Rect(tabs.right() - 52, tabs.y(), 24, 24), true, !searchQuery.isBlank(), true,
                () -> { searchOpen = true; rebuildWorkshop(); });
            workshopButton("library.refresh", "Обновить библиотеку", WorkshopIcon.REFRESH, new WorkshopLayout.Rect(tabs.right() - 24, tabs.y(), 24, 24), true, false, false, this::loadLibrary);
        }
        var footer = shell.footer();
        workshopButton("account.theme", CompanionI18n.english(client()) ? "Appearance" : "Оформление", WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(footer.right() - 120, footer.y(), 24, 20), true, false, true,
            () -> client().setScreen(new WorkshopAppearanceScreen(this)));
        pageButton = workshopButton("library.page_next", "", null, new WorkshopLayout.Rect(footer.right() - 88, footer.y(), 88, 20), true, false, true, this::nextPage);
        updatePageButton();
        if (!shell.split() && compactDetail) {
            workshopButton("library.back_to_list", "Назад", WorkshopIcon.BACK, new WorkshopLayout.Rect(footer.x(), footer.y(), 24, 20), true, false, true,
                () -> { compactDetail = false; rebuildWorkshop(); });
        }
        if (shell.split() || compactDetail || filteredItems().isEmpty()) {
            var meta = shell.metadata();
            var selected = selectedItem();
            workshopButton("library.toggle_favorite", "В избранное", WorkshopIcon.FAVORITE,
                new WorkshopLayout.Rect(meta.right() - 28, meta.y() + 2, 28, 28), selected != null, selected != null && selected.isFavorite(), false, this::quickFavoriteSelected);
            addWorkshopActions(shell.actions());
            if(placementPartCount()>1) {
                var p=shell.preview();int y=p.bottom()-24;
                workshopButton("library.part_previous","Пред.",WorkshopIcon.BACK,new WorkshopLayout.Rect(p.x(),y,24,24),
                    true,false,true,()->changePlacementPart(-1));
                workshopButton("library.part_next","След.",WorkshopIcon.MORE,new WorkshopLayout.Rect(p.right()-24,y,24,24),
                    true,false,true,()->changePlacementPart(1));
            }
        }
    }

    private MapKlussButton workshopButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r,
            boolean enabled, boolean selected, boolean local, Runnable action) {
        boolean permitted = LibraryActionPolicy.permits(developmentFixtureActive, local, id);
        var builder = MapKlussButton.builder(Text.literal(label), ignored -> { if (enabled && permitted) action.run(); })
            .action(id).selected(selected).enabledWhen(() -> enabled && permitted)
            .tooltip(CompanionI18n.text(label)).dimensions(r.x(), r.y(), r.width(), r.height());
        if ("library.install".equals(id)) builder.gold();
        var button = builder.build().workshop(workshopTheme, icon);
        return addDrawableChild(button);
    }

    private void rebuildWorkshop() {
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    @Override
    public void close() {
        libraryRefresh.begin();
        client().setScreen(parent);
    }

    private void addWorkshopActions(WorkshopLayout.Rect bar) {
        boolean hasItem = selectedItem() != null;
        String[] ids = secondaryActions
            ? new String[]{"library.select_art", "library.open_site", "library.sync", "library.import_two_layer", "global.language", "library.toggle_actions"}
            : new String[]{"library.install", "library.place", "library.open_lens", "library.open_tracker", "library.open_two_layer", "library.open_editor", "library.toggle_actions"};
        String[] labels = secondaryActions
            ? new String[]{"Открыть", "Страница", "Синхронизация", "Импорт Two-layer", "RU / EN", "Назад"}
            : new String[]{"Схема", CompanionI18n.english(client()) ? "Place" : "Вставить", "Lens", "Трекер", "Two-layer", "Редактор", "Ещё"};
        WorkshopIcon[] icons = secondaryActions
            ? new WorkshopIcon[]{WorkshopIcon.LIBRARY, WorkshopIcon.LINK, WorkshopIcon.REFRESH, WorkshopIcon.FOLDER, WorkshopIcon.ACCOUNT, WorkshopIcon.BACK}
            : new WorkshopIcon[]{WorkshopIcon.INSTALL, WorkshopIcon.LAYERS, WorkshopIcon.LENS, WorkshopIcon.TRACKER, WorkshopIcon.LAYERS, WorkshopIcon.EDIT, WorkshopIcon.MORE};
        Runnable[] actions = secondaryActions
            ? new Runnable[]{this::openSelectedArt, this::openSelectedSite, this::updateAll, () -> client().setScreen(new SuppressionStartScreen(this, null)), this::toggleWorkshopLanguage, this::toggleSecondaryActions}
            : new Runnable[]{this::quickInstallSelected, this::quickPlaceSelected, this::quickLensSelected, this::quickTrackerSelected, this::openSelectedTwoLayer, this::openSelectedEditor, this::toggleSecondaryActions};
        int regular = ids.length - 2;
        int slot = (bar.width() - 52) / regular;
        int x = bar.x();
        for (int i = 0; i < ids.length; i++) {
            int w = i < regular ? slot - 4 : 24;
            workshopButton(ids[i], labels[i], icons[i], new WorkshopLayout.Rect(x, bar.y(), w, bar.height()),
                i == ids.length - 1 || (secondaryActions && i >= 2) || hasItem, false, i == ids.length - 1 || (secondaryActions && i >= 3),
                actions[i]);
            x += w + 4;
        }
    }

    private void openSelectedTwoLayer() {
        if (developmentFixtureActive) {
            try {
                var catalog = (SuppressionBundleCatalog) Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture")
                    .getMethod("twoLayerCatalog").invoke(null);
                var start = new SuppressionStartScreen(this, null);
                Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture")
                    .getMethod("applyTwoLayerStart", Object.class).invoke(null, start);
                client().setScreen(new SuppressionTileSelectScreen(start, catalog, true));
            } catch (ReflectiveOperationException error) { status = "Preview unavailable"; }
            return;
        }
        var selected = selectedItem();
        if (selected == null) return;
        if (!signedIn) {
            client().setScreen(new DeviceLoginScreen(this));
            return;
        }
        CompanionRuntime actionRuntime = runtime;
        long epoch = libraryRefresh.begin();
        status = "Загрузка...";
        CompletableFuture.runAsync(() -> {
            try {
                var manifest = actionRuntime.syncService().refreshArt(selected.artId()).manifest();
                runOnClient(() -> {
                    if (!libraryRefresh.accepts(epoch) || client().currentScreen != this || !selectedArtId.equals(selected.artId())) return;
                    client().setScreen(new SuppressionStartScreen(this, manifest));
                });
            } catch (Exception e) {
                runOnClient(() -> { if (libraryRefresh.accepts(epoch)) status = CompanionUiErrors.message("download", e); });
            }
        });
    }

    private void toggleWorkshopLanguage() {
        try {
            CompanionI18n.toggle(client());
            rebuildWorkshop();
        } catch (IOException e) {
            status = CompanionUiErrors.message("settings", e);
        }
    }


    private void addSelectedActions(CompanionUiLayout.Rect actionBar, int gap) {
        CompanionLibraryItem selected = selectedItem();
        int actionWidth = Math.max(40, (actionBar.width() - gap * 4) / 5);
        if (!secondaryActions) {
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Открыть"), button -> openSelectedArt()).action("library.select_art").selected(true)
                .enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x(), actionBar.y(), actionWidth, 22).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Схема"), button -> quickInstallSelected()).action("library.install").gold()
                .enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x() + actionWidth + gap, actionBar.y(), actionWidth, 22).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text(selected != null && selected.isFavorite() ? "Убрать" : "В избранное"), button -> quickFavoriteSelected()).action("library.toggle_favorite")
                .selected(selected != null && selected.isFavorite()).enabledWhen(() -> selectedItem() != null)
                .dimensions(actionBar.x() + (actionWidth + gap) * 2, actionBar.y(), actionWidth, 22).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Трекер"), button -> quickTrackerSelected()).action("library.open_tracker")
                .enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x() + (actionWidth + gap) * 3, actionBar.y(), actionWidth, 22).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Ещё"), button -> toggleSecondaryActions())
                .action("library.toggle_actions")
                .dimensions(actionBar.x() + (actionWidth + gap) * 4, actionBar.y(), actionBar.width() - (actionWidth + gap) * 4, 22).build());
            return;
        }
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Страница"), button -> openSelectedSite()).action("library.open_site")
            .technical().enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x(), actionBar.y(), actionWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Редактор"), button -> openSelectedEditor()).action("library.open_editor")
            .technical().enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x() + actionWidth + gap, actionBar.y(), actionWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Синхронизация"), button -> updateAll()).action("library.sync")
            .enabledWhen(() -> signedIn).dimensions(actionBar.x() + (actionWidth + gap) * 2, actionBar.y(), actionWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Two-layer"), button -> client().setScreen(new SuppressionStartScreen(this, null))).action("library.import_two_layer")
            .special().dimensions(actionBar.x() + (actionWidth + gap) * 3, actionBar.y(), actionWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Назад"), button -> toggleSecondaryActions())
            .action("library.toggle_actions")
            .dimensions(actionBar.x() + (actionWidth + gap) * 4, actionBar.y(), actionBar.width() - (actionWidth + gap) * 4, 22).build());
    }

    private void toggleSecondaryActions() {
        secondaryActions = !secondaryActions;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private void openSelectedSite() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) openSite("/art/" + item.artId());
    }

    private void openSelectedEditor() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) openSite("/?art=" + item.artId());
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destinationLabel(destination)))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> { }
            case LENS -> client().setScreen(new LensScreen(this, developmentFixtureActive));
            case SCAN -> client().setScreen(new ScanScreen(this, developmentFixtureActive));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this, developmentFixtureActive));
            case ACCOUNT -> client().setScreen(new CompanionAccountScreen(this, developmentFixtureActive));
            default -> { }
        }
    }

    private String destinationLabel(CompanionUiLayout.Destination destination) {
        return switch (destination) {
            case LIBRARY -> "Библиотека";
            case LENS -> "Lens";
            case SCAN -> "Скан";
            case TRACKER -> "Трекер";
            case ACCOUNT -> signedIn ? "Аккаунт" : "Войти";
            default -> destination.name();
        };
    }


    private void switchView(LibraryView nextView) {
        searchDraft = "";
        if (developmentFixtureActive) {
            view = nextView;
            page = 0;
            searchQuery = "";
            rebuildWorkshop();
            return;
        }
        logoutConfirmation.reset();
        view = nextView;
        page = 0;
        searchQuery = "";
        items.clear();
        rebuildWorkshop();
        loadLibrary();
    }

    private void loadLibrary() {
        logoutConfirmation.reset();
        LibraryView requestedView = view;
        long requestEpoch = libraryRefresh.begin();
        status = "Загрузка: " + requestedView.label + "...";
        showCachedLibraryImmediately(requestedView, requestEpoch);
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime requestRuntime = CompanionRuntime.create(client());
                if (!requestRuntime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> {
                        if (!acceptsLibraryRefresh(requestedView, requestEpoch)) return;
                        runtime = requestRuntime;
                        signedIn = false;
                        sessionStatus = "Сессия: вход не выполнен";
                        status = "Сначала войдите через код входа.";
                        clearChildren();
                        rebuildControls();
                    });
                    return;
                }
                runOnClient(() -> {
                    if (acceptsLibraryRefresh(requestedView, requestEpoch)) signedIn = true;
                });
                ItemListResponse<CompanionLibraryItem> response = switch (requestedView) {
                    case FAVORITES -> requestRuntime.apiClient().favorites();
                    case RECENT -> requestRuntime.apiClient().recent();
                    case MY_ARTS -> requestRuntime.apiClient().library();
                };
                List<CompanionLibraryItem> loadedItems = response.items() == null ? List.of() : response.items();
                if (!libraryRefresh.accepts(requestEpoch)) return;
                requestRuntime.libraryCache().write(requestRuntime.sessionStore().userId(), requestedView.cacheKey, loadedItems);
                runOnClient(() -> {
                    if (!acceptsLibraryRefresh(requestedView, requestEpoch)) return;
                    runtime = requestRuntime;
                    applyLibraryItems(loadedItems);
                    sessionStatus = sessionSummary(requestRuntime.sessionInfo());
                    status = "";
                });
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    runLibraryAction(requestEpoch, () -> expireSessionLocally("Сессия истекла. Войдите заново."));
                } else {
                    loadCachedLibrary(requestedView, requestEpoch, e.getMessage());
                }
            }
        });
    }

    private void showCachedLibraryImmediately(LibraryView requestedView, long requestEpoch) {
        try {
            if (runtime == null || !runtime.sessionStore().hasAccessToken()) return;
            LibraryCache.CachedLibrary cached = runtime.libraryCache().read(runtime.sessionStore().userId(), requestedView.cacheKey);
            if (cached.isEmpty() || !acceptsLibraryRefresh(requestedView, requestEpoch)) return;
            applyLibraryItems(cached.items());
            status = "Показан локальный кеш. Обновляю...";
        } catch (Exception ignored) {
            // A missing or unreadable cache must not delay the network refresh.
        }
    }

    private void loadCachedLibrary(LibraryView requestedView, long requestEpoch, String reason) {
        try {
            CompanionRuntime cachedRuntime = runtime == null ? CompanionRuntime.create(client()) : runtime;
            LibraryCache.CachedLibrary cached = cachedRuntime.libraryCache().read(cachedRuntime.sessionStore().userId(), requestedView.cacheKey);
            runOnClient(() -> {
                if (!acceptsLibraryRefresh(requestedView, requestEpoch)) return;
                status = cached.isEmpty()
                    ? CompanionUiErrors.message("sync", reason)
                    : "Показан локальный кеш.";
                if (!cached.isEmpty()) applyLibraryItems(cached.items());
            });
        } catch (Exception cacheError) {
            runOnClient(() -> {
                if (acceptsLibraryRefresh(requestedView, requestEpoch)) {
                    status = CompanionUiErrors.message("sync", reason);
                }
            });
        }
    }

    private boolean acceptsLibraryRefresh(LibraryView requestedView, long requestEpoch) {
        return view == requestedView && libraryRefresh.accepts(requestEpoch);
    }

    private void applyLibraryItems(List<CompanionLibraryItem> loadedItems) {
        items.clear();
        items.addAll(loadedItems);
        page = clampPage(page);
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private void syncInstalled() {
        status = "Синхронизация установленных схем...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                SyncInstalledResult result = runtime.syncService().refreshInstalledLitematics();
                runOnClient(() -> status = "Синхронизация: " + result.refreshed() + "/" + result.checked() + ", ошибок: " + result.failed() + ".");
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    expireSessionLocally("Сессия истекла. Войдите заново.");
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("sync", e));
                }
            }
        });
    }

    private void updateAll() {
        LibraryView requestedView = view;
        long requestEpoch = libraryRefresh.begin();
        status = "Обновление облака и файлов...";
        showCachedLibraryImmediately(requestedView, requestEpoch);
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime requestRuntime = CompanionRuntime.create(client());
                if (!requestRuntime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> {
                        if (!acceptsLibraryRefresh(requestedView, requestEpoch)) return;
                        runtime = requestRuntime;
                        signedIn = false;
                        sessionStatus = "Сессия: вход не выполнен";
                        status = "Сначала войдите через код входа.";
                        clearChildren();
                        rebuildControls();
                    });
                    return;
                }

                CompanionSyncService syncService = requestRuntime.syncService();
                CompletableFuture<SyncInstalledResult> syncFuture = CompletableFuture.supplyAsync(syncService::refreshInstalledLitematics);
                ItemListResponse<CompanionLibraryItem> response = switch (requestedView) {
                    case FAVORITES -> requestRuntime.apiClient().favorites();
                    case RECENT -> requestRuntime.apiClient().recent();
                    case MY_ARTS -> requestRuntime.apiClient().library();
                };
                List<CompanionLibraryItem> loadedItems = response.items() == null ? List.of() : response.items();
                if (!libraryRefresh.accepts(requestEpoch)) return;
                requestRuntime.libraryCache().write(requestRuntime.sessionStore().userId(), requestedView.cacheKey, loadedItems);
                runOnClient(() -> {
                    if (!acceptsLibraryRefresh(requestedView, requestEpoch)) return;
                    runtime = requestRuntime;
                    signedIn = true;
                    sessionStatus = sessionSummary(requestRuntime.sessionInfo());
                    applyLibraryItems(loadedItems);
                    status = "Обновлено: " + loadedItems.size() + ". Схемы синхронизируются...";
                });
                syncFuture.whenComplete((sync, syncError) -> runOnClient(() -> {
                    if (!acceptsLibraryRefresh(requestedView, requestEpoch)) return;
                    status = syncError == null
                        ? "Обновлено: " + loadedItems.size() + " / схемы " + sync.refreshed() + "/" + sync.checked() + "."
                        : "Облако обновлено. Синхронизация схем не завершена.";
                }));
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    runLibraryAction(requestEpoch, () -> expireSessionLocally("Сессия истекла. Войдите заново."));
                } else {
                    loadCachedLibrary(requestedView, requestEpoch, e.getMessage());
                }
            }
        });
    }

    private void logout() {
        libraryRefresh.begin();
        status = "Выход из аккаунта...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime logoutRuntime = runtime == null ? CompanionRuntime.create(client()) : runtime;
                String warning = logoutRuntime.revokeAndClearSession();
                runOnClient(() -> {
                    LensManager.instance().clearForLogout();
                    runtime = logoutRuntime;
                    signedIn = false;
                    sessionStatus = "Сессия: вход не выполнен";
                    items.clear();
                    page = 0;
                    status = warning == null
                        ? "Вы вышли. Установленные файлы остались на диске."
                        : "Локальный выход. " + warning;
                    clearChildren();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                runOnClient(() -> status = CompanionUiErrors.message("login", e));
            }
        });
    }

    private void accountAction() {
        if (!signedIn) {
            logoutConfirmation.reset();
            client().setScreen(new DeviceLoginScreen(this));
            return;
        }
        if (!logoutConfirmation.confirmOrArm()) {
            status = "Нажмите ещё раз для подтверждения";
            if (accountButton != null) accountButton.setMessage(CompanionI18n.text("Подтвердить выход"));
            return;
        }
        logout();
    }

    private String accountButtonLabel() {
        if (!signedIn) return "Войти";
        return logoutConfirmation.armed() ? "Подтвердить выход" : "Выйти";
    }

    private void openSite(String path) {
        logoutConfirmation.reset();
        try {
            CompanionRuntime siteRuntime = runtime == null ? CompanionRuntime.create(client()) : runtime;
            Util.getOperatingSystem().open(siteRuntime.config().siteUri(path));
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void rebuildArtButtons() {
        var shell = WorkshopLayout.library(width, height);
        if (!shell.split() && compactDetail) return;
        var list = workshopList();
        var visible = filteredItems();
        page = clampPage(page);
        int start = page * visibleRows();
        for (int i = start; i < Math.min(start + visibleRows(), visible.size()); i++) {
            var item = visible.get(i);
            workshopButton("library.highlight_art", "", null,
                new WorkshopLayout.Rect(list.x(), list.y() + (i - start) * 48, list.width(), 44), true, item.artId().equals(selectedArtId), true,
                () -> { compactDetail = !shell.split(); selectItem(item); });
        }
    }


    private void ensureSelectedItem(List<CompanionLibraryItem> visibleItems) {
        if (visibleItems.isEmpty()) {
            selectedArtId = "";
            return;
        }
        boolean exists = visibleItems.stream().anyMatch(item -> item.artId().equals(selectedArtId));
        if (!exists) selectedArtId = visibleItems.getFirst().artId();
    }

    private void selectItem(CompanionLibraryItem item) {
        selectedArtId = item.artId();
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private CompanionLibraryItem selectedItem() {
        return filteredItems().stream().filter(item -> item.artId().equals(selectedArtId)).findFirst().orElse(null);
    }

    private void openSelectedArt() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) client().setScreen(new CompanionArtScreen(this, item.artId(), item.title(), developmentFixtureActive));
    }

    private void quickInstallSelected() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) quickInstall(item);
    }

    private void quickFavoriteSelected() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) quickToggleFavorite(item);
    }

    private void quickLensSelected(){
        var item=selectedItem();if(item==null)return;
        client().setScreen(new LensScreen(this,developmentFixtureActive));
        if(!developmentFixtureActive)LensManager.instance().startCloud(client(),item.artId(),item.currentVersionId());
    }
    private void quickTrackerSelected() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) quickOpenTracker(item);
    }

    private void quickToggleFavorite(CompanionLibraryItem item) {
        if (!signedIn) {
            status = "Сначала войдите через код входа.";
            return;
        }

        boolean targetValue = !item.isFavorite();
        long actionEpoch = libraryRefresh.begin();
        CompanionRuntime actionRuntime = runtime;
        status = targetValue ? "Добавление в избранное..." : "Удаление из избранного...";
        CompletableFuture.runAsync(() -> {
            try {
                actionRuntime.apiClient().setFavorite(item.artId(), targetValue);
                CompanionLibraryItem updated = item.withFavorite(targetValue);
                syncFavoriteCaches(actionRuntime, updated, targetValue);
                runLibraryAction(actionEpoch, () -> {
                    replaceVisibleItem(updated);
                    status = targetValue ? "Добавлено в избранное: " + updated.title() : "Убрано из избранного: " + updated.title();
                    clearChildren();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                runLibraryAction(actionEpoch, () -> {
                    if (isAuthFailure(e)) expireSessionLocally("Сессия истекла. Войдите заново.");
                    else status = CompanionUiErrors.message("sync", e);
                });
            }
        });
    }

    private void quickPlaceSelected() {
        var item = selectedItem();
        if(item==null||developmentFixtureActive)return;
        placementPartCount();final int part=placementPart;
        var world=client().world;
        var origin=client().player==null?null:client().player.getBlockPos().toImmutable();
        if(!signedIn||runtime==null||world==null||origin==null){
            status=CompanionI18n.english(client())?"Sign in and open a world.":"Войдите в аккаунт и откройте мир.";return;
        }
        long epoch=libraryRefresh.begin();var actionRuntime=runtime;
        status=CompanionI18n.english(client())?"Preparing placement...":"Подготовка размещения...";
        CompletableFuture.runAsync(()->{
            try{
                var prepared=LibraryPlacementSource.prepare(actionRuntime.apiClient(),client().runDirectory.toPath(),
                    item.artId(),item.currentVersionId(),part);
                runLibraryAction(epoch,()->{
                    var selected=selectedItem();
                    if(client().world!=world||client().player==null||selected==null||!selected.artId().equals(item.artId())
                        ||!java.util.Objects.equals(selected.currentVersionId(),item.currentVersionId())||placementPart!=part)return;
                    if(prepared.twoLayer()&&SuppressionManager.instance().active()){
                        status=CompanionI18n.english(client())?"A Two-layer session is active.":"Уже запущена стройка Two-layer.";return;
                    }
                    var artOrigin=prepared.schematicOrigin(origin.getX(),origin.getY(),origin.getZ());
                    var placementOrigin=new net.minecraft.util.math.BlockPos(artOrigin.x(),artOrigin.y(),artOrigin.z());
                    var result=prepared.twoLayer()
                        ? OptionalLitematicaAdapter.createPlacement(prepared.path(),origin,prepared.planSha256())
                        : OptionalLitematicaAdapter.createArtPlacement(prepared.path(),placementOrigin,prepared.sha256());
                    status=CompanionI18n.english(client())
                        ?(result.placed()?"Schematic placed in Litematica.":"Could not place schematic. Check Litematica and MaLiLib.")
                        :result.message();
                });
            }catch(Exception error){
                runLibraryAction(epoch,()->status=CompanionUiErrors.message("download",error));
            }
        });
    }

    private void quickInstall(CompanionLibraryItem item) {
        if (!signedIn) {
            status = "Сначала войдите через код входа.";
            return;
        }

        status = "Установка схемы...";
        long actionEpoch = libraryRefresh.begin();
        CompanionRuntime actionRuntime = runtime;
        CompletableFuture.runAsync(() -> {
            try {
                InstalledArtifact installed = actionRuntime.syncService().installLitematic(item.artId());
                CompanionTelemetryManager.record(CompanionTelemetryEvent.SCHEMATIC_INSTALLED);
                LitematicaStatus litematicaStatus = actionRuntime.litematicaStatus();
                runLibraryAction(actionEpoch, () -> {
                    status = litematicaStatus.ready()
                        ? "Схема установлена: " + installed.filename()
                        : "Схема установлена: " + installed.filename() + " / " + litematicaStatus.warning();
                    clearChildren();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                runLibraryAction(actionEpoch, () -> {
                    if (isAuthFailure(e)) expireSessionLocally("Сессия истекла. Войдите заново.");
                    else status = CompanionUiErrors.message("download", e);
                });
            }
        });
    }

    private void quickOpenTracker(CompanionLibraryItem item) {
        if (!signedIn) {
            status = "Сначала войдите через код входа.";
            return;
        }

        status = "Открываю трекер...";
        long trackerEpoch = libraryRefresh.begin();
        CompanionRuntime actionRuntime = runtime;
        CompletableFuture.runAsync(() -> {
            try {
                TrackerForArtResult tracker = actionRuntime.syncService().trackerForArtResult(item.artId());
                BuildSessionState session = tracker.session();
                if (tracker.created()) CompanionTelemetryManager.record(CompanionTelemetryEvent.TRACKER_CREATED);
                runOnClient(() -> {
                    if (!libraryRefresh.accepts(trackerEpoch) || client().currentScreen != this
                            || !selectedArtId.equals(item.artId())) return;
                    client().setScreen(new TrackerSessionScreen(this, session.id()));
                });
            } catch (Exception e) {
                runLibraryAction(trackerEpoch, () -> {
                    if (isAuthFailure(e)) expireSessionLocally("Сессия истекла. Войдите заново.");
                    else status = CompanionUiErrors.message("tracker", e);
                });
            }
        });
    }

    void applyDeletedArt(String artId, String title) {
        items.removeIf(item -> item.artId().equals(artId));
        page = clampPage(page);
        status = "Арт удален: " + title;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private WorkshopLayout.Rect workshopList() {
        var shell = WorkshopLayout.library(width, height);
        return shell.split() ? shell.list() : new WorkshopLayout.Rect(shell.preview().x(), shell.preview().y(), shell.preview().width(), shell.footer().y() - 4 - shell.preview().y());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var shell = WorkshopLayout.library(width, height);
        context.fill(0, 0, width, height, 0x88000000);
        WorkshopChrome.frame(context::fill, shell.frame(), workshopTheme);
        WorkshopChrome.librarySections(context::fill, shell, workshopTheme);
        if (shell.navigation().width() >= 548) {
            WorkshopDraw.text(context, textRenderer, "MAPKLUSS", shell.navigation().x() + 6, shell.navigation().y() + 10, 92, workshopTheme.color("accent"));
        }
        var footer = shell.footer();
        String footerText = status.isBlank() ? (signedIn ? "Cloud" : CompanionI18n.translate("Войти")) : CompanionI18n.translate(status);
        int footerOffset = !shell.split() && compactDetail ? 32 : 4;
        WorkshopDraw.text(context, textRenderer, footerText, footer.x() + footerOffset, footer.y() + 6, footer.width() - 128 - footerOffset, workshopTheme.color("text-secondary"));
        super.render(context, mouseX, mouseY, delta);
        if (shell.split() || !compactDetail) {
            var list = workshopList();
            var visible = filteredItems();
            int start = page * visibleRows();
            for (int i = start; i < Math.min(start + visibleRows(), visible.size()); i++) {
                var item = visible.get(i);
                int y = list.y() + (i - start) * 48;
                drawWorkshopPreview(context, item, new WorkshopLayout.Rect(list.x() + 5, y + 5, 34, 34));
                WorkshopDraw.text(context, textRenderer, item.title(), list.x() + 45, y + 10, list.width() - 51, workshopTheme.color("text-primary"));
                WorkshopDraw.text(context, textRenderer, item.gridLabel() + "  " + item.modeLabel(), list.x() + 45, y + 26, list.width() - 51, workshopTheme.color("text-secondary"));
            }
            if (visible.isEmpty()) {
                WorkshopDraw.text(context, textRenderer, CompanionI18n.translate("Список пуст"), list.x() + 8, list.y() + 12, list.width() - 16, workshopTheme.color("text-secondary"));
            }
        }
        if (shell.split() || compactDetail) {
            var p = placementPreview(shell);
            context.fill(p.x(), p.y(), p.right(), p.bottom(), workshopTheme.color("field-bg"));
            var item = selectedItem();
            if (item != null) {
                drawWorkshopPreview(context, item, new WorkshopLayout.Rect(p.x() + 6, p.y() + 6, Math.max(0, p.width() - 12), Math.max(0, p.height() - 12)));
                if(placementPartCount()>1) {
                    WorkshopDraw.text(context, textRenderer,
                        (CompanionI18n.english(client())?"Map ":"Карта ")+(placementPart+1)+"/"+placementPartCount()
                            +"  "+(placementPart%item.grid().wide()+1)+":"+(placementPart/item.grid().wide()+1),
                        p.x()+32,shell.preview().bottom()-18,Math.max(0,p.width()-64),workshopTheme.color("text-primary"));
                    var area=WorkshopLayout.contain(new WorkshopLayout.Rect(p.x()+6,p.y()+6,Math.max(0,p.width()-12),Math.max(0,p.height()-12)),
                        item.grid().wide()*128,item.grid().tall()*128);
                    int col=placementPart%item.grid().wide(),row=placementPart/item.grid().wide();
                    int left=area.x()+col*area.width()/item.grid().wide(),right=area.x()+(col+1)*area.width()/item.grid().wide();
                    int top=area.y()+row*area.height()/item.grid().tall(),bottom=area.y()+(row+1)*area.height()/item.grid().tall();
                    int color=workshopTheme.color("accent");
                    if(right>left&&bottom>top){context.fill(left,top,right,top+1,color);context.fill(left,bottom-1,right,bottom,color);
                        context.fill(left,top,left+1,bottom,color);context.fill(right-1,top,right,bottom,color);}
                }
                var meta = shell.metadata();
                WorkshopDraw.text(context, textRenderer, item.title(), meta.x() + 4, meta.y() + 4, meta.width() - 40, workshopTheme.color("text-primary"));
                WorkshopDraw.text(context, textRenderer, item.gridLabel() + "  " + item.modeLabel() + "  " + item.privacyLabel(), meta.x() + 4, meta.y() + 20, meta.width() - 40, workshopTheme.color("text-secondary"));
            }
        }
    }

    private void drawWorkshopPreview(DrawContext context, CompanionLibraryItem item, WorkshopLayout.Rect area) {
        if (developmentFixtureActive) {
            int index = Math.max(0, items.indexOf(item)) % 4;
            String[] names = {"starry-night", "great-wave", "pearl-portrait", "mapkluss-logo"};
            int[][] sizes = {{256, 256}, {384, 256}, {256, 384}, {128, 128}};
            var texture = net.minecraft.util.Identifier.of(MapKlussCompanionClient.MOD_ID, "textures/dev/library/" + names[index] + ".png");
            WorkshopDraw.image(context, texture, area, sizes[index][0], sizes[index][1]);
            return;
        }
        var preview = CompanionPreviewTextures.request(item);
        if (preview.ready()) WorkshopDraw.image(context, preview.identifier(), area, preview.imageWidth(), preview.imageHeight());
        else WorkshopDraw.icon(context, WorkshopIcon.LIBRARY, area.x() + Math.max(0, (area.width() - 16) / 2), area.y() + Math.max(0, (area.height() - 16) / 2), workshopTheme.color("text-disabled"));
    }



    private void drawLibraryPreview(DrawContext context, CompanionLibraryItem item, int x, int rowY, int rowWidth) {
        int y = rowY;
        boolean selected = item.artId().equals(selectedArtId);
        context.fill(x, y, x + rowWidth, y + THUMB_SIZE, selected ? UiTheme.SURFACE_RAISED : UiTheme.SURFACE);
        context.fill(x, y + THUMB_SIZE, x + rowWidth, y + THUMB_SIZE + 1, UiTheme.BORDER);
        if (selected) context.fill(x, y + 4, x + 2, y + THUMB_SIZE - 4, UiTheme.LIME);
        MapKlussUi.drawPreviewWell(context, x + 4, y + 4, x + THUMB_SIZE - 4, y + THUMB_SIZE - 4);
        if (developmentFixtureActive) {
            MapKlussUi.drawCenteredIn(context, textRenderer, item.gridLabel(), x + THUMB_SIZE / 2, y + 12, THUMB_SIZE - 12, MapKlussUi.MUTED);
            MapKlussUi.drawCenteredIn(context, textRenderer, "PNG", x + THUMB_SIZE / 2, y + 24, THUMB_SIZE - 12, MapKlussUi.DIM);
            return;
        }

        CompanionPreviewTextures.PreviewTexture preview = CompanionPreviewTextures.request(item);
        if (preview.ready()) {
            int imageWidth = preview.imageWidth();
            int imageHeight = preview.imageHeight();
            int inner = THUMB_SIZE - 10;
            double scale = Math.min((double) inner / imageWidth, (double) inner / imageHeight);
            int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            int drawX = x + 5 + (inner - drawWidth) / 2;
            int drawY = y + 5 + (inner - drawHeight) / 2;
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                preview.identifier(),
                drawX,
                drawY,
                0.0F,
                0.0F,
                drawWidth,
                drawHeight,
                imageWidth,
                imageHeight,
                imageWidth,
                imageHeight
            );
            return;
        }

        int color = preview.loading() ? MapKlussUi.MUTED : MapKlussUi.DIM;
        String grid = item.gridLabel();
        MapKlussUi.drawCenteredIn(context, textRenderer, grid, x + THUMB_SIZE / 2, y + 12, THUMB_SIZE - 12, color);
        MapKlussUi.drawCenteredIn(context, textRenderer, "PNG", x + THUMB_SIZE / 2, y + 24, THUMB_SIZE - 12, MapKlussUi.DIM);
    }




    private void applySearch() {
        searchQuery = searchDraft.trim().toLowerCase(java.util.Locale.ROOT);
        page = 0;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private void clearSearch() {
        searchQuery = "";
        searchDraft = "";
        page = 0;
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    private InstalledArtifactIndex loadInstalledIndex() {
        try {
            return InstalledArtifactIndex.load(LitematicaPaths.companionIndexPath(client().runDirectory.toPath()));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected boolean submitFocusedInput() {
        if (searchInput == null || !searchInput.isFocused()) return false;
        applySearch();
        return true;
    }

    private String itemLabel(CompanionLibraryItem item, InstalledArtifactIndex installedIndex) {
        StringBuilder label = new StringBuilder();
        if (item.isFavorite()) label.append("★ ");
        if (installedIndex != null && !installedIndex.findByArt(item.artId()).isEmpty()) label.append("[L] ");
        label.append(item.title()).append("  ").append(item.gridLabel());
        label.append(" / ").append(item.modeLabel());
        label.append(" / ").append(item.privacyLabel());
        return label.toString();
    }

    private String cachedNow() {
        return Instant.now().toString();
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


    private CompanionUiLayout.Rect libraryControls(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect host = shell.hasInspector() ? shell.inspector() : shell.content();
        return new CompanionUiLayout.Rect(host.x() + 8, host.y(), Math.max(1, host.width() - 16), 64);
    }

    private CompanionUiLayout.Rect selectedStage(CompanionUiLayout.Shell shell) {
        if (shell.mode() == CompanionUiLayout.Mode.COMPACT) return CompanionUiLayout.Rect.EMPTY;
        CompanionUiLayout.Rect content = shell.content();
        int top = content.y() + (shell.hasInspector() ? 10 : 68);
        int bottomReserve = shell.hasInspector() ? 44 : Math.max(92, content.height() * 42 / 100);
        return new CompanionUiLayout.Rect(
            content.x() + 10,
            top,
            Math.max(1, content.width() - 20),
            Math.max(72, content.bottom() - top - bottomReserve)
        );
    }

    private CompanionUiLayout.Rect selectedActionBar(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect stage = selectedStage(shell);
        if (stage.width() > 0) {
            return new CompanionUiLayout.Rect(stage.x(), stage.bottom() + 8, stage.width(), 22);
        }
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 8, content.bottom() - 28, Math.max(1, content.width() - 16), 22);
    }

    private CompanionUiLayout.Rect libraryList(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect controls = libraryControls(shell);
        if (shell.hasInspector()) {
            CompanionUiLayout.Rect host = shell.inspector();
            return new CompanionUiLayout.Rect(host.x() + 8, controls.bottom() + 6, Math.max(1, host.width() - 16), Math.max(1, host.bottom() - controls.bottom() - 14));
        }
        CompanionUiLayout.Rect content = shell.content();
        int top = shell.mode() == CompanionUiLayout.Mode.COMPACT
            ? controls.bottom() + 6
            : selectedActionBar(shell).bottom() + 8;
        int bottom = shell.mode() == CompanionUiLayout.Mode.COMPACT ? selectedActionBar(shell).y() - 6 : content.bottom() - 8;
        return new CompanionUiLayout.Rect(content.x() + 8, top, Math.max(1, content.width() - 16), Math.max(1, bottom - top));
    }

    private int visibleRows() {
        return Math.max(1, workshopList().height() / 48);
    }

    private int bottomReserved() {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return sideRailLayout(panelWidth, left) ? 34 : 118;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return false;
    }

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return MapKlussUi.centeredLeft(width, panelWidth);
    }

    private List<CompanionLibraryItem> filteredItems() {
        var base = developmentFixtureActive && view == LibraryView.FAVORITES ? items.stream().filter(CompanionLibraryItem::isFavorite).toList() : items;
        if (searchQuery.isBlank()) return base;
        List<CompanionLibraryItem> filtered = new ArrayList<>();
        for (CompanionLibraryItem item : base) {
            String haystack = (item.title() + " " + item.gridLabel() + " " + item.mode() + " " + item.modeLabel() + " " + item.privacy() + " " + item.privacyLabel()).toLowerCase();
            if (haystack.contains(searchQuery)) filtered.add(item);
        }
        return filtered;
    }

    private void replaceVisibleItem(CompanionLibraryItem updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).artId().equals(updated.artId())) {
                items.set(i, updated);
                break;
            }
        }
        if (!updated.isFavorite() && view == LibraryView.FAVORITES) {
            items.removeIf(candidate -> candidate.artId().equals(updated.artId()));
            page = clampPage(page);
        }
    }

    private void syncFavoriteCaches(CompanionRuntime runtime, CompanionLibraryItem updated, boolean favorite) throws IOException {
        String userId = runtime.sessionStore().userId();
        runtime.libraryCache().replaceViewItem(userId, "my", updated);
        runtime.libraryCache().replaceViewItem(userId, "recent", updated);

        if (favorite) {
            runtime.libraryCache().upsertViewItem(userId, "favorites", updated);
        } else {
            runtime.libraryCache().removeViewItem(userId, "favorites", updated.artId());
        }

        Optional<ManifestCache.CachedManifest> cachedManifest = runtime.manifestCache().read(userId, updated.artId());
        if (cachedManifest.isPresent()) {
            CompanionManifest manifest = cachedManifest.get().manifest();
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
                favorite,
                manifest.collectionIds(),
                manifest.artifacts(),
                manifest.updatedAt()
            ));
        }
    }

    private String sessionSummary(CompanionSessionInfo session) {
        if (session == null || !session.isSignedIn()) return "Сессия: вход не выполнен";
        String state = session.isExpired() ? "истекла" : "активна";
        String expiry = session.expiresAt() == null ? "до ?" : "до " + formatInstant(session.expiresAt());
        return "Сессия: " + session.shortUserId() + " / " + state + " / " + expiry;
    }

    private boolean isAuthFailure(Exception error) {
        return CompanionAuthSupport.isAuthFailure(error);
    }

    private void expireSessionLocally(String nextStatus) {
        libraryRefresh.begin();
        try {
            CompanionRuntime expiringRuntime = runtime == null ? CompanionRuntime.create(client()) : runtime;
            CompanionAuthSupport.clearSessionQuietly(expiringRuntime);
            runOnClient(() -> {
                runtime = expiringRuntime;
                signedIn = false;
                sessionStatus = "Сессия: вход не выполнен";
                items.clear();
                page = 0;
                status = nextStatus;
                clearChildren();
                rebuildControls();
                rebuildArtButtons();
            });
        } catch (Exception clearError) {
            runOnClient(() -> status = nextStatus);
        }
    }

    private String formatInstant(String value) {
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return value == null ? "неизвестно" : value;
        }
    }

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
    }

    private void runLibraryAction(long epoch, Runnable task) {
        runOnClient(() -> {
            if (client().currentScreen == this) libraryRefresh.runIfCurrent(epoch, task);
        });
    }

    private enum LibraryView {
        MY_ARTS("мои арты", "my"),
        FAVORITES("избранное", "favorites"),
        RECENT("недавние", "recent");

        private final String label;
        private final String cacheKey;

        LibraryView(String label, String cacheKey) {
            this.label = label;
            this.cacheKey = cacheKey;
        }
    }
}
