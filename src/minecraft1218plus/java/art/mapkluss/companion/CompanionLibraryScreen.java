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

public final class CompanionLibraryScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int SECTION_WIDTH = 500;
    private static final int SIDE_RAIL_WIDTH = 134;
    private static final int SIDE_RAIL_GAP = 22;
    private static final int ROWS = 7;
    private static final int LIST_Y = 144;
    private static final int ROW_HEIGHT = 58;
    private static final int THUMB_SIZE = 44;
    private static final int THUMB_GAP = 8;
    private static final int BOTTOM_RESERVED = 92;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private CompanionRuntime runtime;
    private final List<CompanionLibraryItem> items = new ArrayList<>();
    private LibraryView view = LibraryView.MY_ARTS;
    private boolean signedIn;
    private String status = "";
    private String sessionStatus = "Сессия: вход не выполнен";
    private String searchQuery = "";
    private int page;
    private ClickableWidget pageButton;
    private TextFieldWidget searchInput;
    private MapKlussButton accountButton;
    private final CompanionConfirmation logoutConfirmation = new CompanionConfirmation();

    public CompanionLibraryScreen(Screen parent) {
        super(Text.literal("Библиотека MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        refreshSignedInState();
        rebuildControls();
        rebuildArtButtons();
        loadLibrary();
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
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int gap = 4;
        if (sideRailLayout(panelWidth, left)) {
            addSideRailControls(panelWidth, left);
        } else {
            int fourButtonWidth = Math.max(48, (panelWidth - gap * 3) / 4);
            int row1 = height - 92;
            int row2 = height - 58;
            addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> loadLibrary())
                .dimensions(left, row1, fourButtonWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Синхронизация"), button -> updateAll())
                .dimensions(left + fourButtonWidth + gap, row1, fourButtonWidth, 20).build());
            MapKlussButton.Builder accountBuilder = MapKlussButton.builder(Text.literal(accountButtonLabel()), button -> accountAction())
                .dimensions(left + (fourButtonWidth + gap) * 2, row1, fourButtonWidth, 20);
            if (signedIn) accountBuilder.danger();
            accountButton = addDrawableChild(accountBuilder.tooltip(CompanionI18n.text(signedIn ? "Выйти из аккаунта MapKluss" : "Войти в MapKluss")).navigationOrder(signedIn ? 1000 : 0).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Открыть сайт"), button -> openSite("/cloud"))
                .dimensions(left + (fourButtonWidth + gap) * 3, row1, fourButtonWidth, 20).build());
            int sixButtonWidth = Math.max(42, (panelWidth - gap * 4) / 5);
            addDrawableChild(MapKlussButton.builder(Text.literal("Коллекции"), button -> client().setScreen(new CompanionCollectionsScreen(this)))
                .dimensions(left, row2, sixButtonWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Lens"), button -> client().setScreen(new LensScreen(this)))
                .gold().dimensions(left + sixButtonWidth + gap, row2, sixButtonWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Скан карты"), button -> client().setScreen(new ScanScreen(this)))
                .dimensions(left + (sixButtonWidth + gap) * 2, row2, sixButtonWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> client().setScreen(new TrackerOpenScreen(this)))
                .dimensions(left + (sixButtonWidth + gap) * 3, row2, sixButtonWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Импорт Two-layer"), button -> client().setScreen(new SuppressionStartScreen(this, null)))
                .gold().dimensions(left + (sixButtonWidth + gap) * 4, row2, sixButtonWidth, 20).build());
        }

        int tabButtonWidth = Math.max(48, (panelWidth - gap * 3) / 4);
        addDrawableChild(MapKlussButton.builder(Text.literal("Мои арты"), button -> switchView(LibraryView.MY_ARTS))
            .selected(view == LibraryView.MY_ARTS)
            .dimensions(left, 64, tabButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Избранное"), button -> switchView(LibraryView.FAVORITES))
            .selected(view == LibraryView.FAVORITES)
            .dimensions(left + tabButtonWidth + gap, 64, tabButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Недавние"), button -> switchView(LibraryView.RECENT))
            .selected(view == LibraryView.RECENT)
            .dimensions(left + (tabButtonWidth + gap) * 2, 64, tabButtonWidth, 20).build());
        pageButton = addDrawableChild(MapKlussButton.builder(pageButtonText(), button -> nextPage())
            .dimensions(left + (tabButtonWidth + gap) * 3, 64, tabButtonWidth, 20).build());

        int searchButtonWidth = 58;
        int searchWidth = Math.max(80, panelWidth - searchButtonWidth * 2 - gap * 2);
        searchInput = new TextFieldWidget(textRenderer, left, 98, searchWidth, 20, CompanionI18n.text("Поиск артов"));
        searchInput.setMaxLength(80);
        searchInput.setText(searchQuery);
        addDrawableChild(searchInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Поиск"), button -> applySearch())
            .dimensions(left + searchWidth + gap, 98, searchButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Очистить"), button -> clearSearch())
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, 98, searchButtonWidth, 20).build());
        addDrawableChild(MapKlussUi.languageButton(this));
        addDrawableChild(MapKlussUi.backButton(this, parent, left));
        updatePageButton();
        setFocused(searchInput);
        searchInput.setFocused(true);
    }

    private void addSideRailControls(int panelWidth, int left) {
        int x = sideRailLeft(panelWidth, left);
        int buttonWidth = SIDE_RAIL_WIDTH;
        MapKlussButton.Builder accountBuilder = MapKlussButton.builder(Text.literal(accountButtonLabel()), button -> accountAction())
            .dimensions(x, 78, buttonWidth, 20);
        if (signedIn) accountBuilder.danger();
        accountButton = addDrawableChild(accountBuilder.tooltip(CompanionI18n.text(signedIn ? "Выйти из аккаунта MapKluss" : "Войти в MapKluss")).navigationOrder(signedIn ? 1000 : 0).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт облака"), button -> openSite("/cloud"))
            .dimensions(x, 104, buttonWidth, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> loadLibrary())
            .dimensions(x, 162, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Синхронизация"), button -> updateAll())
            .dimensions(x, 188, buttonWidth, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Коллекции"), button -> client().setScreen(new CompanionCollectionsScreen(this)))
            .dimensions(x, 246, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Скан карты"), button -> client().setScreen(new ScanScreen(this)))
            .dimensions(x, 272, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Lens"), button -> client().setScreen(new LensScreen(this)))
            .gold().dimensions(x, 298, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> client().setScreen(new TrackerOpenScreen(this)))
            .dimensions(x, 324, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Импорт Two-layer"), button -> client().setScreen(new SuppressionStartScreen(this, null)))
            .gold().dimensions(x, 350, buttonWidth, 20).build());

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
                        clearChildren();
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
                    clearChildren();
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
                clearChildren();
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
                        clearChildren();
                        rebuildControls();
                    });
                    return;
                }

                SyncInstalledResult sync = runtime.syncService().refreshInstalledLitematics();
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
                    status = "Обновлено: " + loadedItems.size()
                        + " / схемы " + sync.refreshed() + "/" + sync.checked() + ".";
                    clearChildren();
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

    private void logout() {
        status = "Выход из аккаунта...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime logoutRuntime = runtime == null ? CompanionRuntime.create(client()) : runtime;
                String warning = logoutRuntime.revokeAndClearSession();
                runOnClient(() -> {
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
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int x = screenLeft(panelWidth);
        int y = LIST_Y;
        int gap = 4;
        int contentX = x + THUMB_SIZE + THUMB_GAP;
        int contentWidth = Math.max(180, panelWidth - THUMB_SIZE - THUMB_GAP);
        int actionWidth = Math.max(36, (contentWidth - gap * 4) / 5);
        int favWidth = actionWidth;
        int quickWidth = actionWidth;
        int trackerWidth = actionWidth;
        int webWidth = actionWidth;
        int editWidth = Math.max(36, contentWidth - actionWidth * 4 - gap * 4);
        InstalledArtifactIndex installedIndex = loadInstalledIndex();
        List<CompanionLibraryItem> visibleItems = filteredItems();
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleItems.size());
        for (int i = start; i < end; i++) {
            CompanionLibraryItem item = visibleItems.get(i);
            int rowY = y + (i - start) * ROW_HEIGHT;
            addDrawableChild(MapKlussButton.builder(MapKlussUi.clippedText(textRenderer, itemLabel(item, installedIndex), contentWidth - 8), button ->
                client().setScreen(new CompanionArtScreen(this, item.artId(), item.title())))
                .dimensions(contentX, rowY, contentWidth, 20).build());
            int actionY = rowY + 24;
            addDrawableChild(MapKlussButton.builder(Text.literal(item.isFavorite() ? "Убрать" : "В избранное"), button -> quickToggleFavorite(item))
                .selected(item.isFavorite())
                .dimensions(contentX, actionY, favWidth, 18).build());
            boolean installed = installedIndex != null && !installedIndex.findByArt(item.artId()).isEmpty();
            addDrawableChild(MapKlussButton.builder(Text.literal(installed ? "Обновить" : "+ Схема"), button -> quickInstall(item)).gold()
                .selected(installed)
                .dimensions(contentX + favWidth + gap, actionY, quickWidth, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> quickOpenTracker(item))
                .dimensions(contentX + favWidth + quickWidth + gap * 2, actionY, trackerWidth, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Сайт"), button -> openSite("/art/" + item.artId()))
                .dimensions(contentX + favWidth + quickWidth + trackerWidth + gap * 3, actionY, webWidth, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openSite("/?art=" + item.artId()))
                .dimensions(contentX + favWidth + quickWidth + trackerWidth + webWidth + gap * 4, actionY, editWidth, 18).build());
        }
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
                    clearChildren();
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
                LitematicaStatus litematicaStatus = actionRuntime.litematicaStatus();
                runOnClient(() -> {
                    status = litematicaStatus.ready()
                        ? "Схема установлена: " + installed.filename()
                        : "Схема установлена: " + installed.filename() + " / " + litematicaStatus.warning();
                    clearChildren();
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
                BuildSessionState session = actionRuntime.syncService().trackerForArt(item.artId());
                runOnClient(() -> client().setScreen(new TrackerSessionScreen(this, session.id())));
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
        clearChildren();
        rebuildControls();
        rebuildArtButtons();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        boolean sideRail = sideRailLayout(panelWidth, left);
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 10, MapKlussUi.panelBottom(height));
        if (sideRail) {
            int railLeft = sideRailLeft(panelWidth, left);
            MapKlussUi.drawPanelAt(context, railLeft - 8, railLeft + SIDE_RAIL_WIDTH + 8, 46, MapKlussUi.panelBottom(height));
            drawSideRailSections(context, railLeft);
        }
        MapKlussUi.drawSectionAt(context, textRenderer, null, left, panelWidth, 58, 64);
        MapKlussUi.drawSectionAt(context, textRenderer, "Арты", left, panelWidth, LIST_Y - 24, Math.max(42, height - LIST_Y - bottomReserved() + 28));
        if (!sideRail) drawActionGroups(context);
        drawLibraryPreviews(context, left, panelWidth);
        MapKlussUi.drawHeader(context, textRenderer, title.getString(), "", width, 18);
        MapKlussUi.drawStatusIn(context, textRenderer, status, left + panelWidth / 2, 38, panelWidth - 16);
        MapKlussUi.drawFieldLabel(context, textRenderer, "Поиск артов", left, 98, panelWidth);
        boolean empty = filteredItems().isEmpty();
        if (empty) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                view == LibraryView.RECENT ? "Недавних артов пока нет" : "Список пуст",
                signedIn ? "Сохрани арт на сайте или обнови библиотеку" : "Войди через код, чтобы открыть облако",
                left,
                LIST_Y + 18,
                panelWidth,
                Math.max(40, height - LIST_Y - bottomReserved() - 22)
            );
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawLibraryPreviews(DrawContext context, int left, int panelWidth) {
        List<CompanionLibraryItem> visibleItems = filteredItems();
        int rows = visibleRows();
        int start = page * rows;
        int end = Math.min(start + rows, visibleItems.size());
        for (int i = start; i < end; i++) {
            CompanionLibraryItem item = visibleItems.get(i);
            int rowY = LIST_Y + (i - start) * ROW_HEIGHT;
            drawLibraryPreview(context, item, left, rowY);
        }
    }

    private void drawLibraryPreview(DrawContext context, CompanionLibraryItem item, int x, int rowY) {
        int y = rowY;
        context.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, 0xFF050509);
        context.fill(x - 1, y - 1, x + THUMB_SIZE + 1, y, 0xAA57FF6E);
        context.fill(x - 1, y + THUMB_SIZE, x + THUMB_SIZE + 1, y + THUMB_SIZE + 1, 0xAA57FF6E);
        context.fill(x - 1, y, x, y + THUMB_SIZE, 0xAA57FF6E);
        context.fill(x + THUMB_SIZE, y, x + THUMB_SIZE + 1, y + THUMB_SIZE, 0xAA57FF6E);

        CompanionPreviewTextures.PreviewTexture preview = CompanionPreviewTextures.request(item);
        if (preview.ready()) {
            int imageWidth = preview.imageWidth();
            int imageHeight = preview.imageHeight();
            double scale = Math.min((double) THUMB_SIZE / imageWidth, (double) THUMB_SIZE / imageHeight);
            int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            int drawX = x + (THUMB_SIZE - drawWidth) / 2;
            int drawY = y + (THUMB_SIZE - drawHeight) / 2;
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
        MapKlussUi.drawCenteredIn(context, textRenderer, grid, x + THUMB_SIZE / 2, y + 12, THUMB_SIZE - 6, color);
        MapKlussUi.drawCenteredIn(context, textRenderer, "PNG", x + THUMB_SIZE / 2, y + 24, THUMB_SIZE - 6, MapKlussUi.DIM);
    }

    private void drawSideRailSections(DrawContext context, int railLeft) {
        MapKlussUi.drawSectionAt(context, textRenderer, "Аккаунт", railLeft, SIDE_RAIL_WIDTH, 58, 76);
        MapKlussUi.drawSectionAt(context, textRenderer, "Облако", railLeft, SIDE_RAIL_WIDTH, 142, 76);
        MapKlussUi.drawSectionAt(context, textRenderer, "Инструменты", railLeft, SIDE_RAIL_WIDTH, 226, 122);
    }

    private void drawActionGroups(DrawContext context) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Облако", left, panelWidth, height - 92, 20);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Инструменты", left, panelWidth, height - 58, 20);
    }

    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getText().trim().toLowerCase();
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

    private InstalledArtifactIndex loadInstalledIndex() {
        try {
            return InstalledArtifactIndex.load(LitematicaPaths.companionIndexPath(client().runDirectory.toPath()));
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
        return MapKlussUi.visibleRows(height, LIST_Y, bottomReserved(), ROW_HEIGHT, ROWS);
    }

    private int bottomReserved() {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return sideRailLayout(panelWidth, left) ? 34 : 118;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return height >= 380 && MapKlussUi.rightRailFits(width, panelWidth, SIDE_RAIL_WIDTH, SIDE_RAIL_GAP);
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
