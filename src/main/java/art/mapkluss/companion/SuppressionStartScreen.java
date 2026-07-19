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
    private String status = "";
    private boolean busy;
    private boolean stopConfirmation;
    private MapKlussButton stopButton;

    public SuppressionStartScreen(Screen parent, CompanionManifest manifest) {
        super(Text.literal("Two-layer Builder"));
        this.parent = parent;
        this.manifest = manifest;
    }

    @Override
    protected void init() {
        clearChildren();
        SuppressionStartLayout.Layout layout = SuppressionStartLayout.calculate(width, height);
        int panelWidth = layout.panelWidth();
        int left = layout.left();
        int buttonWidth = layout.splitSources() ? (panelWidth - 8) / 2 : panelWidth;
        int stopWidth = Math.min(112, Math.max(80, panelWidth / 3));
        int sessionWidth = panelWidth - stopWidth - 8;
        addDrawableChild(MapKlussButton.builder(Text.literal("Из облака"), button -> startCloud())
            .gold().tooltip(CompanionI18n.text(manifest != null && manifest.hasSuppressionBundle() ? "Облачный план" : "Требуется арт с Two-layer файлами"))
            .dimensions(left, layout.cloudY(), buttonWidth, 20).enabledWhen(() -> !busy && !SuppressionManager.instance().active()
                && manifest != null && manifest.hasSuppressionBundle()).build());
        addDrawableChild(MapKlussButton.builder(Text.literal("Импорт ZIP MapKluss"), button -> chooseLocalZip())
            .dimensions(layout.splitSources() ? left + buttonWidth + 8 : left, layout.localY(), buttonWidth, 20)
            .enabledWhen(() -> !busy && !SuppressionManager.instance().active()).build());
        addDrawableChild(MapKlussButton.builder(Text.literal(sessionActionLabel()), button -> {
                resetStopConfirmation();
                SuppressionManager.instance().handleWorldAction(client());
                client().setScreen(null);
            })
            .gold().dimensions(left, layout.sessionY(), sessionWidth, 20)
            .visibleWhen(() -> SuppressionManager.instance().active())
            .enabledWhen(() -> !busy).build());
        stopButton = addDrawableChild(MapKlussButton.builder(Text.literal("Остановить"), button -> stopBuilding())
            .danger().navigationOrder(1000).dimensions(left + sessionWidth + 8, layout.sessionY(), stopWidth, 20)
            .visibleWhen(() -> SuppressionManager.instance().active())
            .enabledWhen(() -> !busy).build());
        addDrawableChild(MapKlussUi.languageButton(this));
        int panelBottom = Math.min(layout.bottom(), MapKlussUi.panelBottom(height));
        addDrawableChild(MapKlussUi.backButton(this, parent, left, panelBottom, () -> !busy));
    }

    private void startCloud() {
        if (manifest == null || !manifest.hasSuppressionBundle() || busy) return;
        resetStopConfirmation();
        busy = true;
        status = "Загрузка плана…";
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                if (!runtime.sessionStore().hasAccessToken()) throw new java.io.IOException("Сначала войдите в MapKluss");
                SuppressionBundleCatalog catalog = SuppressionBundleService.downloadCatalog(runtime.apiClient(), manifest);
                client().execute(() -> openCatalog(catalog));
            } catch (Exception error) {
                client().execute(() -> {
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
        CompletableFuture.runAsync(() -> {
            try {
                String selectedPath = chooseZipPath();
                if (selectedPath == null || selectedPath.isBlank()) {
                    client().execute(() -> {
                        busy = false;
                        status = "Выбор отменён.";
                    });
                    return;
                }
                Path selected = Path.of(selectedPath);
                SuppressionBundleCatalog catalog = SuppressionBundleReader.readCatalog(selected);
                client().execute(() -> openCatalog(catalog));
            } catch (Exception error) {
                MapKlussCompanionClient.LOGGER.warn("Could not import local Two-layer ZIP.", error);
                client().execute(() -> {
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
            status = "Не удалось начать: " + readableError(error);
        }
    }

    private void openCatalog(SuppressionBundleCatalog catalog) {
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
                SuppressionBundleInstaller.Installed installed = SuppressionBundleInstaller.install(client().runDirectory.toPath(), bundle);
                client().execute(() -> finishStart(bundle, installed));
            } catch (Exception error) {
                client().execute(() -> {
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
            if (stopButton != null) stopButton.setMessage(Text.literal("Подтвердить"));
            status = "Прогресс будет удалён. Нажмите ещё раз.";
            return;
        }
        try {
            SuppressionManager.instance().stop(client());
            stopConfirmation = false;
            status = "Постройка остановлена.";
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
        SuppressionStartLayout.Layout layout = SuppressionStartLayout.calculate(width, height);
        int panelWidth = layout.panelWidth();
        int left = layout.left();
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, layout.top(), Math.min(layout.bottom(), MapKlussUi.panelBottom(height)));
        MapKlussUi.drawHeader(context, textRenderer, "TWO-LAYER", "", width, layout.top() + 11);
        MapKlussUi.drawWrappedCenteredIn(context, textRenderer, status, width / 2, layout.statusY(), panelWidth - 16,
            2, busy ? MapKlussUi.GOLD : MapKlussUi.statusColor(status));
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (!busy) client().setScreen(parent);
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
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
