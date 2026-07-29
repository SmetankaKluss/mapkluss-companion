package art.mapkluss.companion;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

class MapKlussButton extends AbstractWidget {
    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    private static final int KEY_SPACE = 32;
    private static final String ELLIPSIS = "...";

    private static final int BG = 0xF0141820;
    private static final int BG_HOVER = 0xF0202834;
    private static final int BG_DISABLED = 0xA00C0E12;
    private static final int BORDER = 0xFF465260;
    private static final int BORDER_HOVER = 0xFF57FF6E;
    private static final int BORDER_FOCUS = 0xFF31D8E8;
    private static final int EDGE_LIGHT = 0xFF6C7887;
    private static final int EDGE_DARK = 0xFF050609;
    private static final int TEXT = 0xFFF0E7D2;
    private static final int TEXT_DISABLED = 0xFF867C6C;
    private static final int DANGER_COLOR = 0xFFFF4455;
    private static final int DANGER_BG = 0xF01B1015;
    private static final int GOLD_COLOR = 0xFFFFD45A;
    private static final int GOLD_BG = 0xF01C1910;
    private static final int EXPORT_COLOR = 0xFFFF784D;
    private static final int EXPORT_BG = 0xF01E1410;
    private static final int TECHNICAL_COLOR = 0xFF31D8E8;
    private static final int TECHNICAL_BG = 0xF00D1B22;
    private static final int SPECIAL_COLOR = 0xFFBC94FF;
    private static final int SPECIAL_BG = 0xF0171322;

    private final PressAction onPress;
    private final Tone tone;
    private final BooleanSupplier visibleWhen;
    private final BooleanSupplier enabledWhen;
    private boolean selected;

