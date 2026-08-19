package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
        super(Component.literal("MapKluss UI Lab"));
        this.parent = parent;
        this.model = model;
    }

    Screen parent() {
        return parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        guideBounds.clear();
        addToolbar();
        addFixtureActions();
    }

    private void addToolbar() {
        int left = 10;
        int widthAvailable = width - 20;
        int firstWidth = Math.max(72, (widthAvailable - TOOLBAR_GAP * 3) / 4);
        int x = left;
        addToolbarButton("Page · " + model.title(), x, TOOLBAR_TOP, firstWidth, model::nextPage);
        x += firstWidth + TOOLBAR_GAP;
        addToolbarButton("State · " + model.stateLabel(), x, TOOLBAR_TOP, firstWidth, model::nextState);
        x += firstWidth + TOOLBAR_GAP;
        addToolbarButton(model.english() ? "EN" : "RU", x, TOOLBAR_TOP, firstWidth, model::toggleLanguage);
        x += firstWidth + TOOLBAR_GAP;
        addToolbarButton("View · " + model.viewport().label(), x, TOOLBAR_TOP, width - 10 - x, model::nextViewport);

        int secondTop = TOOLBAR_TOP + TOOLBAR_ROW_HEIGHT + TOOLBAR_GAP;
        int secondWidth = Math.max(46, (widthAvailable - TOOLBAR_GAP * 7) / 8);
        x = left;
        addToolbarButton("◀ page", x, secondTop, secondWidth, model::previousPage);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton("page ▶", x, secondTop, secondWidth, model::nextPage);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton("◀ state", x, secondTop, secondWidth, model::previousState);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton("state ▶", x, secondTop, secondWidth, model::nextState);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.longCopy() ? "Long" : "Short", x, secondTop, secondWidth, model::toggleLongCopy);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton(model.guides() ? "Bounds on" : "Bounds", x, secondTop, secondWidth, model::toggleGuides);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton("GUI · " + model.guiScale().label(), x, secondTop, secondWidth, model::nextGuiScale);
        x += secondWidth + TOOLBAR_GAP;
        addToolbarButton("Capture", x, secondTop, width - 10 - x, this::capture);
    }

    private void addToolbarButton(String label, int x, int y, int buttonWidth, Runnable action) {
        addRenderableWidget(MapKlussButton.builder(Component.literal(label), button -> {
                action.run();
                client().gui.setScreen(new MapKlussUiLabScreen(parent, model));
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
        int buttonWidth = Math.max(70, (frame.width() - 28 - gap) / 2);
        int left = frame.left() + 14;
        addRenderableWidget(MapKlussButton.builder(Component.literal(model.actionPrimary()), button -> model.notice("Primary action"))
            .special().dimensions(left, buttonY, buttonWidth, 20)
            .enabledWhen(model::actionsEnabled).build());
        guideBounds.add(new Bounds(left, buttonY, buttonWidth, 20, "primary"));
        int secondLeft = left + buttonWidth + gap;
        int secondWidth = Math.max(54, frame.right() - 14 - secondLeft);
        addRenderableWidget(MapKlussButton.builder(Component.literal(model.actionSecondary()), button -> model.notice("Secondary action"))
            .technical().dimensions(secondLeft, buttonY, secondWidth, 20)
            .enabledWhen(model::actionsEnabled).build());
        guideBounds.add(new Bounds(secondLeft, buttonY, secondWidth, 20, "secondary"));
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
                    client().gui.setScreen(new MapKlussUiLabScreen(parent, model));
                });
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not capture MapKluss UI Lab screenshot.", error);
                client().execute(() -> {
                    model.notice("Capture failed · see log");
                    client().gui.setScreen(new MapKlussUiLabScreen(parent, model));
                });
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        MapKlussUi.drawHeader(
            context,
            font,
            "UI LAB · " + model.title(),
            model.stateLabel() + " · " + model.viewport().label(),
            width,
            11
        );
        FixtureFrame frame = frame();
        MapKlussUi.drawPanelAt(context, frame.left() - 8, frame.right() + 8, frame.top(), frame.bottom());
        renderFixture(context, frame);
        if (!model.notice().isBlank()) {
            MapKlussUi.drawCenteredIn(context, font, model.notice(), width / 2, frame.bottom() - 42,
                frame.width() - 30, MapKlussUi.CYAN);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (model.guides()) renderGuides(context, mouseX, mouseY);
    }

    private void renderFixture(GuiGraphicsExtractor context, FixtureFrame frame) {
        int contentLeft = frame.left() + 12;
        int contentTop = frame.top() + 12;
        int contentWidth = frame.width() - 24;
        int contentBottom = frame.bottom() - 54;
        int contentHeight = Math.max(36, contentBottom - contentTop);
        MapKlussUi.drawSectionAt(context, font, model.title(), contentLeft, contentWidth, contentTop, contentHeight);
        MapKlussUi.drawStatusIn(context, font, model.status(), frame.centerX(), contentTop + 17, contentWidth - 32);
        if (!model.populated()) {
            MapKlussUi.drawEmptyState(context, font, model.stateLabel(), model.emptyDetail(),
                contentLeft + 4, contentTop + 30, contentWidth - 8, contentHeight - 34,
                MapKlussUi.statusColor(model.statusKind()));
            return;
        }

        int bodyLeft = contentLeft + 10;
        int bodyTop = contentTop + 40;
        int bodyWidth = contentWidth - 20;
        renderPage(context, bodyLeft, bodyTop, bodyWidth, Math.max(40, contentBottom - bodyTop - 6));
    }

    private void renderPage(GuiGraphicsExtractor context, int left, int top, int bodyWidth, int bodyHeight) {
        switch (model.page()) {
            case ART -> {
                int preview = Math.min(bodyHeight, Math.max(72, bodyWidth / 2));
                int previewLeft = left + bodyWidth - preview;
                MapKlussUi.drawPreviewWell(context, previewLeft, top, previewLeft + preview, top + preview);
                drawMiniPreview(context, previewLeft + 6, top + 6, preview - 12, preview - 12, 4);
                MapKlussUi.drawLeft(context, font, model.english() ? "Northern cliff" : "Северный утёс",
                    left, top + 6, Math.max(50, previewLeft - left - 12), MapKlussUi.WHITE);
                MapKlussUi.drawLeft(context, font, "3×3 · MC 26.2",
                    left, top + 26, Math.max(50, previewLeft - left - 12), MapKlussUi.CYAN);
            }
            case LENS -> renderColumns(context, left, top, bodyWidth, "SESSIONS", "PLACEMENTS", MapKlussUi.AMETHYST);
            case TWO_LAYER -> renderCard(context, left, top, bodyWidth,
                model.english() ? "PLAN SOURCE" : "ИСТОЧНИК ПЛАНА", "tile 2/6 · stage 17/64", MapKlussUi.AMETHYST);
            case LOGIN -> renderCard(context, left, top, bodyWidth,
                model.english() ? "DEVICE SIGN-IN" : "ВХОД НА УСТРОЙСТВЕ", "ABCD-EFGH", MapKlussUi.GOLD);
            case UPDATE -> renderCard(context, left, top, bodyWidth,
                model.english() ? "UPDATE AVAILABLE" : "ДОСТУПНО ОБНОВЛЕНИЕ", "MapKluss Companion 0.13.1", MapKlussUi.GOLD);
            case TRACKER -> renderCard(context, left, top, bodyWidth,
                model.english() ? "BUILD TRACKER" : "ТРЕКЕР СБОРКИ", "White wool · 1248 / 4096", MapKlussUi.CYAN);
            case SCAN -> renderCard(context, left, top, bodyWidth,
                model.english() ? "DETECTED GRID" : "НАЙДЕНА ПЛОСКОСТЬ", "4×3 · 12/12 maps", MapKlussUi.ACCENT);
            case COLLECTIONS -> renderRows(context, left, top, bodyWidth,
                model.english() ? "Collection" : "Коллекция", 3);
            case LIBRARY -> renderRows(context, left, top, bodyWidth,
                model.english() ? "Local fixture art" : "Локальный пример", 3);
        }
    }

    private void renderRows(GuiGraphicsExtractor context, int left, int top, int bodyWidth, String prefix, int rows) {
        for (int row = 0; row < rows; row++) {
            int y = top + row * 30;
            MapKlussUi.drawDataStrip(context, left, left + bodyWidth, y, y + 24);
            MapKlussUi.drawLeft(context, font,
                row == 1 && model.longCopy() ? prefix + " · long fixture title for narrow layout verification" : prefix + " " + (row + 1),
                left + 14, y + 7, bodyWidth - 28, MapKlussUi.WHITE);
        }
    }

    private void renderColumns(
        GuiGraphicsExtractor context,
        int left,
        int top,
        int bodyWidth,
        String first,
        String second,
        int color
    ) {
        int gap = 10;
        int column = (bodyWidth - gap) / 2;
        renderCard(context, left, top, column, first, "MK-7F2A", color);
        renderCard(context, left + column + gap, top, column, second, "3×2 · 6/6", color);
    }

    private void renderCard(
        GuiGraphicsExtractor context,
        int left,
        int top,
        int bodyWidth,
        String title,
        String detail,
        int color
    ) {
        MapKlussUi.drawDataStrip(context, left, left + bodyWidth, top, top + 78);
        MapKlussUi.drawLeft(context, font, title, left + 14, top + 12, bodyWidth - 28, color);
        MapKlussUi.drawLeft(context, font, detail, left + 14, top + 38, bodyWidth - 28, MapKlussUi.WHITE);
    }

    private void drawMiniPreview(GuiGraphicsExtractor context, int left, int top, int previewWidth, int previewHeight, int seed) {
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

    private void renderGuides(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        for (Bounds bounds : guideBounds) {
            int color = bounds.contains(mouseX, mouseY) ? MapKlussUi.GOLD : MapKlussUi.CYAN;
            context.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + 1, color);
            context.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), color);
            context.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.bottom(), color);
            context.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), color);
            if (bounds.contains(mouseX, mouseY)) {
                MapKlussUi.drawLeft(context, font,
                    bounds.name() + " · " + bounds.x() + "," + bounds.y() + " · " + bounds.width() + "×" + bounds.height(),
                    10, height - 12, width - 20, MapKlussUi.GOLD);
            }
        }
    }

    @Override
    public void onClose() {
        client().gui.setScreen(parent);
    }

    private FixtureFrame frame() {
        int availableWidth = Math.max(220, width - 28);
        int availableHeight = Math.max(150, height - FRAME_TOP - 10);
        int frameWidth = model.viewport().fitWidth(availableWidth);
        int frameHeight = model.viewport().fitHeight(availableHeight);
        int left = (width - frameWidth) / 2;
        return new FixtureFrame(left, FRAME_TOP, left + frameWidth, FRAME_TOP + frameHeight);
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
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
