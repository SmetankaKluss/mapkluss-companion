package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public final class DeviceLoginScreen extends Screen {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private DeviceStartResponse login;
    private ClickableWidget copyButton;
    private ClickableWidget pollButton;
    private ClickableWidget autoPollButton;
    private String status = "";
    private boolean autoPollEnabled = true;
    private volatile int pollLoopGeneration;
    private volatile boolean closed;
    private long loginExpiresAtMs;
    private CompanionSessionInfo sessionInfo;

    public DeviceLoginScreen(Screen parent) {
        super(Text.literal("Вход MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        closed = false;
        refreshSessionInfo();
        clearChildren();
        CompanionUiLayout.Shell shell = loginShell();
        CompanionUiLayout.Rect panel = loginPanel(shell);
        addLoginControls(shell, panel);
        addNavigationControls(shell);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
        updateButtons();
    }

    private void addLoginControls(CompanionUiLayout.Shell shell, CompanionUiLayout.Rect panel) {
        int gap = 6;
        int x = panel.x() + 14;
        int innerWidth = Math.max(1, panel.width() - 28);
        int row1 = panel.bottom() - 54;
        int row2 = panel.bottom() - 28;
        int third = Math.max(48, (innerWidth - gap * 2) / 3);
        int half = Math.max(68, (innerWidth - gap) / 2);
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Получить код"), button -> startLogin()).selected(true)
            .action("account.login_start")
            .dimensions(x, row1, third, 22).build());
        autoPollButton = addDrawableChild(MapKlussButton.builder(autoPollButtonText(), button -> toggleAutoPoll())
            .action("account.login_auto_poll")
            .technical()
            .selected(autoPollEnabled)
            .dimensions(x + third + gap, row1, third, 22).build());
        pollButton = addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Проверить"), button -> pollLogin())
            .action("account.login_poll")
            .technical().dimensions(x + (third + gap) * 2, row1, innerWidth - (third + gap) * 2, 22).build());
        copyButton = addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Копировать код"), button -> copyUserCode())
            .action("account.copy_code")
            .technical().dimensions(x, row2, half, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Открыть сайт"), button -> openDevicePage())
            .action("account.open_site")
            .dimensions(x + half + gap, row2, innerWidth - half - gap, 22).build());
    }

    private void startLogin() {
        final int generation = ++pollLoopGeneration;
        status = "Создаю код входа...";
        MapKlussCompanionClient.LOGGER.info("Device login start requested.");
        CompletableFuture.runAsync(() -> {
            try {
                CompanionRuntime runtime = CompanionRuntime.create(client());
                DeviceStartResponse response = runtime.apiClient().startDeviceLogin();
                MapKlussCompanionClient.LOGGER.info(
                    "Device login code created: expiresIn={}s poll={}s.", response.expiresIn(), response.interval()
                );
                runOnClient(generation, () -> {
                    login = response;
                    loginExpiresAtMs = System.currentTimeMillis() + Math.max(1, response.expiresIn()) * 1000L;
                    status = "Код готов. Откройте сайт и подтвердите вход.";
                    updateButtons();
                    if (autoPollEnabled) {
                        startAutoPollLoop();
                    }
                });
            } catch (Exception e) {
                MapKlussCompanionClient.LOGGER.error("Device login start failed.", e);
                runOnClient(generation, () -> status = CompanionUiErrors.message("login", e));
            }
        });
    }

    private void pollLogin() {
        if (login == null) {
            status = "Сначала нажмите «Получить код»";
            return;
        }
        final int generation = pollLoopGeneration;
        status = "Проверяю подтверждение...";
        CompletableFuture.runAsync(() -> {
            try {
                pollLoginOnce(generation);
            } catch (Exception e) {
                runOnClient(generation, () -> status = CompanionUiErrors.message("login", e));
            }
        });
    }

    private void toggleAutoPoll() {
        autoPollEnabled = !autoPollEnabled;
        if (autoPollEnabled && login != null) {
            status = "Автопроверка включена.";
            startAutoPollLoop();
        } else if (!autoPollEnabled) {
            pollLoopGeneration++;
            status = "Автопроверка выключена.";
        }
        updateButtons();
    }

    private void startAutoPollLoop() {
        if (login == null) return;
        final int generation = ++pollLoopGeneration;
        status = "Жду подтверждение на сайте...";
        updateButtons();
        CompletableFuture.runAsync(() -> {
            while (autoPollEnabled && login != null && generation == pollLoopGeneration) {
                if (System.currentTimeMillis() >= loginExpiresAtMs) {
                    runOnClient(generation, () -> {
                        status = "Код истек. Нажмите Получить код еще раз.";
                        login = null;
                        updateButtons();
                    });
                    return;
                }
                try {
                    Thread.sleep(Math.max(1, login.interval()) * 1000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!autoPollEnabled || login == null || generation != pollLoopGeneration) return;
                try {
                    if (pollLoginOnce(generation)) return;
                } catch (Exception e) {
                    runOnClient(generation, () -> {
                        status = CompanionUiErrors.message("login", e);
                        updateButtons();
                    });
                    return;
                }
            }
        });
    }

    private boolean pollLoginOnce(int generation) throws Exception {
        if (!isCurrent(generation) || login == null) return true;
        CompanionRuntime runtime = CompanionRuntime.create(client());
        DevicePollResponse response = runtime.apiClient().pollDeviceLogin(login.deviceCode());
        if (!isCurrent(generation)) return true;
        if ("approved".equals(response.status()) && response.accessToken() != null) {
            MapKlussCompanionClient.LOGGER.info("Device login approved for user {}.", response.userId());
            runOnClient(generation, () -> {
                try {
                    runtime.saveSession(response.accessToken(), response.userId());
                    CompanionTelemetryManager.record(CompanionTelemetryEvent.LOGIN_COMPLETED);
                    pollLoopGeneration++;
                    login = null;
                    sessionInfo = runtime.sessionInfo();
                    status = "Вход подтвержден. Возвращаю в библиотеку...";
                    updateButtons();
                    client().setScreen(parent);
                } catch (Exception error) {
                    status = CompanionUiErrors.message("login", error);
                    updateButtons();
                }
            });
            return true;
        }
        if ("expired".equalsIgnoreCase(response.status()) || "denied".equalsIgnoreCase(response.status())) {
            MapKlussCompanionClient.LOGGER.warn("Device login ended with status {}.", response.status());
            runOnClient(generation, () -> {
                pollLoopGeneration++;
                login = null;
                status = loginStatus(response.status());
                updateButtons();
            });
            return true;
        }
        MapKlussCompanionClient.LOGGER.info("Device login poll status: {}.", response.status());
        runOnClient(generation, () -> status = loginStatus(response.status()));
        return false;
    }

    private void openDevicePage() {
        try {
            CompanionRuntime runtime = CompanionRuntime.create(client());
            String path = "/device";
            if (login != null && login.userCode() != null && !login.userCode().isBlank()) {
                path += "?code=" + login.userCode();
            }
            Util.getOperatingSystem().open(runtime.config().siteUri(path));
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void copyUserCode() {
        if (login == null || login.userCode() == null || login.userCode().isBlank()) {
            status = "Сначала нажмите «Получить код»";
            return;
        }
        client().keyboard.setClipboard(login.userCode());
        status = "Код скопирован";
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.ACCOUNT, CompanionI18n.translate("Аккаунт"),
                java.util.List.of(CompanionI18n.translate("Вход")), status), false
        );
        CompanionUiLayout.Rect panel = loginPanel(shell);
        context.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), UiTheme.SURFACE_RAISED);
        context.fill(panel.x(), panel.y(), panel.x() + 3, panel.bottom(), login == null ? UiTheme.AMBER : UiTheme.LIME);
        int x = panel.x() + 14;
        int textWidth = Math.max(1, panel.width() - 28);
        MapKlussUi.drawLeft(context, textRenderer, login == null ? "Вход в MapKluss" : "Подтвердите вход на сайте", x, panel.y() + 13, textWidth, MapKlussUi.WHITE);
        if (panel.height() >= 180) {
            MapKlussUi.drawLeft(context, textRenderer, sessionSummary(), x, panel.y() + 31, textWidth, sessionColor());
        }
        if (login != null) {
            int codeY = panel.height() >= 180 ? panel.y() + 62 : panel.y() + 31;
            context.fill(x, codeY - 8, panel.right() - 14, codeY + 28, UiTheme.SURFACE_INPUT);
            MapKlussUi.drawCenteredIn(context, textRenderer, login.userCode(), panel.x() + panel.width() / 2, codeY, textWidth, MapKlussUi.ACCENT);
            if (panel.height() >= 180) {
                String timing = CompanionI18n.english(client())
                    ? "Expires in " + remainingSeconds() + "s / checks every " + login.interval() + "s / " + (autoPollEnabled ? "automatically" : "manually")
                    : "Истекает через " + remainingSeconds() + "с / проверка каждые " + login.interval() + "с / " + (autoPollEnabled ? "автоматически" : "вручную");
                MapKlussUi.drawCenteredIn(context, textRenderer, timing, panel.x() + panel.width() / 2, codeY + 16, textWidth, MapKlussUi.MUTED);
            }
        } else {
            int emptyY = panel.height() >= 180 ? panel.y() + 70 : panel.y() + 38;
            MapKlussUi.drawCenteredIn(context, textRenderer, "Получите код и подтвердите его на mapkluss.art", panel.x() + panel.width() / 2, emptyY, textWidth, MapKlussUi.MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.ACCOUNT);
    }

    private void updateButtons() {
        if (copyButton != null) copyButton.active = login != null && login.userCode() != null && !login.userCode().isBlank();
        if (pollButton != null) pollButton.active = login != null;
        if (autoPollButton != null) {
            autoPollButton.setMessage(autoPollButtonText());
            if (autoPollButton instanceof MapKlussButton mapKlussButton) {
                mapKlussButton.setSelected(autoPollEnabled);
            }
        }
    }

    private Text autoPollButtonText() {
        return CompanionI18n.text(autoPollEnabled ? "Авто: вкл" : "Авто: выкл");
    }

    private CompanionUiLayout.Shell loginShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect loginPanel(CompanionUiLayout.Shell shell) {
        return CompanionUiLayout.focusedPanel(shell.content(), 560, 270);
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destinationLabel(destination)))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().setScreen(new LensScreen(this));
            case SCAN -> client().setScreen(new ScanScreen(this));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this));
            case ACCOUNT -> client().setScreen(parent);
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

    private String loginStatus(String value) {
        return switch (value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)) {
            case "pending" -> "Ожидание подтверждения";
            case "approved" -> "Вход подтвержден";
            case "expired" -> "Код истёк";
            case "denied" -> "Вход отклонён";
            default -> CompanionI18n.english(client()) ? "Waiting for approval" : "Ожидание подтверждения";
        };
    }

    private long remainingSeconds() {
        if (login == null) return 0;
        return Math.max(0, (loginExpiresAtMs - System.currentTimeMillis()) / 1000L);
    }

    private String sessionSummary() {
        try {
            CompanionSessionInfo session = sessionInfo;
            if (session == null) return "Сессия: недоступна";
            if (!session.isSignedIn()) return "Сессия: вход не выполнен";
            String saved = formatInstant(session.savedAt());
            if (CompanionI18n.english(client())) {
                String expiry = session.expiresAt() == null ? "expiry unknown" : "until " + formatInstant(session.expiresAt());
                String state = session.isExpired() ? "expired" : "active";
                return "Session: " + session.shortUserId() + " / " + state + " / saved " + saved + " / " + expiry;
            }
            String expiry = session.expiresAt() == null ? "срок неизвестен" : "до " + formatInstant(session.expiresAt());
            String state = session.isExpired() ? "истекла" : "активна";
            return "Сессия: " + session.shortUserId() + " / " + state + " / сохранена " + saved + " / " + expiry;
        } catch (Exception e) {
            return "Сессия: недоступна";
        }
    }

    private int sessionColor() {
        try {
            CompanionSessionInfo session = sessionInfo;
            if (session == null) return 0xFFD9C27A;
            if (!session.isSignedIn()) return 0xFFD9C27A;
            return session.isExpired() ? 0xFFFF9B7D : 0xFF8FE388;
        } catch (Exception e) {
            return 0xFFD9C27A;
        }
    }

    private String formatInstant(String value) {
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return value == null ? "неизвестно" : value;
        }
    }

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private void refreshSessionInfo() {
        try {
            sessionInfo = CompanionRuntime.create(client()).sessionInfo();
        } catch (Exception ignored) {
            sessionInfo = null;
        }
    }

    private boolean isCurrent(int generation) {
        return !closed && generation == pollLoopGeneration;
    }

    private void runOnClient(int generation, Runnable task) {
        client().execute(() -> {
            if (isCurrent(generation) && client().currentScreen == this) task.run();
        });
    }

    @Override
    public void removed() {
        closed = true;
        pollLoopGeneration++;
        super.removed();
    }

    @Override
    public void close() {
        closed = true;
        pollLoopGeneration++;
        super.close();
    }
}
