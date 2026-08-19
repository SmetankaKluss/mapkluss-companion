package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompanionLibraryScreen extends Screen {
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
    private String selectedArtId = "";
    private int page;
    private boolean secondaryActions;
    private boolean telemetryRecorded;
    private AbstractWidget pageButton;
    private EditBox searchInput;
    private MapKlussButton accountButton;
    private final CompanionConfirmation logoutConfirmation = new CompanionConfirmation();

    public CompanionLibraryScreen(Screen parent) {
        this(parent, false);
    }

    CompanionLibraryScreen(Screen parent, boolean syncOnOpen) {
        super(Component.literal("Библиотека MapKluss"));
        this.parent = parent;
        this.syncOnOpen = syncOnOpen;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (!telemetryRecorded) {
            telemetryRecorded = true;
            CompanionTelemetryManager.record(CompanionTelemetryEvent.LIBRARY_OPENED);
        }
        refreshSignedInState();
        rebuildControls();
        rebuildArtButtons();
        if (syncOnOpen) updateAll();
        else loadLibrary();
    }

    private void refreshSignedInState() {
        try {
            runtime = CompanionRuntime.create(client());
            signedIn = runtime.sessionStore().hasAccessToken();
            sessionStatus = sessionSummary(runtime.sessionInfo());
        } catch (Exception e) {
            signedIn = false;
            sessionStatus = "Сессия: недоступна";
        }
    }

    private void rebuildControls() {
        ensureSelectedItem(filteredItems());
        CompanionUiLayout.Shell shell = libraryShell();
        addNavigationControls(shell);
        CompanionUiLayout.Rect controls = libraryControls(shell);
        int gap = 4;
        int tabButtonWidth = Math.max(44, (controls.width() - gap * 3) / 4);
        int tabsY = controls.y() + 8;
        addRenderableWidget(MapKlussButton.builder(Component.literal("Мои арты"), button -> switchView(LibraryView.MY_ARTS))
            .action("library.my_arts")
            .selected(view == LibraryView.MY_ARTS).dimensions(controls.x(), tabsY, tabButtonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Избранное"), button -> switchView(LibraryView.FAVORITES))
            .action("library.favorites")
            .selected(view == LibraryView.FAVORITES).dimensions(controls.x() + tabButtonWidth + gap, tabsY, tabButtonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Недавние"), button -> switchView(LibraryView.RECENT))
            .action("library.recent")
            .selected(view == LibraryView.RECENT).dimensions(controls.x() + (tabButtonWidth + gap) * 2, tabsY, tabButtonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Коллекции"), button -> client().gui.setScreen(new CompanionCollectionsScreen(this)))
            .action("library.collections")
            .dimensions(controls.x() + (tabButtonWidth + gap) * 3, tabsY, tabButtonWidth, 20).build());

        int searchY = tabsY + 28;
        int refreshWidth = 24;
        int clearWidth = 24;
        int pageWidth = 48;
        int searchButtonWidth = 50;
        int searchWidth = Math.max(52, controls.width() - refreshWidth - clearWidth - pageWidth - searchButtonWidth - gap * 4);
        searchInput = new EditBox(font, controls.x(), searchY, searchWidth, 20, CompanionI18n.text("Поиск артов"));
        searchInput.setMaxLength(80);
        searchInput.setValue(searchQuery);
        addRenderableWidget(searchInput);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Поиск"), button -> applySearch())
            .action("library.search")
            .dimensions(controls.x() + searchWidth + gap, searchY, searchButtonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("×"), button -> clearSearch())
            .action("library.search_clear")
            .tooltip(CompanionI18n.text("Очистить поиск"))
            .dimensions(controls.x() + searchWidth + searchButtonWidth + gap * 2, searchY, clearWidth, 20).build());
        pageButton = addRenderableWidget(MapKlussButton.builder(pageButtonText(), button -> nextPage())
            .action("library.page_next")
            .dimensions(controls.x() + searchWidth + searchButtonWidth + clearWidth + gap * 3, searchY, pageWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> loadLibrary())
            .action("library.refresh")
            .technical().tooltip(CompanionI18n.text("Обновить библиотеку"))
            .dimensions(controls.right() - refreshWidth, searchY, refreshWidth, 20).build());

        CompanionUiLayout.Rect actionBar = selectedActionBar(shell);
        addSelectedActions(actionBar, gap);

        int langX = shell.topBar().right() - 38;
        addRenderableWidget(MapKlussUi.languageButtonAt(this, langX, shell.topBar().y() + 9));
        updatePageButton();
    }

    private void addSelectedActions(CompanionUiLayout.Rect actionBar, int gap) {
        CompanionLibraryItem selected = selectedItem();
        int actionWidth = Math.max(40, (actionBar.width() - gap * 4) / 5);
        if (!secondaryActions) {
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Открыть"), button -> openSelectedArt()).selected(true)
                .action("library.select_art")
                .enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x(), actionBar.y(), actionWidth, 22).build());
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Схема"), button -> quickInstallSelected()).gold()
                .action("library.install")
                .enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x() + actionWidth + gap, actionBar.y(), actionWidth, 22).build());
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text(selected != null && selected.isFavorite() ? "Убрать" : "В избранное"), button -> quickFavoriteSelected())
                .action("library.toggle_favorite")
                .selected(selected != null && selected.isFavorite()).enabledWhen(() -> selectedItem() != null)
                .dimensions(actionBar.x() + (actionWidth + gap) * 2, actionBar.y(), actionWidth, 22).build());
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Трекер"), button -> quickTrackerSelected())
                .action("library.open_tracker")
                .enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x() + (actionWidth + gap) * 3, actionBar.y(), actionWidth, 22).build());
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Ещё"), button -> toggleSecondaryActions())
                .action("library.toggle_actions")
                .dimensions(actionBar.x() + (actionWidth + gap) * 4, actionBar.y(), actionBar.width() - (actionWidth + gap) * 4, 22).build());
            return;
        }
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Страница"), button -> openSelectedSite())
            .action("library.open_site")
            .technical().enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x(), actionBar.y(), actionWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Редактор"), button -> openSelectedEditor())
            .action("library.open_editor")
            .technical().enabledWhen(() -> selectedItem() != null).dimensions(actionBar.x() + actionWidth + gap, actionBar.y(), actionWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Синхронизация"), button -> updateAll())
            .action("library.sync")
            .enabledWhen(() -> signedIn).dimensions(actionBar.x() + (actionWidth + gap) * 2, actionBar.y(), actionWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Two-layer"), button -> client().gui.setScreen(new SuppressionStartScreen(this, null)))
            .action("library.import_two_layer")
            .special().dimensions(actionBar.x() + (actionWidth + gap) * 3, actionBar.y(), actionWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Назад"), button -> toggleSecondaryActions())
            .action("library.toggle_actions")
            .dimensions(actionBar.x() + (actionWidth + gap) * 4, actionBar.y(), actionBar.width() - (actionWidth + gap) * 4, 22).build());
    }

    private void toggleSecondaryActions() {
        secondaryActions = !secondaryActions;
        clearWidgets();
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
            addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destinationLabel(destination)))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> { }
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
            case ACCOUNT -> signedIn ? "Аккаунт" : "Войти";
            default -> destination.name();
        };
    }

    private void addSideRailControls(int panelWidth, int left) {
        int x = sideRailLeft(panelWidth, left);
        int buttonWidth = SIDE_RAIL_WIDTH;
        MapKlussButton.Builder accountBuilder = MapKlussButton.builder(Component.literal(accountButtonLabel()), button -> accountAction())
            .dimensions(x, 78, buttonWidth, 20);
        if (signedIn) accountBuilder.danger();
        accountButton = addRenderableWidget(accountBuilder.tooltip(CompanionI18n.text(signedIn ? "Выйти из аккаунта MapKluss" : "Войти в MapKluss")).navigationOrder(signedIn ? 1000 : 0).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт облака"), button -> openSite("/cloud"))
            .technical().dimensions(x, 104, buttonWidth, 20).build());

        addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> loadLibrary())
            .dimensions(x, 162, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Синхронизация"), button -> updateAll())
            .technical().dimensions(x, 188, buttonWidth, 20).build());

        addRenderableWidget(MapKlussButton.builder(Component.literal("Коллекции"), button -> client().gui.setScreen(new CompanionCollectionsScreen(this)))
            .dimensions(x, 246, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Скан карты"), button -> client().gui.setScreen(new ScanScreen(this)))
            .dimensions(x, 272, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Lens"), button -> client().gui.setScreen(new LensScreen(this)))
            .special().dimensions(x, 298, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Трекер"), button -> client().gui.setScreen(new TrackerOpenScreen(this)))
            .dimensions(x, 324, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Импорт Two-layer"), button -> client().gui.setScreen(new SuppressionStartScreen(this, null)))
            .special().dimensions(x, 350, buttonWidth, 20).build());

    }

    private void switchView(LibraryView nextView) {
        logoutConfirmation.reset();
        view = nextView;
        page = 0;
        searchQuery = "";
        items.clear();
        init();
    }

    private void loadLibrary() {
        logoutConfirmation.reset();
        LibraryView requestedView = view;
        status = "Загрузка: " + requestedView.label + "...";
        CompletableFuture.runAsync(() -> {
            try {
                runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> {
                        signedIn = false;
                        sessionStatus = "Сессия: вход не выполнен";
                        status = "Сначала войдите через код входа.";
                        clearWidgets();
                        rebuildControls();
                    });
                    return;
                }
                runOnClient(() -> signedIn = true);
                ItemListResponse<CompanionLibraryItem> response = switch (requestedView) {
                    case FAVORITES -> runtime.apiClient().favorites();
                    case RECENT -> runtime.apiClient().recent();
                    case MY_ARTS -> runtime.apiClient().library();
                };
                List<CompanionLibraryItem> loadedItems = response.items() == null ? List.of() : response.items();
                runtime.libraryCache().write(runtime.sessionStore().userId(), requestedView.cacheKey, loadedItems);
                runOnClient(() -> {
                    if (view != requestedView) return;
                    items.clear();
                    items.addAll(loadedItems);
                    sessionStatus = sessionSummary(runtime.sessionInfo());
                    page = clampPage(page);
                    status = "";
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    expireSessionLocally("Сессия истекла. Войдите заново.");
                } else {
                    loadCachedLibrary(requestedView, e.getMessage());
                }
            }
        });
    }

    private void loadCachedLibrary(LibraryView requestedView, String reason) {
        try {
            CompanionRuntime cachedRuntime = runtime == null ? CompanionRuntime.create(client()) : runtime;
            LibraryCache.CachedLibrary cached = cachedRuntime.libraryCache().read(cachedRuntime.sessionStore().userId(), requestedView.cacheKey);
            runOnClient(() -> {
                if (view != requestedView) return;
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
        status = "Обновление облака и файлов...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> {
                        signedIn = false;
                        sessionStatus = "Сессия: вход не выполнен";
                        status = "Сначала войдите через код входа.";
                        clearWidgets();
                        rebuildControls();
                    });
                    return;
                }

                CompanionSyncService syncService = runtime.syncService();
                CompletableFuture<SyncInstalledResult> syncFuture = CompletableFuture.supplyAsync(syncService::refreshInstalledLitematics);
                ItemListResponse<CompanionLibraryItem> response = switch (requestedView) {
                    case FAVORITES -> runtime.apiClient().favorites();
                    case RECENT -> runtime.apiClient().recent();
                    case MY_ARTS -> runtime.apiClient().library();
                };
                List<CompanionLibraryItem> loadedItems = response.items() == null ? List.of() : response.items();
                runtime.libraryCache().write(runtime.sessionStore().userId(), requestedView.cacheKey, loadedItems);
                runOnClient(() -> {
                    if (view != requestedView) return;
                    signedIn = true;
                    sessionStatus = sessionSummary(runtime.sessionInfo());
                    items.clear();
                    items.addAll(loadedItems);
                    page = clampPage(page);
                    status = "Обновлено: " + loadedItems.size() + ". Схемы синхронизируются...";
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
                syncFuture.whenComplete((sync, syncError) -> runOnClient(() -> {
                    if (view != requestedView) return;
                    status = syncError == null
                        ? "Обновлено: " + loadedItems.size() + " / схемы " + sync.refreshed() + "/" + sync.checked() + "."
                        : "Облако обновлено. Синхронизация схем не завершена.";
                }));
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    expireSessionLocally("Сессия истекла. Войдите заново.");
                } else {
                    loadCachedLibrary(requestedView, e.getMessage());
                }
            }
        });
    }

    private void logout() {
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
                    clearWidgets();
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
            client().gui.setScreen(new DeviceLoginScreen(this));
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
            Util.getPlatform().openUri(siteRuntime.config().siteUri(path));
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void rebuildArtButtons() {
        CompanionUiLayout.Shell shell = libraryShell();
        CompanionUiLayout.Rect list = libraryList(shell);
        int contentX = list.x() + THUMB_SIZE + THUMB_GAP;
        int contentWidth = Math.max(80, list.width() - THUMB_SIZE - THUMB_GAP);
        InstalledArtifactIndex installedIndex = loadInstalledIndex();
        List<CompanionLibraryItem> visibleItems = filteredItems();
        ensureSelectedItem(visibleItems);
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleItems.size());
        for (int i = start; i < end; i++) {
            CompanionLibraryItem item = visibleItems.get(i);
            int rowY = list.y() + (i - start) * ROW_HEIGHT;
            boolean selected = item.artId().equals(selectedArtId);
            addRenderableWidget(MapKlussButton.builder(MapKlussUi.clippedText(font, itemLabel(item, installedIndex), contentWidth - 8), button -> selectItem(item))
                .selected(selected).tooltip(CompanionI18n.text(item.title()))
                .dimensions(contentX, rowY, contentWidth, THUMB_SIZE).build());
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
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
    }

    private CompanionLibraryItem selectedItem() {
        return filteredItems().stream().filter(item -> item.artId().equals(selectedArtId)).findFirst().orElse(null);
    }

    private void openSelectedArt() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) client().gui.setScreen(new CompanionArtScreen(this, item.artId(), item.title()));
    }

    private void quickInstallSelected() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) quickInstall(item);
    }

    private void quickFavoriteSelected() {
        CompanionLibraryItem item = selectedItem();
        if (item != null) quickToggleFavorite(item);
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
        status = targetValue ? "Добавление в избранное..." : "Удаление из избранного...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime actionRuntime = CompanionRuntime.create(client());
                actionRuntime.apiClient().setFavorite(item.artId(), targetValue);
                CompanionLibraryItem updated = item.withFavorite(targetValue);
                syncFavoriteCaches(actionRuntime, updated, targetValue);
                runOnClient(() -> {
                    replaceVisibleItem(updated);
                    status = targetValue ? "Добавлено в избранное: " + updated.title() : "Убрано из избранного: " + updated.title();
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    expireSessionLocally("Сессия истекла. Войдите заново.");
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("sync", e));
                }
            }
        });
    }

    private void quickInstall(CompanionLibraryItem item) {
        if (!signedIn) {
            status = "Сначала войдите через код входа.";
            return;
        }

        status = "Установка схемы...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime actionRuntime = CompanionRuntime.create(client());
                InstalledArtifact installed = actionRuntime.syncService().installLitematic(item.artId());
                CompanionTelemetryManager.record(CompanionTelemetryEvent.SCHEMATIC_INSTALLED);
                LitematicaStatus litematicaStatus = actionRuntime.litematicaStatus();
                runOnClient(() -> {
                    status = litematicaStatus.ready()
                        ? "Схема установлена: " + installed.filename()
                        : "Схема установлена: " + installed.filename() + " / " + litematicaStatus.warning();
                    clearWidgets();
                    rebuildControls();
                    rebuildArtButtons();
                });
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    expireSessionLocally("Сессия истекла. Войдите заново.");
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("download", e));
                }
            }
        });
    }

    private void quickOpenTracker(CompanionLibraryItem item) {
        if (!signedIn) {
            status = "Сначала войдите через код входа.";
            return;
        }

        status = "Открываю трекер...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime actionRuntime = CompanionRuntime.create(client());
                TrackerForArtResult tracker = actionRuntime.syncService().trackerForArtResult(item.artId());
                BuildSessionState session = tracker.session();
                if (tracker.created()) CompanionTelemetryManager.record(CompanionTelemetryEvent.TRACKER_CREATED);
                runOnClient(() -> client().gui.setScreen(new TrackerSessionScreen(this, session.id())));
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    expireSessionLocally("Сессия истекла. Войдите заново.");
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("tracker", e));
                }
            }
        });
    }

    void applyDeletedArt(String artId, String title) {
        items.removeIf(item -> item.artId().equals(artId));
        page = clampPage(page);
        status = "Арт удален: " + title;
        clearWidgets();
        rebuildControls();
        rebuildArtButtons();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, font, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.LIBRARY, CompanionI18n.translate("Библиотека"), status),
            true
        );
        CompanionUiLayout.Rect controls = libraryControls(shell);
        CompanionUiLayout.Rect list = libraryList(shell);
        context.fill(controls.x(), controls.y(), controls.right(), controls.bottom(), UiTheme.SURFACE_RAISED);
        context.fill(list.x(), list.y() - 1, list.right(), list.y(), UiTheme.BORDER);
        drawLibraryPreviews(context, list);
        drawSelectedStage(context, shell);
        boolean empty = filteredItems().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                font,
                view == LibraryView.RECENT ? "Недавних артов пока нет" : "Список пуст",
                signedIn ? "Сохрани арт на сайте или обнови библиотеку" : "Войди через код, чтобы открыть облако",
                list.x(), list.y(), list.width(), Math.max(40, list.height())
            );
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.LIBRARY);
        CompanionUiLayout.Rect refresh = new CompanionUiLayout.Rect(controls.right() - 24, controls.y() + 36, 24, 20);
        MapKlussUi.drawIcon(context, MapKlussIcon.REFRESH, refresh.x() + 4, refresh.y() + 2, MapKlussUi.CYAN);
    }

    private void drawLibraryPreviews(GuiGraphicsExtractor context, CompanionUiLayout.Rect list) {
        List<CompanionLibraryItem> visibleItems = filteredItems();
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleItems.size());
        for (int i = start; i < end; i++) {
            CompanionLibraryItem item = visibleItems.get(i);
            int rowY = list.y() + (i - start) * ROW_HEIGHT;
            drawLibraryPreview(context, item, list.x(), rowY, list.width());
        }
    }

    private void drawLibraryPreview(GuiGraphicsExtractor context, CompanionLibraryItem item, int x, int rowY, int rowWidth) {
        int y = rowY;
        boolean selected = item.artId().equals(selectedArtId);
        context.fill(x, y, x + rowWidth, y + THUMB_SIZE, selected ? UiTheme.SURFACE_RAISED : UiTheme.SURFACE);
        context.fill(x, y + THUMB_SIZE, x + rowWidth, y + THUMB_SIZE + 1, UiTheme.BORDER);
        if (selected) context.fill(x, y + 4, x + 2, y + THUMB_SIZE - 4, UiTheme.LIME);
        MapKlussUi.drawPreviewWell(context, x + 4, y + 4, x + THUMB_SIZE - 4, y + THUMB_SIZE - 4);

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
            context.blit(
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
        MapKlussUi.drawCenteredIn(context, font, grid, x + THUMB_SIZE / 2, y + 12, THUMB_SIZE - 12, color);
        MapKlussUi.drawCenteredIn(context, font, "PNG", x + THUMB_SIZE / 2, y + 24, THUMB_SIZE - 12, MapKlussUi.DIM);
    }

    private void drawSelectedStage(GuiGraphicsExtractor context, CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect stage = selectedStage(shell);
        if (stage.width() <= 0 || stage.height() <= 0) return;
        MapKlussUi.drawPreviewWell(context, stage.x(), stage.y(), stage.right(), stage.bottom());
        CompanionLibraryItem item = selectedItem();
        if (item == null) return;
        CompanionPreviewTextures.PreviewTexture preview = CompanionPreviewTextures.request(item);
        int metaHeight = 34;
        int boxWidth = Math.max(1, stage.width() - 24);
        int boxHeight = Math.max(1, stage.height() - metaHeight - 20);
        int boxX = stage.x() + 12;
        int boxY = stage.y() + 10;
        if (preview.ready()) {
            int imageWidth = preview.imageWidth();
            int imageHeight = preview.imageHeight();
            double scale = Math.min((double) boxWidth / imageWidth, (double) boxHeight / imageHeight);
            int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            context.blit(
                RenderPipelines.GUI_TEXTURED, preview.identifier(),
                boxX + (boxWidth - drawWidth) / 2, boxY + (boxHeight - drawHeight) / 2,
                0.0F, 0.0F, drawWidth, drawHeight,
                imageWidth, imageHeight, imageWidth, imageHeight
            );
        } else {
            MapKlussUi.drawEmptyState(context, font, preview.loading() ? "Загрузка превью" : "Превью недоступно",
                item.gridLabel(), boxX, boxY, boxWidth, boxHeight);
        }
        int metaY = stage.bottom() - metaHeight;
        context.fill(stage.x() + 1, metaY, stage.right() - 1, stage.bottom() - 1, UiTheme.SURFACE_RAISED);
        MapKlussUi.drawLeft(context, font, item.title(), stage.x() + 12, metaY + 7, stage.width() - 24, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, font,
            item.gridLabel() + "  ·  " + item.modeLabel() + "  ·  " + item.privacyLabel(),
            stage.x() + 12, metaY + 20, stage.width() - 24, MapKlussUi.MUTED);
    }

    private void drawSideRailSections(GuiGraphicsExtractor context, int railLeft) {
        MapKlussUi.drawSectionAt(context, font, "Аккаунт", railLeft, SIDE_RAIL_WIDTH, 58, 76);
        MapKlussUi.drawSectionAt(context, font, "Облако", railLeft, SIDE_RAIL_WIDTH, 142, 76);
        MapKlussUi.drawSectionAt(context, font, "Инструменты", railLeft, SIDE_RAIL_WIDTH, 226, 122);
    }

    private void drawActionGroups(GuiGraphicsExtractor context) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        MapKlussUi.drawActionGroupLabel(context, font, "Облако", left, panelWidth, height - 92, 20);
        MapKlussUi.drawActionGroupLabel(context, font, "Инструменты", left, panelWidth, height - 58, 20);
    }

    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getValue().trim().toLowerCase();
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

    private InstalledArtifactIndex loadInstalledIndex() {
        try {
            return InstalledArtifactIndex.load(LitematicaPaths.companionIndexPath(client().gameDirectory.toPath()));
        } catch (IOException e) {
            return null;
        }
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

    private CompanionUiLayout.Shell libraryShell() {
        return CompanionUiLayout.shell(width, height, true);
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
        CompanionUiLayout.Rect list = libraryList(libraryShell());
        return Math.max(1, Math.min(ROWS, list.height() / ROW_HEIGHT));
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
        if (searchQuery.isBlank()) return items;
        List<CompanionLibraryItem> filtered = new ArrayList<>();
        for (CompanionLibraryItem item : items) {
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
                clearWidgets();
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

    private Minecraft client() {
        return Minecraft.getInstance();
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
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
