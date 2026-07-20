package art.mapkluss.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SuppressionTileSelectScreen extends Screen {
    private static final int DESIRED_PANEL_WIDTH = 500;
    private static final int BUTTON_HEIGHT = 24;
    private static final int GAP = 6;

    private final Screen parent;
    private final SuppressionBundleCatalog catalog;
    private int page;
    private boolean busy;
    private String status = "";

    public SuppressionTileSelectScreen(Screen parent, SuppressionBundleCatalog catalog) {
        super(Text.literal("Two-layer maps"));
        this.parent = parent;
        this.catalog = catalog;
    }

    @Override
    protected void init() {
        clearChildren();
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
            addDrawableChild(MapKlussButton.builder(tileLabel(tile), button -> startTile(tile))
                .gold()
                .tooltip(CompanionI18n.text("Строка " + (tile.row() + 1) + ", столбец " + (tile.column() + 1)))
                .dimensions(x, y, buttonWidth, BUTTON_HEIGHT)
                .enabledWhen(() -> !busy && !SuppressionManager.instance().active())
                .build());
        }

        int footerY = panelBottom() - 29;
        int navWidth = 80;
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Пред."), button -> changePage(-1))
            .dimensions(left + 92, footerY, navWidth, 20)
            .visibleWhen(() -> pageCount > 1)
            .enabledWhen(() -> !busy && page > 0)
            .build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("След."), button -> changePage(1))
            .dimensions(left + panelWidth - navWidth, footerY, navWidth, 20)
            .visibleWhen(() -> pageCount > 1)
            .enabledWhen(() -> !busy && page + 1 < pageCount)
            .build());
        addDrawableChild(MapKlussUi.languageButton(this));
        addDrawableChild(MapKlussUi.backButton(this, parent, left, panelBottom(), () -> !busy));
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
        CompletableFuture.runAsync(() -> {
            try {
                SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.install(
                    client().runDirectory.toPath(), tile.bundle());
                client().execute(() -> {
                    try {
                        SuppressionManager.instance().start(client(), tile.bundle(), installed);
                        client().setScreen(null);
                    } catch (Exception error) {
                        busy = false;
                        status = "Не удалось начать: " + CompanionUiErrors.message("two-layer", error);
                    }
                });
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not start Two-layer map part {}.", tile.index(), error);
                client().execute(() -> {
                    busy = false;
                    status = "Не удалось подготовить карту: " + CompanionUiErrors.message("two-layer", error);
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, DESIRED_PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int bottom = panelBottom();
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, panelTop(), bottom);
        MapKlussUi.drawHeader(context, textRenderer, "ВЫБЕРИТЕ КАРТУ", "", width, panelTop() + 12);
        MapKlussUi.drawCenteredIn(
            context, textRenderer,
            catalog.gridWide() + "×" + catalog.gridTall() + " · " + catalog.tiles().size() + " карт",
            width / 2, panelTop() + 34, panelWidth - 24, MapKlussUi.CYAN
        );
        int pageSize = columns(panelWidth) * visibleRows();
        int pages = Math.max(1, (catalog.tiles().size() + pageSize - 1) / pageSize);
        if (pages > 1) {
            MapKlussUi.drawCenteredIn(context, textRenderer, "Стр " + (page + 1) + "/" + pages,
                width / 2, bottom - 24, 100, MapKlussUi.MUTED);
        }
        MapKlussUi.drawWrappedCenteredIn(context, textRenderer, status, width / 2,
            bottom - 47, panelWidth - 28, 1, busy ? MapKlussUi.GOLD : MapKlussUi.statusColor(status));
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (!busy) client().setScreen(parent);
    }

    private Text tileLabel(SuppressionBundleCatalog.Tile tile) {
        return Text.literal("#" + tile.index() + " · " + (tile.column() + 1) + "×" + (tile.row() + 1));
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

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
