package art.mapkluss.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

final class CompanionTelemetryConsentScreen extends Screen {
    private final Screen parent;
    private String status = "";

    CompanionTelemetryConsentScreen(Screen parent) {
        super(Text.literal("MapKluss Companion"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        int width = Math.min(300, this.width - 32);
        int x = (this.width - width) / 2;
        int y = this.height / 2 + 26;
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Делиться анонимной статистикой"), button -> choose(true))
            .action("telemetry.enable").dimensions(x, y, width, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Нет"), button -> choose(false))
            .action("telemetry.disable").dimensions(x, y + 30, width, 22).build());
    }

    private void choose(boolean enabled) {
        try {
            if (enabled) CompanionTelemetryManager.enable(); else CompanionTelemetryManager.disable();
            client.setScreen(parent);
        } catch (Exception error) {
            status = CompanionI18n.translate("Не удалось сохранить выбор.");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xE8101116);
        MapKlussUi.drawCentered(context, textRenderer, "MapKluss Companion", width, height / 2 - 54, MapKlussUi.WHITE);
        MapKlussUi.drawCentered(context, textRenderer, CompanionI18n.translate("Помочь улучшать мод анонимной статистикой?"), width, height / 2 - 30, MapKlussUi.WHITE);
        MapKlussUi.drawCentered(context, textRenderer, CompanionI18n.translate("Без аккаунта, серверов, артов и координат."), width, height / 2 - 14, MapKlussUi.MUTED);
        if (!status.isBlank()) MapKlussUi.drawCentered(context, textRenderer, status, width, height / 2 + 88, UiTheme.RED);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() { }
}
