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

public final class ScanScreen extends Screen {
    private static final int PANEL_WIDTH = 500;
    private static final int SECTION_WIDTH = 500;
    private static final int SIDE_RAIL_WIDTH = 138;
    private static final int SIDE_RAIL_GAP = 22;
    private static final int ACTION_ROWS = 4;
    private static final int ACTION_ROW_HEIGHT = 34;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int ACTION_BOTTOM_MARGIN = 32;
    private static final int MIN_ACTION_TOP = 88;

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

    public ScanScreen(Screen parent) {
        super(Text.literal("Скан MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        loadHistory();
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int gap = 4;
        int smallButtonWidth = Math.max(52, Math.min(72, (panelWidth - gap * 2) / 6));
        int titleWidth = Math.max(96, panelWidth - smallButtonWidth * 2 - gap * 2);
        titleInput = new TextFieldWidget(textRenderer, left, 76, titleWidth, 20, CompanionI18n.text("Название скана"));
        titleInput.setMaxLength(120);
        titleInput.setText(draft == null ? "" : draft.title());
        addDrawableChild(titleInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Применить"), button -> applyDraftTitle()).gold()
            .tooltip(CompanionI18n.text("Применить название скана"))
            .dimensions(left + titleWidth + gap, 76, smallButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сброс"), button -> resetDraftTitle())
            .dimensions(left + titleWidth + smallButtonWidth + gap * 2, 76, smallButtonWidth, 20).build());

        addPagedActionControls(left, panelWidth, gap);
        addDrawableChild(MapKlussUi.languageButton(this));
        addDrawableChild(MapKlussUi.backButton(this, parent, left));
        setFocused(null);
        titleInput.setFocused(false);
        updateButtonStates();
    }

    private void addPagedActionControls(int left, int panelWidth, int gap) {
        if (height < 178) return;
        String[] groups = {"Скан", "Результат", "История", "Открыть"};
        int tabWidth = Math.max(46, (panelWidth - gap * 3) / 4);
        int tabsY = actionTabsY();
        for (int i = 0; i < groups.length; i++) {
            final int page = i;
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text(groups[i]), button -> {
                    actionPage = page;
                    deleteConfirmation.reset();
                    init();
                }).selected(actionPage == i).dimensions(left + (tabWidth + gap) * i, tabsY,
                    i == groups.length - 1 ? panelWidth - (tabWidth + gap) * i : tabWidth, 20).build());
        }
        int rowY = actionTop();
        if (actionPage == 0) {
            int w = Math.max(48, (panelWidth - gap * 3) / 4);
            addDrawableChild(MapKlussButton.builder(Text.literal("Из руки"), button -> scanHand()).tooltip(CompanionI18n.text("Сканировать карту в руке")).dimensions(left, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Одна рамка"), button -> scanFrame()).tooltip(CompanionI18n.text("Сканировать рамку под прицелом")).dimensions(left + w + gap, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Вся стена"), button -> scanWall()).tooltip(CompanionI18n.text("Найти всю стену карт автоматически")).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("По углам"), button -> scanManualWall()).tooltip(CompanionI18n.text("Сканировать область между углами A и B")).dimensions(left + (w + gap) * 3, rowY, panelWidth - (w + gap) * 3, 20).build());
            return;
        }
        if (actionPage == 1) {
            int w = Math.max(36, (panelWidth - gap * 4) / 5);
            addDrawableChild(MapKlussButton.builder(Text.literal("Угол A"), button -> setCornerA()).tooltip(CompanionI18n.text("Запомнить первый угол стены")).dimensions(left, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Угол B"), button -> setCornerB()).tooltip(CompanionI18n.text("Запомнить второй угол стены")).dimensions(left + w + gap, rowY, w, 20).build());
            savePngButton = addDrawableChild(MapKlussButton.builder(Text.literal("PNG"), button -> savePng()).gold().tooltip(CompanionI18n.text("Сохранить PNG скана")).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
            uploadButton = addDrawableChild(MapKlussButton.builder(Text.literal("В облако"), button -> uploadScan()).gold().tooltip(CompanionI18n.text("Загрузить скан в облако")).dimensions(left + (w + gap) * 3, rowY, w, 20).build());
            refreshImportButton = addDrawableChild(MapKlussButton.builder(Text.literal("Проверить"), button -> refreshImportStatus()).tooltip(CompanionI18n.text("Проверить состояние импорта")).dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
            return;
        }
        if (actionPage == 2) {
            int w = Math.max(36, (panelWidth - gap * 4) / 5);
            addDrawableChild(MapKlussButton.builder(Text.literal("Пред."), button -> selectHistory(-1)).tooltip(CompanionI18n.text("Предыдущий скан")).dimensions(left, rowY, w, 20).build());
            loadButton = addDrawableChild(MapKlussButton.builder(Text.literal("Загрузить"), button -> loadSelectedHistory()).tooltip(CompanionI18n.text("Загрузить выбранный скан")).dimensions(left + w + gap, rowY, w, 20).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("След."), button -> selectHistory(1)).tooltip(CompanionI18n.text("Следующий скан")).dimensions(left + (w + gap) * 2, rowY, w, 20).build());
            deleteButton = addDrawableChild(MapKlussButton.builder(Text.literal(deleteConfirmation.armed() ? "Подтвердить удаление" : "Удалить"), button -> requestDeleteSelectedHistory()).danger()
                .tooltip(CompanionI18n.text("Удалить скан из локальной истории")).navigationOrder(1000)
                .dimensions(left + (w + gap) * 3, rowY, w, 20).build());
            folderButton = addDrawableChild(MapKlussButton.builder(Text.literal("Папка"), button -> openLocalScanPath()).tooltip(CompanionI18n.text("Открыть папку скана")).dimensions(left + (w + gap) * 4, rowY, panelWidth - (w + gap) * 4, 20).build());
            return;
        }
        int w = Math.max(52, (panelWidth - gap * 2) / 3);
        artButton = addDrawableChild(MapKlussButton.builder(Text.literal("Арт"), button -> openSavedArt()).gold().dimensions(left, rowY, w, 20).build());
        editorButton = addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openEditor()).gold().dimensions(left + w + gap, rowY, w, 20).build());
        cloudButton = addDrawableChild(MapKlussButton.builder(Text.literal("Облако"), button -> openCloud()).gold().dimensions(left + (w + gap) * 2, rowY, panelWidth - (w + gap) * 2, 20).build());
    }

