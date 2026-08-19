package art.mapkluss.companion;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.concurrent.CompletableFuture;

final class CompanionAccountScreen extends Screen {
    private final Screen parent;
    private CompanionRuntime runtime;
    private CompanionSessionInfo session;
    private String status = "";
    private boolean detailsVisible;
    private boolean logoutArmed;

    CompanionAccountScreen(Screen parent) {
        super(CompanionI18n.text("Аккаунт"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        refreshSession();
        CompanionUiLayout.Shell shell = accountShell();
        addNavigationControls(shell);

        CompanionUiLayout.Rect content = shell.content();
        int x = content.x() + 16;
        int available = Math.max(120, content.width() - 32);
        int cardWidth = Math.min(460, available);
        int gap = 6;
        int buttonWidth = Math.max(72, (cardWidth - gap) / 2);
        int y = accountActionsY(content);

        MapKlussButton.Builder account = MapKlussButton.builder(CompanionI18n.text(isSignedIn() ? "Выйти" : "Войти"), button -> accountAction())
            .action(isSignedIn() ? "account.logout" : "account.login_start")
            .selected(!isSignedIn()).dimensions(x, y, buttonWidth, 22);
        if (isSignedIn()) account.danger();
        addRenderableWidget(account.build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Сайт облака"), button -> openSite("/cloud"))
            .action("account.open_site")
            .technical().dimensions(x + buttonWidth + gap, y, buttonWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Синхронизация"), button -> client().gui.setScreen(new CompanionLibraryScreen(this, true)))
            .action("account.sync")
            .enabledWhen(this::isSignedIn).dimensions(x, y + 30, buttonWidth, 22).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Проверить обновление"), button -> checkForUpdate())
            .action("account.update")
            .dimensions(x + buttonWidth + gap, y + 30, buttonWidth, 22).build());
        boolean telemetryEnabled = CompanionTelemetryManager.consent() == CompanionTelemetrySettings.Consent.ENABLED;
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text(telemetryEnabled ? "Анонимная статистика: вкл." : "Анонимная статистика: выкл."), button -> toggleTelemetry())
            .action("account.telemetry")
            .selected(telemetryEnabled).dimensions(x, y + 60, cardWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text(detailsVisible ? "Скрыть детали" : "Детали"), button -> {
            detailsVisible = !detailsVisible;
            init();
        }).action("account.details").dimensions(x, y + 88, cardWidth, 20).build());

        addRenderableWidget(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addRenderableWidget(MapKlussButton.builder(Component.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destinationLabel(destination)))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().gui.setScreen(new LensScreen(this));
            case SCAN -> client().gui.setScreen(new ScanScreen(this));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> { }
            default -> { }
        }
    }

    private String destinationLabel(CompanionUiLayout.Destination destination) {
        return switch (destination) {
            case LIBRARY -> "Библиотека";
            case LENS -> "Lens";
            case SCAN -> "Скан";
            case TRACKER -> "Трекер";
            case ACCOUNT -> "Аккаунт";
            default -> destination.name();
        };
    }

    private void refreshSession() {
        try {
            runtime = CompanionRuntime.create(client());
            session = runtime.sessionInfo();
        } catch (Exception error) {
            runtime = null;
            session = null;
            status = CompanionUiErrors.message("login", error);
        }
    }

    private boolean isSignedIn() {
        return runtime != null && runtime.sessionStore().hasAccessToken();
    }

    private void accountAction() {
        if (!isSignedIn()) {
            client().gui.setScreen(new DeviceLoginScreen(this));
            return;
        }
        if (!logoutArmed) {
            logoutArmed = true;
            status = "Нажмите ещё раз для подтверждения";
            init();
            return;
        }
        status = "Выход из аккаунта...";
        CompletableFuture.runAsync(() -> {
            try {
                String warning = runtime.revokeAndClearSession();
                client().execute(() -> {
                    LensManager.instance().clearForLogout();
                    logoutArmed = false;
                    status = warning == null ? "Вы вышли. Установленные файлы остались на диске." : "Локальный выход. " + warning;
                    init();
                });
            } catch (Exception error) {
                client().execute(() -> status = CompanionUiErrors.message("login", error));
            }
        });
    }

    private void openSite(String path) {
        try {
            CompanionRuntime current = runtime == null ? CompanionRuntime.create(client()) : runtime;
            Util.getPlatform().openUri(current.config().siteUri(path));
        } catch (Exception error) {
            status = CompanionUiErrors.message("site", error);
        }
    }

    private void checkForUpdate() {
        status = "Проверяю обновления...";
        String currentVersion = FabricLoader.getInstance()
            .getModContainer(MapKlussCompanionClient.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");
        new CompanionReleaseChecker().findUpdate(currentVersion).thenAccept(release -> client().execute(() -> {
            if (release.isPresent()) {
                client().gui.setScreen(new CompanionUpdateScreen(this, release.get().version()));
            } else {
                status = "Установлена актуальная версия.";
                init();
            }
        }));
    }

    private void toggleTelemetry() {
        try {
            if (CompanionTelemetryManager.consent() == CompanionTelemetrySettings.Consent.ENABLED) {
                CompanionTelemetryManager.disable();
                status = "Анонимная статистика отключена.";
            } else {
                CompanionTelemetryManager.enable();
                status = "Анонимная статистика включена.";
            }
            init();
        } catch (Exception error) {
            status = "Не удалось сохранить выбор.";
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, font, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.ACCOUNT, CompanionI18n.translate("Аккаунт"), status), false
        );
        CompanionUiLayout.Rect content = shell.content();
        int x = content.x() + 16;
        int available = Math.max(120, content.width() - 32);
        int cardWidth = Math.min(460, available);
        int y = content.y() + 14;
        context.fill(x, y, x + cardWidth, y + Math.min(190, content.height() - 28), UiTheme.SURFACE_RAISED);
        context.fill(x, y, x + 3, y + Math.min(190, content.height() - 28), isSignedIn() ? UiTheme.LIME : UiTheme.AMBER);
        MapKlussUi.drawLeft(context, font, CompanionI18n.translate(isSignedIn() ? "Cloud подключён" : "Вход не выполнен"), x + 14, y + 12, cardWidth - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, font,
            CompanionI18n.translate(isSignedIn() ? "Библиотека, Lens и прогресс синхронизируются" : "Войдите через код на сайте MapKluss"),
            x + 14, y + 29, cardWidth - 28, MapKlussUi.MUTED);
        if (detailsVisible && session != null) {
            int detailsY = accountActionsY(content) + 116;
            if (detailsY + 39 <= content.bottom() - 8) {
                MapKlussUi.drawLeft(context, font, "ID: " + session.shortUserId(), x + 14, detailsY, cardWidth - 28, MapKlussUi.MUTED);
                MapKlussUi.drawLeft(context, font, CompanionI18n.translate("Сохранено: ") + safe(session.savedAt()), x + 14, detailsY + 15, cardWidth - 28, MapKlussUi.DIM);
                MapKlussUi.drawLeft(context, font, CompanionI18n.translate("Истекает: ") + safe(session.expiresAt()), x + 14, detailsY + 30, cardWidth - 28, MapKlussUi.DIM);
            }
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.ACCOUNT);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private CompanionUiLayout.Shell accountShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private int accountActionsY(CompanionUiLayout.Rect content) {
        int offset = CompanionUiLayout.clamp(content.height() - 96, 54, 94);
        return content.y() + offset;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }
}
