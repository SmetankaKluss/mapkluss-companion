package art.mapkluss.companion;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.concurrent.CompletableFuture;

final class CompanionAccountScreen extends Screen {
    private final Screen parent;
    private CompanionRuntime runtime;
    private CompanionSessionInfo session;
    private String status = "";
    private boolean detailsVisible;
    private boolean logoutArmed;
    private int updateRequest;
    private boolean checkingUpdate;
    private final boolean fixture;
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);

    CompanionAccountScreen(Screen parent) {
        this(parent, false);
    }

    CompanionAccountScreen(Screen parent, boolean fixture) {
        super(CompanionI18n.text("Аккаунт"));
        this.parent = parent;
        this.fixture = fixture;
    }

    @Override
    protected void init() {
        clearChildren();
        if (!fixture) refreshSession();
        try { theme = WorkshopTheme.of(CompanionConfig.load(client().runDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        var shell = WorkshopLayout.account(width, height);
        var nav = shell.navigation();
        int slot = (nav.width() - 28) / 5;
        WorkshopIcon[] icons = {WorkshopIcon.LIBRARY, WorkshopIcon.LENS, WorkshopIcon.SCAN, WorkshopIcon.TRACKER, WorkshopIcon.ACCOUNT};
        for (int i = 0; i < 5; i++) {
            var destination = CompanionUiLayout.Destination.values()[i];
            accountButton(CompanionActionInventory.navigationAction(destination), destinationLabel(destination), icons[i],
                new WorkshopLayout.Rect(nav.x() + i * slot, nav.y(), slot - 4, nav.height()),
                true, i == 4, () -> openDestination(destination));
        }
        accountButton("global.back", "Назад", WorkshopIcon.CLOSE, new WorkshopLayout.Rect(nav.right() - 24, nav.y(), 24, nav.height()), true, false, this::close);
        var footer = shell.footer();
        accountButton("account.details", detailsVisible ? "Назад" : "Детали", detailsVisible ? WorkshopIcon.BACK : WorkshopIcon.MORE,
            new WorkshopLayout.Rect(footer.right() - 92, footer.y(), 92, 20), true, detailsVisible, () -> { detailsVisible = !detailsVisible; init(); });
        if (detailsVisible) return;
        int left = nav.x(), top = shell.preview().y(), gap = 6, w = (nav.width() - gap) / 2;
        accountButton(isSignedIn() ? "account.logout" : "account.login_start", logoutArmed ? "Подтвердить выход" : isSignedIn() ? "Выйти" : "Войти",
            WorkshopIcon.ACCOUNT, new WorkshopLayout.Rect(left, top, w, 24), true, !isSignedIn(), this::accountAction);
        accountButton("account.open_site", "Сайт облака", WorkshopIcon.LINK, new WorkshopLayout.Rect(left+w+gap, top, w, 24), !fixture, false, () -> openSite("/cloud"));
        accountButton("account.sync", "Синхронизация", WorkshopIcon.REFRESH, new WorkshopLayout.Rect(left, top+30, w, 24), !fixture && isSignedIn(), false, () -> client().setScreen(new CompanionLibraryScreen(this, true)));
        accountButton("account.update", "Проверить обновление", WorkshopIcon.DOWNLOAD, new WorkshopLayout.Rect(left+w+gap, top+30, w, 24), !checkingUpdate, false, this::checkForUpdate);
        accountButton("account.theme", CompanionI18n.english(client()) ? "Appearance" : "Оформление", WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(left, top+60, w, 24), true, false, () -> client().setScreen(new WorkshopAppearanceScreen(this)));
        accountButton("global.language", CompanionI18n.english(client()) ? "English / RU" : "Русский / EN", null,
            new WorkshopLayout.Rect(left+w+gap, top+60, w, 24), true, false, () -> {
                try { CompanionI18n.toggle(client()); init(); }
                catch (Exception e) { status = "Не удалось сохранить выбор."; }
            });
        boolean telemetryEnabled = !fixture && CompanionTelemetryManager.consent() == CompanionTelemetrySettings.Consent.ENABLED;
        accountButton("account.telemetry", telemetryEnabled ? "Анонимная статистика: вкл." : "Анонимная статистика: выкл.", null,
            new WorkshopLayout.Rect(left, top+90, nav.width(), 24), !fixture, telemetryEnabled, this::toggleTelemetry);
    }

    private void accountButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r, boolean enabled, boolean selected, Runnable callback) {
        var builder = MapKlussButton.builder(CompanionI18n.text(label), button -> callback.run()).action(id)
            .enabledWhen(() -> enabled).selected(selected).tooltip(CompanionI18n.text(label))
            .dimensions(r.x(),r.y(),r.width(),r.height());
        if ("account.logout".equals(id)) builder.danger();
        addDrawableChild(builder.build().workshop(theme, icon));
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
            case LENS -> client().setScreen(new LensScreen(this, fixture));
            case SCAN -> client().setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this, fixture));
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
        if (fixture) { client().setScreen(new DeviceLoginScreen(this, true)); return; }
        if (!isSignedIn()) {
            client().setScreen(new DeviceLoginScreen(this));
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
            Util.getOperatingSystem().open(current.config().siteUri(path));
        } catch (Exception error) {
            status = CompanionUiErrors.message("site", error);
        }
    }

    private void checkForUpdate() {
        if (fixture) { client().setScreen(new CompanionUpdateScreen(this, "0.14.0", true)); return; }
        if (checkingUpdate) return;
        checkingUpdate = true;
        int request = ++updateRequest;
        status = "Проверяю обновления...";
        String currentVersion = FabricLoader.getInstance()
            .getModContainer(MapKlussCompanionClient.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");
        new CompanionReleaseChecker().findUpdate(currentVersion).whenComplete((release, error) -> client().execute(() -> {
            if (request != updateRequest || client().currentScreen != this) return;
            checkingUpdate = false;
            if (error != null) {
                status = CompanionUiErrors.message("update", error);
                return;
            }
            if (release.isPresent()) {
                client().setScreen(new CompanionUpdateScreen(this, release.get().version()));
            } else {
                status = "Установлена актуальная версия.";
                init();
            }
        }));
    }

    @Override
    public void removed() {
        updateRequest++;
        checkingUpdate = false;
        super.removed();
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
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0,0,width,height,0x88000000);
        var shell = WorkshopLayout.account(width,height);
        WorkshopChrome.frame(context::fill,shell.frame(),theme);
        var nav=shell.navigation();
        context.fill(nav.x(),nav.bottom()+1,nav.right(),nav.bottom()+2,theme.color("border-subtle"));
        var heading=shell.tabs();
        WorkshopDraw.text(context,textRenderer,CompanionI18n.translate(isSignedIn() ? "Cloud подключён" : "Вход не выполнен"),heading.x()+4,heading.y()+8,heading.width()-8,theme.color(isSignedIn() ? "success" : "text-secondary"));
        if(detailsVisible) {
            int y=shell.preview().y()+8;
            String[] lines=session==null ? new String[]{CompanionI18n.translate("Вход не выполнен")} : new String[]{"ID: "+session.shortUserId(),CompanionI18n.translate("Сохранено: ")+safe(session.savedAt()),CompanionI18n.translate("Истекает: ")+safe(session.expiresAt())};
            for(String line:lines) { WorkshopDraw.text(context,textRenderer,line,nav.x()+4,y,nav.width()-8,theme.color("text-secondary")); y+=22; }
        }
        var footer=shell.footer();
        context.fill(footer.x(),footer.y()-3,footer.right(),footer.y()-2,theme.color("border-subtle"));
        WorkshopDraw.text(context,textRenderer,CompanionI18n.translate(status),footer.x()+4,footer.y()+6,footer.width()-104,theme.color("text-secondary"));
        super.render(context,mouseX,mouseY,delta);
    }



    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
