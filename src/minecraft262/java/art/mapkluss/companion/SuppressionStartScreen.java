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
        SuppressionStartLayout.Layout layout = SuppressionStartLayout.calculate(width, height);
        int panelWidth = layout.panelWidth();
        int left = layout.left();
        int buttonWidth = layout.splitSources() ? (panelWidth - 8) / 2 : panelWidth;
        int stopWidth = Math.min(112, Math.max(80, panelWidth / 3));
        int sessionWidth = panelWidth - stopWidth - 8;
        addRenderableWidget(MapKlussButton.builder(Component.literal("Из облака"), button -> startCloud())
            .special().tooltip(CompanionI18n.text(manifest != null && manifest.hasSuppressionBundle() ? "Облачный план" : "Требуется арт с Two-layer файлами"))
            .dimensions(left, layout.cloudY(), buttonWidth, 20).enabledWhen(() -> !busy && !SuppressionManager.instance().active()
                && manifest != null && manifest.hasSuppressionBundle()).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Импорт ZIP"), button -> chooseLocalZip())
            .special()
            .dimensions(layout.splitSources() ? left + buttonWidth + 8 : left, layout.localY(), buttonWidth, 20)
            .enabledWhen(() -> !busy && !SuppressionManager.instance().active()).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal(sessionActionLabel()), button -> {
                resetStopConfirmation();
                SuppressionManager.instance().handleWorldAction(client());
                client().gui.setScreen(null);
            })
            .special().dimensions(left, layout.sessionY(), sessionWidth, 20)
            .visibleWhen(() -> SuppressionManager.instance().active())
            .enabledWhen(() -> !busy).build());
        stopButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Остановить"), button -> stopBuilding())
            .danger().navigationOrder(1000).dimensions(left + sessionWidth + 8, layout.sessionY(), stopWidth, 20)
            .visibleWhen(() -> SuppressionManager.instance().active())
            .enabledWhen(() -> !busy).build());
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(
            this, parent, left, layout.bottom(), () -> !busy
        ));
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
        MapKlussUi.drawBackdrop(context, width, height);
        SuppressionStartLayout.Layout layout = SuppressionStartLayout.calculate(width, height);
        int panelWidth = layout.panelWidth();
        int left = layout.left();
        MapKlussUi.drawPanelAt(
            context, left - 10, left + panelWidth + 10, layout.top(), layout.bottom()
        );
        MapKlussUi.drawLocalHeader(
            context, font, "TWO-LAYER", "",
            left - 4, left + panelWidth + 4, layout.top() + (layout.compact() ? 10 : 16)
        );
        int sourceTop = layout.cloudY() - 18;
        int sourceBottom = (layout.splitSources() ? layout.cloudY() : layout.localY()) + 26;
        MapKlussUi.drawSectionAt(context, font, "Источник плана", left, panelWidth, sourceTop,
            sourceBottom - sourceTop);
        MapKlussUi.drawDataStrip(
            context, left, left + panelWidth, layout.statusY() - 5,
            Math.min(layout.backY() - 4, layout.statusY() + 18)
        );
        String visibleStatus = status.isBlank() ? "Выберите источник" : status;
        MapKlussUi.drawWrappedCenteredIn(context, font, visibleStatus, width / 2, layout.statusY(),
            panelWidth - 24, layout.guidanceLines(),
            busy ? MapKlussUi.GOLD : MapKlussUi.statusColor(status));
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
