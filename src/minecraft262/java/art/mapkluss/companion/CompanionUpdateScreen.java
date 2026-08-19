package art.mapkluss.companion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;

final class CompanionUpdateScreen extends Screen {
    private static final URI TELEGRAM_URI = URI.create("https://t.me/mapkluss");
    private static final URI DOWNLOAD_URI = URI.create("https://mapkluss.art/cloud");
    private static final int CARD_WIDTH = 420;
    private static final int CARD_HEIGHT = 148;

    private final Screen parent;
    private final String version;

    CompanionUpdateScreen(Screen parent, String version) {
        super(CompanionI18n.text("Доступна новая версия"));
        this.parent = parent;
        this.version = version;
    }

    @Override
    protected void init() {
        clearWidgets();
        CompanionUiLayout.Rect card = updateCard();
        int gap = 6;
        int buttonWidth = (card.width() - 28 - gap * 2) / 3;
        int buttonY = card.bottom() - 34;
        int left = card.x() + 14;

        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Telegram"), button -> open(TELEGRAM_URI))
            .action("update.telegram")
            .technical().dimensions(left, buttonY, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Скачать мод"), button -> open(DOWNLOAD_URI))
            .action("update.download")
            .exportAction().dimensions(left + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Позже"), button -> onClose())
            .action("update.dismiss")
            .dimensions(left + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        parent.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, UiTheme.SCRIM);
        CompanionUiLayout.Rect card = updateCard();
        context.fill(card.x(), card.y(), card.right(), card.bottom(), UiTheme.SURFACE_RAISED);
        context.fill(card.x(), card.y(), card.x() + 3, card.bottom(), UiTheme.LIME);
        MapKlussUi.drawLeft(context, font, "Доступна новая версия", card.x() + 16, card.y() + 18, card.width() - 32, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, font, "MapKluss Companion " + version + " уже доступен.", card.x() + 16, card.y() + 42, card.width() - 32, MapKlussUi.MUTED);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private CompanionUiLayout.Rect updateCard() {
        return CompanionUiLayout.focusedPanel(new CompanionUiLayout.Rect(0, 0, width, height), CARD_WIDTH, CARD_HEIGHT);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void open(URI uri) {
        Util.getPlatform().openUri(uri);
    }
}
