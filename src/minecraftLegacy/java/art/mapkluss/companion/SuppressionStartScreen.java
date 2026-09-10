package art.mapkluss.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class SuppressionStartScreen extends Screen {
    private final Screen parent;
    private final CompanionManifest manifest;
    private boolean fixture;
    private java.util.List<String> fixtureLines = java.util.List.of();
    private SuppressionStage fixtureStage;

    private boolean sessionActive() { return fixture ? sessionPreview != null : SuppressionManager.instance().active(); }
    private java.util.List<String> sessionLines() { return fixture ? fixtureLines : SuppressionManager.instance().hudLines(); }

    void applyDevelopmentSession(SuppressionBundleCatalog preview, java.util.List<String> lines, SuppressionStage stage) {
        fixture = true;
        sessionPreview = preview;
        fixtureLines = java.util.List.copyOf(lines);
        fixtureStage = stage;
    }

    private String status = "";
    private boolean busy;
    private boolean stopConfirmation;
    private MapKlussButton stopButton;
    private final ScreenRequestGate requests = new ScreenRequestGate();

    public SuppressionStartScreen(Screen parent, CompanionManifest manifest) {
        super(Text.literal("Two-layer Builder"));
        this.parent = parent;
        this.manifest = manifest;
    }

    private SuppressionBundle previewBundle;
    private SuppressionBundleCatalog sessionPreview;

    private WorkshopTheme workshopTheme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private final SuppressionPreviewTexture previewTexture = new SuppressionPreviewTexture();
    private boolean showPreview;

    private MapKlussButton layerButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r,
        java.util.function.BooleanSupplier enabled, boolean selected, Runnable action) {
        var builder = MapKlussButton.builder(CompanionI18n.text(label), button -> {
            if (enabled.getAsBoolean() && (!fixture || !id.startsWith("two_layer.") || id.equals("two_layer.preview"))) action.run();
        }).action(id).tooltip(CompanionI18n.text(label)).selected(selected)
            .dimensions(r.x(), r.y(), r.width(), r.height()).enabledWhen(() -> enabled.getAsBoolean() && (!fixture || !id.startsWith("two_layer.") || id.equals("two_layer.preview")));
        if (id.equals("two_layer.select_part") || id.equals("two_layer.resume")) builder.gold();
        if (id.equals("two_layer.stop")) builder.danger().navigationOrder(1000);
        return addDrawableChild(builder.build().workshop(workshopTheme, icon));
    }

    private WorkshopLayout.Rect part(WorkshopLayout.Rect r, int index, int count) {
        int w = (r.width() - (count - 1) * 4) / count;
        return new WorkshopLayout.Rect(r.x() + index * (w + 4), r.y(), w, r.height());
    }

    private void workshopNavigation(WorkshopTwoLayerLayout.Layout s) {
        try { workshopTheme = WorkshopTheme.of(CompanionConfig.load(client().runDirectory.toPath()).theme()); }
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
            () -> !busy, false, () -> client().setScreen(new WorkshopAppearanceScreen(this)));
        layerButton("global.back", "Назад", WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.navigation().right() - 24, s.navigation().y(), 24, 28),
            () -> !busy, false, this::close);
        layerButton("global.language", CompanionI18n.toggleLabel(client()), null,
            new WorkshopLayout.Rect(s.footer().right() - 32, s.footer().y(), 32, 20),
            () -> !busy, false, () -> { try { CompanionI18n.toggle(client()); init(); } catch (Exception ignored) { } });
    }

    private void text(DrawContext g, String value, WorkshopLayout.Rect r, String color) {
        WorkshopDraw.text(g, textRenderer, CompanionI18n.translate(value), r.x() + 4, r.y() + 7,
            Math.max(0, r.width() - 8), workshopTheme.color(color));
    }

    private void drawPreview(DrawContext g, SuppressionBundleCatalog value, WorkshopLayout.Rect area,
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
        clearChildren();
        var s = WorkshopTwoLayerLayout.at(width, height);
        workshopNavigation(s);
        var bundle = SuppressionManager.instance().previewBundle();
        if (!fixture && bundle != previewBundle) {
            previewTexture.close();
            previewBundle = bundle;
            sessionPreview = bundle == null ? null : SuppressionBundleCatalog.single(bundle);
        }
        if (!sessionActive()) {
        layerButton("two_layer.start_cloud", "Из облака", WorkshopIcon.DOWNLOAD, s.source(0),
            () -> !busy && !sessionActive() && manifest != null && manifest.hasSuppressionBundle(),
            false, this::startCloud);
        layerButton("two_layer.import_zip", "Импорт ZIP", WorkshopIcon.FOLDER, s.source(1),
            () -> !busy && !sessionActive(), false, this::chooseLocalZip);
        } else if (!s.split()) {
            layerButton("two_layer.preview", showPreview ? "Текущий этап" : "Превью", WorkshopIcon.LAYERS, s.paging(),
                () -> !busy, showPreview, () -> { showPreview = !showPreview; init(); });
        }
        layerButton("two_layer.resume", sessionActionLabel(), WorkshopIcon.CHECK, part(s.actions(), 0, 2),
            () -> !busy && sessionActive(), false, () -> {
                resetStopConfirmation();
                SuppressionManager.instance().handleWorldAction(client());
                client().setScreen(null);
            });
        stopButton = layerButton("two_layer.stop", stopConfirmation ? "Подтвердить" : "Остановить", WorkshopIcon.DELETE,
            part(s.actions(), 1, 2), () -> !busy && sessionActive(), false, this::stopBuilding);
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().setScreen(new CompanionLibraryScreen(parent));
            case LENS -> client().setScreen(new LensScreen(this, fixture));
            case SCAN -> client().setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this, fixture));
            case ACCOUNT -> client().setScreen(new CompanionAccountScreen(this, fixture));
            default -> { }
        }
    }

    private void startCloud() {
        if (manifest == null || !manifest.hasSuppressionBundle() || busy || SuppressionManager.instance().active()) return;
        resetStopConfirmation();
        busy = true;
        status = "Загрузка плана…";
        ScreenRequestGate.Token token = requests.begin("two-layer-start");
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) throw new java.io.IOException("Сначала войдите в MapKluss");
                SuppressionBundleCatalog catalog = SuppressionBundleService.downloadCatalog(runtime.apiClient(), manifest);
                runOnClient(token, () -> openCatalog(catalog, token));
            } catch (Exception error) {
                runOnClient(token, () -> {
                    busy = false;
                    status = "Не удалось загрузить план: " + readableError(error);
                });
            }
        });
    }

    private void chooseLocalZip() {
        if (busy || SuppressionManager.instance().active()) return;
        resetStopConfirmation();
        busy = true;
        status = "Выберите ZIP из MapKluss.";
        ScreenRequestGate.Token token = requests.begin("two-layer-start");
        CompletableFuture.runAsync(() -> {
            try {
                String selectedPath = chooseZipPath();
                if (selectedPath == null || selectedPath.isBlank()) {
                    runOnClient(token, () -> {
                        busy = false;
                        status = "Выбор отменён.";
                    });
                    return;
                }
                Path selected = Path.of(selectedPath);
                SuppressionBundleCatalog catalog = SuppressionBundleReader.readCatalog(selected);
                runOnClient(token, () -> openCatalog(catalog, token));
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not import local Two-layer ZIP.", error);
                runOnClient(token, () -> {
                    busy = false;
                    status = "Не удалось загрузить план: " + readableError(error);
                });
            }
        });
    }

    private static String chooseZipPath() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.zip"));
            filters.flip();
            return TinyFileDialogs.tinyfd_openFileDialog(
                "MapKluss Two-layer ZIP",
                Path.of(System.getProperty("user.home"), "Downloads").toString(),
                filters,
                "MapKluss ZIP (*.zip)",
                false
            );
        }
    }

    private static String readableError(Exception error) {
        return CompanionUiErrors.message("download", error);
    }

    private void finishStart(SuppressionBundle bundle, SuppressionBundleInstaller.Installed installed) {
        try {
            SuppressionManager.instance().start(client(), bundle, installed);
            client().setScreen(null);
        } catch (Exception error) {
            busy = false;
            status = SuppressionManager.startFailureMessage(error);
        }
    }

    private void openCatalog(SuppressionBundleCatalog catalog, ScreenRequestGate.Token token) {
        busy = false;
        if (catalog == null || catalog.tiles().isEmpty()) {
            status = "В архиве нет карт Two-layer.";
            return;
        }
        if (catalog.tiles().size() > 1) {
            client().setScreen(new SuppressionTileSelectScreen(this, catalog));
            return;
        }
        SuppressionBundle bundle = catalog.tiles().getFirst().bundle();
        busy = true;
        status = "Подготовка схемы…";
        CompletableFuture.runAsync(() -> {
            try {
                SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.installCatalog(client().runDirectory.toPath(), catalog, 0);
                runOnClient(token, () -> finishStart(bundle, installed));
            } catch (Exception error) {
                runOnClient(token, () -> {
                    busy = false;
                    status = "Не удалось подготовить план: " + readableError(error);
                });
            }
        });
    }

    private void stopBuilding() {
        if (busy || !sessionActive()) return;
        if (!stopConfirmation) {
            stopConfirmation = true;
            if (stopButton != null) stopButton.setMessage(Text.literal("Подтвердить"));
            status = "Прогресс будет удалён. Нажмите ещё раз.";
            return;
        }
        try {
            SuppressionManager.instance().stop(client());
            stopConfirmation = false;
            status = "Постройка остановлена.";
            init();
        } catch (Exception error) {
            stopConfirmation = false;
            if (stopButton != null) stopButton.setMessage(Text.literal("Остановить"));
            status = "Не удалось остановить Two-layer: " + readableError(error);
        }
    }

    private void resetStopConfirmation() {
        stopConfirmation = false;
        if (stopButton != null) stopButton.setMessage(Text.literal("Остановить"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var s = WorkshopTwoLayerLayout.at(width, height);
        context.fill(0, 0, width, height, 0x99000000);
        WorkshopChrome.frame(context::fill, s.frame(), workshopTheme);
        text(context, "Two-layer" + (sessionPreview != null ? " · " + sessionPreview.title()
            : manifest != null ? " · " + manifest.title() : ""), s.title(), "text-primary");
        if (s.split() || (sessionPreview != null && showPreview)) {
            drawPreview(context, sessionPreview, s.preview(), null);
            if (s.split()) text(context, sessionActionLabel(), s.metadata(), "text-secondary");
        }
        if (sessionPreview != null && (s.split() || !showPreview)) {
            var lines = sessionLines();
            for (int i = 0; i < lines.size() && (i + 1) * 22 <= s.list().height(); i++) {
                text(context, lines.get(i), new WorkshopLayout.Rect(s.list().x(), s.list().y() + i * 22,
                    s.list().width(), 22), i == 0 ? "text-primary" : "text-secondary");
            }
        }
        var footer = new WorkshopLayout.Rect(s.footer().x(), s.footer().y(), s.footer().width() - 36, 20);
        text(context, status.isBlank() ? (sessionActive() ? sessionActionLabel() : "План строительства") : status,
            footer, stopConfirmation ? "warning" : busy ? "info" : "text-secondary");
        super.render(context, mouseX, mouseY, delta);
    }

    private ScreenViewModel screenModel() {
        boolean active = sessionActive();
        String visibleStatus = active ? sessionActionLabel() : status;
        ScreenViewModel.StatusKind kind = busy
            ? ScreenViewModel.StatusKind.LOADING
            : active ? ScreenViewModel.StatusKind.SUCCESS : ScreenViewModel.classifyStatus(status);
        return ScreenViewModel.shell(
            CompanionUiLayout.Destination.TWO_LAYER, "Two-layer",
            java.util.List.of(CompanionI18n.translate("План строительства")), visibleStatus, kind
        );
    }



    @Override
    public void close() {
        if (!busy) client().setScreen(parent);
    }

    @Override
    public void removed() {
        requests.detach();
        if (stopConfirmation) { resetStopConfirmation(); status = ""; }
        previewTexture.close();
        super.removed();
    }

    private void runOnClient(ScreenRequestGate.Token token, Runnable task) {
        client().execute(() -> {
            if (requests.isCurrent(token) && client().currentScreen == this) task.run();
        });
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private String sessionActionLabel() {
        SuppressionStage stage = fixture ? fixtureStage : SuppressionManager.instance().stage();
        if (stage == null) return "Текущий шаг";
        return switch (stage) {
            case READY_NEXT -> "Дальше";
            case COMPLETE -> "Завершить";
            case BUILDING -> "Схема построена";
            case WAITING_ANCHOR -> "Задать опору · J";
            case ANCHOR_CONFIRM -> "Подтвердить · J";
            default -> "Продолжить";
        };
    }
}
