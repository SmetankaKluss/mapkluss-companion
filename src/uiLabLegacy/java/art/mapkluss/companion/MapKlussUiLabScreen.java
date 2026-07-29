package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class MapKlussUiLabScreen extends Screen {
    private static final int TOOLBAR_TOP = 32;
    private static final int TOOLBAR_ROW_HEIGHT = 18;
    private static final int TOOLBAR_GAP = 4;
    private static final int FRAME_TOP = 78;

    private final Screen parent;
    private final MapKlussUiLabModel model;
    private final List<Bounds> guideBounds = new ArrayList<>();

    MapKlussUiLabScreen(Screen parent, MapKlussUiLabModel model) {
        super(Text.literal("MapKluss UI Lab"));
        this.parent = parent;
        this.model = model;
    }

    Screen parent() {
        return parent;
    }

    @Override
    protected void init() {
        clearChildren();
        guideBounds.clear();
        addToolbar();
        addFixtureActions();
    }

    private void addToolbar() {
        int left = 10;
        int widthAvailable = width - 20;
        int firstWidth = Math.max(72, (widthAvailable - TOOLBAR_GAP * 3) / 4);
        int x = left;
        addToolbarButton((model.english() ? "Screen · " : "Экран · ") + model.title(), x, TOOLBAR_TOP, firstWidth, model::nextPage);
        x += firstWidth + TOOLBAR_GAP;
        addToolbarButton((model.english() ? "State · " : "Состояние · ") + model.stateLabel(), x, TOOLBAR_TOP, firstWidth, model::nextState);
        x += firstWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "EN" : "RU", x, TOOLBAR_TOP, firstWidth, model::toggleLanguage);
        x += firstWidth + TOOLBAR_GAP;
        addToolbarButton((model.english() ? "Size · " : "Размер · ") + model.viewport().label(), x, TOOLBAR_TOP, width - 10 - x, model::nextViewport);

        int secondTop = TOOLBAR_TOP + TOOLBAR_ROW_HEIGHT + TOOLBAR_GAP;
        int secondWidth = Math.max(46, (widthAvailable - TOOLBAR_GAP * 7) / 8);
        x = left;
        addToolbarButton(model.english() ? "◀ screen" : "◀ экран", x, secondTop, secondWidth, model::previousPage);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "screen ▶" : "экран ▶", x, secondTop, secondWidth, model::nextPage);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "◀ state" : "◀ статус", x, secondTop, secondWidth, model::previousState);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "state ▶" : "статус ▶", x, secondTop, secondWidth, model::nextState);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.longCopy()
            ? (model.english() ? "Long copy" : "Длинный текст")
            : (model.english() ? "Short copy" : "Короткий текст"), x, secondTop, secondWidth, model::toggleLongCopy);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.guides()
            ? (model.english() ? "Bounds on" : "Рамки: вкл")
            : (model.english() ? "Bounds off" : "Рамки: выкл"), x, secondTop, secondWidth, model::toggleGuides);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "Reload" : "Обновить", x, secondTop, secondWidth, this::reloadResources);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "Capture" : "Снимок", x, secondTop, width - 10 - x, this::capture);
    }

    private void addToolbarButton(String label, int x, int y, int buttonWidth, Runnable action) {
        addDrawableChild(MapKlussButton.builder(Text.literal(label), button -> {
                action.run();
                client().setScreen(new MapKlussUiLabScreen(parent, model));
            })
            .technical()
            .dimensions(x, y, Math.max(24, buttonWidth), TOOLBAR_ROW_HEIGHT)
            .build());
        guideBounds.add(new Bounds(x, y, Math.max(24, buttonWidth), TOOLBAR_ROW_HEIGHT, "toolbar"));
    }

    private void addFixtureActions() {
        FixtureFrame frame = frame();
        int buttonY = frame.bottom() - 28;
        int gap = 6;
        int actionsWidth = Math.min(frame.width() - 28, 540);
        int buttonWidth = Math.max(70, (actionsWidth - gap) / 2);
        int left = frame.centerX() - actionsWidth / 2;
        MapKlussButton.Builder primary = MapKlussButton.builder(
            Text.literal(model.actionPrimary()),
            button -> model.notice(model.english() ? "Primary action" : "Основное действие")
        ).dimensions(left, buttonY, buttonWidth, 20).enabledWhen(model::actionsEnabled);
        switch (model.page()) {
            case ART, LENS, TWO_LAYER -> primary.special();
            case UPDATE -> primary.gold();
            default -> primary.selected(true);
        }
        addDrawableChild(primary.build());
        guideBounds.add(new Bounds(left, buttonY, buttonWidth, 20, "primary"));
        int secondLeft = left + buttonWidth + gap;
        MapKlussButton.Builder secondary = MapKlussButton.builder(
            Text.literal(model.actionSecondary()),
            button -> model.notice(model.english() ? "Secondary action" : "Дополнительное действие")
        ).dimensions(secondLeft, buttonY, buttonWidth, 20).enabledWhen(model::actionsEnabled);
        if (model.page() == MapKlussUiLabModel.Page.ART) {
            secondary.exportAction();
        } else {
            secondary.technical();
        }
        addDrawableChild(secondary.build());
        guideBounds.add(new Bounds(secondLeft, buttonY, buttonWidth, 20, "secondary"));
    }

    private void reloadResources() {
        model.notice(MapKlussUiResources.reloadDevelopmentSources()
            ? "JSON reloaded from src/main/resources"
            : "Reload failed · see log");
    }

    private void capture() {
        model.notice("Capturing…");
        CompletableFuture.runAsync(() -> {
            try {
                Path path = MapKlussUiLabCapture.capture(model);
                client().execute(() -> {
                    model.notice("Saved · " + path.getFileName());
                    client().setScreen(new MapKlussUiLabScreen(parent, model));
                });
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not capture MapKluss UI Lab screenshot.", error);
                client().execute(() -> {
                    model.notice("Capture failed · see log");
                    client().setScreen(new MapKlussUiLabScreen(parent, model));
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        MapKlussUi.drawHeader(
            context,
            textRenderer,
            "UI LAB · " + model.title(),
            "",
            width,
            11
        );
        FixtureFrame frame = frame();
        MapKlussUi.drawPanelAt(context, frame.left() - 8, frame.right() + 8, frame.top(), frame.bottom());
        renderFixture(context, frame);
        if (!model.notice().isBlank()) {
            MapKlussUi.drawCenteredIn(
                context,
                textRenderer,
                model.notice(),
                width / 2,
                frame.bottom() - 42,
                frame.width() - 30,
                MapKlussUi.CYAN
            );
        }
        super.render(context, mouseX, mouseY, delta);
        if (model.guides()) renderGuides(context, mouseX, mouseY);
    }

    private void renderFixture(DrawContext context, FixtureFrame frame) {
        int contentLeft = frame.left() + 12;
        int contentTop = frame.top() + 12;
        int contentWidth = frame.width() - 24;
        int contentBottom = frame.bottom() - 54;
        int contentHeight = Math.max(36, contentBottom - contentTop);
        MapKlussUi.drawSectionAt(
            context,
            textRenderer,
            model.title(),
            contentLeft,
            contentWidth,
            contentTop,
            contentHeight
        );
        MapKlussUi.drawRight(
            context,
            textRenderer,
            model.status(),
            contentLeft + contentWidth - 10,
            contentTop + 5,
            Math.max(70, contentWidth / 2),
            MapKlussUi.statusColor(model.status())
        );
        if (!model.populated()) {
            MapKlussUi.drawEmptyState(
                context,
                textRenderer,
                model.stateLabel(),
                model.emptyDetail(),
                contentLeft + 4,
                contentTop + 24,
                contentWidth - 8,
                contentHeight - 28
            );
            return;
        }

        int bodyTop = contentTop + 28;
        switch (model.page()) {
            case LIBRARY -> renderLibrary(context, contentLeft + 10, bodyTop, contentWidth - 20);
            case ART -> renderArt(context, contentLeft + 10, bodyTop, contentWidth - 20, contentBottom - bodyTop - 6);
            case COLLECTIONS -> renderCollections(context, contentLeft + 10, bodyTop, contentWidth - 20);
            case SCAN -> renderScan(context, contentLeft + 10, bodyTop, contentWidth - 20, contentBottom - bodyTop - 6);
            case LENS -> renderLens(context, contentLeft + 10, bodyTop, contentWidth - 20, contentBottom - bodyTop - 6);
            case TRACKER -> renderTracker(context, contentLeft + 10, bodyTop, contentWidth - 20);
            case LOGIN -> renderLogin(context, contentLeft + 10, bodyTop, contentWidth - 20);
            case TWO_LAYER -> renderTwoLayer(context, contentLeft + 10, bodyTop, contentWidth - 20);
            case UPDATE -> renderUpdate(context, contentLeft + 10, bodyTop, contentWidth - 20);
        }
    }

    private void renderLibrary(DrawContext context, int left, int top, int bodyWidth) {
        for (int row = 0; row < 3; row++) {
            int y = top + row * 48;
            MapKlussUi.drawDataStrip(context, left, left + bodyWidth, y, y + 42);
            MapKlussUi.drawPreviewWell(context, left + 6, y + 4, left + 40, y + 38);
            drawMiniPreview(context, left + 10, y + 8, 26, 26, row);
            MapKlussUi.drawLeft(
                context,
                textRenderer,
                row == 1 && model.longCopy()
                    ? "Очень длинное название арта для проверки узкого интерфейса и корректной обрезки"
                    : (model.english() ? "Local fixture art " : "Локальный пример ") + (row + 1),
                left + 50,
                y + 8,
                bodyWidth - 132,
                MapKlussUi.WHITE
            );
            MapKlussUi.drawLeft(
                context,
                textRenderer,
                (row + 1) + "×" + (row + 1) + " · 3D · MC 1.21.11",
                left + 50,
                y + 24,
                bodyWidth - 70,
                MapKlussUi.MUTED
            );
            MapKlussUi.drawRight(context, textRenderer, model.english() ? "READY" : "ГОТОВО",
                left + bodyWidth - 10, y + 8, 68, MapKlussUi.ACCENT);
        }
    }

    private void renderArt(DrawContext context, int left, int top, int bodyWidth, int bodyHeight) {
        int previewSize = Math.min(bodyHeight, Math.max(96, bodyWidth / 2));
        int previewLeft = left + bodyWidth - previewSize;
        MapKlussUi.drawPreviewWell(context, previewLeft, top, previewLeft + previewSize, top + previewSize);
        drawMiniPreview(context, previewLeft + 6, top + 6, previewSize - 12, previewSize - 12, 4);
        int infoWidth = Math.max(60, previewLeft - left - 12);
        int infoHeight = Math.min(bodyHeight, 156);
        MapKlussUi.drawDataStrip(context, left, left + infoWidth, top, top + infoHeight);
        MapKlussUi.drawLeft(context, textRenderer, model.longCopy()
            ? "Северный скалистый утёс · очень длинное название локального примера"
            : (model.english() ? "Northern cliff" : "Северный утёс"), left + 14, top + 12, infoWidth - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "FORMAT" : "ФОРМАТ",
            left + 14, top + 38, 72, MapKlussUi.DIM);
        MapKlussUi.drawLeft(context, textRenderer, "3×3 · 30 " + (model.english() ? "layers" : "слоёв"),
            left + 92, top + 38, infoWidth - 106, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "VERSION" : "ВЕРСИЯ",
            left + 14, top + 58, 72, MapKlussUi.DIM);
        MapKlussUi.drawLeft(context, textRenderer, "Minecraft 1.21.11",
            left + 92, top + 58, infoWidth - 106, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "STATUS" : "СТАТУС",
            left + 14, top + 78, 72, MapKlussUi.DIM);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Schema ready" : "Схема готова",
            left + 92, top + 78, infoWidth - 106, MapKlussUi.ACCENT);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Local preview" : "Локальное превью",
            previewLeft + 8, top + previewSize - 14, previewSize - 16, MapKlussUi.WHITE);
    }

    private void renderCollections(DrawContext context, int left, int top, int bodyWidth) {
        String[] names = model.english()
            ? new String[]{"Spawn district", "Museum hall", "Season archive"}
            : new String[]{"Спавн", "Музейный зал", "Архив сезона"};
        for (int i = 0; i < names.length; i++) {
            int y = top + i * 42;
            MapKlussUi.drawDataStrip(context, left, left + bodyWidth, y, y + 36);
            context.fill(left + 10, y + 10, left + 26, y + 26, i == 0 ? MapKlussUi.GOLD : MapKlussUi.CYAN);
            context.fill(left + 13, y + 13, left + 23, y + 23, 0xFF11161D);
            MapKlussUi.drawLeft(context, textRenderer, names[i], left + 36, y + 8, bodyWidth - 126, MapKlussUi.WHITE);
            MapKlussUi.drawLeft(context, textRenderer,
                model.english() ? "Local collection" : "Локальная коллекция",
                left + 36, y + 21, bodyWidth - 126, MapKlussUi.MUTED);
            MapKlussUi.drawRight(context, textRenderer,
                model.english() ? (i + 2) + " arts" : (i + 2) + " арта",
                left + bodyWidth - 10, y + 14, 80, MapKlussUi.CYAN);
        }
    }

    private void renderScan(DrawContext context, int left, int top, int bodyWidth, int bodyHeight) {
        int gap = 12;
        int summaryWidth = Math.min(250, Math.max(150, bodyWidth / 3));
        int gridLeft = left + summaryWidth + gap;
        int gridWidth = Math.max(80, bodyWidth - summaryWidth - gap);
        int cardHeight = Math.min(bodyHeight, 154);
        MapKlussUi.drawDataStrip(context, left, left + summaryWidth, top, top + cardHeight);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "SCAN RESULT" : "РЕЗУЛЬТАТ СКАНА",
            left + 14, top + 12, summaryWidth - 28, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Plane detected" : "Плоскость найдена",
            left + 14, top + 40, summaryWidth - 28, MapKlussUi.ACCENT);
        MapKlussUi.drawLeft(context, textRenderer, "4×3", left + 14, top + 64, summaryWidth - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer,
            model.english() ? "12 of 12 maps" : "12 из 12 карт",
            left + 14, top + 84, summaryWidth - 28, MapKlussUi.MUTED);
        MapKlussUi.drawPreviewWell(context, gridLeft, top, gridLeft + gridWidth, top + cardHeight);
        drawGrid(context, gridLeft + 8, top + 8, gridWidth - 16, cardHeight - 16, 4, 3);
    }

    private void renderLens(DrawContext context, int left, int top, int bodyWidth, int bodyHeight) {
        int gap = 10;
        int column = (bodyWidth - gap) / 2;
        int cardHeight = Math.min(bodyHeight, 138);
        MapKlussUi.drawDataStrip(context, left, left + column, top, top + cardHeight);
        MapKlussUi.drawDataStrip(context, left + column + gap, left + bodyWidth, top, top + cardHeight);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "SESSIONS" : "СЕССИИ",
            left + 14, top + 12, column - 28, MapKlussUi.AMETHYST);
        MapKlussUi.drawLeft(context, textRenderer, "MK-7F2A", left + 14, top + 40, column - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Owner · active" : "Владелец · активно",
            left + 14, top + 60, column - 28, MapKlussUi.ACCENT);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Realtime connected" : "Realtime подключён",
            left + 14, top + 86, column - 28, MapKlussUi.MUTED);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "PLACEMENTS" : "РАЗМЕЩЕНИЯ",
            left + column + gap + 14, top + 12, column - 28, MapKlussUi.AMETHYST);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Art wall" : "Стена арта",
            left + column + gap + 14, top + 40, column - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer,
            model.english() ? "3×2 · 6 of 6 frames" : "3×2 · 6 из 6 рамок",
            left + column + gap + 14, top + 60, column - 28, MapKlussUi.CYAN);
        drawProgress(context, left + column + gap + 14, top + 88, column - 28, 1.0F, MapKlussUi.AMETHYST);
    }

    private void renderTracker(DrawContext context, int left, int top, int bodyWidth) {
        MapKlussUi.drawFieldLabel(context, textRenderer,
            model.english() ? "BUILD UUID" : "UUID СБОРКИ", left + 2, top + 14, bodyWidth - 4);
        MapKlussUi.drawLeft(context, textRenderer, "7f2a-91bc-42d0", left + 12, top + 19, bodyWidth - 24, MapKlussUi.WHITE);
        MapKlussUi.drawDataStrip(context, left, left + bodyWidth, top + 52, top + 126);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "MATERIAL PROGRESS" : "ПРОГРЕСС МАТЕРИАЛОВ",
            left + 14, top + 64, bodyWidth - 28, MapKlussUi.CYAN);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "White wool" : "Белая шерсть",
            left + 14, top + 88, bodyWidth - 130, MapKlussUi.WHITE);
        MapKlussUi.drawRight(context, textRenderer, "1 248 / 4 096",
            left + bodyWidth - 14, top + 88, 110, MapKlussUi.MUTED);
        drawProgress(context, left + 14, top + 106, bodyWidth - 28, 1248.0F / 4096.0F, MapKlussUi.ACCENT);
    }

    private void renderLogin(DrawContext context, int left, int top, int bodyWidth) {
        int cardWidth = Math.min(bodyWidth, 420);
        int cardLeft = left + (bodyWidth - cardWidth) / 2;
        MapKlussUi.drawDataStrip(context, cardLeft, cardLeft + cardWidth, top, top + 142);
        MapKlussUi.drawCenteredIn(context, textRenderer, model.english() ? "Device sign-in" : "Вход на устройстве",
            cardLeft + cardWidth / 2, top + 14, cardWidth - 24, MapKlussUi.CYAN);
        MapKlussUi.drawCenteredIn(context, textRenderer, model.english() ? "ONE-TIME CODE" : "ОДНОРАЗОВЫЙ КОД",
            cardLeft + cardWidth / 2, top + 44, cardWidth - 24, MapKlussUi.DIM);
        MapKlussUi.drawCenteredIn(context, textRenderer, "ABCD-EFGH", cardLeft + cardWidth / 2, top + 64,
            cardWidth - 24, MapKlussUi.GOLD);
        MapKlussUi.drawCenteredIn(context, textRenderer, model.english() ? "Local placeholder code" : "Локальный тестовый код",
            cardLeft + cardWidth / 2, top + 94, cardWidth - 24, MapKlussUi.MUTED);
        MapKlussUi.drawCenteredIn(context, textRenderer,
            model.english() ? "Open the site and confirm sign-in" : "Откройте сайт и подтвердите вход",
            cardLeft + cardWidth / 2, top + 112, cardWidth - 24, MapKlussUi.WHITE);
    }

    private void renderTwoLayer(DrawContext context, int left, int top, int bodyWidth) {
        int cardWidth = Math.min(bodyWidth, 620);
        int cardLeft = left + (bodyWidth - cardWidth) / 2;
        MapKlussUi.drawDataStrip(context, cardLeft, cardLeft + cardWidth, top, top + 144);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "PLAN SOURCE" : "ИСТОЧНИК ПЛАНА",
            cardLeft + 14, top + 12, cardWidth - 28, MapKlussUi.AMETHYST);
        MapKlussUi.drawLeft(context, textRenderer, model.english() ? "Cloud artifact" : "Облачный арт",
            cardLeft + 14, top + 40, cardWidth - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer,
            model.english() ? "Part 2 of 6" : "Часть 2 из 6",
            cardLeft + 14, top + 66, cardWidth / 2 - 20, MapKlussUi.CYAN);
        MapKlussUi.drawRight(context, textRenderer,
            model.english() ? "Stage 17 of 64" : "Этап 17 из 64",
            cardLeft + cardWidth - 14, top + 66, cardWidth / 2 - 20, MapKlussUi.WHITE);
        drawProgress(context, cardLeft + 14, top + 92, cardWidth - 28, 17.0F / 64.0F, MapKlussUi.AMETHYST);
        MapKlussUi.drawLeft(context, textRenderer,
            model.english() ? "Ready to continue locally" : "Готово к локальному продолжению",
            cardLeft + 14, top + 116, cardWidth - 28, MapKlussUi.ACCENT);
    }

    private void renderUpdate(DrawContext context, int left, int top, int bodyWidth) {
        int cardWidth = Math.min(bodyWidth, 420);
        int cardLeft = left + (bodyWidth - cardWidth) / 2;
        MapKlussUi.drawPanelAt(context, cardLeft, cardLeft + cardWidth, top, top + 148);
        MapKlussUi.drawLocalHeader(context, textRenderer, model.english() ? "Update available" : "Доступно обновление",
            "", cardLeft + 8, cardLeft + cardWidth - 8, top + 18);
        MapKlussUi.drawCenteredIn(context, textRenderer, "MapKluss Companion 0.12.0",
            cardLeft + cardWidth / 2, top + 64, cardWidth - 28, MapKlussUi.GOLD);
        MapKlussUi.drawCenteredIn(context, textRenderer,
            model.english() ? "New tools and compatibility fixes" : "Новые инструменты и исправления совместимости",
            cardLeft + cardWidth / 2, top + 90, cardWidth - 28, MapKlussUi.WHITE);
        MapKlussUi.drawCenteredIn(context, textRenderer,
            model.english() ? "Download from the official page" : "Скачайте с официальной страницы",
            cardLeft + cardWidth / 2, top + 112, cardWidth - 28, MapKlussUi.MUTED);
    }

    private void drawMiniPreview(DrawContext context, int left, int top, int previewWidth, int previewHeight, int seed) {
        int cell = Math.max(2, Math.min(previewWidth, previewHeight) / 12);
        int[] colors = {0xFF172A35, 0xFF2C6874, 0xFF31D8E8, 0xFFE0D6C0, 0xFF57FF6E, 0xFF8E806B};
        for (int y = 0; y < previewHeight; y += cell) {
            for (int x = 0; x < previewWidth; x += cell) {
                int color = colors[Math.floorMod(x / cell + y / cell * 3 + seed, colors.length)];
                context.fill(left + x, top + y, Math.min(left + previewWidth, left + x + cell),
                    Math.min(top + previewHeight, top + y + cell), color);
            }
        }
    }

    private void drawGrid(DrawContext context, int left, int top, int gridWidth, int gridHeight, int columns, int rows) {
        int cellWidth = Math.max(8, gridWidth / columns);
        int cellHeight = Math.max(8, gridHeight / rows);
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int x = left + column * cellWidth;
                int y = top + row * cellHeight;
                context.fill(x, y, x + cellWidth - 2, y + cellHeight - 2,
                    ((row + column) & 1) == 0 ? 0xFF245567 : 0xFF162B35);
            }
        }
    }

    private void drawProgress(DrawContext context, int left, int top, int width, float progress, int color) {
        int safeWidth = Math.max(12, width);
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        context.fill(left, top, left + safeWidth, top + 8, 0xFF050609);
        context.fill(left + 1, top + 1, left + safeWidth - 1, top + 7, 0xFF343E4A);
        context.fill(left + 2, top + 2, left + safeWidth - 2, top + 6, 0xFF0A0D12);
        context.fill(left + 2, top + 2, left + 2 + Math.round((safeWidth - 4) * clamped), top + 6, color);
    }

    private void renderGuides(DrawContext context, int mouseX, int mouseY) {
        for (Bounds bounds : guideBounds) {
            int color = bounds.contains(mouseX, mouseY) ? MapKlussUi.GOLD : MapKlussUi.CYAN;
            context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
            context.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
            context.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
            context.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), color);
            if (bounds.contains(mouseX, mouseY)) {
                MapKlussUi.drawLeft(context, textRenderer,
                    bounds.name() + " · " + bounds.x() + "," + bounds.y() + " · " + bounds.width() + "×" + bounds.height(),
                    10, height - 12, width - 20, MapKlussUi.GOLD);
            }
        }
    }

    @Override
    public void close() {
        client().setScreen(parent);
    }

    private FixtureFrame frame() {
        int availableWidth = Math.max(220, width - 28);
        int availableHeight = Math.max(150, height - FRAME_TOP - 10);
        int frameWidth = model.viewport().fitWidth(availableWidth);
        int frameHeight = model.viewport().fitHeight(availableHeight);
        int left = (width - frameWidth) / 2;
        return new FixtureFrame(left, FRAME_TOP, left + frameWidth, FRAME_TOP + frameHeight);
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private record FixtureFrame(int left, int top, int right, int bottom) {
        int width() {
            return right - left;
        }

        int centerX() {
            return (left + right) / 2;
        }
    }

    private record Bounds(int x, int y, int width, int height, String name) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
}
