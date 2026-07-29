package art.mapkluss.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;

final class CompanionUpdateScreen extends Screen {
    private static final URI TELEGRAM_URI = URI.create("https://t.me/mapkluss");
    private static final URI DOWNLOAD_URI = URI.create("https://mapkluss.art/cloud");
    private static final int WORKSPACE_WIDTH = 1120;
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
        clearChildren();
        int panelWidth = MapKlussUi.panelWidth(width, CARD_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int top = Math.max(64, (height - CARD_HEIGHT) / 2);
        int gap = 5;
        int buttonWidth = (panelWidth - gap * 2) / 3;
        int buttonY = top + 98;

        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Telegram"), button -> open(TELEGRAM_URI))
            .technical().dimensions(left, buttonY, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Скачать мод"), button -> open(DOWNLOAD_URI))
            .exportAction().dimensions(left + buttonWidth + gap, buttonY, buttonWidth, 20).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Позже"), button -> close())
            .dimensions(left + (buttonWidth + gap) * 2, buttonY, buttonWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        parent.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawBackdrop(context, width, height);
        int workspaceWidth = MapKlussUi.panelWidth(width, WORKSPACE_WIDTH);
        int workspaceLeft = MapKlussUi.centeredLeft(width, workspaceWidth);
        MapKlussUi.drawPanelAt(
            context, workspaceLeft - 10, workspaceLeft + workspaceWidth + 10,
            46, MapKlussUi.panelBottom(height)
        );
        MapKlussUi.drawLocalHeader(
            context, textRenderer, "Обновление Companion", "",
            workspaceLeft - 4, workspaceLeft + workspaceWidth + 4, 60
        );
        int top = Math.max(64, (height - CARD_HEIGHT) / 2);
        int panelWidth = MapKlussUi.panelWidth(width, CARD_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        MapKlussUi.drawPanelAt(context, left, left + panelWidth, top, top + CARD_HEIGHT);
        MapKlussUi.drawSectionAt(context, textRenderer, "Обновление", left, panelWidth, top + 42, 46);
        MapKlussUi.drawLocalHeader(
            context, textRenderer, "Доступна новая версия", "",
            left + 8, left + panelWidth - 8, top + 18
        );
        MapKlussUi.drawWrappedCentered(
            context,
            textRenderer,
            "MapKluss Companion " + version + " уже доступен.",
            width,
            top + 52,
            panelWidth - 32,
            2,
            MapKlussUi.MUTED
        );
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    private void open(URI uri) {
        Util.getOperatingSystem().open(uri);
    }
}
