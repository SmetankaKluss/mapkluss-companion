package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Local appearance preferences; never initializes a Cloud runtime. */
final class WorkshopAppearanceScreen extends Screen {
    private final Screen parent;
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private String error = "";

    WorkshopAppearanceScreen(Screen parent) {
        super(Component.literal("Themes"));
        this.parent = parent;
    }

    private WorkshopLayout.Rect frame() {
        return WorkshopLayout.appearance(width, height).frame();
    }

    private boolean english() { return CompanionI18n.english(Minecraft.getInstance()); }

    @Override
    protected void init() {
        clearWidgets();
        try { theme = WorkshopTheme.of(CompanionConfig.load(Minecraft.getInstance().gameDirectory.toPath()).theme()); }
        catch (Exception e) { error = english() ? "Could not load settings" : "Не удалось загрузить настройки"; }
        var f = frame();
        String[] labels = {"Classic", "Deep Ocean", "Ember Forge", "Amethyst", "Acid Grove", "Cobalt Pulse", "Midnight"};
        var layout = WorkshopLayout.appearance(width, height);
        for (int i = 0; i < WorkshopTheme.IDS.size(); i++) {
            String id = WorkshopTheme.IDS.get(i);
            var candidate = WorkshopTheme.of(id);
            var bounds = layout.themes().get(i);
            addRenderableWidget(MapKlussButton.builder(Component.literal(labels[i]), button -> choose(id))
                .action("account.theme").selected(theme.id().equals(id))
                .dimensions(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .build().workshop(candidate, null));
        }
        addRenderableWidget(MapKlussButton.builder(Component.literal(english() ? "English / RU" : "Русский / EN"), button -> {
            try { CompanionI18n.toggle(Minecraft.getInstance()); error = ""; init(); }
            catch (Exception e) { error = english() ? "Could not save settings" : "Не удалось сохранить настройки"; }
        }).action("global.language").dimensions(f.x() + 12, f.bottom() - 38, f.width() - 64, 24).build().workshop(theme, null));
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Назад"), button -> onClose())
            .action("global.back").tooltip(CompanionI18n.text("Назад"))
            .dimensions(f.right() - 40, f.bottom() - 38, 28, 24).build().workshop(theme, WorkshopIcon.BACK));
    }

    private void choose(String id) {
        try {
            var dir = Minecraft.getInstance().gameDirectory.toPath();
            CompanionConfig.load(dir).withTheme(id).saveForRunDir(dir);
            WorkshopHudTheme.select(id);
            error = "";
            init();
        } catch (Exception e) { error = english() ? "Could not save theme" : "Не удалось сохранить тему"; }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        var f = frame();
        WorkshopChrome.frame(context::fill, f, theme);
        WorkshopDraw.text(context, font, english() ? "Appearance" : "Оформление", f.x() + 12, f.y() + 14, f.width() - 24, theme.color("text-primary"));
        super.extractRenderState(context, mouseX, mouseY, delta);
        if (!error.isBlank()) WorkshopDraw.text(context, font, error, f.x() + 12, f.bottom() - 11, f.width() - 24, theme.color("error"));
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }
}