    MapKlussButton(
        int x,
        int y,
        int width,
        int height,
        Component message,
        PressAction onPress,
        Tone tone,
        boolean selected,
        BooleanSupplier visibleWhen,
        BooleanSupplier enabledWhen
    ) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.tone = tone;
        this.selected = selected;
        this.visibleWhen = visibleWhen;
        this.enabledWhen = enabledWhen;
    }

    static Builder builder(Component message, PressAction onPress) {
        return new Builder(message, onPress);
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visibleWhen.getAsBoolean()) return;
        int x = getX();
        int y = getY();
        boolean enabled = active && enabledWhen.getAsBoolean();
        boolean hot = enabled && (isHovered() || isFocused());
        int lift = hot ? 1 : 0;
        y -= lift;
        int right = x + getWidth();
        int bottom = y + getHeight();
        int accent = tone.accentColor();
        int normalBorder = selected ? accent : tone.borderColor();
        int border = enabled ? (hot && !isFocused() ? accent : normalBorder) : 0x55505050;
        int bg = enabled ? (hot ? tone.hoverBg() : tone.bg()) : BG_DISABLED;
        int textColor = enabled ? (hot || selected || tone != Tone.DEFAULT ? accent : TEXT) : TEXT_DISABLED;

        context.fill(x, y, right, bottom, bg);
        context.fill(x, y, right, y + 1, hot ? accent : EDGE_LIGHT);
        context.fill(x, y, x + 1, bottom, hot ? accent : EDGE_LIGHT);
        context.fill(x, bottom - 2, right, bottom, EDGE_DARK);
        context.fill(right - 2, y, right, bottom, EDGE_DARK);
        context.fill(x + 1, y + 1, right - 1, y + 2, border);
        context.fill(x + 1, y + 1, x + 2, bottom - 1, border);
        if (isFocused() && enabled) {
            context.fill(x - 1, y - 1, right + 1, y, BORDER_FOCUS);
            context.fill(x - 1, bottom, right + 1, bottom + 1, BORDER_FOCUS);
            context.fill(x - 1, y, x, bottom, BORDER_FOCUS);
            context.fill(right, y, right + 1, bottom, BORDER_FOCUS);
        }
        if (selected) {
            context.fill(x + 3, y + 3, right - 3, y + 4, accent);
            context.fill(x + 3, bottom - 4, right - 3, bottom - 3, accent);
        } else if (tone != Tone.DEFAULT && enabled) {
            context.fill(x + 3, bottom - 4, right - 3, bottom - 3, tone.underlineColor());
            context.fill(x + 4, y + 4, x + 7, y + 7, accent);
        }
        if (hot) {
            context.fill(x + 3, bottom - 4, right - 3, bottom - 3, tone.underlineColor());
        }

        Font renderer = Minecraft.getInstance().font;
        String clipped = clip(renderer, CompanionI18n.translate(getMessage().getString()), Math.max(0, getWidth() - 10));
        context.centeredText(renderer, clipped, x + getWidth() / 2, y + (getHeight() - renderer.lineHeight) / 2 + 1, textColor);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubleClick) {
        if (!active || !visible || !visibleWhen.getAsBoolean() || !enabledWhen.getAsBoolean()) return;
        onPress.onPress(this);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (!active || !visible || !visibleWhen.getAsBoolean() || !enabledWhen.getAsBoolean() || !isFocused()) return false;
        int key = keyInput.input();
        if (key != KEY_ENTER && key != KEY_NUMPAD_ENTER && key != KEY_SPACE) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.onPress(this);
        return true;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        if (active && visibleWhen.getAsBoolean() && enabledWhen.getAsBoolean()) {
            AbstractWidget.playButtonClickSound(soundManager);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visibleWhen.getAsBoolean() && super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        Component original = getMessage();
        setMessage(Component.literal(CompanionI18n.translate(original.getString())));
        try {
            defaultButtonNarrationText(builder);
        } finally {
            setMessage(original);
        }
    }

    private static String clip(Font renderer, String value, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (renderer.width(value) <= maxWidth) return value;
        int ellipsisWidth = renderer.width(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) return "";
        return renderer.plainSubstrByWidth(value, maxWidth - ellipsisWidth) + ELLIPSIS;
    }

    interface PressAction {
        void onPress(MapKlussButton button);
    }

    enum Tone {
        DEFAULT(BG, BG_HOVER, BORDER, BORDER_HOVER, 0x8057FF6E),
        GOLD(GOLD_BG, 0xF02A2510, 0xFF665D2F, GOLD_COLOR, 0xA0FFD45A),
        DANGER(DANGER_BG, 0xF02B1118, 0xFF68313B, DANGER_COLOR, 0xA0FF4455),
        EXPORT(EXPORT_BG, 0xF02A1810, 0xFF71402F, EXPORT_COLOR, 0xA0FF784D),
        TECHNICAL(TECHNICAL_BG, 0xF0122730, 0xFF2E6670, TECHNICAL_COLOR, 0xA031D8E8),
        SPECIAL(SPECIAL_BG, 0xF0211930, 0xFF5B4775, SPECIAL_COLOR, 0xA0BC94FF);

        private final int bg;
        private final int hoverBg;
        private final int border;
        private final int accent;
        private final int underline;

        Tone(int bg, int hoverBg, int border, int accent, int underline) {
            this.bg = bg;
            this.hoverBg = hoverBg;
            this.border = border;
            this.accent = accent;
            this.underline = underline;
        }

        int bg() {
            return bg;
        }

        int hoverBg() {
            return hoverBg;
        }

        int borderColor() {
            return border;
        }

        int accentColor() {
            return accent;
        }

        int underlineColor() {
            return underline;
        }
    }

    static final class Builder {
        private final Component message;
        private final PressAction onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Tone tone = Tone.DEFAULT;
        private boolean selected;
        private BooleanSupplier visibleWhen = () -> true;
        private BooleanSupplier enabledWhen = () -> true;
        private Component tooltip;
        private int navigationOrder;

        private Builder(Component message, PressAction onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        Builder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        Builder selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        Builder visibleWhen(BooleanSupplier visibleWhen) {
            this.visibleWhen = visibleWhen;
            return this;
        }

        Builder enabledWhen(BooleanSupplier enabledWhen) {
            this.enabledWhen = enabledWhen;
            return this;
        }

        Builder gold() {
            this.tone = Tone.GOLD;
            return this;
        }

        Builder danger() {
            this.tone = Tone.DANGER;
            return this;
        }

        Builder exportAction() {
            this.tone = Tone.EXPORT;
            return this;
        }

        Builder technical() {
            this.tone = Tone.TECHNICAL;
            return this;
        }

        Builder special() {
            this.tone = Tone.SPECIAL;
            return this;
        }

        Builder tooltip(Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        Builder navigationOrder(int navigationOrder) {
            this.navigationOrder = navigationOrder;
            return this;
        }

        MapKlussButton build() {
            MapKlussButton button = new MapKlussButton(x, y, width, height, message, onPress, tone, selected, visibleWhen, enabledWhen);
            button.setTabOrderGroup(navigationOrder);
            if (tooltip != null) button.setTooltip(Tooltip.create(tooltip));
            return button;
        }
    }
}
