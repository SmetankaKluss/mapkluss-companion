package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.WorldSavePath;

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
    private ClickableWidget installButton;
    private ClickableWidget installTilesButton;
    private ClickableWidget removeButton;
    private ClickableWidget mapDatButton;
    private ClickableWidget favoriteButton;
    private ClickableWidget collectionsButton;
    private ClickableWidget pngButton;
    private ClickableWidget materialsButton;
    private ClickableWidget commandsButton;
    private ClickableWidget datapackButton;
    private ClickableWidget downloadMapDatButton;
    private ClickableWidget autoFrameButton;
    private ClickableWidget projectButton;
    private ClickableWidget suppressionButton;
    private ClickableWidget privacyButton;
    private ClickableWidget saveMetaButton;
    private ClickableWidget deleteButton;
    private TextFieldWidget titleInput;
    private CompanionManifest manifest;
    private String status = "";
    private String privacyDraft;
    private boolean deleteArmed;
    private int actionPage;

    public CompanionArtScreen(Screen parent, String artId, String fallbackTitle) {
        super(Text.literal("Арт MapKluss"));
        this.parent = parent;
        this.artId = artId;
        this.fallbackTitle = fallbackTitle;
    }

    @Override
    protected void init() {
        clearChildren();
        CompanionUiLayout.Shell shell = artShell();
        addNavigationControls(shell);
        CompanionUiLayout.Rect host = actionHost(shell);
        int panelWidth = host.width();
        int left = host.x();
        int gap = 4;
        int privacyWidth = Math.max(58, (panelWidth - gap * 2) / 3);
        int saveWidth = privacyWidth;
        int deleteWidth = Math.max(58, panelWidth - privacyWidth - saveWidth - gap * 2);
        int titleWidth = panelWidth;
        int titleY = host.y() + 8;
        titleInput = new TextFieldWidget(textRenderer, left, titleY, titleWidth, 20, CompanionI18n.text("Название арта"));
        titleInput.setMaxLength(120);
        setTitleInputText(manifest == null ? fallbackTitle : manifest.title());
        addDrawableChild(titleInput);
        if (privacyDraft == null) {
            privacyDraft = manifest == null || manifest.privacy() == null || manifest.privacy().isBlank()
                ? "unlisted"
                : manifest.privacy();
        }
        int metadataY = titleY + 24;
        int privacyX = left;
        int saveX = left + privacyWidth + gap;
        int deleteX = left + privacyWidth + saveWidth + gap * 2;
        privacyButton = addDrawableChild(MapKlussButton.builder(privacyButtonText(), button -> cyclePrivacy()).action("art.privacy")
            .dimensions(privacyX, metadataY, privacyWidth, 20).build());
        saveMetaButton = addDrawableChild(MapKlussButton.builder(Text.literal("Сохранить"), button -> saveMetadata()).action("art.save")
            .dimensions(saveX, metadataY, saveWidth, 20).build());
        deleteButton = addDrawableChild(MapKlussButton.builder(deleteButtonText(), button -> deleteArt()).action("art.delete")
            .danger()
            .navigationOrder(1000)
            .dimensions(deleteX, metadataY, deleteWidth, 20).build());

        addPrimaryActions(left, panelWidth, gap, metadataY + 28);
        addCompactActionControls(left, panelWidth, gap);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
        addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> client().setScreen(parent)).action("global.back")
            .tooltip(CompanionI18n.text("Назад")).dimensions(shell.topBar().x() + 4, shell.topBar().y() + 8, 24, 20).build());
        updateActionButtons();
        setFocused(null);
        titleInput.setFocused(false);
        refreshManifest();
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
            case LIBRARY -> client().setScreen(new CompanionLibraryScreen(parent));
            case LENS -> client().setScreen(new LensScreen(this));
            case SCAN -> client().setScreen(new ScanScreen(this));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> client().setScreen(new CompanionAccountScreen(this));
            default -> { }
        }
    }

    private void addPrimaryActions(int left, int panelWidth, int gap, int rowY) {
        int width = Math.max(48, (panelWidth - gap * 2) / 3);
        installButton = addDrawableChild(MapKlussButton.builder(Text.literal("Установить"), button -> installLitematic()).action("art.install")
            .gold().selected(installedLitematic() != null).dimensions(left, rowY, width, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openSite("/?art=" + artId)).action("art.open_editor")
            .technical().dimensions(left + width + gap, rowY, width, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> openArtTracker()).action("art.track")
            .dimensions(left + (width + gap) * 2, rowY, panelWidth - (width + gap) * 2, 22).build());
    }

    private void addCompactActionControls(int left, int panelWidth, int gap) {
        int tabsY = actionTabsY();
        String[] groups = {"Файлы", "Облако", "Стройка", "Ещё"};
        String[] tooltips = {"Файлы и схемы", "Облачный арт", "Инструменты постройки", "Экспорт и архивы"};
        int tabWidth = Math.max(42, (panelWidth - gap * 3) / 4);
        for (int i = 0; i < groups.length; i++) {
            final int page = i;
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text(groups[i]), button -> {
                    actionPage = page;
                    init();
                }).selected(actionPage == i).tooltip(CompanionI18n.text(tooltips[i]))
                .action(CompanionActionInventory.artTabAction(i))
                .dimensions(left + (tabWidth + gap) * i, tabsY, i == groups.length - 1 ? panelWidth - (tabWidth + gap) * i : tabWidth, 20).build());
        }
        int rowY = actionTop();
        switch (Math.max(0, Math.min(actionPage, 3))) {
            case 0 -> addCompactSchemaActions(left, panelWidth, gap, rowY);
            case 1 -> addCompactCloudActions(left, panelWidth, gap, rowY);
            case 2 -> addCompactBuildActions(left, panelWidth, gap, rowY);
            default -> addCompactMoreActions(left, panelWidth, gap, rowY);
        }
    }

    private void addCompactSchemaActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        installTilesButton = addDrawableChild(MapKlussButton.builder(Text.literal("+ По картам"), button -> installLitematicTiles()).action("art.install_tiles").gold().tooltip(CompanionI18n.text("Установить схемы по картам"))
            .dimensions(left, rowY, w, 20).build());
        removeButton = addDrawableChild(MapKlussButton.builder(Text.literal("- Схема"), button -> removeLitematic()).action("art.remove_install").danger().tooltip(CompanionI18n.text("Удалить установленную схему"))
            .dimensions(left + w + gap, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> refreshManifest()).action("art.refresh")
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Папка схем"), button -> openSchematicFolder()).action("art.schematics_folder")
            .dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactCloudActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        favoriteButton = addDrawableChild(MapKlussButton.builder(favoriteButtonText(), button -> toggleFavorite()).action("art.favorite")
            .selected(manifest != null && manifest.isFavorite()).dimensions(left, rowY, w, 20).build());
        collectionsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Коллекции"), button -> openCollections()).action("art.collections")
            .dimensions(left + w + gap, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Страница"), button -> openSite("/art/" + artId)).action("art.open_page").technical()
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Папка файлов"), button -> openDownloadsFolder()).action("art.files_folder")
            .dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactBuildActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        autoFrameButton = addDrawableChild(MapKlussButton.builder(Text.literal("Для рамок"), button -> prepareAutoFrame()).action("art.prepare_autoframe").gold()
            .dimensions(left, rowY, w, 20).build());
        suppressionButton = addDrawableChild(MapKlussButton.builder(Text.literal("Two-layer"), button -> openSuppression()).action("art.two_layer").special()
            .dimensions(left + w + gap, rowY, w, 20).build());
        materialsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Материалы"), button -> downloadFirst("materials_txt", "materials_csv")).action("art.download_materials")
            .technical().dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        commandsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Команды"), button -> downloadFirst("frame_commands")).action("art.download_commands")
            .technical().dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactMoreActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(42, (panelWidth - gap * 4) / 5);
        pngButton = addDrawableChild(MapKlussButton.builder(Text.literal("PNG"), button -> downloadFirst("preview_png")).action("art.download_png")
            .technical().dimensions(left, rowY, w, 20).build());
        datapackButton = addDrawableChild(MapKlussButton.builder(Text.literal("Datapack"), button -> downloadFirst("frame_datapack")).action("art.download_datapack")
            .technical().dimensions(left + w + gap, rowY, w, 20).build());
        mapDatButton = addDrawableChild(MapKlussButton.builder(Text.literal("Импорт MapDat"), button -> importMapDat()).action("art.import_mapdat")
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        downloadMapDatButton = addDrawableChild(MapKlussButton.builder(Text.literal("MapDat ZIP"), button -> downloadFirst("mapdat_zip")).action("art.download_mapdat")
            .technical().dimensions(left + (w + gap) * 3, rowY, w, 20).build());
        projectButton = addDrawableChild(MapKlussButton.builder(Text.literal("Проект"), button -> downloadFirst("project")).action("art.download_project")
            .technical().dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
    }

    private void addCompactLinkActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 2) / 3);
        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт арта"), button -> openSite("/art/" + artId)).technical().dimensions(left, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openSite("/?art=" + artId)).technical().dimensions(left + w + gap, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> openArtTracker()).technical().dimensions(left + (w + gap) * 2, rowY, panelWidth - (w + gap) * 2, 20).build());
    }

    private void addCompactLibraryActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        favoriteButton = addDrawableChild(MapKlussButton.builder(favoriteButtonText(), button -> toggleFavorite()).selected(manifest != null && manifest.isFavorite()).dimensions(left, rowY, w, 20).build());
        collectionsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Коллекции"), button -> openCollections()).dimensions(left + w + gap, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Файлы"), button -> openDownloadsFolder()).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Схемы"), button -> openSchematicFolder()).dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactExportActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        pngButton = addDrawableChild(MapKlussButton.builder(Text.literal("PNG"), button -> downloadFirst("preview_png")).exportAction().dimensions(left, rowY, w, 20).build());
        materialsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Материалы"), button -> downloadFirst("materials_txt", "materials_csv")).exportAction().dimensions(left + w + gap, rowY, w, 20).build());
        commandsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Команды"), button -> downloadFirst("frame_commands")).exportAction().dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        datapackButton = addDrawableChild(MapKlussButton.builder(Text.literal("Датапак"), button -> downloadFirst("frame_datapack")).exportAction().dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactArchiveActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(42, (panelWidth - gap * 4) / 5);
        autoFrameButton = addDrawableChild(MapKlussButton.builder(Text.literal("Для рамок"), button -> prepareAutoFrame()).gold().dimensions(left, rowY, w, 20).build());
        suppressionButton = addDrawableChild(MapKlussButton.builder(Text.literal("Two-layer"), button -> openSuppression()).special().dimensions(left + w + gap, rowY, w, 20).build());
        mapDatButton = addDrawableChild(MapKlussButton.builder(Text.literal("Импорт MapDat"), button -> importMapDat()).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        downloadMapDatButton = addDrawableChild(MapKlussButton.builder(Text.literal("Скачать MapDat"), button -> downloadFirst("mapdat_zip")).exportAction().dimensions(left + (w + gap) * 3, rowY, w, 20).build());
        projectButton = addDrawableChild(MapKlussButton.builder(Text.literal("Скачать проект"), button -> downloadFirst("project")).exportAction().dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
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

        installButton = addDrawableChild(MapKlussButton.builder(Text.literal("+ Целиком"), button -> installLitematic()).gold()
            .dimensions(left, schemaY, columnWidth, 20).build());
        installTilesButton = addDrawableChild(MapKlussButton.builder(Text.literal("+ По картам"), button -> installLitematicTiles()).gold()
            .dimensions(left, schemaY + 24, columnWidth, 20).build());
        removeButton = addDrawableChild(MapKlussButton.builder(Text.literal("- Схема"), button -> removeLitematic()).danger()
            .dimensions(left, schemaY + 48, columnWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> refreshManifest())
            .dimensions(left, schemaY + 72, columnWidth, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт арта"), button -> openSite("/art/" + artId))
            .technical().dimensions(right, navigationY, columnWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openSite("/?art=" + artId))
            .technical().dimensions(right, navigationY + 24, columnWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Трекер"), button -> openArtTracker())
            .technical().dimensions(right, navigationY + 48, columnWidth, 20).build());

        favoriteButton = addDrawableChild(MapKlussButton.builder(favoriteButtonText(), button -> toggleFavorite())
            .selected(manifest != null && manifest.isFavorite())
            .dimensions(left, libraryY, columnWidth, 20).build());
        collectionsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Коллекции"), button -> openCollections())
            .dimensions(left, libraryY + 24, columnWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Папка файлов"), button -> openDownloadsFolder())
            .dimensions(left, libraryY + 48, columnWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Папка схем"), button -> openSchematicFolder())
            .dimensions(left, libraryY + 72, columnWidth, 20).build());

        pngButton = addDrawableChild(MapKlussButton.builder(Text.literal("PNG превью"), button -> downloadFirst("preview_png"))
            .exportAction().dimensions(right, exportY, columnWidth, 20).build());
        materialsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Материалы"), button -> downloadFirst("materials_txt", "materials_csv"))
            .exportAction().dimensions(right, exportY + 24, columnWidth, 20).build());
        commandsButton = addDrawableChild(MapKlussButton.builder(Text.literal("Команды"), button -> downloadFirst("frame_commands"))
            .exportAction().dimensions(right, exportY + 48, columnWidth, 20).build());
        datapackButton = addDrawableChild(MapKlussButton.builder(Text.literal("Датапак"), button -> downloadFirst("frame_datapack"))
            .exportAction().dimensions(right, exportY + 72, columnWidth, 20).build());

        autoFrameButton = addDrawableChild(MapKlussButton.builder(Text.literal("Для рамок"), button -> prepareAutoFrame())
            .gold().dimensions(left, archiveY, columnWidth, 20).build());
        mapDatButton = addDrawableChild(MapKlussButton.builder(Text.literal("Импорт MapDat"), button -> importMapDat())
            .dimensions(left, archiveY + 24, columnWidth, 20).build());
        downloadMapDatButton = addDrawableChild(MapKlussButton.builder(Text.literal("Скачать MapDat"), button -> downloadFirst("mapdat_zip"))
            .exportAction().dimensions(left, archiveY + 48, columnWidth, 20).build());
        projectButton = addDrawableChild(MapKlussButton.builder(Text.literal("Скачать проект"), button -> downloadFirst("project"))
            .exportAction().dimensions(left, archiveY + 72, columnWidth, 20).build());
        suppressionButton = addDrawableChild(MapKlussButton.builder(Text.literal("Two-layer"), button -> openSuppression())
            .special().dimensions(right, archiveY, columnWidth, 20).build());
    }

    private boolean sidePreviewLayout() {
        return artShell().hasInspector();
    }

    static boolean usesSidePreview(int screenWidth) {
        return screenWidth >= CompanionUiLayout.WIDE_MIN_WIDTH;
    }

    private boolean distributedActionLayout() {
        return false;
    }

    private boolean detailVisible() {
        return height >= 300;
    }

    private int titleRowY() {
        return actionHost(artShell()).y() + 8;
    }

    private int actionTabsY() {
        CompanionUiLayout.Rect host = actionHost(artShell());
        int desired = host.y() + 90;
        int latest = Math.max(host.y() + 76, host.bottom() - 48);
        return Math.min(desired, latest);
    }

    private int controlsWidth() {
        return actionHost(artShell()).width();
    }

    private int controlsLeft(int panelWidth) {
        return actionHost(artShell()).x();
    }

    private int previewLeft(int panelWidth) {
        return artPreview(artShell()).x();
    }

    private int actionTop() {
        return actionTabsY() + 24;
    }

    private CompanionUiLayout.Shell artShell() {
        return CompanionUiLayout.shell(width, height, true);
    }

    private CompanionUiLayout.Rect actionHost(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect host = shell.hasInspector() ? shell.inspector() : shell.content();
        return new CompanionUiLayout.Rect(host.x() + 8, host.y(), Math.max(1, host.width() - 16), host.height());
    }

    private CompanionUiLayout.Rect artPreview(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        int top = shell.hasInspector() ? content.y() + 10 : actionTop() + 30;
        int bottom = content.bottom() - 10;
        return new CompanionUiLayout.Rect(content.x() + 10, top, Math.max(1, content.width() - 20), Math.max(48, bottom - top));
    }

    private boolean previewVisible(CompanionUiLayout.Shell shell) {
        if (shell.hasInspector()) return true;
        return shell.content().bottom() - (actionTop() + 30) >= 64;
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
                CompanionTelemetryManager.record(CompanionTelemetryEvent.SCHEMATIC_INSTALLED);
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
                CompanionTelemetryManager.record(CompanionTelemetryEvent.SCHEMATIC_INSTALLED);
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
            Util.getOperatingSystem().open(folder.toUri());
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
            Util.getOperatingSystem().open(folder.toUri());
            status = "Папка загрузок открыта.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openSite(String path) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getOperatingSystem().open(runtime.config().siteUri(path));
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
        if (!client().isInSingleplayer() || !client().isIntegratedServerRunning()) {
            status = "MAP.DAT импорт работает только в одиночном мире.";
            return;
        }
        MinecraftServer server = client().getServer();
        if (server == null) {
            status = "Локальный сервер недоступен.";
            return;
        }
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);
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
        client().setScreen(new SuppressionStartScreen(this, manifest));
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
                TrackerForArtResult tracker = runtime.syncService().trackerForArtResult(artId);
                BuildSessionState session = tracker.session();
                if (tracker.created()) CompanionTelemetryManager.record(CompanionTelemetryEvent.TRACKER_CREATED);
                runOnClient(() -> client().setScreen(new TrackerSessionScreen(this, session.id())));
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
        client().setScreen(new CompanionArtCollectionsScreen(this, artId, fallbackTitle, manifest));
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
        String nextTitle = titleInput == null ? "" : titleInput.getText().trim();
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
                    client().setScreen(parent);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        String name = manifest == null ? fallbackTitle : manifest.title();
        ScreenViewModel model = ScreenViewModel.shell(
            CompanionUiLayout.Destination.ART,
            CompanionI18n.translate("Библиотека"),
            List.of(name),
            status
        );
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height, model, true, 52
        );
        CompanionUiLayout.Rect host = actionHost(shell);
        CompanionUiLayout.Rect preview = artPreview(shell);
        context.fill(host.x(), host.y(), host.right(), host.bottom(), UiTheme.SURFACE_RAISED);
        if (previewVisible(shell)) {
            MapKlussUi.drawPreviewWell(context, preview.x(), preview.y(), preview.right(), preview.bottom());
        }
        if (manifest != null && previewVisible(shell)) {
            int metaHeight = Math.min(34, Math.max(20, preview.height() / 5));
            drawPreview(context, preview.x() + 6, preview.y() + 6, Math.max(1, preview.width() - 12), Math.max(1, preview.height() - metaHeight - 12), false);
            int metaY = preview.bottom() - metaHeight;
            context.fill(preview.x() + 1, metaY, preview.right() - 1, preview.bottom() - 1, UiTheme.SURFACE_RAISED);
            MapKlussUi.drawLeft(context, textRenderer,
                manifest.grid().wide() + "x" + manifest.grid().tall() + "  ·  " + modeLabel(manifest.mode()) + "  ·  " + privacyLabel(manifest.privacy()),
                preview.x() + 12, metaY + 7, preview.width() - 24, MapKlussUi.WHITE);
            if (metaHeight >= 30) {
                MapKlussUi.drawLeft(context, textRenderer, artifactFilesLine(), preview.x() + 12, metaY + 20, preview.width() - 24, MapKlussUi.MUTED);
            }
        } else if (previewVisible(shell)) {
            drawPreviewPlaceholder(context, preview.x() + 6, preview.y() + 6, Math.max(1, preview.width() - 12), Math.max(1, preview.height() - 12),
                "Загрузка превью", "Облако готовит изображение арта", MapKlussUi.MUTED);
        }
        MapKlussUi.drawFieldLabel(context, textRenderer, "Название арта", host.x(), host.y() + 8, host.width());
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.ART);
        MapKlussUi.drawIcon(context, MapKlussIcon.BACK, shell.topBar().x() + 8, shell.topBar().y() + 10, MapKlussUi.MUTED);
    }

    private void drawActionGroups(DrawContext context, int left, int panelWidth) {
        if (distributedActionLayout()) {
            drawDistributedActionGroups(context, left, panelWidth);
        }
    }

    private void drawDistributedActionGroups(DrawContext context, int left, int panelWidth) {
        int columnWidth = (panelWidth - DISTRIBUTED_ACTION_COLUMN_GAP) / 2;
        int right = left + columnWidth + DISTRIBUTED_ACTION_COLUMN_GAP;
        int startY = actionTop();
        int schemaY = startY;
        int navigationY = startY;
        int libraryY = schemaY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        int exportY = navigationY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        int archiveY = libraryY + actionGroupHeight(4) + DISTRIBUTED_ACTION_ROW_GAP;
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Схема", left, columnWidth, schemaY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Библиотека", left, columnWidth, libraryY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Архивы", left, columnWidth, archiveY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Переходы", right, columnWidth, navigationY, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, textRenderer, "Экспорт", right, columnWidth, exportY, ACTION_BUTTON_HEIGHT);
    }

    private void drawPreview(DrawContext context, int x, int y, int boxWidth, int boxHeight, boolean framed) {
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

    private void drawPreviewPlaceholder(DrawContext context, int x, int y, int boxWidth, int boxHeight, String title, String detail, int color) {
        int cardWidth = Math.min(260, Math.max(120, boxWidth - 24));
        int cardHeight = 52;
        int cardLeft = x + Math.max(0, (boxWidth - cardWidth) / 2);
        int cardTop = y + Math.max(0, (boxHeight - cardHeight) / 2);
        MapKlussUi.drawPreviewWell(context, cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
        MapKlussUi.drawCenteredIn(context, textRenderer, title, cardLeft + cardWidth / 2, cardTop + 11, cardWidth - 14, color);
        MapKlussUi.drawCenteredIn(context, textRenderer, detail, cardLeft + cardWidth / 2, cardTop + 28, cardWidth - 14, MapKlussUi.DIM);
    }

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private Text favoriteButtonText() {
        return Text.literal(manifest != null && manifest.isFavorite() ? "Убрать" : "В избранное");
    }

    private Text privacyButtonText() {
        return Text.literal(privacyLabel(selectedPrivacy()));
    }

    private Text deleteButtonText() {
        return Text.literal(deleteArmed ? "Точно?" : "Удалить");
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
            installButton.setMessage(Text.literal(installed == null ? "+ Целиком" : "Обновить"));
            if (installButton instanceof MapKlussButton mapKlussButton) {
                mapKlussButton.setSelected(installed != null && !installed.artifactId().contains("#"));
            }
            installButton.active = manifest != null && hasArtifact("litematic");
        }
        if (installTilesButton != null) {
            installTilesButton.setMessage(Text.literal(installed == null ? "+ По картам" : "По картам"));
            if (installTilesButton instanceof MapKlussButton mapKlussButton) {
                mapKlussButton.setSelected(installed != null && installed.artifactId().contains("#"));
            }
            installTilesButton.active = manifest != null && hasArtifact("litematic_tiles_zip");
        }
        if (removeButton != null) {
            removeButton.setMessage(Text.literal(installed == null ? "- Схема" : "Удалить"));
            removeButton.active = installed != null;
        }
        if (mapDatButton != null) {
            mapDatButton.setMessage(Text.literal(hasArtifact("mapdat_zip") ? "MapDat" : "MapDat N/A"));
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
            suppressionButton.setMessage(Text.literal("Two-layer"));
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
            if (!desiredTitle.equals(titleInput.getText())) {
                setTitleInputText(desiredTitle);
            }
            titleInput.setEditable(canEditMetadata());
        }
    }

    private void setTitleInputText(String value) {
        if (titleInput == null) return;
        titleInput.setText(value == null ? "" : value);
        titleInput.setCursorToStart(false);
        titleInput.setSelectionStart(0);
        titleInput.setSelectionEnd(0);
    }

    private boolean hasArtifact(String kind) {
        return manifest != null && manifest.artifacts().stream().anyMatch(artifact -> kind.equals(artifact.kind()));
    }

    private InstalledArtifact installedLitematic() {
        if (manifest == null) return null;
        try {
            InstalledArtifactIndex index = InstalledArtifactIndex.load(LitematicaPaths.companionIndexPath(client().runDirectory.toPath()));
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