    private int actionTabsY() {
        return Math.max(108, height - 78);
    }

    private void addSideRailControls(int railLeft) {
        int gap = 4;
        int halfWidth = (SIDE_RAIL_WIDTH - gap) / 2;

        addDrawableChild(MapKlussButton.builder(Text.literal("Рука"), button -> scanHand())
            .dimensions(railLeft, 78, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Рамка"), button -> scanFrame())
            .dimensions(railLeft + halfWidth + gap, 78, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Стена"), button -> scanWall())
            .dimensions(railLeft, 104, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Вручную"), button -> scanManualWall())
            .dimensions(railLeft + halfWidth + gap, 104, halfWidth, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Угол A"), button -> setCornerA())
            .dimensions(railLeft, 162, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Угол B"), button -> setCornerB())
            .dimensions(railLeft + halfWidth + gap, 162, halfWidth, 20).build());
        savePngButton = addDrawableChild(MapKlussButton.builder(Text.literal("Сохранить PNG"), button -> savePng()).gold()
            .dimensions(railLeft, 188, SIDE_RAIL_WIDTH, 20).build());
        uploadButton = addDrawableChild(MapKlussButton.builder(Text.literal("В облако"), button -> uploadScan()).gold()
            .dimensions(railLeft, 214, SIDE_RAIL_WIDTH, 20).build());
        refreshImportButton = addDrawableChild(MapKlussButton.builder(Text.literal("Проверить импорт"), button -> refreshImportStatus())
            .dimensions(railLeft, 240, SIDE_RAIL_WIDTH, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Пред."), button -> selectHistory(-1))
            .dimensions(railLeft, 298, halfWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("След."), button -> selectHistory(1))
            .dimensions(railLeft + halfWidth + gap, 298, halfWidth, 20).build());
        loadButton = addDrawableChild(MapKlussButton.builder(Text.literal("Загрузить"), button -> loadSelectedHistory())
            .dimensions(railLeft, 324, SIDE_RAIL_WIDTH, 20).build());
        deleteButton = addDrawableChild(MapKlussButton.builder(Text.literal("Удалить"), button -> deleteSelectedHistory()).danger()
            .dimensions(railLeft, 350, halfWidth, 20).build());
        folderButton = addDrawableChild(MapKlussButton.builder(Text.literal("Папка"), button -> openLocalScanPath())
            .dimensions(railLeft + halfWidth + gap, 350, halfWidth, 20).build());

        int openY = Math.max(390, height - 104);
        artButton = addDrawableChild(MapKlussButton.builder(Text.literal("Арт"), button -> openSavedArt()).gold()
            .dimensions(railLeft, openY, halfWidth, 20).build());
        editorButton = addDrawableChild(MapKlussButton.builder(Text.literal("Редактор"), button -> openEditor()).gold()
            .dimensions(railLeft + halfWidth + gap, openY, halfWidth, 20).build());
        cloudButton = addDrawableChild(MapKlussButton.builder(Text.literal("Облако"), button -> openCloud()).gold()
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
            cornerB = MapScanService.captureTargetCorner(client());
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
        status = "Загрузка скана в облако...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                ScanUploadResponse response = runtime.apiClient().uploadScan(draft);
                ScanHistoryEntry updatedEntry = attachUploadToHistory(response);
                ScanImportDetails details = null;
                try {
                    details = runtime.apiClient().scanImport(response.importId());
                    rememberImportDetails(details);
                } catch (Exception ignored) {
                    // Upload itself already succeeded; import details can be refreshed later from Cloud/Art actions.
                }
                ScanImportDetails finalDetails = details;
                runOnClient(() -> {
                    upload = response;
                    boolean hasCreatedArt = finalDetails != null && finalDetails.hasCreatedArt();
                    syncTitleInput();
                    updateButtonStates();
                    status = response.reused()
                        ? (hasCreatedArt
                            ? "Скан уже загружен и привязан к арту."
                            : "Скан уже загружен: " + response.importId())
                        : (hasCreatedArt
                            ? "Скан загружен, найден сохраненный арт."
                            : "Скан загружен: " + response.importId());
                    if (updatedEntry != null) {
                        loadHistory();
                        historyIndex = 0;
                    }
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("sync", e));
                }
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
        String importId = activeImportId();
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                String path = "/cloud";
                String nextStatus = "Открыта страница облака.";
                if (importId != null) {
                    ScanImportDetails details = runtime.apiClient().scanImport(importId);
                    rememberImportDetails(details);
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
                Util.getOperatingSystem().open(runtime.config().siteUri(finalPath));
                runOnClient(() -> status = finalStatus);
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("site", e));
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
        String importId = activeImportId();
        if (importId == null) {
            status = "Сначала загрузите скан, чтобы открыть арт.";
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                ScanImportDetails details = runtime.apiClient().scanImport(importId);
                rememberImportDetails(details);
                if (!details.hasCreatedArt()) {
                    runOnClient(() -> status = "Этот скан еще не сохранен как арт.");
                    return;
                }
                runOnClient(() -> {
                    client().setScreen(new CompanionArtScreen(this, details.createdArtId(), draft == null ? "Сканированный арт" : draft.title()));
                    status = "Открыт сохраненный арт в моде.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("site", e));
                }
            }
        });
    }

    private void openEditor() {
        String importId = activeImportId();
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
        String importId = activeImportId();
        if (importId == null) {
            status = "Сначала загрузите скан.";
            return;
        }
        status = "Проверка импорта в облаке...";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) {
                    runOnClient(() -> status = "Сначала войдите через код входа.");
                    return;
                }
                ScanImportDetails details = runtime.apiClient().scanImport(importId);
                rememberImportDetails(details);
                runOnClient(() -> {
                    updateButtonStates();
                    status = details.hasCreatedArt()
                        ? "Импорт привязан к сохраненному арту."
                        : "Импорт загружен, но еще не сохранен как арт.";
                });
            } catch (Exception e) {
                if (CompanionAuthSupport.isAuthFailure(e)) {
                    expireSessionLocally(CompanionAuthSupport.expiredMessage());
                } else {
                    runOnClient(() -> status = CompanionUiErrors.message("sync", e));
                }
            }
        });
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
        MapKlussUi.drawSectionAt(context, textRenderer, null, left, panelWidth, 64, 38);
        int detailsBottom = detailBottom();
        if (detailsBottom > 112) {
            MapKlussUi.drawSectionAt(context, textRenderer, null, left, panelWidth, 106, detailsBottom - 106);
        }
        if (height >= 178) {
            MapKlussUi.drawSectionAt(context, textRenderer, null, left, panelWidth, actionTabsY() - 5, Math.max(26, height - actionTabsY() - 29));
        }
        if (!sideRail) drawActionGroups(context);
        MapKlussUi.drawHeader(context, textRenderer, title.getString(), "", width, 14);
        MapKlussUi.drawStatusIn(context, textRenderer, status, left + panelWidth / 2, 36, panelWidth - 16);
        MapKlussUi.drawFieldLabel(context, textRenderer, "Название скана", left, 76, panelWidth);
        int detailBottom = detailBottom();
        if (draft == null && history.isEmpty()) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                "Сканов пока нет",
                "Выбери карту в руке, рамку или стену с картами",
                left,
                132,
                panelWidth,
                Math.max(40, detailBottom - 132)
            );
        }
        if (draft != null) {
            String size = (draft.wide() * 128) + "x" + (draft.tall() * 128) + " PNG";
            String warning = draft.missingMaps() == 0 ? "" : " / пропущено " + draft.missingMaps();
            drawDetailLine(context, draft.title() + "  " + size + warning, 120, MapKlussUi.WHITE, detailBottom);
        }
        if (cornerA != null || cornerB != null) {
            String a = cornerA == null ? "A: нет" : "A: " + cornerA.label();
            String b = cornerB == null ? "B: нет" : "B: " + cornerB.label();
            drawDetailLine(context, a + " / " + b, 132, MapKlussUi.MUTED, detailBottom);
        }
        if (!history.isEmpty() && historyIndex >= 0 && historyIndex < history.size()) {
            ScanHistoryEntry entry = history.get(historyIndex);
            String imported = entry.hasImport() ? " / в облаке" : "";
            String active = isSelectedHistoryActiveDraft() ? " / активный" : "";
            drawDetailLine(
                context,
                "История " + (historyIndex + 1) + "/" + history.size() + ": " + entry.title() + imported + active,
                148,
                isSelectedHistoryActiveDraft() ? MapKlussUi.ACCENT : MapKlussUi.MUTED,
                detailBottom
            );
            String sourceLine = "Источник: " + readableSource(entry.source()) + " / " + entry.wide() + "x" + entry.tall();
            if (entry.missingMaps() > 0) {
                sourceLine += " / пропущено " + entry.missingMaps();
            }
            drawDetailLine(context, sourceLine, 160, MapKlussUi.MUTED, detailBottom);

            String localFile = Path.of(entry.localPath()).getFileName().toString();
            drawDetailLine(context, "Локальный файл: " + localFile, 172, MapKlussUi.CYAN, detailBottom);

            String cloudLine = entry.hasImport()
                ? "Импорт в облаке: " + entry.importId()
                : "Импорт в облаке: еще не загружен";
            drawDetailLine(context, cloudLine, 184, entry.hasImport() ? MapKlussUi.ACCENT : MapKlussUi.GOLD, detailBottom);
            if (entry.hasImport()) {
                String artLine = entry.hasCreatedArt()
                    ? "Сохраненный арт: " + entry.createdArtId()
                    : "Сохраненный арт: еще не создан";
                drawDetailLine(context, artLine, 196, entry.hasCreatedArt() ? MapKlussUi.ACCENT : MapKlussUi.GOLD, detailBottom);
            }
        }
        super.render(context, mouseX, mouseY, delta);
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
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return sideRailLayout(panelWidth, left) ? height - 24 : actionTop() - 8;
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

    private void drawDetailLine(DrawContext context, String value, int y, int color, int detailBottom) {
        if (y + 9 > detailBottom) return;
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        MapKlussUi.drawCenteredIn(context, textRenderer, value, left + panelWidth / 2, y, panelWidth - 14, color);
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

    private ScanHistoryEntry attachUploadToHistory(ScanUploadResponse response) throws Exception {
        if (draft == null) return null;
        Path output = currentDraftLocalPath();
        if (!Files.exists(output)) {
            Files.createDirectories(output.getParent());
            Files.write(output, draft.pngBytes());
        }
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
        return store.attachUpload(output.toString(), response);
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
        if (upload != null && upload.importId() != null && !upload.importId().isBlank()) return upload.importId();
        ScanHistoryEntry entry = selectedHistoryEntry();
        if (entry != null) {
            String importId = entry.importId();
            if (importId != null && !importId.isBlank()) return importId;
        }
        return null;
    }

    private ScanHistoryEntry selectedHistoryEntry() {
        if (history.isEmpty() || historyIndex < 0 || historyIndex >= history.size()) return null;
        return history.get(historyIndex);
    }

    private void updateButtonStates() {
        ScanHistoryEntry entry = selectedHistoryEntry();
        boolean hasDraft = draft != null;
        boolean hasHistoryEntry = entry != null;
        boolean hasImport = activeImportId() != null;
        boolean hasCreatedArt = entry != null && entry.hasCreatedArt();

        if (savePngButton != null) savePngButton.active = hasDraft;
        if (uploadButton != null) uploadButton.active = hasDraft;
        if (refreshImportButton != null) refreshImportButton.active = hasImport;
        if (loadButton != null) loadButton.active = hasHistoryEntry;
        if (deleteButton != null) deleteButton.active = hasHistoryEntry;
        if (folderButton != null) folderButton.active = hasHistoryEntry || hasDraft;
        if (artButton != null) artButton.active = hasImport || hasCreatedArt;
        if (editorButton != null) editorButton.active = hasImport;
        if (cloudButton != null) cloudButton.active = hasImport || hasCreatedArt;
    }

    private void rememberImportDetails(ScanImportDetails details) throws Exception {
        if (details == null || details.importId() == null || details.importId().isBlank()) return;
        if (history.isEmpty() || historyIndex < 0 || historyIndex >= history.size()) return;
        ScanHistoryEntry entry = history.get(historyIndex);
        ScanHistoryStore store = ScanHistoryStore.load(LitematicaPaths.scanHistoryPath(client().runDirectory.toPath()));
        store.attachImportDetails(entry.localPath(), details);
        loadHistory();
        runOnClient(this::updateButtonStates);
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

    private void expireSessionLocally(String nextStatus) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            CompanionAuthSupport.clearSessionQuietly(runtime);
        } catch (Exception ignored) {
        }
        runOnClient(() -> status = nextStatus);
    }
}
