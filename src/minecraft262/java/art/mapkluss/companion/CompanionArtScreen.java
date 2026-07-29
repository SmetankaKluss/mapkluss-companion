package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompanionArtScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int SIDE_PANEL_WIDTH = 420;
    private static final int SIDE_LAYOUT_MIN_WIDTH = 620;
    private static final int SIDE_MARGIN = 16;
    private static final int PREVIEW_GAP = 14;
    private static final int SECTION_WIDTH = 500;
    private static final int ACTION_ROWS = 5;
    private static final int ACTION_ROW_HEIGHT = 34;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int ACTION_BOTTOM_MARGIN = 40;
    private static final int MIN_ACTION_TOP = 176;
    private static final int DISTRIBUTED_ACTION_MIN_HEIGHT = 620;
    private static final int DISTRIBUTED_ACTION_COLUMN_GAP = 8;
    private static final int DISTRIBUTED_ACTION_ROW_GAP = 6;
    private static final int DETAIL_TOP = 60;
    private static final int DETAIL_HEIGHT = 76;
    private static final int TITLE_ROW_Y = 150;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final String artId;
    private final String fallbackTitle;
    private AbstractWidget installButton;
    private AbstractWidget installTilesButton;
    private AbstractWidget removeButton;
    private AbstractWidget mapDatButton;
    private AbstractWidget favoriteButton;
    private AbstractWidget collectionsButton;
    private AbstractWidget pngButton;
    private AbstractWidget materialsButton;
    private AbstractWidget commandsButton;
    private AbstractWidget datapackButton;
    private AbstractWidget downloadMapDatButton;
    private AbstractWidget autoFrameButton;
    private AbstractWidget projectButton;
    private AbstractWidget suppressionButton;
    private AbstractWidget privacyButton;
    private AbstractWidget saveMetaButton;
    private AbstractWidget deleteButton;
    private EditBox titleInput;
    private CompanionManifest manifest;
    private String status = "";
    private String privacyDraft;
    private boolean deleteArmed;
    private int actionPage;

    public CompanionArtScreen(Screen parent, String artId, String fallbackTitle) {
        super(Component.literal("Арт MapKluss"));
        this.parent = parent;
        this.artId = artId;
        this.fallbackTitle = fallbackTitle;
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelWidth = controlsWidth();
        int left = controlsLeft(panelWidth);
        int gap = 4;
        boolean stackedMetadata = sidePreviewLayout();
        int privacyWidth = stackedMetadata ? (panelWidth - gap * 2) / 3 : Math.max(82, Math.min(104, panelWidth / 5));
        int saveWidth = stackedMetadata ? privacyWidth : Math.max(72, Math.min(90, panelWidth / 5));
        int deleteWidth = stackedMetadata ? panelWidth - privacyWidth - saveWidth - gap * 2 : Math.max(68, Math.min(86, panelWidth / 5));
        int titleWidth = stackedMetadata ? panelWidth : Math.max(112, panelWidth - privacyWidth - saveWidth - deleteWidth - gap * 3);
        int titleY = titleRowY();
        titleInput = new EditBox(font, left, titleY, titleWidth, 20, CompanionI18n.text("Название арта"));
        titleInput.setMaxLength(120);
        setTitleInputText(manifest == null ? fallbackTitle : manifest.title());
        addRenderableWidget(titleInput);
        if (privacyDraft == null) {
            privacyDraft = manifest == null || manifest.privacy() == null || manifest.privacy().isBlank()
                ? "unlisted"
                : manifest.privacy();
        }
        int metadataY = stackedMetadata ? titleY + 24 : titleY;
        int privacyX = stackedMetadata ? left : left + titleWidth + gap;
        int saveX = stackedMetadata ? left + privacyWidth + gap : left + titleWidth + privacyWidth + gap * 2;
        int deleteX = stackedMetadata ? left + privacyWidth + saveWidth + gap * 2 : left + titleWidth + privacyWidth + saveWidth + gap * 3;
        privacyButton = addRenderableWidget(MapKlussButton.builder(privacyButtonText(), button -> cyclePrivacy())
            .dimensions(privacyX, metadataY, privacyWidth, 20).build());
        saveMetaButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Сохранить"), button -> saveMetadata())
            .dimensions(saveX, metadataY, saveWidth, 20).build());
        deleteButton = addRenderableWidget(MapKlussButton.builder(deleteButtonText(), button -> deleteArt())
            .danger()
            .navigationOrder(1000)
            .dimensions(deleteX, metadataY, deleteWidth, 20).build());

        if (distributedActionLayout()) {
            addDistributedActionControls(left, panelWidth);
        } else {
            addCompactActionControls(left, panelWidth, gap);
        }
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left));
        updateActionButtons();
        setFocused(null);
        titleInput.setFocused(false);
        refreshManifest();
    }

    private void addCompactActionControls(int left, int panelWidth, int gap) {
        if (!compactActionsFit()) return;
        int tabsY = actionTabsY();
        String[] groups = {"Схема", "Ссылки", "Облако", "Экспорт", "Ещё"};
        String[] tooltips = {"Схема", "Переходы", "Библиотека", "Экспорт", "Архивы"};
        int tabWidth = Math.max(38, (panelWidth - gap * 4) / 5);
        for (int i = 0; i < groups.length; i++) {
            final int page = i;
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text(groups[i]), button -> {
                    actionPage = page;
                    init();
                }).selected(actionPage == i).tooltip(CompanionI18n.text(tooltips[i]))
                .dimensions(left + (tabWidth + gap) * i, tabsY, i == groups.length - 1 ? panelWidth - (tabWidth + gap) * i : tabWidth, 20).build());
        }
        int rowY = actionTop();
        switch (Math.max(0, Math.min(actionPage, 4))) {
            case 0 -> addCompactSchemaActions(left, panelWidth, gap, rowY);
            case 1 -> addCompactLinkActions(left, panelWidth, gap, rowY);
            case 2 -> addCompactLibraryActions(left, panelWidth, gap, rowY);
            case 3 -> addCompactExportActions(left, panelWidth, gap, rowY);
            default -> addCompactArchiveActions(left, panelWidth, gap, rowY);
        }
    }

    private void addCompactSchemaActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        installButton = addRenderableWidget(MapKlussButton.builder(Component.literal("+ Целиком"), button -> installLitematic()).gold().tooltip(CompanionI18n.text("Установить полную схему"))
            .dimensions(left, rowY, w, 20).build());
        installTilesButton = addRenderableWidget(MapKlussButton.builder(Component.literal("+ По картам"), button -> installLitematicTiles()).gold().tooltip(CompanionI18n.text("Установить схемы по картам"))
            .dimensions(left + w + gap, rowY, w, 20).build());
        removeButton = addRenderableWidget(MapKlussButton.builder(Component.literal("- Схема"), button -> removeLitematic()).danger().tooltip(CompanionI18n.text("Удалить установленную схему"))
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> refreshManifest())
            .dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactLinkActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 2) / 3);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт арта"), button -> openSite("/art/" + artId)).technical().dimensions(left, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Редактор"), button -> openSite("/?art=" + artId)).technical().dimensions(left + w + gap, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Трекер"), button -> openArtTracker()).technical().dimensions(left + (w + gap) * 2, rowY, panelWidth - (w + gap) * 2, 20).build());
    }

    private void addCompactLibraryActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        favoriteButton = addRenderableWidget(MapKlussButton.builder(favoriteButtonText(), button -> toggleFavorite()).selected(manifest != null && manifest.isFavorite()).dimensions(left, rowY, w, 20).build());
        collectionsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Коллекции"), button -> openCollections()).dimensions(left + w + gap, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Файлы"), button -> openDownloadsFolder()).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Схемы"), button -> openSchematicFolder()).dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactExportActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        pngButton = addRenderableWidget(MapKlussButton.builder(Component.literal("PNG"), button -> downloadFirst("preview_png")).exportAction().dimensions(left, rowY, w, 20).build());
        materialsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Материалы"), button -> downloadFirst("materials_txt", "materials_csv")).exportAction().dimensions(left + w + gap, rowY, w, 20).build());
        commandsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Команды"), button -> downloadFirst("frame_commands")).exportAction().dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        datapackButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Датапак"), button -> downloadFirst("frame_datapack")).exportAction().dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactArchiveActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(42, (panelWidth - gap * 4) / 5);
        autoFrameButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Для рамок"), button -> prepareAutoFrame()).gold().dimensions(left, rowY, w, 20).build());
        suppressionButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Two-layer"), button -> openSuppression()).special().dimensions(left + w + gap, rowY, w, 20).build());
        mapDatButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Импорт MapDat"), button -> importMapDat()).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        downloadMapDatButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Скачать MapDat"), button -> downloadFirst("mapdat_zip")).exportAction().dimensions(left + (w + gap) * 3, rowY, w, 20).build());
        projectButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Скачать проект"), button -> downloadFirst("project")).exportAction().dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
    }

    private void addDistributedActionControls(int left, int panelWidth) {
        int columnWidth = (panelWidth - DISTRIBUTED_ACTION_COLUMN_GAP) / 2;
        int right = left + columnWidth + DISTRIBUTED_ACTION_COLUMN_GAP;
        int startY = actionTop();
        int schemaY = startY;
        int navigationY = startY;
        int libraryY = schemaY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        int exportY = navigationY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        int archiveY = libraryY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;

        installButton = addRenderableWidget(MapKlussButton.builder(Component.literal("+ Целиком"), button -> installLitematic()).gold()
            .dimensions(left, schemaY, columnWidth, 20).build());
        installTilesButton = addRenderableWidget(MapKlussButton.builder(Component.literal("+ По картам"), button -> installLitematicTiles()).gold()
            .dimensions(left, schemaY + 24, columnWidth, 20).build());
        removeButton = addRenderableWidget(MapKlussButton.builder(Component.literal("- Схема"), button -> removeLitematic()).danger()
            .dimensions(left, schemaY + 48, columnWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> refreshManifest())
            .dimensions(left, schemaY + 72, columnWidth, 20).build());

        addRenderableWidget(MapKlussButton.builder(Component.literal("Сайт арта"), button -> openSite("/art/" + artId))
            .technical().dimensions(right, navigationY, columnWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Редактор"), button -> openSite("/?art=" + artId))
            .technical().dimensions(right, navigationY + 24, columnWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Трекер"), button -> openArtTracker())
            .technical().dimensions(right, navigationY + 48, columnWidth, 20).build());

        favoriteButton = addRenderableWidget(MapKlussButton.builder(favoriteButtonText(), button -> toggleFavorite())
            .selected(manifest != null && manifest.isFavorite())
            .dimensions(left, libraryY, columnWidth, 20).build());
        collectionsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Коллекции"), button -> openCollections())
            .dimensions(left, libraryY + 24, columnWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Папка файлов"), button -> openDownloadsFolder())
            .dimensions(left, libraryY + 48, columnWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Папка схем"), button -> openSchematicFolder())
            .dimensions(left, libraryY + 72, columnWidth, 20).build());

        pngButton = addRenderableWidget(MapKlussButton.builder(Component.literal("PNG превью"), button -> downloadFirst("preview_png"))
            .exportAction().dimensions(right, exportY, columnWidth, 20).build());
        materialsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Материалы"), button -> downloadFirst("materials_txt", "materials_csv"))
            .exportAction().dimensions(right, exportY + 24, columnWidth, 20).build());
        commandsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Команды"), button -> downloadFirst("frame_commands"))
            .exportAction().dimensions(right, exportY + 48, columnWidth, 20).build());
        datapackButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Датапак"), button -> downloadFirst("frame_datapack"))
            .exportAction().dimensions(right, exportY + 72, columnWidth, 20).build());

        autoFrameButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Для рамок"), button -> prepareAutoFrame())
            .gold().dimensions(left, archiveY, columnWidth, 20).build());
        mapDatButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Импорт MapDat"), button -> importMapDat())
            .dimensions(left, archiveY + 24, columnWidth, 20).build());
        downloadMapDatButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Скачать MapDat"), button -> downloadFirst("mapdat_zip"))
            .exportAction().dimensions(left, archiveY + 48, columnWidth, 20).build());
        projectButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Скачать проект"), button -> downloadFirst("project"))
            .exportAction().dimensions(left, archiveY + 72, columnWidth, 20).build());
        suppressionButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Two-layer"), button -> openSuppression())
            .special().dimensions(right, archiveY, columnWidth, 20).build());
    }

    private boolean sidePreviewLayout() {
        return usesSidePreview(width);
    }

    static boolean usesSidePreview(int screenWidth) {
        return screenWidth >= SIDE_LAYOUT_MIN_WIDTH;
    }

    private boolean distributedActionLayout() {
        return sidePreviewLayout() && height >= DISTRIBUTED_ACTION_MIN_HEIGHT;
    }

    private boolean detailVisible() {
        return height >= 300;
    }

    private int titleRowY() {
        return detailVisible() ? TITLE_ROW_Y : 76;
    }

    private int actionTabsY() {
        return titleRowY() + (sidePreviewLayout() ? 54 : 30);
    }

    private boolean compactActionsFit() {
        return actionTop() + ACTION_BUTTON_HEIGHT <= height - 36;
    }

    private int controlsWidth() {
        return sidePreviewLayout()
            ? Math.max(280, Math.min(SIDE_PANEL_WIDTH, width * 42 / 100))
            : MapKlussUi.panelWidth(width, PANEL_WIDTH);
    }

    private int controlsLeft(int panelWidth) {
        if (sidePreviewLayout()) return SIDE_MARGIN;
        return MapKlussUi.centeredLeft(width, panelWidth);
    }

    private int previewLeft(int panelWidth) {
        return controlsLeft(panelWidth) + panelWidth + PREVIEW_GAP;
    }

    private int actionTop() {
        if (!distributedActionLayout()) {
            return actionTabsY() + 24;
        }
        if (sidePreviewLayout()) {
            int rowSpan = Math.max(0, ACTION_ROWS - 1) * ACTION_ROW_HEIGHT + ACTION_BUTTON_HEIGHT;
            int maxTop = height - ACTION_BOTTOM_MARGIN - rowSpan;
            int preferredTop = titleRowY() + 40;
            if (maxTop < preferredTop) return Math.max(0, maxTop);
            return Math.min(preferredTop, maxTop);
        }
        return CompanionLayout.actionTop(
            height,
            MIN_ACTION_TOP,
            ACTION_ROWS,
            ACTION_ROW_HEIGHT,
            ACTION_BUTTON_HEIGHT,
            ACTION_BOTTOM_MARGIN
        );
    }

    private int actionGroupHeight(int buttonCount) {
        return Math.max(0, buttonCount - 1) * 24 + ACTION_BUTTON_HEIGHT + 22;
    }

    private void refreshManifest() {
        status = "Обновление...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                CompanionManifest previous = resolvePreviousManifest(runtime);
                ArtRefreshResult refresh = runtime.syncService().refreshArt(artId);
                CompanionManifest loaded = refresh.manifest();
                syncManifestState(runtime, previous, loaded);
                LitematicaStatus litematicaStatus = runtime.litematicaStatus();
                runOnClient(() -> {
                    manifest = loaded;
                    privacyDraft = loaded.privacy();
                    updateFavoriteButton();
                    updateActionButtons();
                    status = refreshStatus(refresh, litematicaStatus);
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession("Показан кеш. " + CompanionAuthSupport.expiredMessage(), true);
                } else {
                    loadCachedManifest(e.getMessage());
                }
            }
        });
    }

    private void loadCachedManifest(String reason) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            java.util.Optional<ManifestCache.CachedManifest> cached = runtime.manifestCache().read(runtime.sessionStore().userId(), artId);
            runOnClient(() -> {
                if (cached.isPresent()) {
                    manifest = cached.get().manifest();
                    privacyDraft = manifest.privacy();
                    updateFavoriteButton();
                    updateActionButtons();
                    status = "Показан локальный кеш.";
                } else {
                    status = CompanionUiErrors.message("sync", reason);
                }
            });
        } catch (Exception cacheError) {
            runOnClient(() -> status = CompanionUiErrors.message("sync", reason));
        }
    }

    private void installLitematic() {
        status = "Установка схемы...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                InstalledArtifact installed = runtime.syncService().installLitematic(artId);
                LitematicaStatus litematicaStatus = runtime.litematicaStatus();
                runOnClient(() -> {
                    updateActionButtons();
                    status = litematicaStatus.ready()
                        ? "Схема установлена: " + installed.filename()
                        : "Схема установлена: " + installed.filename() + " / " + litematicaStatus.warning();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("download", e));
                }
            }
        });
    }

    private void installLitematicTiles() {
        status = "Установка схем по картам...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                List<InstalledArtifact> installed = runtime.syncService().installLitematicTiles(artId);
                LitematicaStatus litematicaStatus = runtime.litematicaStatus();
                runOnClient(() -> {
                    updateActionButtons();
                    String base = "Схемы по картам: " + installed.size();
                    status = litematicaStatus.ready() ? base : base + " / " + litematicaStatus.warning();
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("download", e));
                }
            }
        });
    }

    private void openSchematicFolder() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Path folder = runtime.schematicDir();
            java.nio.file.Files.createDirectories(folder);
            Util.getPlatform().openUri(folder.toUri());
            status = "Папка схем открыта.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openDownloadsFolder() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Path folder = runtime.syncService().downloadsDir();
            java.nio.file.Files.createDirectories(folder);
            Util.getPlatform().openUri(folder.toUri());
            status = "Папка загрузок открыта.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openSite(String path) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri(path));
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void removeLitematic() {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        status = "Удаление схемы...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                boolean removed = runtime.syncService().removeLitematicsForArt(artId);
                runOnClient(() -> {
                    updateActionButtons();
                    status = removed ? "Схема удалена." : "Удалять нечего.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("delete", e));
                }
            }
        });
    }

    private void importMapDat() {
        if (!client().isLocalServer() || !client().hasSingleplayerServer()) {
            status = "MAP.DAT импорт работает только в одиночном мире.";
            return;
        }
        MinecraftServer server = client().getSingleplayerServer();
        if (server == null) {
            status = "Локальный сервер недоступен.";
            return;
        }
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        status = "Импорт MAP.DAT...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                MapDatImportResult result = runtime.syncService().importMapDat(artId, worldDir);
                runOnClient(() -> status = "Карты импортированы: " + result.startMapId() + "-" + result.endMapId() + ", backup: " + result.backupPath().getFileName());
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("download", e));
                }
            }
        });
    }

    private void prepareAutoFrame() {
        if (manifest == null || !hasArtifact("mapdat_zip")) {
            status = "Для AutoFrame нужен архив MAP.DAT.";
            return;
        }
        status = "Подготовка AutoFrame...";
        AutoFrameManager.instance().prepare(manifest).whenComplete((template, error) -> runOnClient(() -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                MapKlussCompanionClient.LOGGER.warn("Failed to prepare AutoFrame template.", cause);
                status = "Не удалось подготовить AutoFrame.";
                return;
            }
            status = "AutoFrame готов: " + template.title();
        }));
    }

    private void openSuppression() {
        client().gui.setScreen(new SuppressionStartScreen(this, manifest));
    }

    private void toggleFavorite() {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        boolean targetValue = !manifest.isFavorite();
        status = targetValue ? "Добавление в избранное..." : "Удаление из избранного...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                runtime.apiClient().setFavorite(artId, targetValue);
                CompanionManifest updated = withFavorite(manifest, targetValue);
                syncManifestState(runtime, manifest, updated);
                runOnClient(() -> {
                    manifest = updated;
                    updateFavoriteButton();
                    status = targetValue ? "Добавлено в избранное." : "Убрано из избранного.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("sync", e));
                }
            }
        });
    }

    private void openArtTracker() {
        status = "Открываю трекер...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                BuildSessionState session = runtime.syncService().trackerForArt(artId);
                runOnClient(() -> client().gui.setScreen(new TrackerSessionScreen(this, session.id())));
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("tracker", e));
                }
            }
        });
    }

    private void openCollections() {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        client().gui.setScreen(new CompanionArtCollectionsScreen(this, artId, fallbackTitle, manifest));
    }

    private void cyclePrivacy() {
        if (!canEditMetadata()) {
            status = "Редактировать может только владелец.";
            return;
        }
        String next = switch (selectedPrivacy()) {
            case "private" -> "unlisted";
            default -> "private";
        };
        privacyDraft = next;
        if (privacyButton != null) {
            privacyButton.setMessage(privacyButtonText());
        }
    }

    private void saveMetadata() {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        if (!canEditMetadata()) {
            status = "Редактировать может только владелец.";
            return;
        }
        String nextTitle = titleInput == null ? "" : titleInput.getValue().trim();
        if (nextTitle.isEmpty()) {
            status = "Введите название арта.";
            return;
        }
        String nextPrivacy = selectedPrivacy();
        if (nextTitle.equals(manifest.title()) && nextPrivacy.equals(manifest.privacy())) {
            status = "Название и приватность не изменились.";
            return;
        }

        status = "Сохранение настроек арта...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                CompanionManifest previous = manifest;
                runtime.apiClient().updateArt(artId, nextTitle, nextPrivacy);
                ArtRefreshResult refresh = runtime.syncService().refreshArt(artId);
                CompanionManifest updated = refresh.manifest();
                syncManifestState(runtime, previous, updated);
                LitematicaStatus litematicaStatus = runtime.litematicaStatus();
                runOnClient(() -> {
                    manifest = updated;
                    privacyDraft = updated.privacy();
                    updateFavoriteButton();
                    updateActionButtons();
                    status = metadataSaveStatus(refresh, litematicaStatus);
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), true);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("save", e));
                }
            }
        });
    }

    private void deleteArt() {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        if (!canEditMetadata()) {
            status = "Удалить может только владелец.";
            return;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            updateActionButtons();
            status = "Нажмите Удалить еще раз для удаления.";
            return;
        }

        status = "Удаление арта...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                CompanionManifest deleting = manifest;
                runtime.apiClient().deleteArt(artId);
                cleanupDeletedArt(runtime, deleting);
                runOnClient(() -> {
                    if (parent instanceof CompanionLibraryScreen libraryScreen) {
                        libraryScreen.applyDeletedArt(artId, deleting.title());
                    } else if (parent instanceof CompanionCollectionItemsScreen collectionItemsScreen) {
                        collectionItemsScreen.applyDeletedArt(artId, deleting.title());
                    }
                    client().gui.setScreen(parent);
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), true);
                } else {
                    runOnClient(() -> {
                        deleteArmed = false;
                        updateActionButtons();
                        status = CompanionUiErrors.message("delete", e);
                    });
                }
            }
        });
    }

    private void downloadFirst(String... kinds) {
        if (manifest == null) {
            status = "Сначала обновите арт.";
            return;
        }
        Optional<CompanionArtifact> artifact = manifest.artifacts().stream()
            .filter(candidate -> {
                for (String kind : kinds) {
                    if (kind.equals(candidate.kind())) return true;
                }
                return false;
            })
            .findFirst();
        if (artifact.isEmpty()) {
            status = "Файл недоступен.";
            return;
        }

        status = "Скачивание: " + artifact.get().kind() + "...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                Path target = runtime.syncService().downloadArtifact(artifact.get());
                runOnClient(() -> status = "Сохранено: " + target.getFileName());
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    handleExpiredSession(CompanionAuthSupport.expiredMessage(), false);
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("download", e));
                }
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        int panelWidth = controlsWidth();
        int left = controlsLeft(panelWidth);
        int titleY = titleRowY();
        int panelBottom = MapKlussUi.panelBottom(height);
        if (sidePreviewLayout()) {
            MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 46, panelBottom);
            if (detailVisible()) MapKlussUi.drawSectionAt(context, font, "Детали", left, panelWidth, DETAIL_TOP, DETAIL_HEIGHT);
            MapKlussUi.drawSectionAt(context, font, null, left, panelWidth, titleY - 14, 62);
        } else {
            MapKlussUi.drawPanel(context, width, PANEL_WIDTH + 28, 46, panelBottom);
            if (detailVisible()) MapKlussUi.drawSection(context, font, "Детали", width, SECTION_WIDTH, DETAIL_TOP, DETAIL_HEIGHT);
            MapKlussUi.drawSection(context, font, null, width, SECTION_WIDTH, titleY - 14, 36);
        }
        if (distributedActionLayout() || compactActionsFit()) {
            int actionY = distributedActionLayout() ? actionTop() - 18 : actionTabsY() - 5;
            int actionHeight = distributedActionLayout()
                ? Math.max(24, MapKlussUi.contentBottom(height) - actionY)
                : 54;
            if (sidePreviewLayout()) {
                MapKlussUi.drawSectionAt(context, font, null, left, panelWidth, actionY, actionHeight);
            } else {
                MapKlussUi.drawSection(context, font, null, width, SECTION_WIDTH, actionY, actionHeight);
            }
            drawActionGroups(context, left, panelWidth);
        }
        MapKlussUi.drawStatusIn(context, font, status, left + panelWidth / 2, 42, panelWidth - 12);
        MapKlussUi.drawFieldLabel(context, font, "Название арта", left, titleY, panelWidth);
        if (sidePreviewLayout()) {
            int previewX = previewLeft(panelWidth);
            int previewY = 56;
            int previewWidth = Math.max(80, width - previewX - SIDE_MARGIN);
            int dataTop = Math.max(previewY + 48, panelBottom - 31);
            int previewHeight = Math.max(40, dataTop - previewY - 6);
            MapKlussUi.drawPanelAt(context, previewX - 10, previewX + previewWidth + 10, 46, panelBottom);
            MapKlussUi.drawPreviewWell(context, previewX, previewY, previewX + previewWidth, previewY + previewHeight);
            MapKlussUi.drawDataStrip(context, previewX, previewX + previewWidth, dataTop, panelBottom - 9);
            if (manifest != null) {
                if (detailVisible()) {
                    int textX = left + 8;
                    int textWidth = Math.max(90, panelWidth - 16);
                    MapKlussUi.drawLeft(context, font, manifest.grid().wide() + "x" + manifest.grid().tall() + " / " + modeLabel(manifest.mode()) + " / " + privacyLabel(manifest.privacy()), textX, 76, textWidth, MapKlussUi.WHITE);
                    MapKlussUi.drawLeft(context, font, metaSummaryLine(), textX, 92, textWidth, MapKlussUi.CYAN);
                    MapKlussUi.drawLeft(context, font, artifactFilesLine(), textX, 108, textWidth, MapKlussUi.ACCENT);
                    MapKlussUi.drawLeft(context, font, artifactUpdatedLine(), textX, 122, textWidth, MapKlussUi.MUTED);
                }
                drawPreview(context, previewX + 5, previewY + 5, Math.max(1, previewWidth - 10), Math.max(1, previewHeight - 10), false);
                MapKlussUi.drawLeft(
                    context,
                    font,
                    manifest.grid().wide() + "x" + manifest.grid().tall() + "  •  " + modeLabel(manifest.mode()) + "  •  " + privacyLabel(manifest.privacy()),
                    previewX + 14,
                    dataTop + 7,
                    Math.max(30, previewWidth - 24),
                    MapKlussUi.CYAN
                );
            } else {
                drawPreviewPlaceholder(context, previewX + 5, previewY + 5, Math.max(1, previewWidth - 10), Math.max(1, previewHeight - 10), "Загрузка превью", "Облако готовит изображение арта", MapKlussUi.MUTED);
            }
        } else if (manifest != null && detailVisible()) {
                drawPreview(context, left + 8, DETAIL_TOP + 17, 74, 50, true);
                int textX = left + 92;
                int textWidth = Math.max(90, panelWidth - 100);
                MapKlussUi.drawLeft(context, font, manifest.grid().wide() + "x" + manifest.grid().tall() + " / " + modeLabel(manifest.mode()) + " / " + privacyLabel(manifest.privacy()), textX, 70, textWidth, MapKlussUi.WHITE);
                MapKlussUi.drawLeft(context, font, metaSummaryLine(), textX, 84, textWidth, MapKlussUi.CYAN);
                MapKlussUi.drawLeft(context, font, artifactFilesLine(), textX, 98, textWidth, MapKlussUi.ACCENT);
                MapKlussUi.drawLeft(context, font, artifactUpdatedLine(), textX, 112, textWidth, MapKlussUi.MUTED);
        }
        String name = manifest == null ? fallbackTitle : manifest.title();
        MapKlussUi.drawHeader(context, font, sidePreviewLayout() ? "Библиотека / " + name : name, "", width, 18);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawActionGroups(GuiGraphicsExtractor context, int left, int panelWidth) {
        if (distributedActionLayout()) {
            drawDistributedActionGroups(context, left, panelWidth);
        }
    }

    private void drawDistributedActionGroups(GuiGraphicsExtractor context, int left, int panelWidth) {
        int columnWidth = (panelWidth - DISTRIBUTED_ACTION_COLUMN_GAP) / 2;
        int right = left + columnWidth + DISTRIBUTED_ACTION_COLUMN_GAP;
        int startY = actionTop();
        int schemaY = startY;
        int navigationY = startY;
        int libraryY = schemaY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        int exportY = navigationY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        int archiveY = libraryY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        MapKlussUi.drawActionGroupLabel(context, font, "Схема", left, columnWidth, schemaY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, font, "Библиотека", left, columnWidth, libraryY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, font, "Архивы", left, columnWidth, archiveY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, font, "Переходы", right, columnWidth, navigationY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, font, "Экспорт", right, columnWidth, exportY, ACTION_BUTTON_HEIGHT);
    }

    private void drawPreview(GuiGraphicsExtractor context, int x, int y, int boxWidth, int boxHeight, boolean framed) {
        if (framed) {
            MapKlussUi.drawPreviewWell(context, x - 3, y - 3, x + boxWidth + 3, y + boxHeight + 3);
        }
        CompanionPreviewTextures.PreviewTexture preview = CompanionPreviewTextures.request(manifest);
        if (preview.ready()) {
            int imageWidth = preview.imageWidth();
            int imageHeight = preview.imageHeight();
            double scale = Math.min((double) boxWidth / imageWidth, (double) boxHeight / imageHeight);
            int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
            int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
            int drawX = x + (boxWidth - drawWidth) / 2;
            int drawY = y + (boxHeight - drawHeight) / 2;
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
        } else {
            drawPreviewPlaceholder(
                context,
                x,
                y,
                boxWidth,
                boxHeight,
                preview.loading() ? "Загрузка превью" : "Превью недоступно",
                preview.loading() ? "Изображение появится здесь" : "Откройте арт на сайте или обновите файлы",
                preview.failed() ? MapKlussUi.DIM : MapKlussUi.MUTED
            );
        }
    }

    private void drawPreviewPlaceholder(GuiGraphicsExtractor context, int x, int y, int boxWidth, int boxHeight, String title, String detail, int color) {
        int cardWidth = Math.min(260, Math.max(120, boxWidth - 24));
        int cardHeight = 52;
        int cardLeft = x + Math.max(0, (boxWidth - cardWidth) / 2);
        int cardTop = y + Math.max(0, (boxHeight - cardHeight) / 2);
        MapKlussUi.drawPreviewWell(context, cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
        MapKlussUi.drawCenteredIn(context, font, title, cardLeft + cardWidth / 2, cardTop + 11, cardWidth - 14, color);
        MapKlussUi.drawCenteredIn(context, font, detail, cardLeft + cardWidth / 2, cardTop + 28, cardWidth - 14, MapKlussUi.DIM);
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }

    private Component favoriteButtonText() {
        return Component.literal(manifest != null && manifest.isFavorite() ? "Убрать" : "В избранное");
    }

    private Component privacyButtonText() {
        return Component.literal(privacyLabel(selectedPrivacy()));
    }

    private Component deleteButtonText() {
        return Component.literal(deleteArmed ? "Точно?" : "Удалить");
    }

    void applyManifestUpdate(CompanionManifest updatedManifest, String newStatus) {
        manifest = updatedManifest;
        privacyDraft = updatedManifest.privacy();
        if (titleInput != null) {
            setTitleInputText(updatedManifest.title());
        }
        updateFavoriteButton();
        updateActionButtons();
        status = newStatus;
    }

    private void updateFavoriteButton() {
        if (favoriteButton != null) {
            favoriteButton.setMessage(favoriteButtonText());
            if (favoriteButton instanceof MapKlussButton mapKlussButton) {
                mapKlussButton.setSelected(manifest != null && manifest.isFavorite());
            }
        }
    }

    private void updateActionButtons() {
        InstalledArtifact installed = installedLitematic();
        if (installButton != null) {
            installButton.setMessage(Component.literal(installed == null ? "+ Целиком" : "Обновить"));
            if (installButton instanceof MapKlussButton mapKlussButton) {
                mapKlussButton.setSelected(installed != null && !installed.artifactId().contains("#"));
            }
            installButton.active = manifest != null && hasArtifact("litematic");
        }
        if (installTilesButton != null) {
            installTilesButton.setMessage(Component.literal(installed == null ? "+ По картам" : "По картам"));
            if (installTilesButton instanceof MapKlussButton mapKlussButton) {
                mapKlussButton.setSelected(installed != null && installed.artifactId().contains("#"));
            }
            installTilesButton.active = manifest != null && hasArtifact("litematic_tiles_zip");
        }
        if (removeButton != null) {
            removeButton.setMessage(Component.literal(installed == null ? "- Схема" : "Удалить"));
            removeButton.active = installed != null;
        }
        if (mapDatButton != null) {
            mapDatButton.setMessage(Component.literal(hasArtifact("mapdat_zip") ? "MapDat" : "MapDat N/A"));
            mapDatButton.active = manifest != null && hasArtifact("mapdat_zip");
        }
        if (favoriteButton != null) {
            favoriteButton.active = manifest != null;
        }
        if (collectionsButton != null) {
            collectionsButton.active = manifest != null;
        }
        if (pngButton != null) {
            pngButton.active = hasArtifact("preview_png");
        }
        if (materialsButton != null) {
            materialsButton.active = hasArtifact("materials_txt") || hasArtifact("materials_csv");
        }
        if (commandsButton != null) {
            commandsButton.active = hasArtifact("frame_commands");
        }
        if (datapackButton != null) {
            datapackButton.active = hasArtifact("frame_datapack");
        }
        if (downloadMapDatButton != null) {
            downloadMapDatButton.active = hasArtifact("mapdat_zip");
        }
        if (autoFrameButton != null) {
            autoFrameButton.active = manifest != null && hasArtifact("mapdat_zip");
        }
        if (projectButton != null) {
            projectButton.active = hasArtifact("project");
        }
        if (suppressionButton != null) {
            suppressionButton.active = manifest != null;
            suppressionButton.setMessage(Component.literal(manifest != null && manifest.hasSuppressionBundle()
                ? "Two-layer"
                : "Импорт Two-layer"));
        }
        if (privacyButton != null) {
            privacyButton.setMessage(privacyButtonText());
            privacyButton.active = canEditMetadata();
        }
        if (saveMetaButton != null) {
            saveMetaButton.active = canEditMetadata() && manifest != null;
        }
        if (deleteButton != null) {
            deleteButton.setMessage(deleteButtonText());
            deleteButton.active = canEditMetadata() && manifest != null;
        }
        if (titleInput != null) {
            String desiredTitle = manifest == null ? fallbackTitle : manifest.title();
            if (!desiredTitle.equals(titleInput.getValue())) {
                setTitleInputText(desiredTitle);
            }
            titleInput.setEditable(canEditMetadata());
        }
    }

    private void setTitleInputText(String value) {
        if (titleInput == null) return;
        titleInput.setValue(value == null ? "" : value);
        titleInput.moveCursorToStart(false);
        titleInput.setCursorPosition(0);
        titleInput.setHighlightPos(0);
    }

    private boolean hasArtifact(String kind) {
        return manifest != null && manifest.artifacts().stream().anyMatch(artifact -> kind.equals(artifact.kind()));
    }

    private InstalledArtifact installedLitematic() {
        if (manifest == null) return null;
        try {
            InstalledArtifactIndex index = InstalledArtifactIndex.load(LitematicaPaths.companionIndexPath(client().gameDirectory.toPath()));
            Optional<CompanionArtifact> artifact = manifest.litematicArtifact();
            if (artifact.isPresent()) {
                Optional<InstalledArtifact> current = index.findSameArtifact(manifest.artId(), artifact.get().id(), artifact.get().sha256());
                if (current.isPresent()) return current.get();
            }
            List<InstalledArtifact> installedForArt = index.findByArt(manifest.artId());
            return installedForArt.isEmpty() ? null : installedForArt.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String metaSummaryLine() {
        if (manifest == null) return "";
        String favorite = manifest.isFavorite() ? "избранное" : "не избранное";
        String version = manifest.minecraftVersion() == null || manifest.minecraftVersion().isBlank()
            ? "mc ?"
            : "mc " + manifest.minecraftVersion();
        return version + " / " + favorite;
    }

    private String artifactFilesLine() {
        if (manifest == null) return "";
        String litematic = installedLitematic() == null ? "схема не установлена" : "схема установлена";
        return litematic;
    }

    private String artifactUpdatedLine() {
        if (manifest == null) return "";
        return "обновлено " + formatInstant(manifest.updatedAt());
    }

    private String refreshStatus(ArtRefreshResult refresh, LitematicaStatus litematicaStatus) {
        String base = "";
        if (refresh.syncedInstalledLitematic() && refresh.installedArtifact() != null) {
            base = "Схема обновлена: " + refresh.installedArtifact().filename() + ".";
        }
        if (!litematicaStatus.ready()) {
            return base.isBlank() ? litematicaStatus.warning() : base + " / " + litematicaStatus.warning();
        }
        return base;
    }

    private String metadataSaveStatus(ArtRefreshResult refresh, LitematicaStatus litematicaStatus) {
        String base = "Настройки арта сохранены.";
        if (refresh.syncedInstalledLitematic() && refresh.installedArtifact() != null) {
            base = "Настройки сохранены / синхр. " + refresh.installedArtifact().filename() + ".";
        }
        if (!litematicaStatus.ready()) {
            return base + " / " + litematicaStatus.warning();
        }
        return base;
    }

    private boolean canEditMetadata() {
        if (manifest == null) return false;
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            String userId = runtime.sessionStore().userId();
            return userId != null && userId.equals(manifest.ownerId());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String selectedPrivacy() {
        if (privacyDraft != null && !privacyDraft.isBlank()) return privacyDraft;
        if (manifest != null && manifest.privacy() != null && !manifest.privacy().isBlank()) {
            return manifest.privacy();
        }
        return "unlisted";
    }

    private static String privacyLabel(String value) {
        if ("private".equalsIgnoreCase(value)) return "приватный";
        if ("unlisted".equalsIgnoreCase(value)) return "по ссылке";
        if ("public".equalsIgnoreCase(value)) return "публичный";
        return value == null || value.isBlank() ? "доступ ?" : value;
    }

    private static String modeLabel(String value) {
        if ("2d".equalsIgnoreCase(value)) return "2D";
        if ("3d".equalsIgnoreCase(value)) return "3D";
        return value == null || value.isBlank() ? "режим ?" : value;
    }

    private String formatInstant(String value) {
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return value == null ? "неизвестно" : value;
        }
    }

    private static CompanionManifest withFavorite(CompanionManifest manifest, boolean favorite) {
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
            favorite,
            manifest.collectionIds(),
            manifest.artifacts(),
            manifest.updatedAt()
        );
    }

    private CompanionManifest resolvePreviousManifest(CompanionRuntime runtime) throws java.io.IOException {
        if (manifest != null) return manifest;
        return runtime.manifestCache()
            .read(runtime.sessionStore().userId(), artId)
            .map(ManifestCache.CachedManifest::manifest)
            .orElse(null);
    }

    private void syncManifestState(CompanionRuntime runtime, CompanionManifest previous, CompanionManifest updated) throws java.io.IOException {
        runtime.manifestCache().write(runtime.sessionStore().userId(), updated);
        syncCachedViews(runtime, updated, updated.isFavorite());
        syncCachedCollectionItems(runtime, previous, updated);
    }

    private void syncCachedViews(CompanionRuntime runtime, CompanionManifest updated, boolean favorite) throws java.io.IOException {
        String userId = runtime.sessionStore().userId();
        CompanionLibraryItem item = toLibraryItem(updated);

        runtime.libraryCache().replaceViewItem(userId, "recent", item);
        runtime.libraryCache().replaceViewItem(userId, "my", item);

        if (favorite) {
            runtime.libraryCache().upsertViewItem(userId, "favorites", item);
        } else {
            runtime.libraryCache().removeViewItem(userId, "favorites", updated.artId());
        }
    }

    private void syncCachedCollectionItems(CompanionRuntime runtime, CompanionManifest previous, CompanionManifest updated) throws java.io.IOException {
        String userId = runtime.sessionStore().userId();
        CompanionLibraryItem item = toLibraryItem(updated);
        LinkedHashSet<String> touchedCollectionIds = new LinkedHashSet<>();
        if (previous != null) touchedCollectionIds.addAll(previous.collectionIds());
        touchedCollectionIds.addAll(updated.collectionIds());

        for (String collectionId : touchedCollectionIds) {
            boolean selected = updated.collectionIds().contains(collectionId);
            boolean previouslySelected = previous == null
                ? selected
                : previous.collectionIds().contains(collectionId);
            runtime.libraryCache().setCollectionItemState(
                userId, collectionId, item, previouslySelected, selected
            );
        }
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

    private void cleanupDeletedArt(CompanionRuntime runtime, CompanionManifest deleting) throws java.io.IOException, InterruptedException {
        String userId = runtime.sessionStore().userId();
        runtime.manifestCache().remove(userId, deleting.artId());
        runtime.libraryCache().removeViewItem(userId, "my", deleting.artId());
        runtime.libraryCache().removeViewItem(userId, "recent", deleting.artId());
        runtime.libraryCache().removeViewItem(userId, "favorites", deleting.artId());
        for (String collectionId : deleting.collectionIds()) {
            runtime.libraryCache().setCollectionItemState(
                userId, collectionId, toLibraryItem(deleting), true, false
            );
        }

        try {
            runtime.syncService().removeLitematicsForArt(deleting.artId());
        } catch (Exception ignored) {
        }
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
    }

    private void handleExpiredSession(String nextStatus, boolean showCachedManifest) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            String cachedUserId = runtime.sessionStore().userId();
            CompanionAuthSupport.clearSessionQuietly(runtime);
            if (showCachedManifest) {
                java.util.Optional<ManifestCache.CachedManifest> cached = runtime.manifestCache().read(cachedUserId, artId);
                runOnClient(() -> {
                    if (cached.isPresent()) {
                        manifest = cached.get().manifest();
                        deleteArmed = false;
                        updateFavoriteButton();
                        updateActionButtons();
                    }
                    status = cached.isPresent() ? nextStatus : CompanionAuthSupport.expiredMessage();
                });
            } else {
                runOnClient(() -> status = nextStatus);
            }
        } catch (Exception ignored) {
            runOnClient(() -> status = CompanionAuthSupport.expiredMessage());
        }
    }
}
