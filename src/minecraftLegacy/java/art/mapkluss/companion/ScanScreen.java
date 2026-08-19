package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanScreen extends Screen {
    private static final int SIDE_RAIL_WIDTH = 138;
    private static final int SIDE_RAIL_GAP = 22;

    private final Screen parent;
    private TextFieldWidget titleInput;
    private ClickableWidget savePngButton;
    private ClickableWidget uploadButton;
    private ClickableWidget refreshImportButton;
    private ClickableWidget loadButton;
    private ClickableWidget deleteButton;
    private ClickableWidget folderButton;
    private ClickableWidget artButton;
    private ClickableWidget editorButton;
    private ClickableWidget cloudButton;
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
        super(Text.literal("Скан MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        requests.attach();
        clearChildren();
        loadHistory();
        CompanionUiLayout.Shell shell = scanShell();
        CompanionUiLayout.Rect work = scanWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        int gap = 6;
        int applyButtonWidth = Math.max(72, textRenderer.getWidth(CompanionI18n.text("Применить")) + 18);
        int resetButtonWidth = Math.max(52, textRenderer.getWidth(CompanionI18n.text("Сброс")) + 18);
        int titleWidth = Math.max(96, panelWidth - applyButtonWidth - resetButtonWidth - gap * 2);
        titleInput = new TextFieldWidget(textRenderer, left, work.y(), titleWidth, 22, CompanionI18n.text("Название скана"));
        titleInput.setMaxLength(120);
        titleInput.setText(draft == null ? "" : draft.title());
        addDrawableChild(titleInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Применить"), button -> applyDraftTitle()).gold()
            .action("scan.title_apply")
            .tooltip(CompanionI18n.text("Применить название скана"))
            .dimensions(left + titleWidth + gap, work.y(), applyButtonWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сброс"), button -> resetDraftTitle())
            .action("scan.title_reset")
            .dimensions(left + titleWidth + applyButtonWidth + gap * 2, work.y(), resetButtonWidth, 22).build());

        addScanModeControls(work);
        addPagedActionControls(left, panelWidth, gap);
        addNavigationControls(shell);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
        setFocused(null);
        titleInput.setFocused(false);
        updateButtonStates();
    }

    @Override
    public void removed() {
        requests.detach();
        super.removed();
    }

    private void addPagedActionControls(int left, int panelWidth, int gap) {
        CompanionUiLayout.Rect work = scanWork(scanShell());
        if (work.height() < 120) return;
        String[] groups = {"Результат", "История", "Открыть"};
        int tabWidth = Math.max(46, (panelWidth - gap * 2) / 3);
        int tabsY = actionTabsY();
        for (int i = 0; i < groups.length; i++) {
            final int page = i;
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text(groups[i]), button -> {
                    actionPage = page;
                    deleteConfirmation.reset();
                    init();
                }).action(CompanionActionInventory.scanTabAction(i)).selected(actionPage == i).dimensions(left + (tabWidth + gap) * i, tabsY,
                    i == groups.length - 1 ? panelWidth - (tabWidth + gap) * i : tabWidth, 20).build());
        }
        int rowY = actionTop();
        if (actionPage == 0) {
            int w = Math.max(36, (panelWidth - gap * 4) / 5);
            addDrawableChild(MapKlussButton.builder(Text.literal("Угол A"), button -> setCornerA()).action("scan.corner_a").tooltip(CompanionI18n.text("Запомнить первый угол стены")).dimensions(left, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Угол B"), button -> setCornerB()).action("scan.corner_b").tooltip(CompanionI18n.text("Запомнить второй угол стены")).dimensions(left + w + gap, rowY, w, 20).build());
            savePngButton = addDrawableChild(MapKlussButton.builder(Text.literal("PNG"), button -> savePng()).action("scan.save_png").exportAction().tooltip(CompanionI18n.text("Сохранить PNG скана")).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
            uploadButton = addDrawableChild(MapKlussButton.builder(Text.literal("В облако"), button -> uploadScan()).action("scan.upload_cloud").special().tooltip(CompanionI18n.text("Загрузить скан в облако")).dimensions(left + (w + gap) * 3, rowY, w, 20).build());
            refreshImportButton = addDrawableChild(MapKlussButton.builder(Text.literal("Проверить"), button -> refreshImportStatus()).action("scan.import_refresh").tooltip(CompanionI18n.text("Проверить состояние импорта")).dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
            return;
        }
        if (actionPage == 1) {
            int w = Math.max(36, (panelWidth - gap * 4) / 5);
            addDrawableChild(MapKlussButton.builder(Text.literal("Пред."), button -> selectHistory(-1)).action("scan.history_previous").tooltip(CompanionI18n.text("Предыдущий скан")).dimensions(left, rowY, w, 20).build());
            loadButton = addDrawableChild(MapKlussButton.builder(Text.literal("Загрузить"), button -> loadSelectedHistory()).action("scan.history_load").tooltip(CompanionI18n.text("Загрузить выбранный скан")).dimensions(left + w + gap, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("След."), button -> selectHistory(1)).action("scan.history_next").tooltip(CompanionI18n.text("Следующий скан")).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
            deleteButton = addDrawableChild(MapKlussButton.builder(Text.literal(deleteConfirmation.armed() ? "Подтвердить удаление" : "Удалить"), button -> requestDeleteSelectedHistory()).danger()
                .action("scan.history_delete")
                .tooltip(CompanionI18n.text("Удалить скан из локальной истории")).navigationOrder(1000)
                .dimensions(left + (w + gap) * 3, rowY, w, 20).build());
            folderButton = addDrawableChild(MapKlussButton.builder(Text.literal("Папка"), button -> openLocalScanPath()).action("scan.open_folder").tooltip(CompanionI18n.text("Открыть папку скана")).dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
            return;
        }
        int w = Math.max(52, (panelWidth - gap * 2) / 3);
        artButton = addDrawableChild(MapKlussButton.builder(Text.literal("Арт"), button -> openSavedArt()).action("scan.open_art").technical().dimensions(left, rowY, w, 20).build());
        editorButton = addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openEditor()).action("scan.open_editor").technical().dimensions(left + w + gap, rowY, w, 20).build());
        cloudButton = addDrawableChild(MapKlussButton.builder(Text.literal("Облако"), button -> openCloud()).action("scan.open_cloud").technical().dimensions(left + (w + gap) * 2, rowY, panelWidth - (w + gap) * 2, 20).build());
    }

    private void addScanModeControls(CompanionUiLayout.Rect work) {
        int gap = 6;
        int y = work.y() + 30;
        int w = Math.max(42, (work.width() - gap * 3) / 4);
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Из руки"), button -> scanHand())
            .action("scan.hand")
            .tooltip(CompanionI18n.text("Сканировать карту в руке")).dimensions(work.x(), y, w, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Рамка"), button -> scanFrame())
            .action("scan.frame")
            .tooltip(CompanionI18n.text("Сканировать рамку под прицелом")).dimensions(work.x() + w + gap, y, w, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Стена"), button -> scanWall())
            .action("scan.wall")
            .tooltip(CompanionI18n.text("Найти всю стену карт автоматически")).dimensions(work.x() + (w + gap) * 2, y, w, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("По углам"), button -> scanManualWall())
            .action("scan.corners")
            .tooltip(CompanionI18n.text("Сканировать область между углами A и B")).dimensions(work.x() + (w + gap) * 3, y, work.width() - (w + gap) * 3, 22).build());
    }

    private int actionTabsY() {
        CompanionUiLayout.Rect work = scanWork(scanShell());
        return work.bottom() - 50;
    }

    private void addSideRailControls(int railLeft) {
        int gap = 4;
        int halfWidth = (SIDE_RAIL_WIDTH - gap) / 2;

        addDrawableChild(MapKlussButton.builder(Text.literal("Рука"), button -> scanHand())
            .action("scan.hand")
            .dimensions(railLeft, 78, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Рамка"), button -> scanFrame())
            .action("scan.frame")
            .dimensions(railLeft + halfWidth + gap, 78, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Стена"), button -> scanWall())
            .action("scan.wall")
            .dimensions(railLeft, 104, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Вручную"), button -> scanManualWall())
            .action("scan.corners")
            .dimensions(railLeft + halfWidth + gap, 104, halfWidth, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Угол A"), button -> setCornerA())
            .action("scan.corner_a")
            .dimensions(railLeft, 162, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Угол B"), button -> setCornerB())
            .action("scan.corner_b")
            .dimensions(railLeft + halfWidth + gap, 162, halfWidth, 20).build());
        savePngButton = addDrawableChild(MapKlussButton.builder(Text.literal("Сохранить PNG"), button -> savePng()).exportAction()
            .action("scan.save_png")
            .dimensions(railLeft, 188, SIDE_RAIL_WIDTH, 20).build());
        uploadButton = addDrawableChild(MapKlussButton.builder(Text.literal("В облако"), button -> uploadScan()).special()
            .action("scan.upload_cloud")
            .dimensions(railLeft, 214, SIDE_RAIL_WIDTH, 20).build());
        refreshImportButton = addDrawableChild(MapKlussButton.builder(Text.literal("Проверить импорт"), button -> refreshImportStatus())
            .action("scan.import_refresh")
            .dimensions(railLeft, 240, SIDE_RAIL_WIDTH, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Пред."), button -> selectHistory(-1))
            .action("scan.history_previous")
            .dimensions(railLeft, 298, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("След."), button -> selectHistory(1))
            .action("scan.history_next")
            .dimensions(railLeft + halfWidth + gap, 298, halfWidth, 20).build());
        loadButton = addDrawableChild(MapKlussButton.builder(Text.literal("Загрузить"), button -> loadSelectedHistory())
            .action("scan.history_load")
            .dimensions(railLeft, 324, SIDE_RAIL_WIDTH, 20).build());
        deleteButton = addDrawableChild(MapKlussButton.builder(Text.literal("Удалить"), button -> deleteSelectedHistory()).danger()
            .action("scan.history_delete")
            .dimensions(railLeft, 350, halfWidth, 20).build());
        folderButton = addDrawableChild(MapKlussButton.builder(Text.literal("Папка"), button -> openLocalScanPath())
            .action("scan.open_folder")
            .dimensions(railLeft + halfWidth + gap, 350, halfWidth, 20).build());

        int openY = Math.max(390, height - 104);
        artButton = addDrawableChild(MapKlussButton.builder(Text.literal("Арт"), button -> openSavedArt()).technical()
            .action("scan.open_art")
            .dimensions(railLeft, openY, halfWidth, 20).build());
        editorButton = addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openEditor()).technical()
            .action("scan.open_editor")
            .dimensions(railLeft + halfWidth + gap, openY, halfWidth, 20).build());
        cloudButton = addDrawableChild(MapKlussButton.builder(Text.literal("Облако"), button -> openCloud()).technical()
            .action("scan.open_cloud")
            .dimensions(railLeft, openY + 26, halfWidth, 20).build());
    }

    private int actionTop() {
        return actionTabsY() + 24;
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
                    if (client().currentScreen == this) updateButtonStates();
                });
            }
        });
    }

    private void openCloud() {
        ScanHistoryEntry selectedEntry = selectedHistoryEntry();
        if (selectedEntry != null && selectedEntry.hasCreatedArt()) {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                Util.getOperatingSystem().open(runtime.config().siteUri("/art/" + selectedEntry.createdArtId()));
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
                    Util.getOperatingSystem().open(runtime.config().siteUri(finalPath));
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
            client().setScreen(new CompanionArtScreen(this, selectedEntry.createdArtId(), selectedEntry.title()));
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
                    client().setScreen(new CompanionArtScreen(this, details.createdArtId(), requestedTitle));
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
            Util.getOperatingSystem().open(runtime.config().siteUri("/?companionImport=" + importId));
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
                target = client().runDirectory.toPath().resolve("mapkluss").resolve("scans");
                Files.createDirectories(target);
            }
            Util.getOperatingSystem().open(target.toUri());
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.SCAN, CompanionI18n.translate("Скан"), status), false
        );
        CompanionUiLayout.Rect work = scanWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        int detailBottom = detailBottom();
        if (draft == null && history.isEmpty()) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                "Сканов пока нет",
                "Выбери карту в руке, рамку или стену с картами",
                left,
                work.y() + 70,
                panelWidth,
                Math.max(40, detailBottom - work.y() - 70)
            );
        }
        if (draft != null) {
            String size = (draft.wide() * 128) + "x" + (draft.tall() * 128) + " PNG";
            String warning = draft.missingMaps() == 0 ? "" : " / пропущено " + draft.missingMaps();
            drawDetailLine(context, draft.title() + "  " + size + warning, work.y() + 70, MapKlussUi.WHITE, detailBottom);
        }
        if (cornerA != null || cornerB != null) {
            String a = cornerA == null ? "A: нет" : "A: " + cornerA.label();
            String b = cornerB == null ? "B: нет" : "B: " + cornerB.label();
            drawDetailLine(context, a + " / " + b, work.y() + 84, MapKlussUi.MUTED, detailBottom);
        }
        if (!history.isEmpty() && historyIndex >= 0 && historyIndex < history.size()) {
            ScanHistoryEntry entry = history.get(historyIndex);
            String imported = entry.hasImport() ? " / в облаке" : "";
            String active = isSelectedHistoryActiveDraft() ? " / активный" : "";
            drawDetailLine(
                context,
                "История " + (historyIndex + 1) + "/" + history.size() + ": " + entry.title() + imported + active,
                work.y() + 102,
                isSelectedHistoryActiveDraft() ? MapKlussUi.ACCENT : MapKlussUi.MUTED,
                detailBottom
            );
            String sourceLine = "Источник: " + readableSource(entry.source()) + " / " + entry.wide() + "x" + entry.tall();
            if (entry.missingMaps() > 0) {
                sourceLine += " / пропущено " + entry.missingMaps();
            }
            drawDetailLine(context, sourceLine, work.y() + 116, MapKlussUi.MUTED, detailBottom);

            String localFile = Path.of(entry.localPath()).getFileName().toString();
            drawDetailLine(context, "Локальный файл: " + localFile, work.y() + 130, MapKlussUi.CYAN, detailBottom);

            String cloudLine = entry.hasImport()
                ? "Импорт в облаке: " + entry.importId()
                : "Импорт в облаке: еще не загружен";
            drawDetailLine(context, cloudLine, work.y() + 144, entry.hasImport() ? MapKlussUi.ACCENT : MapKlussUi.GOLD, detailBottom);
            if (entry.hasImport()) {
                String artLine = entry.hasCreatedArt()
                    ? "Сохраненный арт: " + entry.createdArtId()
                    : "Сохраненный арт: еще не создан";
                drawDetailLine(context, artLine, work.y() + 158, entry.hasCreatedArt() ? MapKlussUi.ACCENT : MapKlussUi.GOLD, detailBottom);
            }
        }
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.SCAN);
    }

    private void drawActionGroups(DrawContext context) {
        // Compact actions use selected group tabs, so no duplicate caption strips are needed.
    }

    private void drawSideRailSections(DrawContext context, int railLeft) {
        MapKlussUi.drawSectionAt(context, textRenderer, "Скан", railLeft, SIDE_RAIL_WIDTH, 58, 76);
        MapKlussUi.drawSectionAt(context, textRenderer, "Результат", railLeft, SIDE_RAIL_WIDTH, 142, 104);
        MapKlussUi.drawSectionAt(context, textRenderer, "История", railLeft, SIDE_RAIL_WIDTH, 278, 100);
        int openY = Math.max(370, height - 124);
        MapKlussUi.drawSectionAt(context, textRenderer, "Открыть", railLeft, SIDE_RAIL_WIDTH, openY, Math.max(76, height - openY - 12));
    }

    private int detailBottom() {
        return actionTabsY() - 8;
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return false;
    }

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return scanWork(scanShell()).x();
    }

    private void drawDetailLine(DrawContext context, String value, int y, int color, int detailBottom) {
        if (y + 9 > detailBottom) return;
        CompanionUiLayout.Rect work = scanWork(scanShell());
        int panelWidth = work.width();
        int left = work.x();
        MapKlussUi.drawCenteredIn(context, textRenderer, value, left + panelWidth / 2, y, panelWidth - 14, color);
    }

    private CompanionUiLayout.Shell scanShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect scanWork(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 14, content.y() + 12, Math.max(1, content.width() - 28), Math.max(1, content.height() - 24));
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
            case LIBRARY -> client().setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().setScreen(new LensScreen(this));
            case SCAN -> { }
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> client().setScreen(new CompanionAccountScreen(this));
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
        return client().runDirectory.toPath().resolve("mapkluss").resolve("scans").resolve(SafeNames.slug(title) + ".png");
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
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
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
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
        return store.rememberUpload(requestedDraft, output, response);
    }

    private void loadHistory() {
        try {
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
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
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
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
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
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
            case "hand" -> "рука";
            case "frame" -> "рамка";
            case "wall" -> "стена";
            case "manual_wall" -> "ручная стена";
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
        titleInput.setText(draft == null ? "" : draft.title());
        status = draft == null ? "Сначала отсканируйте карту." : "Название скана восстановлено.";
    }

    private boolean syncDraftTitle(boolean forceHistoryRename) throws Exception {
        if (draft == null) return false;
        String nextTitle = titleInput == null ? draft.title() : titleInput.getText().trim();
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
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
            store.rename(entry.localPath(), updatedDraft, newPath.toString());
            loadHistory();
            historyIndex = 0;
        } else if (forceHistoryRename) {
            Path newPath = uniqueScanPath(nextTitle, null);
            Files.createDirectories(newPath.getParent());
            Files.write(newPath, updatedDraft.pngBytes());
            ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
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
        Path scansDir = client().runDirectory.toPath().resolve("mapkluss").resolve("scans");
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
        if (titleInput != null) {
            titleInput.setText(draft == null ? "" : draft.title());
        }
    }

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private void runOnClient(Runnable task) {
        client().execute(task);
    }

    private void runOnClient(ScreenRequestGate.Token request, Runnable task) {
        client().execute(() -> {
            if (requests.isCurrent(request) && client().currentScreen == this) task.run();
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
