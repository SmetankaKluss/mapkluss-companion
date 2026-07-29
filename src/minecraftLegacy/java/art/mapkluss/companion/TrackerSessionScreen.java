package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import org.lwjgl.glfw.GLFW;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TrackerSessionScreen extends Screen {
    private static final int PANEL_WIDTH = 1120;
    private static final Map<String, SerialLatestQueue<TrackerMutation>> SESSION_MUTATIONS = new ConcurrentHashMap<>();
    private static final int MATERIAL_ROWS = 12;
    private static final int MATERIAL_Y = 168;
    private static final int MATERIAL_RENDER_Y = 172;
    private static final int MATERIAL_ROW_HEIGHT = 24;
    private static final int ACTION_ROWS = 2;
    private static final int ACTION_ROW_HEIGHT = 34;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int ACTION_BOTTOM_MARGIN = 32;
    private static final int MIN_ACTION_TOP = 210;
    private static final int SIDE_RAIL_WIDTH = 120;
    private static final int SIDE_RAIL_GAP = 22;
    private static final int[] STEP_OPTIONS = new int[] {1, 16, 64};

    private final Screen parent;
    private final String sessionId;
    private BuildSessionState session;
    private BuildSessionState previousSession;
    private String status = "";
    private int scrollOffset;
    private int stepIndex = 2;
    private String searchQuery = "";
    private boolean hideCompleted;
    private boolean loadFailed;
    private ClickableWidget artButton;
    private ClickableWidget stepButton;
    private ClickableWidget undoButton;
    private ClickableWidget hideDoneButton;
    private TextFieldWidget searchInput;
    private final List<ProgressInput> progressInputs = new ArrayList<>();
    private final ScreenRequestGate requests = new ScreenRequestGate();
    private final SerialLatestQueue<TrackerMutation> mutations;

    public TrackerSessionScreen(Screen parent, String sessionId) {
        super(Text.literal("Трекер MapKluss"));
        this.parent = parent;
        this.sessionId = sessionId;
        this.mutations = SESSION_MUTATIONS.computeIfAbsent(sessionId, ignored ->
            new SerialLatestQueue<>(mutation -> mutation.owner().performMutation(mutation), TrackerMutation::replacementKey)
        );
    }

    @Override
    protected void init() {
        requests.attach();
        clearChildren();
        rebuildLayout();
        load();
    }

    @Override
    public void removed() {
        requests.detach();
        super.removed();
    }

    private void load() {
        loadFailed = false;
        status = "Загрузка трекера...";
        rebuildLayout();
        ScreenRequestGate.Token request = requests.begin("load");
        mutations.submit(TrackerMutation.load(this, request));
    }

    private void switchMode(String mode) {
        if (session == null) return;
        BuildSessionState updated = session.withMode(mode);
        if (updated == session) {
            status = "Этот режим уже выбран.";
            return;
        }
        previousSession = session;
        session = updated;
        status = "Переключение режима...";
        rebuildLayout();
        mutations.submit(TrackerMutation.mode(this, requests.begin("tracker-sync"), mode));
    }

    private void rebuildLayout() {
        clearChildren();
        progressInputs.clear();
        scrollOffset = clampScrollOffset(scrollOffset, session);
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int gap = 4;
        addDrawableChild(MapKlussUi.languageButton(this));
        addDrawableChild(MapKlussUi.backButton(this, parent, left));
        if (loadFailed) {
            int buttonWidth = Math.max(80, (panelWidth - gap) / 2);
            int actionY = Math.max(64, height - 64);
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Повторить"), button -> load())
                .gold().dimensions(left, actionY, buttonWidth, 20).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Изменить UUID"), button -> client().setScreen(parent))
                .dimensions(left + buttonWidth + gap, actionY, panelWidth - buttonWidth - gap, 20).build());
            return;
        }
        int searchButtonWidth = 44;
        int hideButtonWidth = Math.max(54, Math.min(82, panelWidth / 4));
        int searchWidth = Math.max(44, panelWidth - searchButtonWidth * 2 - hideButtonWidth - gap * 3);
        searchInput = new TextFieldWidget(textRenderer, left, 102, searchWidth, 20, CompanionI18n.text("Поиск материалов"));
        searchInput.setMaxLength(80);
        searchInput.setText(searchQuery);
        addDrawableChild(searchInput);
        addDrawableChild(MapKlussButton.builder(Text.literal("Найти"), button -> applySearch())
            .dimensions(left + searchWidth + gap, 102, searchButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Сброс"), button -> clearSearch())
            .dimensions(left + searchWidth + searchButtonWidth + gap * 2, 102, searchButtonWidth, 20).build());
        hideDoneButton = addDrawableChild(MapKlussButton.builder(hideDoneButtonText(), button -> toggleHideCompleted())
            .selected(hideCompleted)
            .dimensions(left + searchWidth + searchButtonWidth * 2 + gap * 3, 102, hideButtonWidth, 20).build());

        if (sideRailLayout(panelWidth, left)) {
            addSideRailControls(panelWidth, left);
            setFocused(null);
            searchInput.setFocused(false);
            updateActionButtons();
            rebuildMaterialButtons();
            return;
        }

        int threeButtonWidth = Math.max(58, (panelWidth - gap * 2) / 3);
        int fourButtonWidth = Math.max(44, (panelWidth - gap * 3) / 4);
        int row1 = actionTop();
        int row2 = row1 + ACTION_ROW_HEIGHT;
        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт"), button -> openTrackerSite())
            .technical().dimensions(left, row1, threeButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> load())
            .technical().dimensions(left + threeButtonWidth + gap, row1, threeButtonWidth, 20).build());
        artButton = addDrawableChild(MapKlussButton.builder(Text.literal("Арт"), button -> openRelatedArt())
            .special().dimensions(left + (threeButtonWidth + gap) * 2, row1, panelWidth - (threeButtonWidth + gap) * 2, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Сбор"), button -> switchMode("gathering"))
            .selected(session == null || !"building".equals(session.mode()))
            .dimensions(left, row2, fourButtonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Стройка"), button -> switchMode("building"))
            .selected(session != null && "building".equals(session.mode()))
            .dimensions(left + fourButtonWidth + gap, row2, fourButtonWidth, 20).build());
        stepButton = addDrawableChild(MapKlussButton.builder(stepButtonText(), button -> cycleStep())
            .dimensions(left + (fourButtonWidth + gap) * 2, row2, fourButtonWidth, 20).build());
        undoButton = addDrawableChild(MapKlussButton.builder(Text.literal("Отмена"), button -> undoLastChange()).danger()
            .dimensions(left + (fourButtonWidth + gap) * 3, row2, fourButtonWidth, 20).build());
        setFocused(null);
        searchInput.setFocused(false);
        updateActionButtons();
        rebuildMaterialButtons();
    }

    private void addSideRailControls(int panelWidth, int left) {
        int x = sideRailLeft(panelWidth, left);
        int buttonWidth = SIDE_RAIL_WIDTH;
        addDrawableChild(MapKlussButton.builder(Text.literal("Сайт"), button -> openTrackerSite())
            .technical().dimensions(x, 80, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Обновить"), button -> load())
            .technical().dimensions(x, 106, buttonWidth, 20).build());
        artButton = addDrawableChild(MapKlussButton.builder(Text.literal("Арт"), button -> openRelatedArt())
            .special().dimensions(x, 132, buttonWidth, 20).build());

        addDrawableChild(MapKlussButton.builder(Text.literal("Сбор"), button -> switchMode("gathering"))
            .selected(session == null || !"building".equals(session.mode()))
            .dimensions(x, 206, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Стройка"), button -> switchMode("building"))
            .selected(session != null && "building".equals(session.mode()))
            .dimensions(x, 232, buttonWidth, 20).build());
        stepButton = addDrawableChild(MapKlussButton.builder(stepButtonText(), button -> cycleStep())
            .dimensions(x, 258, buttonWidth, 20).build());
        undoButton = addDrawableChild(MapKlussButton.builder(Text.literal("Отмена"), button -> undoLastChange()).danger()
            .dimensions(x, 284, buttonWidth, 20).build());

    }

    private void rebuildMaterialButtons() {
        if (session == null || session.materials() == null) return;
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int gap = 4;
        int actionsWidth = 44 + 28 + 38 + 38 + 24 + 36 + gap * 5;
        int actionX = left + panelWidth - actionsWidth;
        int y = MATERIAL_Y;
        Map<String, Integer> progress = session.currentProgress();
        List<BuildSessionMaterial> visibleMaterials = filteredMaterials();
        int rows = visibleMaterialRows();
        int start = scrollOffset;
        int end = Math.min(start + rows, visibleMaterials.size());
        for (int i = start; i < end; i++) {
            BuildSessionMaterial material = visibleMaterials.get(i);
            int visibleRow = i - start;
            int rowY = y + visibleRow * MATERIAL_ROW_HEIGHT;
            int value = progress.getOrDefault(material.nbtName(), 0);
            TextFieldWidget input = new TextFieldWidget(textRenderer, actionX, rowY + 1, 44, 18, CompanionI18n.text("Количество"));
            input.setMaxLength(7);
            input.setTextPredicate(text -> text == null || text.isBlank() || text.matches("\\d{0,7}"));
            input.setText(Integer.toString(value));
            progressInputs.add(new ProgressInput(material, input));
            addDrawableChild(input);
            addDrawableChild(MapKlussButton.builder(Text.literal("OK"), button -> applyManualProgress(material, input.getText())).gold()
                .dimensions(actionX + 44 + gap, rowY + 1, 28, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("-" + currentStep()), button -> changeProgress(material, -currentStep())).danger()
                .dimensions(actionX + 72 + gap * 2, rowY + 1, 38, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("+" + currentStep()), button -> changeProgress(material, currentStep())).gold()
                .dimensions(actionX + 110 + gap * 3, rowY + 1, 38, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("0"), button -> setProgress(material, 0)).danger()
                .dimensions(actionX + 148 + gap * 4, rowY + 1, 24, 18).build());
            addDrawableChild(MapKlussButton.builder(Text.literal("Все"), button -> setProgress(material, material.count())).gold()
                .dimensions(actionX + 172 + gap * 5, rowY + 1, 36, 18).build());
        }
    }

    private void applyManualProgress(BuildSessionMaterial material, String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isBlank()) {
            status = "Введите количество для " + material.displayName() + ".";
            return;
        }
        try {
            setProgress(material, Integer.parseInt(normalized));
        } catch (NumberFormatException e) {
            status = "Некорректное количество: " + normalized;
        }
    }

    private void changeProgress(BuildSessionMaterial material, int delta) {
        if (session == null) return;
        BuildSessionState nextSession = session.withProgress(material.nbtName(), delta);
        if (sameProgress(session, nextSession)) {
            status = "Без изменений: " + material.displayName() + ".";
            return;
        }
        previousSession = session;
        session = nextSession;
        status = ("building".equals(nextSession.mode()) ? "Постройка" : "Сбор") + " обновлен локально...";
        syncSession(nextSession);
    }

    private void setProgress(BuildSessionMaterial material, int value) {
        if (session == null) return;
        BuildSessionState nextSession = session.withAbsoluteProgress(material.nbtName(), value);
        if (sameProgress(session, nextSession)) {
            status = value <= 0
                ? material.displayName() + " уже сброшен."
                : material.displayName() + " уже заполнен.";
            return;
        }
        previousSession = session;
        session = nextSession;
        status = value <= 0
            ? "Сброшено локально: " + material.displayName() + "..."
            : "Заполнено локально: " + material.displayName() + "...";
        syncSession(nextSession);
    }

    private void undoLastChange() {
        if (previousSession == null) {
            status = "Отменять нечего.";
            return;
        }
        BuildSessionState restore = previousSession;
        BuildSessionState current = session;
        previousSession = current;
        session = restore;
        status = "Отмена последнего изменения...";
        rebuildLayout();
        if (!java.util.Objects.equals(current.mode(), restore.mode())) {
            mutations.submit(TrackerMutation.combined(this, requests.begin("tracker-sync"), restore.mode(), restore));
        } else {
            syncSession(restore);
        }
    }

    private void syncSession(BuildSessionState nextSession) {
        mutations.submit(TrackerMutation.progress(this, requests.begin("tracker-sync"), nextSession));
    }

    private void performMutation(TrackerMutation mutation) {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            if (!mutation.loadOnly() && mutation.mode() != null) {
                runtime.apiClient().switchTrackerMode(sessionId, mutation.mode());
            }
            if (!mutation.loadOnly() && mutation.session() != null) {
                BuildSessionState snapshot = mutation.session();
                JsonObject gathered = "building".equals(snapshot.mode()) ? null : toJson(snapshot.gathered());
                JsonObject placed = "building".equals(snapshot.mode()) ? toJson(snapshot.placed()) : null;
                runtime.apiClient().updateTracker(sessionId, gathered, placed);
            }
            BuildSessionState loaded = runtime.syncService().tracker(sessionId);
            try {
                rememberTrackerHistory(runtime, loaded);
            } catch (Exception cacheError) {
                MapKlussCompanionClient.LOGGER.debug("Could not cache tracker history.", cacheError);
            }
            runOnClient(mutation.request(), () -> {
                loadFailed = false;
                session = loaded;
                scrollOffset = clampScrollOffset(scrollOffset, loaded);
                status = mutation.loadOnly()
                    ? ""
                    : mutation.mode() == null ? "Синхронизировано." : "Режим: " + readableMode(loaded.mode());
                rebuildLayout();
            });
        } catch (Exception error) {
            if (CompanionAuthSupport.isAuthFailure(error)) {
                expireSessionLocally(mutation.request(), CompanionAuthSupport.expiredMessage());
            } else {
                runOnClient(mutation.request(), () -> {
                    if (mutation.loadOnly()) {
                        session = null;
                        loadFailed = true;
                    }
                    status = CompanionUiErrors.message(mutation.loadOnly() ? "tracker" : "sync", error);
                    rebuildLayout();
                });
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        boolean sideRail = sideRailLayout(panelWidth, left);
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 46, MapKlussUi.panelBottom(height));
        if (sideRail && !loadFailed) {
            int railLeft = sideRailLeft(panelWidth, left);
            MapKlussUi.drawPanelAt(context, railLeft - 8, railLeft + SIDE_RAIL_WIDTH + 8, 46, MapKlussUi.panelBottom(height));
            drawSideRailSections(context, railLeft);
        }
        if (!loadFailed) {
            MapKlussUi.drawSectionAt(context, textRenderer, null, left, panelWidth, 94, 34);
            MapKlussUi.drawSectionAt(context, textRenderer, "Материалы", left, panelWidth, MATERIAL_Y - 32, Math.max(46, materialTableBottomY() - MATERIAL_Y + 34));
            if (!sideRail) drawActionGroups(context);
        }
        MapKlussUi.drawHeader(context, textRenderer, title.getString(), "", width, 16);
        MapKlussUi.drawStatusIn(context, textRenderer, status, left + panelWidth / 2, 36, panelWidth - 18);
        if (loadFailed) {
            MapKlussUi.drawEmptyState(context, textRenderer, "Сессия не найдена", "Проверьте UUID и повторите", left, 86, panelWidth, Math.max(44, height - 158));
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        MapKlussUi.drawFieldLabel(context, textRenderer, "Поиск материалов", left, 102, panelWidth);
        if (session != null) {
            String titleLine = session.info() != null && session.info().title() != null && !session.info().title().isBlank()
                ? session.info().title()
                : sessionId;
            MapKlussUi.drawCenteredIn(context, textRenderer, titleLine, left + panelWidth / 2, 66, panelWidth - 12, MapKlussUi.CYAN);
            int done = "building".equals(session.mode()) ? session.placedBlocks() : session.gatheredBlocks();
            MapKlussUi.drawCenteredIn(
                context,
                textRenderer,
                "Режим: " + readableMode(session.mode()) + " / " + done + " / " + session.totalBlocks(),
                left + panelWidth / 2,
                80,
                panelWidth - 12,
                MapKlussUi.ACCENT
            );
            MapKlussUi.drawCenteredIn(
                context,
                textRenderer,
                pageSummary(),
                left + panelWidth / 2,
                142,
                panelWidth - 12,
                MapKlussUi.WHITE
            );
                int y = MATERIAL_RENDER_Y;
                if (session.materials() != null) {
                    int gap = 4;
                    int actionsWidth = 44 + 28 + 38 + 38 + 24 + 36 + gap * 5;
                    int actionX = left + panelWidth - actionsWidth;
                    int iconX = left + 4;
                    int nameX = left + 26;
                    int countX = Math.max(nameX + 84, actionX - 46);
                    int nameWidth = Math.max(72, countX - nameX - 8);
                    drawMaterialColumnHeaders(context, left, panelWidth, actionX, nameX, nameWidth, countX, actionsWidth);
                    List<BuildSessionMaterial> visibleMaterials = filteredMaterials();
                int rows = visibleMaterialRows();
                int start = scrollOffset;
                int end = Math.min(start + rows, visibleMaterials.size());
                for (int i = start; i < end; i++) {
                    BuildSessionMaterial material = visibleMaterials.get(i);
                    int visibleRow = i - start;
                    int rowY = y + visibleRow * MATERIAL_ROW_HEIGHT;
                    context.drawItem(CompanionMaterialIcons.stackFor(material), iconX, rowY + 1);
                    MapKlussUi.drawLeft(context, textRenderer, materialLabel(material), nameX, rowY + 4, nameWidth, MapKlussUi.MUTED);
                    MapKlussUi.drawLeft(context, textRenderer, "/" + material.count(), countX, rowY + 4, 42, MapKlussUi.WHITE);
                }
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawActionGroups(DrawContext context) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int row1 = actionTop();
        int row2 = row1 + ACTION_ROW_HEIGHT;
        drawActionLabel(context, left, panelWidth, row1, "СЕССИЯ");
        drawActionLabel(context, left, panelWidth, row2, "ПРОГРЕСС");
    }

    private void drawMaterialColumnHeaders(
        DrawContext context,
        int left,
        int panelWidth,
        int actionX,
        int nameX,
        int nameWidth,
        int countX,
        int actionsWidth
    ) {
        int y = MATERIAL_Y - 14;
        context.fill(left + 2, y - 3, left + panelWidth - 2, y - 2, 0x6650505D);
        MapKlussUi.drawLeft(context, textRenderer, "Материал", nameX, y, nameWidth, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "Всего", countX, y, 42, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "Свое", actionX, y, 44, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "OK", actionX + 48, y, 28, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "-" + currentStep(), actionX + 80, y, 38, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "+" + currentStep(), actionX + 122, y, 38, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "0", actionX + 164, y, 24, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, "Все", actionX + actionsWidth - 36, y, 36, MapKlussUi.CYAN);
    }

    private void drawSideRailSections(DrawContext context, int railLeft) {
        MapKlussUi.drawSectionAt(context, textRenderer, "Сессия", railLeft, SIDE_RAIL_WIDTH, 60, 104);
        MapKlussUi.drawSectionAt(context, textRenderer, "Прогресс", railLeft, SIDE_RAIL_WIDTH, 186, 132);
    }

    private void drawActionLabel(DrawContext context, int left, int panelWidth, int rowY, String label) {
        MapKlussUi.drawActionGroupLabel(context, textRenderer, label, left, panelWidth, rowY, ACTION_BUTTON_HEIGHT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isOverMaterialTable(mouseX, mouseY)) {
            clearTableFocus();
            scrollMaterials(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            for (ProgressInput progressInput : progressInputs) {
                if (progressInput.widget().isFocused()) {
                    applyManualProgress(progressInput.material(), progressInput.widget().getText());
                    return true;
                }
            }
        }
        return false;
    }

    private void applySearch() {
        searchQuery = searchInput == null ? "" : searchInput.getText().trim().toLowerCase();
        scrollOffset = 0;
        rebuildLayout();
    }

    private void clearSearch() {
        searchQuery = "";
        scrollOffset = 0;
        rebuildLayout();
    }

    private void toggleHideCompleted() {
        hideCompleted = !hideCompleted;
        scrollOffset = 0;
        rebuildLayout();
    }

    private void cycleStep() {
        stepIndex = (stepIndex + 1) % STEP_OPTIONS.length;
        rebuildLayout();
    }

    private void openRelatedArt() {
        if (session == null || session.art_id() == null || session.art_id().isBlank()) {
            status = "Трекер не привязан к арту.";
            return;
        }
        client().setScreen(new CompanionArtScreen(this, session.art_id(), fallbackTitle()));
    }

    private void openTrackerSite() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            Util.getOperatingSystem().open(runtime.config().siteUri("/build/" + sessionId));
            status = "Трекер открыт в браузере.";
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void updateActionButtons() {
        if (stepButton != null) {
            stepButton.setMessage(stepButtonText());
        }
        if (hideDoneButton != null) {
            hideDoneButton.setMessage(hideDoneButtonText());
        }
        if (undoButton != null) {
            undoButton.active = previousSession != null;
        }
        if (artButton != null) {
            artButton.active = session != null && session.art_id() != null && !session.art_id().isBlank();
        }
    }

    private Text stepButtonText() {
        return Text.literal("Шаг " + currentStep());
    }

    private Text hideDoneButtonText() {
        return Text.literal(hideCompleted ? "Показ." : "Скрыть");
    }

    private int currentStep() {
        return STEP_OPTIONS[stepIndex];
    }

    private int clampScrollOffset(int current, BuildSessionState state) {
        if (state == null) return 0;
        int visible = filteredMaterials(state).size();
        int rows = visibleMaterialRows();
        if (rows <= 0) return 0;
        int max = Math.max(0, visible - rows);
        return Math.max(0, Math.min(current, max));
    }

    private String pageSummary() {
        List<BuildSessionMaterial> visibleMaterials = filteredMaterials(session);
        if (visibleMaterials.isEmpty()) {
            return "Материалов нет.";
        }
        int rows = visibleMaterialRows();
        if (rows <= 0) return "Материалы 0 / " + visibleMaterials.size();
        int start = scrollOffset + 1;
        int end = Math.min(scrollOffset + rows, visibleMaterials.size());
        String filtered = searchQuery.isBlank() && !hideCompleted ? "" : " / фильтр";
        return "Материалы " + start + "-" + end + " / " + visibleMaterials.size() + filtered + " / шаг " + currentStep();
    }

    private int visibleMaterialRows() {
        return Math.max(0, Math.min(MATERIAL_ROWS, (materialTableBottomY() - MATERIAL_Y - 8) / MATERIAL_ROW_HEIGHT));
    }

    private int actionTop() {
        return CompanionLayout.actionTop(
            height,
            MIN_ACTION_TOP,
            ACTION_ROWS,
            ACTION_ROW_HEIGHT,
            ACTION_BUTTON_HEIGHT,
            ACTION_BOTTOM_MARGIN
        );
    }

    private boolean isOverMaterialTable(double mouseX, double mouseY) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return mouseX >= left - 6
            && mouseX <= left + panelWidth + 6
            && mouseY >= MATERIAL_Y - 4
            && mouseY <= materialTableBottomY();
    }

    private int materialTableBottomY() {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        return sideRailLayout(panelWidth, left) ? height - 24 : actionTop() - 18;
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

    private boolean scrollMaterials(double verticalAmount) {
        List<BuildSessionMaterial> visibleMaterials = filteredMaterials(session);
        int rows = visibleMaterialRows();
        if (rows <= 0) return false;
        if (visibleMaterials.size() <= rows) return false;
        int lines = Math.max(1, (int) Math.ceil(Math.abs(verticalAmount)));
        int delta = verticalAmount > 0 ? -lines : lines;
        int next = Math.max(0, Math.min(scrollOffset + delta, visibleMaterials.size() - rows));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        rebuildLayout();
        return true;
    }

    private String fallbackTitle() {
        if (session != null && session.info() != null && session.info().title() != null && !session.info().title().isBlank()) {
            return session.info().title();
        }
        return "Арт трекера";
    }

    private String readableMode(String mode) {
        if ("building".equals(mode)) return "стройка";
        if ("gathering".equals(mode)) return "сбор";
        return mode == null || mode.isBlank() ? "?" : mode;
    }

    private boolean sameProgress(BuildSessionState left, BuildSessionState right) {
        if (left == null || right == null) return false;
        return left.currentProgress().equals(right.currentProgress()) && String.valueOf(left.mode()).equals(String.valueOf(right.mode()));
    }

    private void rememberTrackerHistory(CompanionRuntime runtime, BuildSessionState loaded) {
        try {
            TrackerHistoryStore.load(LitematicaPaths.trackerHistoryPath(client().runDirectory.toPath())).remember(loaded);
        } catch (Exception ignored) {
        }
    }

    private List<BuildSessionMaterial> filteredMaterials() {
        return filteredMaterials(session);
    }

    private List<BuildSessionMaterial> filteredMaterials(BuildSessionState state) {
        if (state == null || state.materials() == null || state.materials().isEmpty()) return List.of();
        Map<String, Integer> progress = state.currentProgress();
        List<BuildSessionMaterial> filtered = new ArrayList<>();
        for (BuildSessionMaterial material : state.materials()) {
            if (hideCompleted && progress.getOrDefault(material.nbtName(), 0) >= material.count()) continue;
            if (!searchQuery.isBlank()) {
                String haystack = (materialLabel(material) + " " + material.displayName() + " " + material.nbtName()).toLowerCase();
                if (!haystack.contains(searchQuery)) continue;
            }
            filtered.add(material);
        }
        return filtered;
    }

    private String materialLabel(BuildSessionMaterial material) {
        String label = material.displayName();
        if (label == null || label.isBlank()) return material.nbtName();
        return label
            .replace(" (vertical)", " вертикально")
            .replace("(vertical)", "вертикально")
            .replace("Vertical", "вертикально");
    }

    private void clearTableFocus() {
        if (searchInput != null) searchInput.setFocused(false);
        for (ProgressInput progressInput : progressInputs) {
            progressInput.widget().setFocused(false);
        }
        setFocused(null);
    }

    private static JsonObject toJson(Map<String, Integer> values) {
        JsonObject json = new JsonObject();
        if (values == null) return json;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue());
        }
        return json;
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

    private record TrackerMutation(
        TrackerSessionScreen owner,
        ScreenRequestGate.Token request,
        String mode,
        BuildSessionState session,
        boolean loadOnly,
        String replacementKey
    ) {
        private static TrackerMutation load(TrackerSessionScreen owner, ScreenRequestGate.Token request) {
            return new TrackerMutation(owner, request, null, null, true, "load");
        }

        private static TrackerMutation mode(TrackerSessionScreen owner, ScreenRequestGate.Token request, String mode) {
            return new TrackerMutation(owner, request, mode, null, false, null);
        }

        private static TrackerMutation progress(
            TrackerSessionScreen owner,
            ScreenRequestGate.Token request,
            BuildSessionState session
        ) {
            return new TrackerMutation(owner, request, null, session, false, "progress");
        }

        private static TrackerMutation combined(
            TrackerSessionScreen owner,
            ScreenRequestGate.Token request,
            String mode,
            BuildSessionState session
        ) {
            return new TrackerMutation(owner, request, mode, session, false, null);
        }
    }

    private record ProgressInput(BuildSessionMaterial material, TextFieldWidget widget) {
    }
}
