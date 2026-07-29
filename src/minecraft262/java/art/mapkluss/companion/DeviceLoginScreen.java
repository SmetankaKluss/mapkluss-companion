package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public final class DeviceLoginScreen extends Screen {
    private static final int PANEL_WIDTH = 1120;
    private static final int SECTION_WIDTH = 1120;
    private static final int ACTION_ROWS = 2;
    private static final int ACTION_ROW_HEIGHT = 34;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int ACTION_BOTTOM_MARGIN = 32;
    private static final int ACTION_TOP = 154;
    private static final int SIDE_RAIL_WIDTH = 142;
    private static final int SIDE_RAIL_GAP = 22;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private DeviceStartResponse login;
    private AbstractWidget copyButton;
    private AbstractWidget pollButton;
    private AbstractWidget autoPollButton;
    private String status = "";
    private boolean autoPollEnabled = true;
    private volatile int pollLoopGeneration;
    private volatile boolean closed;
    private long loginExpiresAtMs;
    private CompanionSessionInfo sessionInfo;

    public DeviceLoginScreen(Screen parent) {
        super(Component.literal("Вход MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        closed = false;
        refreshSessionInfo();
        clearWidgets();
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int gap = 4;
        if (sideRailLayout(panelWidth, left)) {
            addSideRailControls(sideRailLeft(panelWidth, left));
        } else {
            int buttonWidth = Math.max(54, (panelWidth - gap * 2) / 3);
            int halfWidth = Math.max(80, (panelWidth - gap) / 2);
            int row1 = actionTop();
            int row2 = row1 + ACTION_ROW_HEIGHT;
            addRenderableWidget(MapKlussButton.builder(Component.literal("Получить код"), button -> startLogin()).gold()
                .dimensions(left, row1, buttonWidth, 20).build());
            autoPollButton = addRenderableWidget(MapKlussButton.builder(autoPollButtonText(), button -> toggleAutoPoll())
                .technical()
                .selected(autoPollEnabled)
                .dimensions(left + buttonWidth + gap, row1, buttonWidth, 20).build());
            pollButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Проверить"), button -> pollLogin())
                .technical().dimensions(left + (buttonWidth + gap) * 2, row1, buttonWidth, 20).build());
            copyButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Копировать"), button -> copyUserCode())
                .technical().dimensions(left, row2, halfWidth, 20).build());
            addRenderableWidget(MapKlussButton.builder(Component.literal("Открыть сайт"), button -> openDevicePage())
                .technical().dimensions(left + halfWidth + gap, row2, panelWidth - halfWidth - gap, 20).build());
        }
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left));
        updateButtons();
    }

    private void addSideRailControls(int railLeft) {
        addRenderableWidget(MapKlussButton.builder(Component.literal("Получить код"), button -> startLogin()).gold()
            .dimensions(railLeft, 78, SIDE_RAIL_WIDTH, 20).build());
        autoPollButton = addRenderableWidget(MapKlussButton.builder(autoPollButtonText(), button -> toggleAutoPoll())
            .technical()
            .selected(autoPollEnabled)
            .dimensions(railLeft, 104, SIDE_RAIL_WIDTH, 20).build());

        pollButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Проверить"), button -> pollLogin())
            .technical().dimensions(railLeft, 162, SIDE_RAIL_WIDTH, 20).build());
        copyButton = addRenderableWidget(MapKlussButton.builder(Component.literal("Копировать код"), button -> copyUserCode())
            .technical().dimensions(railLeft, 188, SIDE_RAIL_WIDTH, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal("Открыть сайт"), button -> openDevicePage())
            .technical().dimensions(railLeft, 214, SIDE_RAIL_WIDTH, 20).build());

    }

    private int actionTop() {
        int rowSpan = Math.max(0, ACTION_ROWS - 1) * ACTION_ROW_HEIGHT + ACTION_BUTTON_HEIGHT;
        int maxTop = height - ACTION_BOTTOM_MARGIN - rowSpan;
        return Math.max(108, Math.min(ACTION_TOP, maxTop));
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
                    pollLoopGeneration++;
                    login = null;
                    sessionInfo = runtime.sessionInfo();
                    status = "Вход подтвержден. Возвращаю в библиотеку...";
                    updateButtons();
                    client().gui.setScreen(parent);
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
            Util.getPlatform().openUri(runtime.config().siteUri(path));
        } catch (Exception e) {
            status = CompanionUiErrors.message("site", e);
        }
    }

    private void copyUserCode() {
        if (login == null || login.userCode() == null || login.userCode().isBlank()) {
            status = "Сначала нажмите «Получить код»";
            return;
        }
        client().keyboardHandler.setClipboard(login.userCode());
        status = "Код скопирован";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        boolean sideRail = sideRailLayout(panelWidth, left);
        int controlsTop = actionTop();
        MapKlussUi.drawPanelAt(context, left - 10, left + panelWidth + 10, 46, MapKlussUi.panelBottom(height));
        if (sideRail) {
            int railLeft = sideRailLeft(panelWidth, left);
            MapKlussUi.drawPanelAt(context, railLeft - 8, railLeft + SIDE_RAIL_WIDTH + 8, 46, MapKlussUi.panelBottom(height));
            drawSideRailSections(context, railLeft);
        }
        int cardHeight = login == null ? 74 : (controlsTop > 138 ? 68 : Math.max(32, controlsTop - 78));
        MapKlussUi.drawSectionAt(context, font, login == null ? "Вход" : "Код входа", left, panelWidth, 76, cardHeight);
        if (!sideRail) drawActionGroups(context);
        MapKlussUi.drawHeader(context, font, title.getString(), "", width, 20);
        MapKlussUi.drawStatusIn(context, font, status, left + panelWidth / 2, 38, panelWidth - 16);
        MapKlussUi.drawCenteredIn(context, font, sessionSummary(), left + panelWidth / 2, 68, panelWidth - 12, sessionColor());
        if (login != null) {
            int codeY = 90;
            if (controlsTop > 112) {
                MapKlussUi.drawCenteredIn(context, font, login.userCode(), left + panelWidth / 2, codeY, panelWidth - 12, MapKlussUi.ACCENT);
            }
            if (controlsTop > 126) {
                MapKlussUi.drawCenteredIn(context, font, login.verificationUri(), left + panelWidth / 2, codeY + 14, panelWidth - 12, MapKlussUi.CYAN);
            }
            if (controlsTop > 140) {
                String timing = CompanionI18n.english(client())
                    ? "Expires in " + remainingSeconds() + "s / checks every " + login.interval() + "s / " + (autoPollEnabled ? "automatically" : "manually")
                    : "Истекает через " + remainingSeconds() + "с / проверка каждые " + login.interval() + "с / " + (autoPollEnabled ? "автоматически" : "вручную");
                MapKlussUi.drawCenteredIn(context, font, timing, left + panelWidth / 2, codeY + 28, panelWidth - 12, MapKlussUi.MUTED);
            }
        } else {
            MapKlussUi.drawCenteredIn(context, font, "Нажмите «Получить код»", left + panelWidth / 2, 96, panelWidth - 12, MapKlussUi.ACCENT);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
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

    private void drawActionGroups(GuiGraphicsExtractor context) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = screenLeft(panelWidth);
        int row1 = actionTop();
        int row2 = row1 + ACTION_ROW_HEIGHT;
        MapKlussUi.drawActionGroupLabel(context, font, "Проверка", left, panelWidth, row1, ACTION_BUTTON_HEIGHT);
        MapKlussUi.drawActionGroupLabel(context, font, "Код", left, panelWidth, row2, ACTION_BUTTON_HEIGHT);
    }

    private void drawSideRailSections(GuiGraphicsExtractor context, int railLeft) {
        MapKlussUi.drawSectionAt(context, font, "Проверка", railLeft, SIDE_RAIL_WIDTH, 58, 76);
        MapKlussUi.drawSectionAt(context, font, "Код", railLeft, SIDE_RAIL_WIDTH, 142, 102);
    }

    private boolean sideRailLayout(int panelWidth, int left) {
        return false;
    }

    private int sideRailLeft(int panelWidth, int left) {
        return left + panelWidth + SIDE_RAIL_GAP;
    }

    private int screenLeft(int panelWidth) {
        return MapKlussUi.centeredLeft(width, panelWidth);
    }

    private Component autoPollButtonText() {
        return Component.literal(autoPollEnabled ? "Авто: вкл" : "Авто: выкл");
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

    private Minecraft client() {
        return Minecraft.getInstance();
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
            if (isCurrent(generation) && client().gui.screen() == this) task.run();
        });
    }

    @Override
    public void removed() {
        closed = true;
        pollLoopGeneration++;
        super.removed();
    }

    @Override
    public void onClose() {
        closed = true;
        pollLoopGeneration++;
        super.onClose();
    }
}
