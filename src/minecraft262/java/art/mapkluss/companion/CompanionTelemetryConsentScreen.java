package art.mapkluss.companion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CompanionTelemetryConsentScreen extends Screen {
    private final Screen parent;
    private String status = "";
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);

    private int bodyHeight() {
        return font.split(MapKlussText.workshop(CompanionI18n.translate("Помочь улучшать мод анонимной статистикой?")), contentWidth()).size()*12
            + font.split(MapKlussText.workshop(CompanionI18n.translate("Без аккаунта, серверов, артов и координат.")), contentWidth()).size()*12+8;
    }
    private int contentWidth() { return Math.max(1,Math.min(420,width-16)-24); }
    private WorkshopLayout.Rect frame() {
        return WorkshopOverlayLayout.consent(width,height,bodyHeight());
    }

    CompanionTelemetryConsentScreen(Screen parent) {
        super(Component.literal("MapKluss Companion"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        try { theme=WorkshopTheme.of(CompanionConfig.load(minecraft.gameDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        var frame=frame();
        int width=contentWidth(),x=frame.x()+12,y=frame.y()+bodyHeight()+46;
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Делиться анонимной статистикой"), button -> choose(true))
            .action("telemetry.enable").tooltip(CompanionI18n.text("Делиться анонимной статистикой"))
            .dimensions(x, y, width, 24).build().workshop(theme,WorkshopIcon.CHECK));
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Нет"), button -> choose(false))
            .action("telemetry.disable").dimensions(x, y + 28, width, 24).build().workshop(theme,WorkshopIcon.CLOSE));
    }

    private void choose(boolean enabled) {
        try {
            if (enabled) CompanionTelemetryManager.enable(); else CompanionTelemetryManager.disable();
            minecraft.gui.setScreen(parent);
        } catch (Exception error) {
            status = CompanionI18n.translate("Не удалось сохранить выбор.");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        var frame=frame();
        WorkshopChrome.frame(context::fill,frame,theme);
        WorkshopDraw.text(context,font,"MapKluss Companion",frame.x()+12,frame.y()+14,contentWidth(),theme.color("accent"));
        int y=paragraph(context,CompanionI18n.translate("Помочь улучшать мод анонимной статистикой?"),frame.y()+36,"text-primary");
        paragraph(context,CompanionI18n.translate("Без аккаунта, серверов, артов и координат."),y+8,"text-secondary");
        if (!status.isBlank()) WorkshopDraw.text(context,font,status,frame.x()+12,frame.bottom()-18,contentWidth(),UiTheme.RED);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private int paragraph(GuiGraphicsExtractor context,String text,int y,String colour) {
        for(var line:font.split(MapKlussText.workshop(text),contentWidth())) {
            context.text(font,line,frame().x()+12,y,theme.color(colour));
            y+=12;
        }
        return y;
    }

    @Override
    public void onClose() { }
}
