package art.mapkluss.companion;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SuppressionTileSelectScreen extends Screen {
    private static final int BUTTON_HEIGHT = 24;
    private static final int GAP = 6;

    private final Screen parent;
    private final SuppressionBundleCatalog catalog;
    private final boolean fixture;
    private int page;
    private boolean busy;
    private String status = "";
    private final ScreenRequestGate requests = new ScreenRequestGate();

    public SuppressionTileSelectScreen(Screen parent, SuppressionBundleCatalog catalog) {
        this(parent, catalog, false);
    }

    SuppressionTileSelectScreen(Screen parent, SuppressionBundleCatalog catalog, boolean fixture) {
        super(Component.literal("Two-layer maps"));
        this.parent = parent;
        this.catalog = catalog;
        this.fixture = fixture;
    }

    private int selectedIndex;

    private WorkshopTheme workshopTheme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final SuppressionPreviewTexture previewTexture = new SuppressionPreviewTexture();
    private boolean showPreview;

    private MapKlussButton layerButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r,
        java.util.function.BooleanSupplier enabled, boolean selected, Runnable action) {
        var builder = MapKlussButton.builder(CompanionI18n.text(label), button -> {
            if (enabled.getAsBoolean()) action.run();
        }).action(id).tooltip(CompanionI18n.text(label)).selected(selected)
            .dimensions(r.x(), r.y(), r.width(), r.height()).enabledWhen(enabled);
        if (id.equals("two_layer.select_part") || id.equals("two_layer.resume")) builder.gold();
        if (id.equals("two_layer.stop")) builder.danger().navigationOrder(1000);
        return addRenderableWidget(builder.build().workshop(workshopTheme, icon));
    }

    private WorkshopLayout.Rect part(WorkshopLayout.Rect r, int index, int count) {
        int w = (r.width() - (count - 1) * 4) / count;
        return new WorkshopLayout.Rect(r.x() + index * (w + 4), r.y(), w, r.height());
    }

    private void workshopNavigation(WorkshopTwoLayerLayout.Layout s) {
        try { workshopTheme = WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        int slot = (s.navigation().width() - 60) / 5;
        WorkshopIcon[] icons = {WorkshopIcon.LIBRARY, WorkshopIcon.LENS, WorkshopIcon.SCAN, WorkshopIcon.TRACKER, WorkshopIcon.ACCOUNT};
        String[] labels = {"Библиотека", "Lens", "Сканирование", "Трекер", "Аккаунт"};
        for (int i = 0; i < 5; i++) {
            var d = CompanionUiLayout.Destination.values()[i];
            layerButton(CompanionActionInventory.navigationAction(d), labels[i], icons[i],
                new WorkshopLayout.Rect(s.navigation().x() + i * slot, s.navigation().y(), slot - 4, 28),
                () -> !busy, false, () -> openDestination(d));
        }
        layerButton("account.theme", "Оформление", WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(s.navigation().right() - 56, s.navigation().y(), 24, 28),
            () -> !busy, false, () -> client().gui.setScreen(new WorkshopAppearanceScreen(this)));
        layerButton("global.back", "Назад", WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.navigation().right() - 24, s.navigation().y(), 24, 28),
            () -> !busy, false, this::onClose);
        layerButton("global.language", CompanionI18n.toggleLabel(client()), null,
            new WorkshopLayout.Rect(s.footer().right() - 32, s.footer().y(), 32, 20),
            () -> !busy, false, () -> { try { CompanionI18n.toggle(client()); init(); } catch (Exception ignored) { } });
    }

    private void text(GuiGraphicsExtractor g, String value, WorkshopLayout.Rect r, String color) {
        WorkshopDraw.text(g, font, CompanionI18n.translate(value), r.x() + 4, r.y() + 7,
            Math.max(0, r.width() - 8), workshopTheme.color(color));
    }

    private void drawPreview(GuiGraphicsExtractor g, SuppressionBundleCatalog value, WorkshopLayout.Rect area,
        SuppressionBundleCatalog.Tile selected) {
        g.fill(area.x(), area.y(), area.right(), area.bottom(), workshopTheme.color("field-bg"));
        if (value == null) {
            WorkshopDraw.icon(g, WorkshopIcon.LAYERS, area.x() + area.width() / 2 - 8,
                area.y() + area.height() / 2 - 8, workshopTheme.color("text-secondary"));
            return;
        }
        var r = WorkshopLayout.contain(area, value.gridWide() * 128, value.gridTall() * 128);
        try {
            WorkshopDraw.image(g, previewTexture.get(value), area, value.gridWide() * 128, value.gridTall() * 128);
        } catch (RuntimeException error) { text(g, "Превью недоступно", area, "text-secondary"); return; }
        if (selected != null) {
            int x = r.x() + r.width() * selected.column() / value.gridWide();
            int y = r.y() + r.height() * selected.row() / value.gridTall();
            int right = r.x() + r.width() * (selected.column() + 1) / value.gridWide();
            int bottom = r.y() + r.height() * (selected.row() + 1) / value.gridTall();
            int color = workshopTheme.color("accent");
            g.fill(x, y, right, y + 1, color); g.fill(x, bottom - 1, right, bottom, color);
            g.fill(x, y, x + 1, bottom, color); g.fill(right - 1, y, right, bottom, color);
        }
    }

    @Override
    protected void init() {
        requests.attach();
        clearWidgets();
        var s = WorkshopTwoLayerLayout.at(width, height);
        workshopNavigation(s);
        int pages = Math.max(1, (catalog.tiles().size() + s.rows() - 1) / s.rows());
        page = Math.max(0, Math.min(page, pages - 1));
        selectedIndex = Math.max(0, Math.min(selectedIndex, catalog.tiles().size() - 1));
        if (s.split() || !showPreview) {
            int start = page * s.rows();
            for (int i = start; i < Math.min(catalog.tiles().size(), start + s.rows()); i++) {
                int index = i;
                var tile = catalog.tiles().get(i);
                layerButton("two_layer.highlight_part", "#" + tile.index() + " · " + (tile.column() + 1) + "×" + (tile.row() + 1),
                    WorkshopIcon.LAYERS, s.row(i - start), () -> !busy, selectedIndex == i,
                    () -> { selectedIndex = index; init(); });
            }
            layerButton("two_layer.tile_previous_page", "Пред.", WorkshopIcon.BACK, part(s.paging(), 0, 2),
                () -> !busy && page > 0, false, () -> changePage(-1));
            layerButton("two_layer.tile_next_page", "След.", WorkshopIcon.MORE, part(s.paging(), 1, 2),
                () -> !busy && page + 1 < pages, false, () -> changePage(1));
        }
        layerButton("two_layer.select_part", "Начать", WorkshopIcon.INSTALL, s.split() ? s.actions() : part(s.actions(), 0, 2),
            () -> !fixture && !busy && !catalog.tiles().isEmpty() && !SuppressionManager.instance().active(), false,
            () -> startTile(catalog.tiles().get(selectedIndex)));
        if (!s.split()) layerButton("two_layer.preview", showPreview ? "Части арта" : "Превью", WorkshopIcon.LAYERS, part(s.actions(), 1, 2),
            () -> !busy && !s.split(), showPreview, () -> { showPreview = !showPreview; init(); });
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(parent));
            case LENS -> client().gui.setScreen(new LensScreen(this));
            case SCAN -> client().gui.setScreen(new ScanScreen(this));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this));
            default -> { }
        }
    }

    private void changePage(int delta) {
        if (busy) return;
        page += delta;
        init();
    }

    private void startTile(SuppressionBundleCatalog.Tile tile) {
        if (fixture || busy || SuppressionManager.instance().active()) return;
        busy = true;
        status = "Подготовка карты " + tile.index() + "…";
        ScreenRequestGate.Token token = requests.begin("two-layer-start");
        CompletableFuture.runAsync(() -> {
            try {
                SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.installCatalog(
                    client().gameDirectory.toPath(), catalog, tile.index() - 1);
                runOnClient(token, () -> {
                    try {
                        SuppressionManager.instance().start(client(), tile.bundle(), installed);
                        client().gui.setScreen(null);
                    } catch (Exception error) {
                        busy = false;
                        status = SuppressionManager.startFailureMessage(error);
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
        var s = WorkshopTwoLayerLayout.at(width, height);
        context.fill(0, 0, width, height, 0x99000000);
        WorkshopChrome.frame(context::fill, s.frame(), workshopTheme);
        text(context, "Two-layer · " + catalog.title(), s.title(), "text-primary");
        if (s.split() || !showPreview)
            context.fill(s.list().x(), s.list().y(), s.list().right(), s.list().bottom(), workshopTheme.color("surface-primary"));
        if (s.split() || showPreview) {
            var tile = catalog.tiles().isEmpty() ? null : catalog.tiles().get(selectedIndex);
            drawPreview(context, catalog, s.preview(), tile);
            text(context, tile == null ? "" : "#" + tile.index() + " · " + catalog.gridWide() + "×" + catalog.gridTall(),
                s.metadata(), "text-secondary");
        }
        var footer = new WorkshopLayout.Rect(s.footer().x(), s.footer().y(), s.footer().width() - 36, 20);
        text(context, status.isBlank() ? (selectedIndex + 1) + " / " + catalog.tiles().size() : status, footer,
            busy ? "info" : "text-secondary");
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private ScreenViewModel screenModel() {
        return ScreenViewModel.shell(
            CompanionUiLayout.Destination.TWO_LAYER, "Two-layer",
            List.of(CompanionI18n.translate("Выбор части")), status,
            busy ? ScreenViewModel.StatusKind.LOADING : ScreenViewModel.classifyStatus(status)
        );
    }

    @Override
    public void onClose() {
        if (!busy) client().gui.setScreen(parent);
    }

    @Override
    public void removed() {
        requests.detach();
        previewTexture.close();
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

    private static Minecraft client() {
        return Minecraft.getInstance();
    }
}
