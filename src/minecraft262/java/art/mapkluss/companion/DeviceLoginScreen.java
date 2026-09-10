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
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private volatile DeviceStartResponse login;
    private final DeviceLoginPollGate pollGate = new DeviceLoginPollGate();
    private AbstractWidget copyButton;
    private AbstractWidget pollButton;
    private AbstractWidget autoPollButton;
    private String status = "";
    private boolean autoPollEnabled = true;
    private volatile int pollLoopGeneration;
    private volatile boolean closed;
    private long loginExpiresAtMs;
    private CompanionSessionInfo sessionInfo;
    private final boolean fixture;
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);

    public DeviceLoginScreen(Screen parent) {
        this(parent, false);
    }

    DeviceLoginScreen(Screen parent, boolean fixture) {
        super(Component.literal("Вход MapKluss"));
        this.parent = parent;
        this.fixture = fixture;
    }

    @Override
    protected void init() {
        boolean resumePolling = closed && login != null && autoPollEnabled;
        closed = false;
        if (!fixture) refreshSessionInfo();
        clearWidgets();
        try { theme=WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme()); }
        catch(Exception ignored) { }
        var shell=WorkshopLayout.account(width,height);
        var nav=shell.navigation();
        loginButton("global.back","Назад",WorkshopIcon.BACK,new WorkshopLayout.Rect(nav.right()-24,nav.y(),24,28),true,this::onClose);
        loginButton("account.theme",CompanionI18n.english(client()) ? "Appearance" : "Оформление",WorkshopIcon.LAYERS,new WorkshopLayout.Rect(nav.right()-52,nav.y(),24,28),true,()->client().gui.setScreen(new WorkshopAppearanceScreen(this)));
        loginButton("global.language",CompanionI18n.toggleLabel(client()),null,new WorkshopLayout.Rect(nav.right()-96,nav.y(),40,28),true,()-> {
            try { CompanionI18n.toggle(client()); init(); } catch(Exception e) { status="Не удалось сохранить выбор."; }
        });
        int w=nav.width(),x=nav.x(),gap=4,third=(w-gap*2)/3,half=(w-gap)/2;
        int row2=shell.footer().y()-28,row1=row2-28;
        loginButton("account.login_start","Получить код",WorkshopIcon.ACCOUNT,new WorkshopLayout.Rect(x,row1,third,24),!fixture,this::startLogin);
        autoPollButton=loginButton("account.login_auto_poll",autoPollButtonText().getString(),WorkshopIcon.REFRESH,new WorkshopLayout.Rect(x+third+gap,row1,third,24),true,this::toggleAutoPoll);
        pollButton=loginButton("account.login_poll","Проверить",WorkshopIcon.CHECK,new WorkshopLayout.Rect(x+2*(third+gap),row1,w-2*(third+gap),24),!fixture,this::pollLogin);
        copyButton=loginButton("account.copy_code","Копировать код",WorkshopIcon.LAYERS,new WorkshopLayout.Rect(x,row2,half,24),!fixture,this::copyUserCode);
        loginButton("account.open_site","Открыть сайт",WorkshopIcon.LINK,new WorkshopLayout.Rect(x+half+gap,row2,w-half-gap,24),!fixture,this::openDevicePage);
        updateButtons();
        if (resumePolling && !fixture) startAutoPollLoop();
    }

    private MapKlussButton loginButton(String id,String label,WorkshopIcon icon,WorkshopLayout.Rect r,boolean enabled,Runnable callback) {
        var builder=MapKlussButton.builder(CompanionI18n.text(label),button->callback.run()).action(id)
            .enabledWhen(()->enabled).tooltip(CompanionI18n.text(label)).dimensions(r.x(),r.y(),r.width(),r.height());
        if("account.login_start".equals(id))builder.gold();
        return addRenderableWidget(builder.build().workshop(theme,icon));
    }


    private void addLoginControls(CompanionUiLayout.Rect panel) {
        int gap = 6;
        int x = panel.x() + 14;
        int innerWidth = Math.max(1, panel.width() - 28);
        int row1 = panel.bottom() - 54;
        int row2 = panel.bottom() - 28;
        int third = Math.max(48, (innerWidth - gap * 2) / 3);
        int half = Math.max(68, (innerWidth - gap) / 2);
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Получить код"), button -> startLogin()).selected(true)
            .action("account.login_start")
            .dimensions(x, row1, third, 22).build());
        autoPollButton = addRenderableWidget(MapKlussButton.builder(autoPollButtonText(), button -> toggleAutoPoll())
            .action("account.login_auto_poll")
            .technical()
            .selected(autoPollEnabled)
            .dimensions(x + third + gap, row1, third, 22).build());
        pollButton = addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Проверить"), button -> pollLogin())
            .action("account.login_poll")
            .technical().dimensions(x + (third + gap) * 2, row1, innerWidth - (third + gap) * 2, 22).build());
        copyButton = addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Копировать код"), button -> copyUserCode())
            .action("account.copy_code")
            .technical().dimensions(x, row2, half, 22).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Открыть сайт"), button -> openDevicePage())
            .action("account.open_site")
            .dimensions(x + half + gap, row2, innerWidth - half - gap, 22).build());
    }

    private void startLogin() {
        final int generation = ++pollLoopGeneration;
        login = null;
        loginExpiresAtMs = 0;
        updateButtons();
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
        } else if (!autoPollEnabled && login != null) {
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
                    DeviceStartResponse scheduled = login;
                    if (scheduled == null || generation != pollLoopGeneration) return;
                    Thread.sleep(Math.max(1, scheduled.interval()) * 1000L);
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
        if (!pollGate.acquire(generation)) return pollGate.completed(generation);
        boolean terminal = false;
        try {
            terminal = pollLoginExclusively(generation);
            return terminal;
        } finally {
            pollGate.release(generation, terminal);
        }
    }

    private boolean pollLoginExclusively(int generation) throws Exception {
        DeviceStartResponse attempt = login;
        if (!isCurrent(generation) || attempt == null) return true;
        CompanionRuntime runtime = CompanionRuntime.create(client());
        DevicePollResponse response = runtime.apiClient().pollDeviceLogin(attempt.deviceCode());
        if (!isCurrent(generation)) return true;
        if ("approved".equals(response.status()) && response.accessToken() != null) {
            MapKlussCompanionClient.LOGGER.info("Device login approved.");
            runOnClient(generation, () -> {
                try {
                    runtime.saveSession(response.accessToken(), response.userId());
                    CompanionTelemetryManager.record(CompanionTelemetryEvent.LOGIN_COMPLETED);
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor context,int mouseX,int mouseY,float delta) {
        context.fill(0,0,width,height,0x88000000);
        var s=WorkshopLayout.account(width,height);
        WorkshopChrome.frame(context::fill,s.frame(),theme);
        var nav=s.navigation();
        WorkshopDraw.text(context,font,CompanionI18n.translate("Вход MapKluss"),nav.x()+4,nav.y()+10,nav.width()-104,theme.color("text-primary"));
        context.fill(nav.x(),nav.bottom()+1,nav.right(),nav.bottom()+2,theme.color("border-subtle"));
        var code=s.tabs();
        WorkshopDraw.text(context,font,CompanionI18n.translate(login==null ? "Получить код" : "Подтвердите вход на сайте"),code.x()+4,code.y()+8,code.width()-8,theme.color("text-secondary"));
        int y=s.preview().y()+8;
        WorkshopDraw.text(context,font,login==null ? "---- ----" : login.userCode(),nav.x()+8,y,nav.width()-16,theme.color("accent"));
        if(login!=null)WorkshopDraw.text(context,font,(CompanionI18n.english(client()) ? "Expires in " : "Истекает через ")+remainingSeconds()+" s",nav.x()+8,y+18,nav.width()-16,theme.color("text-secondary"));
        var footer=s.footer();
        WorkshopDraw.text(context,font,CompanionI18n.translate(status),footer.x()+4,footer.y()+6,footer.width()-8,theme.color("text-secondary"));
        super.extractRenderState(context,mouseX,mouseY,delta);
    }

    private Component autoPollButtonText() {
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
            case ACCOUNT -> client().gui.setScreen(parent);
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
        client().gui.setScreen(parent);
    }
}
