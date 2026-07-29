package art.mapkluss.companion;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SuppressionTileSelectScreen extends Screen {
    private static final int DESIRED_PANEL_WIDTH = 500;
    private static final int BUTTON_HEIGHT = 24;
    private static final int GAP = 6;

    private final Screen parent;
    private final SuppressionBundleCatalog catalog;
    private int page;
    private boolean busy;
    private String status = "";
    private final ScreenRequestGate requests = new ScreenRequestGate();

    public SuppressionTileSelectScreen(Screen parent, SuppressionBundleCatalog catalog) {
        super(Component.literal("Two-layer maps"));
        this.parent = parent;
        this.catalog = catalog;
    }

    @Override
    protected void init() {
        requests.attach();
        clearWidgets();
        int panelWidth = MapKlussUi.panelWidth(width, DESIRED_PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int columns = columns(panelWidth);
        int rows = visibleRows();
        int pageSize = columns * rows;
        int pageCount = Math.max(1, (catalog.tiles().size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int start = page * pageSize;
        int end = Math.min(catalog.tiles().size(), start + pageSize);
        int buttonWidth = (panelWidth - GAP * (columns - 1)) / columns;
        int gridTop = gridTop();
        for (int index = start; index < end; index++) {
            SuppressionBundleCatalog.Tile tile = catalog.tiles().get(index);
            int local = index - start;
            int x = left + (local % columns) * (buttonWidth + GAP);
            int y = gridTop + (local / columns) * (BUTTON_HEIGHT + GAP);
            addRenderableWidget(MapKlussButton.builder(tileLabel(tile), button -> startTile(tile))
                .special()
                .tooltip(CompanionI18n.text("Строка " + (tile.row() + 1) + ", столбец " + (tile.column() + 1)))
                .dimensions(x, y, buttonWidth, BUTTON_HEIGHT)
                .enabledWhen(() -> !busy && !SuppressionManager.instance().active())
                .build());
        }

        int footerY = panelBottom() - 29;
        int navWidth = 80;
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Пред."), button -> changePage(-1))
            .dimensions(left + 92, footerY, navWidth, 20)
            .visibleWhen(() -> pageCount > 1)
            .enabledWhen(() -> !busy && page > 0)
            .build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("След."), button -> changePage(1))
            .dimensions(left + panelWidth - navWidth, footerY, navWidth, 20)
            .visibleWhen(() -> pageCount > 1)
            .enabledWhen(() -> !busy && page + 1 < pageCount)
            .build());
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left, panelBottom(), () -> !busy));
    }

    private void changePage(int delta) {
        if (busy) return;
        page += delta;
        init();
    }

    private void startTile(SuppressionBundleCatalog.Tile tile) {
        if (busy || SuppressionManager.instance().active()) return;
        busy = true;
        status = "Подготовка карты " + tile.index() + "…";
        ScreenRequestGate.Token token = requests.begin("two-layer-start");
        CompletableFuture.runAsync(() -> {
            try {
                SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.install(
                    client().gameDirectory.toPath(), tile.bundle());
                runOnClient(token, () -> {
                    try {
                        SuppressionManager.instance().start(client(), tile.bundle(), installed);
                        client().gui.setScreen(null);
                    } catch (Exception error) {
                        busy = false;
                        status = "Не удалось начать: " + CompanionUiErrors.message("two-layer", error);
                    }
                });
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not start Two-layer map part {}.", tile.index(), error);
                runOnClient(token, () -> {
                    busy = false;
                    status = "Не удалось подготовить карту: " + CompanionUiErrors.message("two-layer", error);
                });
            }
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        int panelWidth = MapKlussUi.panelWidth(width, DESIRED_PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int bottom = panelBottom();
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, panelTop(), bottom);
        MapKlussUi.drawSectionAt(context, font, "Части арта", left, panelWidth,
            gridTop() - 16, Math.max(42, bottom - gridTop() - 30));
        MapKlussUi.drawLocalHeader(
            context, font, "ВЫБЕРИТЕ КАРТУ", "",
            left - 4, left + panelWidth + 4, panelTop() + 12
        );
        MapKlussUi.drawCenteredIn(
            context, font,
            catalog.gridWide() + "×" + catalog.gridTall() + " · " + catalog.tiles().size() + " карт",
            width / 2, panelTop() + 34, panelWidth - 24, MapKlussUi.CYAN
        );
        int pageSize = columns(panelWidth) * visibleRows();
        int pages = Math.max(1, (catalog.tiles().size() + pageSize - 1) / pageSize);
        if (pages > 1) {
            MapKlussUi.drawCenteredIn(context, font, "Стр " + (page + 1) + "/" + pages,
                width / 2, bottom - 24, 100, MapKlussUi.MUTED);
        }
        MapKlussUi.drawWrappedCenteredIn(context, font, status, width / 2,
            bottom - 47, panelWidth - 28, 1, busy ? MapKlussUi.GOLD : MapKlussUi.statusColor(status));
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (!busy) client().gui.setScreen(parent);
    }

    @Override
    public void removed() {
        requests.detach();
        super.removed();
    }

    private void runOnClient(ScreenRequestGate.Token token, Runnable task) {
        client().execute(() -> {
            if (requests.isCurrent(token) && client().gui.screen() == this) task.run();
        });
    }

    private Component tileLabel(SuppressionBundleCatalog.Tile tile) {
        return Component.literal("#" + tile.index() + " · " + (tile.column() + 1) + "×" + (tile.row() + 1));
    }

    private int columns(int panelWidth) {
        return panelWidth >= 420 ? 4 : 2;
    }

    private int visibleRows() {
        return Math.max(2, Math.min(4, (height - 150) / (BUTTON_HEIGHT + GAP)));
    }

    private int panelTop() {
        int contentHeight = 118 + visibleRows() * (BUTTON_HEIGHT + GAP);
        return Math.max(8, (height - contentHeight) / 2);
    }

    private int panelBottom() {
        return Math.min(height - 8, panelTop() + 118 + visibleRows() * (BUTTON_HEIGHT + GAP));
    }

    private int gridTop() {
        return panelTop() + 58;
    }

    private static Minecraft client() {
        return Minecraft.getInstance();
    }
}
