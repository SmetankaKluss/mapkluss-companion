package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
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
    private boolean privacyDirty;
    private boolean deleteArmed;
    private int actionPage;
    private boolean compactPreview;
    private final boolean fixture;
    private boolean manifestRequested;
    private WorkshopTheme workshopTheme=WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final ArtTitleDraft titleDraft;

    public CompanionArtScreen(Screen parent, String artId, String fallbackTitle) {
        this(parent, artId, fallbackTitle, false);
    }

    CompanionArtScreen(Screen parent, String artId, String fallbackTitle, boolean fixture) {
        super(Component.literal("Арт MapKluss"));
        this.fixture=fixture;
        this.parent = parent;
        this.artId = artId;
        this.fallbackTitle = fallbackTitle;
        this.titleDraft = new ArtTitleDraft(fallbackTitle);
    }

    @Override
    public void onClose() {
        client().gui.setScreen(parent);
    }

    @Override
    protected void init() {
        fixtureLocalButtons.clear();
        if (titleInput != null) titleDraft.edit(titleInput.getValue());
        clearWidgets();
        try { workshopTheme=WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme()); }
        catch(Exception ignored) { }
        var s=WorkshopArtLayout.at(width,height);
        var nav=s.navigation();
        int slot=(nav.width()-56)/5;
        WorkshopIcon[] icons={WorkshopIcon.LIBRARY,WorkshopIcon.LENS,WorkshopIcon.SCAN,WorkshopIcon.TRACKER,WorkshopIcon.ACCOUNT};
        String[] labels={"Библиотека","Lens","Скан","Трекер","Аккаунт"};
        for(int i=0;i<5;i++) {
            var destination=CompanionUiLayout.Destination.values()[i];
            artWorkshopButton(CompanionActionInventory.navigationAction(destination),labels[i],icons[i],
                new WorkshopLayout.Rect(nav.x()+i*slot,nav.y(),slot-4,28),()->openDestination(destination));
        }
        artWorkshopButton("art.preview",CompanionI18n.english(client())?"Preview":"Превью",WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(nav.right()-52,nav.y(),24,28),()->{ compactPreview=!compactPreview; init(); });
        artWorkshopButton("global.back","Назад",WorkshopIcon.BACK,
            new WorkshopLayout.Rect(nav.right()-24,nav.y(),24,28),()->client().gui.setScreen(parent));
        var primary=s.primary();
        int third=(primary.width()-8)/3;
        installButton=artWorkshopButton("art.install","Установить",WorkshopIcon.INSTALL,
            new WorkshopLayout.Rect(primary.x(),primary.y(),third,28),this::installLitematic);
        artWorkshopButton("art.open_editor","Редактор",WorkshopIcon.EDIT,
            new WorkshopLayout.Rect(primary.x()+third+4,primary.y(),third,28),()->openSite("/?art="+artId));
        artWorkshopButton("art.track","Трекер",WorkshopIcon.TRACKER,
            new WorkshopLayout.Rect(primary.x()+2*(third+4),primary.y(),primary.width()-2*(third+4),28),this::openArtTracker);
        if(s.split() || !compactPreview) {
            String[] groups={"Файлы","Облако","Стройка","Ещё"};
            WorkshopIcon[] groupIcons={WorkshopIcon.FOLDER,WorkshopIcon.LIBRARY,WorkshopIcon.LAYERS,WorkshopIcon.MORE};
            int tabWidth=(s.tabs().width()-12)/4;
            for(int i=0;i<4;i++) {
                final int page=i;
                artWorkshopButton(CompanionActionInventory.artTabAction(i),groups[i],groupIcons[i],
                    new WorkshopLayout.Rect(s.tabs().x()+i*(tabWidth+4),s.tabs().y(),tabWidth,24),
                    ()->{ actionPage=page; deleteArmed=false; init(); }).setSelected(actionPage==i);
            }
            int x=s.controls().x(), y=s.controls().y(), w=s.controls().width();
            var existingControls=new java.util.HashSet<Object>(children());
            if(actionPage==1) {
                titleInput=new EditBox(font,x,y,w,20,CompanionI18n.text("Название арта"));
                titleInput.setMaxLength(120);
                setTitleInputText(titleDraft.value());
                addRenderableWidget(titleInput);
                if(privacyDraft==null) privacyDraft=manifest==null?"unlisted":manifest.privacy();
                int cell=(w-8)/3;
                privacyButton=artWorkshopButton("art.privacy",privacyButtonText().getString(),null,new WorkshopLayout.Rect(x,y+24,cell,20),this::cyclePrivacy);
                saveMetaButton=artWorkshopButton("art.save","Сохранить",WorkshopIcon.CHECK,new WorkshopLayout.Rect(x+cell+4,y+24,cell,20),this::saveMetadata);
                deleteButton=artWorkshopButton("art.delete",deleteButtonText().getString(),WorkshopIcon.DELETE,new WorkshopLayout.Rect(x+2*(cell+4),y+24,w-2*(cell+4),20),this::deleteArt);
                existingControls.addAll(children());
                addCompactCloudActions(x,w,4,y+48);
            } else if(actionPage==0) addCompactSchemaActions(x,w,4,y);
            else if(actionPage==2) addCompactBuildActions(x,w,4,y);
            else addCompactMoreActions(x,w,4,y);
            if(s.split()) {
                int index=0, cellWidth=(w-4)/2, top=y+(actionPage==1?48:0);
                for(var child:children()) if(child instanceof MapKlussButton b && !existingControls.contains(child)) {
                    b.setX(x+(index%2)*(cellWidth+4));
                    b.setY(top+(index/2)*24);
                    b.setWidth(cellWidth);
                    index++;
                }
            }
        }
        for(var child:children()) if(child instanceof MapKlussButton b && !workshopButtons.contains(b)) b.workshop(workshopTheme,null);
        workshopButtons.clear();
        updateActionButtons();
        if(fixture) {
            for(var child:children()) if(child instanceof MapKlussButton b && !fixtureLocalButtons.contains(b)) b.active=false;
            if(collectionsButton!=null)collectionsButton.active=true;
        } else if(!manifestRequested) { manifestRequested=true; refreshManifest(); }
    }

    private final java.util.Set<MapKlussButton> workshopButtons=new java.util.HashSet<>();
    private final java.util.Set<MapKlussButton> fixtureLocalButtons=new java.util.HashSet<>();

    private MapKlussButton artWorkshopButton(String id,String label,WorkshopIcon icon,WorkshopLayout.Rect r,Runnable callback) {
        var builder=MapKlussButton.builder(CompanionI18n.text(label),button->callback.run()).action(id)
            .tooltip(CompanionI18n.text(label)).dimensions(r.x(),r.y(),r.width(),r.height());
        if("art.install".equals(id))builder.gold();
        if("art.delete".equals(id))builder.danger();
        var button=addRenderableWidget(builder.build().workshop(workshopTheme,icon));
        workshopButtons.add(button);
        if(id.startsWith("nav.") || id.startsWith("art.tab_") || id.equals("art.preview") || id.equals("global.back")) fixtureLocalButtons.add(button);
        return button;
    }


    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destination.name()))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(parent));
            case LENS -> client().gui.setScreen(new LensScreen(this, fixture));
            case SCAN -> client().gui.setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this, fixture));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this, fixture));
            default -> { }
        }
    }

    private void addPrimaryActions(int left, int panelWidth, int gap, int rowY) {
        int width = Math.max(48, (panelWidth - gap * 2) / 3);
        installButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Установить"), button -> installLitematic()).action("art.install")
            .gold().selected(installedLitematic() != null).dimensions(left, rowY, width, 22).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Редактор"), button -> openSite("/?art=" + artId)).action("art.open_editor")
            .technical().dimensions(left + width + gap, rowY, width, 22).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Трекер"), button -> openArtTracker()).action("art.track")
            .dimensions(left + (width + gap) * 2, rowY, panelWidth - (width + gap) * 2, 22).build());
    }


    private void addCompactSchemaActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        installTilesButton = addRenderableWidget(MapKlussButton.builder(Component.literal("+ По картам"), button -> installLitematicTiles()).action("art.install_tiles").gold().tooltip(CompanionI18n.text("Установить схемы по картам"))
            .dimensions(left, rowY, w, 20).build());
        removeButton = addRenderableWidget(MapKlussButton.builder(Component.literal("- Схема"), button -> removeLitematic()).action("art.remove_install").danger().tooltip(CompanionI18n.text("Удалить установленную схему"))
            .dimensions(left + w + gap, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Обновить"), button -> refreshManifest()).action("art.refresh")
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Папка схем"), button -> openSchematicFolder()).action("art.schematics_folder")
            .dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactCloudActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        favoriteButton = addRenderableWidget(MapKlussButton.builder(favoriteButtonText(), button -> toggleFavorite()).action("art.favorite")
            .selected(manifest != null && manifest.isFavorite()).dimensions(left, rowY, w, 20).build());
        collectionsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Коллекции"), button -> openCollections()).action("art.collections")
            .dimensions(left + w + gap, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Страница"), button -> openSite("/art/" + artId)).action("art.open_page").technical()
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Папка файлов"), button -> openDownloadsFolder()).action("art.files_folder")
            .dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactBuildActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(46, (panelWidth - gap * 3) / 4);
        autoFrameButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Для рамок"), button -> prepareAutoFrame()).action("art.prepare_autoframe").gold()
            .dimensions(left, rowY, w, 20).build());
        suppressionButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Two-layer"), button -> openSuppression()).action("art.two_layer").special()
            .dimensions(left + w + gap, rowY, w, 20).build());
        materialsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Материалы"), button -> downloadFirst("materials_txt", "materials_csv")).action("art.download_materials")
            .technical().dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        commandsButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Команды"), button -> downloadFirst("frame_commands")).action("art.download_commands")
            .technical().dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
    }

    private void addCompactMoreActions(int left, int panelWidth, int gap, int rowY) {
        int w = Math.max(42, (panelWidth - gap * 4) / 5);
        pngButton = addRenderableWidget(MapKlussButton.builder(Component.literal("PNG"), button -> downloadFirst("preview_png")).action("art.download_png")
            .technical().dimensions(left, rowY, w, 20).build());
        datapackButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Datapack"), button -> downloadFirst("frame_datapack")).action("art.download_datapack")
            .technical().dimensions(left + w + gap, rowY, w, 20).build());
        mapDatButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Импорт MapDat"), button -> importMapDat()).action("art.import_mapdat")
            .dimensions(left + (w + gap) * 2, rowY, w, 20).build());
        downloadMapDatButton = addRenderableWidget(MapKlussButton.builder(Component.literal("MapDat ZIP"), button -> downloadFirst("mapdat_zip")).action("art.download_mapdat")
            .technical().dimensions(left + (w + gap) * 3, rowY, w, 20).build());
        projectButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Проект"), button -> downloadFirst("project")).action("art.download_project")
            .technical().dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
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
                    if (!privacyDirty) privacyDraft = loaded.privacy();
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
                    if (!privacyDirty) privacyDraft = manifest.privacy();
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
                TrackerForArtResult tracker = runtime.syncService().trackerForArtResult(artId);
                BuildSessionState session = tracker.session();
                if (tracker.created()) CompanionTelemetryManager.record(CompanionTelemetryEvent.TRACKER_CREATED);
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
        if(fixture) {
            client().gui.setScreen(new CompanionArtCollectionsScreen(this,artId,fallbackTitle,null,true));
            return;
        }
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
        privacyDirty = true;
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
                    if (java.util.Objects.equals(privacyDraft, nextPrivacy)) {
                        privacyDraft = updated.privacy();
                        privacyDirty = false;
                    }
                    updateFavoriteButton();
                    updateActionButtons();
                    status = metadataSaveStatus(refresh, litematicaStatus);
                    if (titleInput != null) titleDraft.edit(titleInput.getValue());
                    titleDraft.saved(nextTitle, updated.title());
                    setTitleInputText(titleDraft.value());
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
    public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        context.fill(0,0,width,height,0x88000000);
        var s=WorkshopArtLayout.at(width,height);
        WorkshopChrome.frame(context::fill,s.frame(),workshopTheme);
        var nav=s.navigation();
        context.fill(nav.x(),nav.bottom()+1,nav.right(),nav.bottom()+2,workshopTheme.color("border-subtle"));
        WorkshopDraw.text(context,font,manifest==null?fallbackTitle:manifest.title(),
            s.title().x()+4,s.title().y()+6,s.title().width()-8,workshopTheme.color("text-primary"));
        if(s.split() || compactPreview) {
            if(fixture) {
                WorkshopDraw.image(context,net.minecraft.resources.Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID,"textures/dev/library/starry-night.png"),s.preview(),256,256);
            } else if(manifest!=null) {
                var preview=CompanionPreviewTextures.request(manifest);
                if(preview.ready()) WorkshopDraw.image(context,preview.identifier(),s.preview(),preview.imageWidth(),preview.imageHeight());
                else WorkshopDraw.text(context,font,CompanionI18n.translate(preview.loading()?"Загрузка превью":"Превью недоступно"),
                    s.preview().x()+4,s.preview().y()+8,s.preview().width()-8,workshopTheme.color("text-secondary"));
            } else WorkshopDraw.text(context,font,CompanionI18n.translate("Загрузка превью"),
                s.preview().x()+4,s.preview().y()+8,s.preview().width()-8,workshopTheme.color("text-secondary"));
        }
        var footer=s.footer();
        WorkshopDraw.text(context,font,CompanionI18n.translate(status),footer.x()+4,footer.y()+6,footer.width()-8,workshopTheme.color("text-secondary"));
        super.extractRenderState(context,mouseX,mouseY,delta);
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
        if (!privacyDirty) privacyDraft = updatedManifest.privacy();
        if (titleInput != null) {
            titleDraft.edit(titleInput.getValue());
            titleDraft.receive(updatedManifest.title());
            setTitleInputText(titleDraft.value());
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
            suppressionButton.setMessage(Component.literal("Two-layer"));
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
            titleDraft.edit(titleInput.getValue());
            titleDraft.receive(manifest == null ? fallbackTitle : manifest.title());
            String desiredTitle = titleDraft.value();
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
