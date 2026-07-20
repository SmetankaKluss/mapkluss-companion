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

final class MapKlussButton extends AbstractWidget {
    private static final int KEY_ENTER = 257;
    private static final int KEY_NUMPAD_ENTER = 335;
    private static final int KEY_SPACE = 32;
    private static final String ELLIPSIS = "...";

    private static final int BG = 0xE0101018;
    private static final int BG_HOVER = 0xE0182020;
    private static final int BG_DISABLED = 0x55101014;
    private static final int BORDER = 0x8857FF6E;
    private static final int BORDER_HOVER = 0xFF57FF6E;
    private static final int BORDER_FOCUS = 0xFFD9C27A;
    private static final int TEXT = 0xFFECECEC;
    private static final int TEXT_DISABLED = 0xFF8A8A8A;
    private static final int DANGER_COLOR = 0xFFFF6677;
    private static final int DANGER_BG = 0xE0181012;
    private static final int GOLD_COLOR = 0xFFD9C27A;
    private static final int GOLD_BG = 0xE0181710;

    private final PressAction onPress;
    private final Tone tone;
    private final BooleanSupplier visibleWhen;
    private final BooleanSupplier enabledWhen;
    private boolean selected;

    private MapKlussButton(
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
        int right = x + getWidth();
        int bottom = y + getHeight();
        boolean enabled = active && enabledWhen.getAsBoolean();
        boolean hot = enabled && (isHovered() || isFocused());
        int accent = tone.accentColor();
        int normalBorder = selected ? accent : tone.borderColor();
        int border = enabled ? (hot && !isFocused() ? accent : normalBorder) : 0x55505050;
        int bg = enabled ? (hot ? tone.hoverBg() : tone.bg()) : BG_DISABLED;
        int textColor = enabled ? (hot || selected ? accent : TEXT) : TEXT_DISABLED;

        context.fill(x, y, right, bottom, bg);
        context.fill(x, y, right, y + 1, border);
        context.fill(x, bottom - 1, right, bottom, border);
        context.fill(x, y, x + 1, bottom, border);
        context.fill(right - 1, y, right, bottom, border);
        if (isFocused() && enabled) {
            context.fill(x - 1, y - 1, right + 1, y, BORDER_FOCUS);
            context.fill(x - 1, bottom, right + 1, bottom + 1, BORDER_FOCUS);
            context.fill(x - 1, y, x, bottom, BORDER_FOCUS);
            context.fill(right, y, right + 1, bottom, BORDER_FOCUS);
        }
        if (selected) {
            context.fill(x + 2, y + 2, x + 5, bottom - 2, accent);
        }
        if (hot) {
            context.fill(x + 2, bottom - 3, right - 2, bottom - 2, tone.underlineColor());
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
        GOLD(GOLD_BG, 0xE0201E12, 0x88D9C27A, GOLD_COLOR, 0x80D9C27A),
        DANGER(DANGER_BG, 0xE0221015, 0x88FF6677, DANGER_COLOR, 0x80FF6677);

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
