package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanScreen extends WorkshopTrackerScreen {
    @Override
    protected boolean submitFocusedInput() {
        if (titleInput == null || !titleInput.isFocused()) return false;
        if (draft != null && (!fixture || localAction("scan.title_apply"))) applyDraftTitle();
        return true;
    }


    private final Screen parent;
    private EditBox titleInput;
    private AbstractWidget savePngButton;
    private AbstractWidget uploadButton;
    private AbstractWidget refreshImportButton;
    private AbstractWidget loadButton;
    private AbstractWidget deleteButton;
    private AbstractWidget folderButton;
    private AbstractWidget artButton;
    private AbstractWidget editorButton;
    private AbstractWidget cloudButton;
    private MapScanDraft draft;
    private ScanUploadResponse upload;
    private MapFrameCorner cornerA;
    private MapFrameCorner cornerB;
    private final List<ScanHistoryEntry> history = new ArrayList<>();
    private int historyIndex = -1;
    private String status = "";
    private int actionPage;
    private final CompanionConfirmation deleteConfirmation = new CompanionConfirmation();
    private final ScreenRequestGate requests = new ScreenRequestGate();
    private final AtomicBoolean uploadInFlight = new AtomicBoolean();

    public ScanScreen(Screen parent) {
        super(Component.literal("Скан MapKluss"));
        this.parent = parent;
    }


    private WorkshopTheme workshopTheme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final ScanPreviewTexture previewTexture = new ScanPreviewTexture();
    private MapScanDraft previewDraft;
    private boolean previewFailed;
    private boolean historyLoaded;
    private boolean fixture;
    private boolean fixtureApplied;
    private boolean showDetails;
    private String pendingTitle = "";
    private int scanMode = -1;

    public ScanScreen(Screen parent, boolean fixture) {
        this(parent);
        this.fixture = fixture;
    }

    void applyDevelopmentData(MapScanDraft value, List<ScanHistoryEntry> entries, String message) {
        if (!fixture) return;
        draft = value;
        history.clear();
        history.addAll(entries);
        historyIndex = history.isEmpty() ? -1 : 0;
        status = message;
        syncTitleInput();
    }

    private boolean localAction(String id) {
        return id.startsWith("nav.") || id.startsWith("global.") || id.equals("account.theme")
            || id.startsWith("scan.tab_") || id.equals("scan.details")
            || id.equals("scan.history_previous") || id.equals("scan.history_next") || id.equals("scan.title_reset");
    }

    private MapKlussButton scanButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r,
        java.util.function.BooleanSupplier enabled, boolean selected, Runnable action) {
        var builder = MapKlussButton.builder(CompanionI18n.text(label), button -> {
            if (enabled.getAsBoolean() && (!fixture || localAction(id))) action.run();
        }).action(id).tooltip(CompanionI18n.text(label)).selected(selected)
            .dimensions(r.x(), r.y(), r.width(), r.height())
            .enabledWhen(() -> enabled.getAsBoolean() && (!fixture || localAction(id)));
        if (id.equals("scan.upload_cloud") || id.equals("scan.history_load")) builder.gold();
        if (id.equals("scan.history_delete")) builder.danger().navigationOrder(1000);
        return addRenderableWidget(builder.build().workshop(workshopTheme, icon));
    }

    private WorkshopLayout.Rect part(WorkshopLayout.Rect r, int index, int count) {
        return WorkshopScanLayout.part(r, index, count);
    }

    @Override
    protected void init() {
        requests.attach();
        if (titleInput != null) pendingTitle = titleInput.getValue();
        if (fixture && !fixtureApplied) {
            fixtureApplied = true;
            try {
                Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture")
                    .getMethod("applyScan", Object.class).invoke(null, this);
            } catch (ReflectiveOperationException ignored) { }
        }
        clearWidgets();
        savePngButton = uploadButton = refreshImportButton = loadButton = deleteButton = null;
        folderButton = artButton = editorButton = cloudButton = null;
        if (!historyLoaded && !fixture) { loadHistory(); historyLoaded = true; }
        var s = WorkshopScanLayout.at(width, height);
        workshopNavigation(s);
        var t = s.title();
        titleInput = new EditBox(font, t.x(), t.y(), t.width() - 56, 20, CompanionI18n.text("Название скана"));
        titleInput.setMaxLength(120);
        titleInput.setValue(pendingTitle);
        addRenderableWidget(titleInput);
        scanButton("scan.title_apply", "Применить название скана", WorkshopIcon.CHECK,
            new WorkshopLayout.Rect(t.right() - 52, t.y(), 24, 20), () -> draft != null, false, this::applyDraftTitle);
        scanButton("scan.title_reset", "Сброс", WorkshopIcon.REFRESH,
            new WorkshopLayout.Rect(t.right() - 24, t.y(), 24, 20), () -> draft != null, false, this::resetDraftTitle);
        scanButton("scan.hand", "Из руки", WorkshopIcon.SCAN, part(s.modes(), 0, 4), () -> true, scanMode == 0, () -> { scanMode = 0; scanHand(); init(); });
        scanButton("scan.frame", "Рамка", WorkshopIcon.SCAN, part(s.modes(), 1, 4), () -> true, scanMode == 1, () -> { scanMode = 1; scanFrame(); init(); });
        scanButton("scan.wall", "Стена", WorkshopIcon.SCAN, part(s.modes(), 2, 4), () -> true, scanMode == 2, () -> { scanMode = 2; scanWall(); init(); });
        scanButton("scan.corners", "По углам", WorkshopIcon.SCAN, part(s.modes(), 3, 4), () -> cornerA != null && cornerB != null, scanMode == 3, () -> { scanMode = 3; scanManualWall(); init(); });
        String[] labels = {"Результат", "История", "Открыть"};
        var tabs = new WorkshopLayout.Rect(s.tabs().x(), s.tabs().y(), s.tabs().width() - (s.split() ? 0 : 28), 20);
        for (int i = 0; i < 3; i++) {
            final int page = i;
            scanButton(CompanionActionInventory.scanTabAction(i), labels[i], null, part(tabs, i, 3),
                () -> true, actionPage == i, () -> { actionPage = page; showDetails = page != 0; deleteConfirmation.reset(); init(); });
        }
        if (!s.split()) scanButton("scan.details", "Превью", WorkshopIcon.MORE,
            new WorkshopLayout.Rect(s.tabs().right() - 24, s.tabs().y(), 24, 20),
            () -> true, !showDetails, () -> { showDetails = !showDetails; init(); });
        addWorkshopActions(s.actions());
        setFocused(null);
        titleInput.setFocused(false);
        updateButtonStates();
    }

    private void addWorkshopActions(WorkshopLayout.Rect row) {
        if (actionPage == 0) {
            scanButton("scan.corner_a", "Угол A", WorkshopIcon.SCAN, part(row, 0, 5), () -> true, cornerA != null, () -> { setCornerA(); init(); });
            scanButton("scan.corner_b", "Угол B", WorkshopIcon.SCAN, part(row, 1, 5), () -> cornerA != null, cornerB != null, () -> { setCornerB(); init(); });
            savePngButton = scanButton("scan.save_png", "PNG", WorkshopIcon.DOWNLOAD, part(row, 2, 5), () -> draft != null, false, this::savePng);
            uploadButton = scanButton("scan.upload_cloud", "В облако", WorkshopIcon.INSTALL, part(row, 3, 5), () -> draft != null && !uploadInFlight.get(), false, this::uploadScan);
            refreshImportButton = scanButton("scan.import_refresh", "Проверить", WorkshopIcon.REFRESH, part(row, 4, 5), () -> activeImportId() != null, false, this::refreshImportStatus);
        } else if (actionPage == 1) {
            scanButton("scan.history_previous", "Пред.", WorkshopIcon.BACK, part(row, 0, 5), () -> !history.isEmpty(), false, () -> { selectHistory(-1); init(); });
            loadButton = scanButton("scan.history_load", "Загрузить", WorkshopIcon.INSTALL, part(row, 1, 5), () -> selectedHistoryEntry() != null, false, this::loadSelectedHistory);
            scanButton("scan.history_next", "След.", WorkshopIcon.MORE, part(row, 2, 5), () -> !history.isEmpty(), false, () -> { selectHistory(1); init(); });
            deleteButton = scanButton("scan.history_delete", deleteConfirmation.armed() ? "Подтвердить удаление" : "Удалить", WorkshopIcon.DELETE, part(row, 3, 5), () -> selectedHistoryEntry() != null, false, this::requestDeleteSelectedHistory);
            folderButton = scanButton("scan.open_folder", "Папка", WorkshopIcon.FOLDER, part(row, 4, 5), () -> selectedHistoryEntry() != null || draft != null, false, this::openLocalScanPath);
        } else {
            artButton = scanButton("scan.open_art", "Арт", WorkshopIcon.LIBRARY, part(row, 0, 3), () -> activeImportId() != null || (selectedHistoryEntry() != null && selectedHistoryEntry().hasCreatedArt()), false, this::openSavedArt);
            editorButton = scanButton("scan.open_editor", "Редактор", WorkshopIcon.EDIT, part(row, 1, 3), () -> activeImportId() != null, false, this::openEditor);
            cloudButton = scanButton("scan.open_cloud", "Облако", WorkshopIcon.LINK, part(row, 2, 3), () -> activeImportId() != null || (selectedHistoryEntry() != null && selectedHistoryEntry().hasCreatedArt()), false, this::openCloud);
        }
    }

    private void workshopNavigation(WorkshopScanLayout.Layout s) {
        try { workshopTheme = WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        int slot = (s.navigation().width() - 60) / 5;
        WorkshopIcon[] icons = {WorkshopIcon.LIBRARY, WorkshopIcon.LENS, WorkshopIcon.SCAN, WorkshopIcon.TRACKER, WorkshopIcon.ACCOUNT};
        String[] labels = {"Библиотека", "Lens", "Скан", "Трекер", "Аккаунт"};
        for (int i = 0; i < 5; i++) {
            var d = CompanionUiLayout.Destination.values()[i];
            scanButton(CompanionActionInventory.navigationAction(d), labels[i], icons[i],
                new WorkshopLayout.Rect(s.navigation().x() + i * slot, s.navigation().y(), slot - 4, 28),
                () -> true, d == CompanionUiLayout.Destination.SCAN, () -> openDestination(d));
        }
        scanButton("account.theme", "Оформление", WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(s.navigation().right() - 56, s.navigation().y(), 24, 28), () -> true, false,
            () -> client().gui.setScreen(new WorkshopAppearanceScreen(this)));
        scanButton("global.back", "Назад", WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.navigation().right() - 24, s.navigation().y(), 24, 28), () -> true, false, this::onClose);
        scanButton("global.language", CompanionI18n.toggleLabel(client()), null,
            new WorkshopLayout.Rect(s.footer().right() - 32, s.footer().y(), 32, 20), () -> true, false,
            () -> { try { CompanionI18n.toggle(client()); init(); } catch (Exception ignored) { } });
    }

    @Override
    public void onClose() { client().gui.setScreen(parent); }

    @Override
    public void removed() {
        requests.detach();
        historyLoaded = false;
        deleteConfirmation.reset();
        previewTexture.close();
        previewDraft = null;
        previewFailed = false;
        super.removed();
    }

    private void scanHand() {
        try {
            draft = MapScanService.scanHeldMap(client());
            upload = null;
            persistDraft();
            syncTitleInput();
            updateButtonStates();
            status = "Скан карты из руки: " + draft.title();
        } catch (Exception e) {
            status = CompanionUiErrors.message("scan", e);
        }
    }

    private void scanFrame() {
        try {
            draft = MapScanService.scanTargetFrame(client());
            upload = null;
            persistDraft();
            syncTitleInput();
            updateButtonStates();
            status = "Скан карты из рамки: " + draft.title();
        } catch (Exception e) {
            status = CompanionUiErrors.message("scan", e);
        }
    }

    private void scanWall() {
        try {
            draft = MapScanService.scanTargetWall(client());
            upload = null;
            persistDraft();
            syncTitleInput();
            updateButtonStates();
            status = draft.missingMaps() == 0
                ? "Скан стены: " + draft.wide() + "x" + draft.tall()
                : "Скан стены, пропущено карт: " + draft.missingMaps() + ".";
        } catch (Exception e) {
            status = CompanionUiErrors.message("scan", e);
        }
    }

    private void setCornerA() {
        try {
            cornerA = MapScanService.captureTargetCorner(client());
            status = "Угол A: " + cornerA.label();
        } catch (Exception e) {
            status = CompanionUiErrors.message("scan", e);
        }
    }

    private void setCornerB() {
        try {
            cornerB = MapScanService.captureTargetCorner(client(), cornerA);
            status = "Угол B: " + cornerB.label();
        } catch (Exception e) {
            status = CompanionUiErrors.message("scan", e);
        }
    }

    private void scanManualWall() {
        try {
            draft = MapScanService.scanWallBetweenCorners(client(), cornerA, cornerB);
            upload = null;
            persistDraft();
            syncTitleInput();
            updateButtonStates();
            status = draft.missingMaps() == 0
                ? "Ручной скан стены: " + draft.wide() + "x" + draft.tall()
                : "Ручной скан, пропущено карт: " + draft.missingMaps() + ".";
        } catch (Exception e) {
            status = CompanionUiErrors.message("scan", e);
        }
    }

    private void savePng() {
        if (draft == null) {
            status = "Сначала отсканируйте карту.";
            return;
        }
        try {
            if (!syncDraftTitle(false)) return;
            Path output = currentDraftLocalPath();
            Files.createDirectories(output.getParent());
            Files.write(output, draft.pngBytes());
            rememberHistory(output);
            syncTitleInput();
            updateButtonStates();
            status = "Сохранено: " + output.getFileName();
        } catch (Exception e) {
            status = CompanionUiErrors.message("save", e);
        }
    }

    private void uploadScan() {
        if (draft == null) {
            status = "Сначала отсканируйте карту.";
            return;
        }
        try {
            if (!syncDraftTitle(false)) return;
        } catch (Exception e) {
            status = CompanionUiErrors.message("save", e);
            return;
        }
        if (!uploadInFlight.compareAndSet(false, true)) {
            status = "Загрузка скана уже выполняется.";
            return;
        }
        MapScanDraft requestedDraft = draft;
        Path requestedPath = currentDraftLocalPath();
        ScreenRequestGate.Token request = requests.begin("upload");
        status = "Загрузка скана в облако...";
        updateButtonStates();
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(request, () -> status = "Сначала войдите через код входа.");
                    return;
                }
                ScanUploadResponse response = runtime.apiClient().uploadScan(requestedDraft);
                attachUploadToHistory(response, requestedDraft, requestedPath);
                ScanImportDetails details = null;
                try {
                    details = runtime.apiClient().scanImport(response.importId());
                    persistImportDetails(requestedPath.toString(), details);
                } catch (Exception ignored) {
                    // Upload itself already succeeded; import details can be refreshed later from Cloud/Art actions.
                }
                ScanImportDetails finalDetails = details;
                runOnClient(request, () -> {
                    boolean stillSelected = draft == requestedDraft;
                    if (stillSelected) upload = response;
                    reloadHistoryAt(requestedPath.toString(), stillSelected);
                    boolean hasCreatedArt = finalDetails != null && finalDetails.hasCreatedArt();
                    syncTitleInput();
                    updateButtonStates();
                    String completed = response.reused()
                        ? (hasCreatedArt
                            ? "Скан уже загружен и привязан к арту."
                            : "Скан уже загружен: " + response.importId())
                        : (hasCreatedArt
                            ? "Скан загружен, найден сохраненный арт."
                            : "Скан загружен: " + response.importId());
                    status = stillSelected ? completed : "Загрузка предыдущего скана завершена.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(request, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(request, () -> status = CompanionUiErrors.message("sync", e));
                }
            } finally {
                uploadInFlight.set(false);
                client().execute(() -> {
                    if (client().gui.screen() == this) updateButtonStates();
                });
            }
        });
    }

    private void openCloud() {
        ScanHistoryEntry selectedEntry = selectedHistoryEntry();
        if (selectedEntry != null && selectedEntry.hasCreatedArt()) {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                Util.getPlatform().openUri(runtime.config().siteUri("/art/" + selectedEntry.createdArtId()));
                updateButtonStates();
                status = "Открыт сохраненный арт из кеша.";
            } catch (Exception e) {
                status = CompanionUiErrors.message("site", e);
            }
            return;
        }
        ActiveScanTarget target = activeTarget();
        String importId = target == null ? null : target.importId();
        String localPath = target == null ? null : target.localPath();
        ScreenRequestGate.Token request = requests.begin("open-cloud");
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                String path = "/cloud";
                String nextStatus = "Открыта страница облака.";
                if (importId != null) {
                    ScanImportDetails details = runtime.apiClient().scanImport(importId);
                    persistImportDetails(localPath, details);
                    if (details.hasCreatedArt()) {
                        path = "/art/" + details.createdArtId();
                        nextStatus = "Открыт сохраненный арт.";
                    } else {
                        path = "/cloud?import=" + importId;
                        nextStatus = "Открыт импорт в облаке.";
                    }
                }
                String finalPath = path;
                String finalStatus = nextStatus;
                runOnClient(request, () -> {
                    if (localPath != null) reloadHistoryAt(localPath, true);
                    Util.getPlatform().openUri(runtime.config().siteUri(finalPath));
                    status = finalStatus;
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(request, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(request, () -> status = CompanionUiErrors.message("site", e));
                }
            }
        });
    }

    private void openSavedArt() {
        ScanHistoryEntry selectedEntry = selectedHistoryEntry();
        if (selectedEntry != null && selectedEntry.hasCreatedArt()) {
            client().gui.setScreen(new CompanionArtScreen(this, selectedEntry.createdArtId(), selectedEntry.title()));
            status = "Открыт сохраненный арт в моде.";
            return;
        }
        ActiveScanTarget target = activeTarget();
        String importId = target == null ? null : target.importId();
        if (importId == null) {
            status = "Сначала загрузите скан, чтобы открыть арт.";
            return;
        }
        String localPath = target.localPath();
        String requestedTitle = target.title();
        ScreenRequestGate.Token request = requests.begin("open-art");
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                ScanImportDetails details = runtime.apiClient().scanImport(importId);
                persistImportDetails(localPath, details);
                if (!details.hasCreatedArt()) {
                    runOnClient(request, () -> status = "Этот скан еще не сохранен как арт.");
                    return;
                }
                runOnClient(request, () -> {
                    client().gui.setScreen(new CompanionArtScreen(this, details.createdArtId(), requestedTitle));
                    status = "Открыт сохраненный арт в моде.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(request, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(request, () -> status = CompanionUiErrors.message("site", e));
                }
            }
        });
    }

    private void openEditor() {
        ActiveScanTarget target = activeTarget();
        String importId = target == null ? null : target.importId();
        if (importId == null) {
            status = "Сначала загрузите скан, чтобы открыть редактор.";
            return;
        }
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getPlatform().openUri(runtime.config().siteUri("/?companionImport=" + importId));
            updateButtonStates();
            status = "Скан открыт в редакторе.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void openLocalScanPath() {
        try {
            Path target;
            if (!history.isEmpty() && historyIndex >= 0 && historyIndex < history.size()) {
                target = Path.of(history.get(historyIndex).localPath());
                if (!Files.exists(target)) {
                    status = "Локальный файл скана не найден.";
                    return;
                }
            } else {
                target = client().gameDirectory.toPath().resolve("mapkluss").resolve("scans");
                Files.createDirectories(target);
            }
            Util.getPlatform().openUri(target.toUri());
            status = Files.isDirectory(target)
                ? "Папка сканов открыта."
                : "Локальный файл скана открыт.";
            updateButtonStates();
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void refreshImportStatus() {
        ActiveScanTarget target = activeTarget();
        String importId = target == null ? null : target.importId();
        if (importId == null) {
            status = "Сначала загрузите скан.";
            return;
        }
        status = "Проверка импорта в облаке...";
        String localPath = target.localPath();
        ScreenRequestGate.Token request = requests.begin("refresh-import");
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(request, () -> status = "Сначала войдите через код входа.");
                    return;
                }
                ScanImportDetails details = runtime.apiClient().scanImport(importId);
                persistImportDetails(localPath, details);
                runOnClient(request, () -> {
                    if (localPath != null) reloadHistoryAt(localPath, true);
                    updateButtonStates();
                    status = details.hasCreatedArt()
                        ? "Импорт привязан к сохраненному арту."
                        : "Импорт загружен, но еще не сохранен как арт.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(request, CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(request, () -> status = CompanionUiErrors.message("sync", e));
                }
            }
        });
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        var s = WorkshopScanLayout.at(width, height);
        context.fill(0, 0, width, height, 0x88000000);
        WorkshopChrome.frame(context::fill, s.frame(), workshopTheme);
        if (s.split() || showDetails) drawScanDetails(context, s.details());
        if (s.split() || !showDetails) {
            var area = s.preview();
            context.fill(area.x(), area.y(), area.right(), area.bottom(), workshopTheme.color("field-bg"));
            // Draft identity, not title/history matching, owns the native texture.
            if (previewDraft != draft) {
                previewTexture.close();
                previewDraft = draft;
                previewFailed = false;
            }
            boolean otherHistory = actionPage == 1 && !isSelectedHistoryActiveDraft();
            if (draft != null && !previewFailed && !otherHistory && area.height() > 0) {
                try {
                    WorkshopDraw.image(context, previewTexture.get(draft), area, draft.wide() * 128, draft.tall() * 128);
                } catch (Exception error) { previewFailed = true; }
            }
            if (draft == null || previewFailed || otherHistory)
                scanText(context, otherHistory && selectedHistoryEntry() != null ? "Загрузить" :
                    draft == null ? "Сканов пока нет" : "Превью недоступно", area, 0, "text-secondary");
        }
        scanText(context, uploadInFlight.get() ? "Загрузка..." : status,
            new WorkshopLayout.Rect(s.footer().x(), s.footer().y(), s.footer().width() - 40, 20), 0, "text-secondary");
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void scanText(GuiGraphicsExtractor g, String value, WorkshopLayout.Rect r, int row, String color) {
        int y = r.y() + 4 + row * 15;
        if (y + 9 > r.bottom()) return;
        WorkshopDraw.text(g, font, CompanionI18n.translate(value), r.x() + 4, y,
            Math.max(0, r.width() - 8), workshopTheme.color(color));
    }

    private void drawScanDetails(GuiGraphicsExtractor g, WorkshopLayout.Rect area) {
        g.fill(area.x(), area.y(), area.right(), area.bottom(), workshopTheme.color("surface-secondary"));
        if (actionPage == 1) {
            var entry = selectedHistoryEntry();
            scanText(g, "История " + (historyIndex < 0 ? 0 : historyIndex + 1) + "/" + history.size(), area, 0, "text-secondary");
            if (entry == null) return;
            scanText(g, entry.title(), area, 1, "text-primary");
            scanText(g, entry.wide() + "x" + entry.tall() + " / " + readableSource(entry.source()), area, 2, "text-secondary");
            scanText(g, entry.hasCreatedArt() ? "Арт" : entry.hasImport() ? "В облаке" : "Локально", area, 3, "accent");
            scanText(g, isSelectedHistoryActiveDraft() ? "Активный" : "", area, 4, "text-secondary");
            return;
        }
        scanText(g, draft == null ? "Сканов пока нет" : draft.title(), area, 0, "text-primary");
        if (draft != null) {
            scanText(g, (draft.wide() * 128) + "x" + (draft.tall() * 128) + " PNG", area, 1, "text-secondary");
            scanText(g, readableSource(draft.source()), area, 2, "text-secondary");
            scanText(g, "Пропущено карт: " + draft.missingMaps(), area, 3, draft.missingMaps() == 0 ? "text-secondary" : "warning");
        }
        scanText(g, "A: " + (cornerA == null ? "-" : cornerA.label()), area, 4, "text-secondary");
        scanText(g, "B: " + (cornerB == null ? "-" : cornerB.label()), area, 5, "text-secondary");
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().gui.setScreen(new LensScreen(this, fixture));
            case SCAN -> { }
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this, fixture));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this, fixture));
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

    private Path scanPath(String title) {
        return client().gameDirectory.toPath().resolve("mapkluss").resolve("scans").resolve(SafeNames.slug(title) + ".png");
    }

    private void persistDraft() throws Exception {
        if (draft == null) return;
        Path output = currentDraftLocalPath();
        Files.createDirectories(output.getParent());
        Files.write(output, draft.pngBytes());
        rememberHistory(output);
    }

    private void rememberHistory(Path output) throws Exception {
        if (draft == null) return;
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
        store.remember(draft, output);
        loadHistory();
        historyIndex = 0;
    }

    private ScanHistoryEntry attachUploadToHistory(
        ScanUploadResponse response,
        MapScanDraft requestedDraft,
        Path output
    ) throws Exception {
        if (requestedDraft == null || output == null) return null;
        if (!Files.exists(output)) {
            Files.createDirectories(output.getParent());
            Files.write(output, requestedDraft.pngBytes());
        }
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
        return store.rememberUpload(requestedDraft, output, response);
    }

    private void loadHistory() {
        try {
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
            history.clear();
            history.addAll(store.entries());
            if (history.isEmpty()) historyIndex = -1;
            else if (historyIndex < 0 || historyIndex >= history.size()) historyIndex = 0;
        } catch (Exception ignored) {
            history.clear();
            historyIndex = -1;
        }
    }

    private void selectHistory(int delta) {
        deleteConfirmation.reset();
        if (history.isEmpty()) {
            status = "Локальной истории сканов пока нет.";
            return;
        }
        historyIndex = Math.floorMod(historyIndex + delta, history.size());
        ScanHistoryEntry entry = history.get(historyIndex);
        status = "Выбран скан из истории: " + entry.title();
    }

    private void loadSelectedHistory() {
        if (history.isEmpty() || historyIndex < 0 || historyIndex >= history.size()) {
            status = "Локальной истории сканов пока нет.";
            return;
        }
        ScanHistoryEntry entry = history.get(historyIndex);
        if (isSelectedHistoryActiveDraft()) {
            status = "Этот скан уже открыт как активный черновик.";
            return;
        }
        try {
            Path path = Path.of(entry.localPath());
            byte[] pngBytes = Files.readAllBytes(path);
            draft = new MapScanDraft(entry.title(), entry.source(), entry.wide(), entry.tall(), entry.missingMaps(), pngBytes);
            upload = entry.hasImport()
                ? new ScanUploadResponse(entry.importId(), null, null, entry.uploadedSha256(), entry.uploadedAt(), true)
                : null;
            syncTitleInput();
            updateButtonStates();
            status = "Локальный скан загружен: " + entry.title();
        } catch (Exception e) {
            status = CompanionUiErrors.message("sync", e);
        }
    }

    private String activeImportId() {
        ActiveScanTarget target = activeTarget();
        return target == null ? null : target.importId();
    }

    private ScanHistoryEntry selectedHistoryEntry() {
        if (history.isEmpty() || historyIndex < 0 || historyIndex >= history.size()) return null;
        return history.get(historyIndex);
    }

    private ActiveScanTarget activeTarget() {
        ScanHistoryEntry entry = selectedHistoryEntry();
        if (entry != null) {
            String importId = entry.importId();
            return new ActiveScanTarget(
                importId == null || importId.isBlank() ? null : importId,
                entry.localPath(),
                entry.title() == null || entry.title().isBlank() ? "Сканированный арт" : entry.title()
            );
        }
        if (draft == null) return null;
        String importId = upload == null || upload.importId() == null || upload.importId().isBlank()
            ? null : upload.importId();
        return new ActiveScanTarget(importId, currentDraftLocalPath().toString(), draft.title());
    }

    private void updateButtonStates() {
        ScanHistoryEntry entry = selectedHistoryEntry();
        boolean hasDraft = draft != null;
        boolean hasHistoryEntry = entry != null;
        boolean hasImport = activeImportId() != null;
        boolean hasCreatedArt = entry != null && entry.hasCreatedArt();

        if (savePngButton != null) savePngButton.active = hasDraft;
        if (uploadButton != null) uploadButton.active = hasDraft && !uploadInFlight.get();
        if (refreshImportButton != null) refreshImportButton.active = hasImport;
        if (loadButton != null) loadButton.active = hasHistoryEntry;
        if (deleteButton != null) deleteButton.active = hasHistoryEntry;
        if (folderButton != null) folderButton.active = hasHistoryEntry || hasDraft;
        if (artButton != null) artButton.active = hasImport || hasCreatedArt;
        if (editorButton != null) editorButton.active = hasImport;
        if (cloudButton != null) cloudButton.active = hasImport || hasCreatedArt;
    }

    private void persistImportDetails(String localPath, ScanImportDetails details) throws Exception {
        if (localPath == null || localPath.isBlank() || details == null
            || details.importId() == null || details.importId().isBlank()) return;
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
        store.attachImportDetails(localPath, details);
    }

    private void reloadHistoryAt(String localPath, boolean selectRequested) {
        loadHistory();
        if (selectRequested && localPath != null) {
            for (int index = 0; index < history.size(); index++) {
                if (localPath.equals(history.get(index).localPath())) {
                    historyIndex = index;
                    break;
                }
            }
        }
        updateButtonStates();
    }

    private void deleteSelectedHistory() {
        if (history.isEmpty() || historyIndex < 0 || historyIndex >= history.size()) {
            status = "Локальной истории сканов пока нет.";
            return;
        }
        ScanHistoryEntry entry = history.get(historyIndex);
        try {
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
            boolean removed = store.remove(entry.localPath());
            if (!removed) {
                status = "Запись истории уже удалена.";
                loadHistory();
                return;
            }
            try {
                Files.deleteIfExists(Path.of(entry.localPath()));
            } catch (Exception ignored) {
                // Keep history cleanup successful even if the PNG was already gone or locked.
            }
            loadHistory();
            if (history.isEmpty()) {
                historyIndex = -1;
                if (isDraftMatchingEntry(entry)) {
                    draft = null;
                    upload = null;
                }
            } else if (historyIndex >= history.size()) {
                historyIndex = history.size() - 1;
            }
            if (isDraftMatchingEntry(entry)) {
                draft = null;
                upload = null;
            }
            updateButtonStates();
            status = "Скан удален из истории: " + entry.title();
        } catch (Exception e) {
            status = CompanionUiErrors.message("delete", e);
        }
    }

    private void requestDeleteSelectedHistory() {
        if (selectedHistoryEntry() == null) {
            status = "Локальной истории сканов пока нет.";
            return;
        }
        if (!deleteConfirmation.confirmOrArm()) {
            status = "Нажмите ещё раз для подтверждения";
            if (deleteButton != null) deleteButton.setMessage(CompanionI18n.text("Подтвердить удаление"));
            return;
        }
        deleteSelectedHistory();
    }

    private boolean isSelectedHistoryActiveDraft() {
        if (history.isEmpty() || historyIndex < 0 || historyIndex >= history.size()) return false;
        return isDraftMatchingEntry(history.get(historyIndex));
    }

    private boolean isDraftMatchingEntry(ScanHistoryEntry entry) {
        if (draft == null || entry == null) return false;
        return draft.title().equals(entry.title())
            && draft.source().equals(entry.source())
            && draft.wide() == entry.wide()
            && draft.tall() == entry.tall()
            && draft.missingMaps() == entry.missingMaps();
    }

    private String readableSource(String source) {
        return switch (source) {
            case "hand" -> "Из руки";
            case "frame" -> "Рамка";
            case "wall" -> "Стена";
            case "manual_wall" -> "По углам";
            default -> source;
        };
    }

    private void applyDraftTitle() {
        if (draft == null) {
            status = "Сначала отсканируйте карту.";
            return;
        }
        try {
            if (!syncDraftTitle(true)) return;
            syncTitleInput();
            updateButtonStates();
            status = "Название скана обновлено: " + draft.title();
        } catch (Exception e) {
            status = CompanionUiErrors.message("save", e);
        }
    }

    private void resetDraftTitle() {
        if (titleInput == null) return;
        titleInput.setValue(draft == null ? "" : draft.title());
        status = draft == null ? "Сначала отсканируйте карту." : "Название скана восстановлено.";
    }

    private boolean syncDraftTitle(boolean forceHistoryRename) throws Exception {
        if (draft == null) return false;
        String nextTitle = titleInput == null ? draft.title() : titleInput.getValue().trim();
        if (nextTitle.isEmpty()) {
            status = "Введите название скана.";
            return false;
        }
        if (nextTitle.equals(draft.title())) return true;

        MapScanDraft updatedDraft = new MapScanDraft(
            nextTitle,
            draft.source(),
            draft.wide(),
            draft.tall(),
            draft.missingMaps(),
            draft.pngBytes()
        );
        ScanHistoryEntry entry = selectedHistoryEntry();
        if (entry != null && isDraftMatchingEntry(entry)) {
            Path oldPath = Path.of(entry.localPath());
            Path newPath = uniqueScanPath(nextTitle, oldPath);
            if (!oldPath.toAbsolutePath().normalize().equals(newPath.toAbsolutePath().normalize())) {
                Files.createDirectories(newPath.getParent());
                if (Files.exists(oldPath)) {
                    Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
            store.rename(entry.localPath(), updatedDraft, newPath.toString());
            loadHistory();
            historyIndex = 0;
        } else if (forceHistoryRename) {
            Path newPath = uniqueScanPath(nextTitle, null);
            Files.createDirectories(newPath.getParent());
            Files.write(newPath, updatedDraft.pngBytes());
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().gameDirectory.toPath()));
            store.remember(updatedDraft, newPath);
            loadHistory();
            historyIndex = 0;
        }
        draft = updatedDraft;
        return true;
    }

    private Path currentDraftLocalPath() {
        ScanHistoryEntry entry = selectedHistoryEntry();
        if (entry != null && isDraftMatchingEntry(entry)) {
            return Path.of(entry.localPath());
        }
        return uniqueScanPath(draft == null ? "scan" : draft.title(), null);
    }

    private Path uniqueScanPath(String title, Path preferredExistingPath) {
        Path scansDir = client().gameDirectory.toPath().resolve("mapkluss").resolve("scans");
        String slug = SafeNames.slug(title);
        Path candidate = scansDir.resolve(slug + ".png");
        if (preferredExistingPath != null && preferredExistingPath.toAbsolutePath().normalize().equals(candidate.toAbsolutePath().normalize())) {
            return candidate;
        }
        if (!Files.exists(candidate)) return candidate;
        int suffix = 2;
        while (true) {
            Path next = scansDir.resolve(slug + "_" + suffix + ".png");
            if (preferredExistingPath != null && preferredExistingPath.toAbsolutePath().normalize().equals(next.toAbsolutePath().normalize())) {
                return next;
            }
            if (!Files.exists(next)) return next;
            suffix++;
        }
    }

    private void syncTitleInput() {
        pendingTitle = draft == null ? "" : draft.title();
        if (titleInput != null) {
            titleInput.setValue(draft == null ? "" : draft.title());
        }
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
    }

    private void runOnClient(ScreenRequestGate.Token request, Runnable task) {
        client().execute(() -> {
            if (requests.isCurrent(request) && client().gui.screen() == this) task.run();
        });
    }

    private void expireSessionLocally(ScreenRequestGate.Token request, String nextStatus) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            CompanionAuthSupport.clearSessionQuietly(runtime);
        } catch (Exception ignored) {
        }
        runOnClient(request, () -> status = nextStatus);
    }

    private record ActiveScanTarget(String importId, String localPath, String title) {
    }
}
