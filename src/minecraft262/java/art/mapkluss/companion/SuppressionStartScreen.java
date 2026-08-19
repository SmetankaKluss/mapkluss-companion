package art.mapkluss.companion;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SuppressionStartScreen extends Screen {
    private final Screen parent;
    private final CompanionManifest manifest;
    private String status = "";
    private boolean busy;
    private boolean stopConfirmation;
    private MapKlussButton stopButton;
    private final ScreenRequestGate requests = new ScreenRequestGate();

    public SuppressionStartScreen(Screen parent, CompanionManifest manifest) {
        super(Component.literal("Two-layer Builder"));
        this.parent = parent;
        this.manifest = manifest;
    }

    @Override
    protected void init() {
        requests.attach();
        clearWidgets();
        CompanionUiLayout.Shell shell = workflowShell();
        addNavigationControls(shell);
        CompanionUiLayout.Rect panel = taskPanel(shell);
        int gap = 6;
        int innerLeft = panel.x() + 12;
        int innerWidth = Math.max(1, panel.width() - 24);
        int sourceY = panel.y() + 54;
        int buttonWidth = Math.max(54, (innerWidth - gap) / 2);
        addRenderableWidget(MapKlussButton.builder(Component.literal("Из облака"), button -> startCloud())
            .action("two_layer.start_cloud")
            .special().tooltip(CompanionI18n.text(manifest != null && manifest.hasSuppressionBundle() ? "Облачный план" : "Требуется арт с Two-layer файлами"))
            .dimensions(innerLeft, sourceY, buttonWidth, 22).enabledWhen(() -> !busy && !SuppressionManager.instance().active()
                && manifest != null && manifest.hasSuppressionBundle()).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Импорт ZIP"), button -> chooseLocalZip())
            .action("two_layer.import_zip")
            .special()
            .dimensions(innerLeft + buttonWidth + gap, sourceY, innerWidth - buttonWidth - gap, 22)
            .enabledWhen(() -> !busy && !SuppressionManager.instance().active()).build());

        int footerY = panel.bottom() - 34;
        int backWidth = Math.min(88, Math.max(64, innerWidth / 5));
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Назад"), button -> client().gui.setScreen(parent))
            .action("global.back")
            .dimensions(innerLeft, footerY, backWidth, 22).enabledWhen(() -> !busy).build());

        int stopWidth = Math.min(104, Math.max(76, innerWidth / 4));
        int sessionX = innerLeft + backWidth + gap;
        int sessionWidth = Math.max(56, innerWidth - backWidth - stopWidth - gap * 2);
        addRenderableWidget(MapKlussButton.builder(Component.literal(sessionActionLabel()), button -> {
                resetStopConfirmation();
                SuppressionManager.instance().handleWorldAction(client());
                client().gui.setScreen(null);
            })
            .action("two_layer.resume")
            .special().dimensions(sessionX, footerY, sessionWidth, 22)
            .visibleWhen(() -> SuppressionManager.instance().active())
            .enabledWhen(() -> !busy).build());
        stopButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Остановить"), button -> stopBuilding())
            .action("two_layer.stop")
            .danger().navigationOrder(1000).dimensions(sessionX + sessionWidth + gap, footerY, stopWidth, 22)
            .visibleWhen(() -> SuppressionManager.instance().active())
            .enabledWhen(() -> !busy).build());
        addRenderableWidget(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destination.name()))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).enabledWhen(() -> !busy).build());
        }
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

    private void startCloud() {
        if (manifest == null || !manifest.hasSuppressionBundle() || busy) return;
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
        if (busy) return;
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
            client().gui.setScreen(null);
        } catch (Exception error) {
            busy = false;
            status = "Не удалось начать: " + readableError(error);
        }
    }

    private void openCatalog(SuppressionBundleCatalog catalog, ScreenRequestGate.Token token) {
        busy = false;
        if (catalog == null || catalog.tiles().isEmpty()) {
            status = "В архиве нет карт Two-layer.";
            return;
        }
        if (catalog.tiles().size() > 1) {
            client().gui.setScreen(new SuppressionTileSelectScreen(this, catalog));
            return;
        }
        SuppressionBundle bundle = catalog.tiles().getFirst().bundle();
        busy = true;
        status = "Подготовка схемы…";
        CompletableFuture.runAsync(() -> {
            try {
                SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.install(client().gameDirectory.toPath(), bundle);
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
        if (busy || !SuppressionManager.instance().active()) return;
        if (!stopConfirmation) {
            stopConfirmation = true;
            if (stopButton != null) stopButton.setMessage(Component.literal("Подтвердить"));
            status = "Прогресс будет удалён. Нажмите ещё раз.";
            return;
        }
        try {
            SuppressionManager.instance().stop(client());
            stopConfirmation = false;
            status = "Постройка остановлена.";
        } catch (Exception error) {
            stopConfirmation = false;
            if (stopButton != null) stopButton.setMessage(Component.literal("Остановить"));
            status = "Не удалось остановить Two-layer: " + readableError(error);
        }
    }

    private void resetStopConfirmation() {
        stopConfirmation = false;
        if (stopButton != null) stopButton.setMessage(Component.literal("Остановить"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        ScreenViewModel model = screenModel();
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, font, width, height, model, false
        );
        CompanionUiLayout.Rect panel = taskPanel(shell);
        MapKlussUi.drawPanelAt(context, panel.x(), panel.right(), panel.y(), panel.bottom());
        MapKlussUi.drawLeft(context, font, "План строительства", panel.x() + 12, panel.y() + 12,
            panel.width() - 24, MapKlussUi.WHITE);
        MapKlussUi.drawSectionAt(context, font, "Источник", panel.x() + 12, panel.width() - 24,
            panel.y() + 38, 48);
        int statusTop = panel.y() + 88;
        int statusBottom = panel.bottom() - 42;
        MapKlussUi.drawDataStrip(context, panel.x() + 12, panel.right() - 12, statusTop, statusBottom);
        String visibleStatus = status.isBlank() ? "Выберите источник" : status;
        if (SuppressionManager.instance().active()) {
            visibleStatus = "Текущий этап: " + sessionActionLabel();
        }
        MapKlussUi.drawWrappedCenteredIn(context, font, visibleStatus, panel.x() + panel.width() / 2,
            statusTop + Math.max(7, (statusBottom - statusTop) / 2 - 4), panel.width() - 48, 2,
            MapKlussUi.statusColor(model.statusKind()));
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private ScreenViewModel screenModel() {
        boolean active = SuppressionManager.instance().active();
        String visibleStatus = active ? sessionActionLabel() : status;
        ScreenViewModel.StatusKind kind = busy
            ? ScreenViewModel.StatusKind.LOADING
            : active ? ScreenViewModel.StatusKind.SUCCESS : ScreenViewModel.classifyStatus(status);
        return ScreenViewModel.shell(
            CompanionUiLayout.Destination.TWO_LAYER, "Two-layer",
            java.util.List.of(CompanionI18n.translate("План строительства")), visibleStatus, kind
        );
    }

    private CompanionUiLayout.Shell workflowShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect taskPanel(CompanionUiLayout.Shell shell) {
        return CompanionUiLayout.focusedPanel(shell.content(), 520, 210);
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

    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    private static String sessionActionLabel() {
        SuppressionStage stage = SuppressionManager.instance().stage();
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
