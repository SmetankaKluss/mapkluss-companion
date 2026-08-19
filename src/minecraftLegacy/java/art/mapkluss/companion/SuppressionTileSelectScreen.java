package art.mapkluss.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SuppressionTileSelectScreen extends Screen {
    private static final int BUTTON_HEIGHT = 24;
    private static final int GAP = 6;

    private final Screen parent;
    private final SuppressionBundleCatalog catalog;
    private int page;
    private boolean busy;
    private String status = "";
    private final ScreenRequestGate requests = new ScreenRequestGate();

    public SuppressionTileSelectScreen(Screen parent, SuppressionBundleCatalog catalog) {
        super(Text.literal("Two-layer maps"));
        this.parent = parent;
        this.catalog = catalog;
    }

    @Override
    protected void init() {
        requests.attach();
        clearChildren();
        CompanionUiLayout.Shell shell = workflowShell();
        addNavigationControls(shell);
        CompanionUiLayout.Rect panel = selectionPanel(shell);
        int innerLeft = panel.x() + 12;
        int innerWidth = Math.max(1, panel.width() - 24);
        int columns = columns(innerWidth);
        int rows = visibleRows(panel);
        int pageSize = columns * rows;
        int pageCount = Math.max(1, (catalog.tiles().size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int start = page * pageSize;
        int end = Math.min(catalog.tiles().size(), start + pageSize);
        int buttonWidth = (innerWidth - GAP * (columns - 1)) / columns;
        int gridTop = gridTop(panel);
        for (int index = start; index < end; index++) {
            SuppressionBundleCatalog.Tile tile = catalog.tiles().get(index);
            int local = index - start;
            int x = innerLeft + (local % columns) * (buttonWidth + GAP);
            int y = gridTop + (local / columns) * (BUTTON_HEIGHT + GAP);
            addDrawableChild(MapKlussButton.builder(tileLabel(tile), button -> startTile(tile))
                .action("two_layer.select_part")
                .special()
                .tooltip(CompanionI18n.text("Строка " + (tile.row() + 1) + ", столбец " + (tile.column() + 1)))
                .dimensions(x, y, buttonWidth, BUTTON_HEIGHT)
                .enabledWhen(() -> !busy && !SuppressionManager.instance().active())
                .build());
        }

        int footerY = panel.bottom() - 34;
        int backWidth = Math.min(88, Math.max(64, innerWidth / 5));
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Назад"), button -> client().setScreen(parent))
            .action("global.back")
            .dimensions(innerLeft, footerY, backWidth, 22).enabledWhen(() -> !busy).build());
        int navWidth = Math.min(80, Math.max(56, innerWidth / 5));
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Пред."), button -> changePage(-1))
            .action("two_layer.tile_previous_page")
            .dimensions(panel.x() + panel.width() / 2 - navWidth - GAP / 2, footerY, navWidth, 22)
            .visibleWhen(() -> pageCount > 1)
            .enabledWhen(() -> !busy && page > 0)
            .build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("След."), button -> changePage(1))
            .action("two_layer.tile_next_page")
            .dimensions(panel.x() + panel.width() / 2 + GAP / 2, footerY, navWidth, 22)
            .visibleWhen(() -> pageCount > 1)
            .enabledWhen(() -> !busy && page + 1 < pageCount)
            .build());
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destination.name()))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).enabledWhen(() -> !busy).build());
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
                    client().runDirectory.toPath(), tile.bundle());
                runOnClient(token, () -> {
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
                runOnClient(token, () -> {
                    busy = false;
                    status = "Не удалось подготовить карту: " + CompanionUiErrors.message("two-layer", error);
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ScreenViewModel model = screenModel();
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height, model, false
        );
        CompanionUiLayout.Rect panel = selectionPanel(shell);
        MapKlussUi.drawPanelAt(context, panel.x(), panel.right(), panel.y(), panel.bottom());
        MapKlussUi.drawLeft(context, textRenderer, "Части арта", panel.x() + 12, panel.y() + 12,
            panel.width() - 24, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer,
            catalog.gridWide() + "×" + catalog.gridTall() + " · " + catalog.tiles().size() + " частей",
            panel.x() + 12, panel.y() + 29, panel.width() - 24, MapKlussUi.CYAN);
        int pageSize = columns(panel.width() - 24) * visibleRows(panel);
        int pages = Math.max(1, (catalog.tiles().size() + pageSize - 1) / pageSize);
        if (pages > 1) {
            MapKlussUi.drawCenteredIn(context, textRenderer, "Стр " + (page + 1) + "/" + pages,
                panel.x() + panel.width() / 2, panel.bottom() - 28, 100, MapKlussUi.MUTED);
        }
        MapKlussUi.drawWrappedCenteredIn(context, textRenderer, status, panel.x() + panel.width() / 2,
            panel.bottom() - 52, panel.width() - 28, 1, MapKlussUi.statusColor(model.statusKind()));
        super.render(context, mouseX, mouseY, delta);
    }

    private ScreenViewModel screenModel() {
        return ScreenViewModel.shell(
            CompanionUiLayout.Destination.TWO_LAYER, "Two-layer",
            List.of(CompanionI18n.translate("Выбор части")), status,
            busy ? ScreenViewModel.StatusKind.LOADING : ScreenViewModel.classifyStatus(status)
        );
    }

    @Override
    public void close() {
        if (!busy) client().setScreen(parent);
    }

    @Override
    public void removed() {
        requests.detach();
        super.removed();
    }

    private void runOnClient(ScreenRequestGate.Token token, Runnable task) {
        client().execute(() -> {
            if (requests.isCurrent(token) && client().currentScreen == this) task.run();
        });
    }

    private Text tileLabel(SuppressionBundleCatalog.Tile tile) {
        return Text.literal("#" + tile.index() + " · " + (tile.column() + 1) + "×" + (tile.row() + 1));
    }

    private int columns(int panelWidth) {
        if (panelWidth >= 560) return 4;
        if (panelWidth >= 360) return 3;
        return 2;
    }

    private int visibleRows(CompanionUiLayout.Rect panel) {
        return Math.max(1, Math.min(5, (panel.height() - 112) / (BUTTON_HEIGHT + GAP)));
    }

    private int gridTop(CompanionUiLayout.Rect panel) {
        return panel.y() + 50;
    }

    private CompanionUiLayout.Shell workflowShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect selectionPanel(CompanionUiLayout.Shell shell) {
        return CompanionUiLayout.focusedPanel(shell.content(), 680, 320);
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
