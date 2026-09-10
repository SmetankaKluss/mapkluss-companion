package art.mapkluss.companion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;

final class CompanionUpdateScreen extends Screen {
    private static final URI TELEGRAM_URI = URI.create("https://t.me/mapkluss");
    private static final URI DOWNLOAD_URI = URI.create("https://mapkluss.art/cloud");
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);

    private final Screen parent;
    private final String version;
    private final boolean fixture;

    CompanionUpdateScreen(Screen parent, String version) {
        this(parent, version, false);
    }

    CompanionUpdateScreen(Screen parent, String version, boolean fixture) {
        super(CompanionI18n.text("Доступна новая версия"));
        this.parent = parent;
        this.version = version;
        this.fixture = fixture;
    }

    @Override
    protected void init() {
        clearWidgets();
        try { theme = WorkshopTheme.of(CompanionConfig.load(minecraft.gameDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        var card = updateCard();
        var telegram = WorkshopOverlayLayout.action(card, 0);
        var download = WorkshopOverlayLayout.action(card, 1);
        var dismiss = WorkshopOverlayLayout.action(card, 2);
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Telegram"), button -> open(TELEGRAM_URI))
            .action("update.telegram").enabledWhen(() -> !fixture).tooltip(CompanionI18n.text("Telegram"))
            .dimensions(telegram.x(), telegram.y(), telegram.width(), telegram.height()).build().workshop(theme, WorkshopIcon.LINK));
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Скачать мод"), button -> open(DOWNLOAD_URI))
            .action("update.download").gold().enabledWhen(() -> !fixture).tooltip(CompanionI18n.text("Скачать мод"))
            .dimensions(download.x(), download.y(), download.width(), download.height()).build().workshop(theme, WorkshopIcon.DOWNLOAD));
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Позже"), button -> onClose())
            .action("update.dismiss").tooltip(CompanionI18n.text("Позже"))
            .dimensions(dismiss.x(), dismiss.y(), dismiss.width(), dismiss.height()).build().workshop(theme, WorkshopIcon.BACK));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // The parent is inactive: rendering it would reuse stale bounds after a resize.
        context.fill(0, 0, width, height, 0x88000000);
        var card = updateCard();
        WorkshopChrome.frame(context::fill, card, theme);
        WorkshopDraw.icon(context, WorkshopIcon.DOWNLOAD, card.x()+12, card.y()+14, theme.color("accent"));
        WorkshopDraw.text(context, font, CompanionI18n.translate("Доступна новая версия"),
            card.x()+36, card.y()+18, card.width()-48, theme.color("text-primary"));
        WorkshopDraw.text(context, font, "MapKluss Companion " + version,
            card.x()+12, card.y()+48, card.width()-24, theme.color("text-secondary"));
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (fixture && width >= 600 && height >= 360) {
            try {
                Class.forName("art.mapkluss.companion.MapKlussLibraryHarness")
                    .getMethod("drawOverlaySamples", Object.class).invoke(null, context);
            } catch (ReflectiveOperationException ignored) { }
        }
    }

    private WorkshopLayout.Rect updateCard() {
        return WorkshopOverlayLayout.update(width, height);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void open(URI uri) {
        if (fixture) return;
        Util.getPlatform().openUri(uri);
    }
}
