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

    private static final int BG = UiTheme.SURFACE;
    private static final int BG_HOVER = UiTheme.SURFACE_RAISED;
    private static final int BG_DISABLED = 0xD00D1014;
    private static final int BORDER = UiTheme.BORDER;
    private static final int BORDER_FOCUS = UiTheme.CYAN;
    private static final int TEXT = UiTheme.TEXT;
    private static final int TEXT_DISABLED = UiTheme.TEXT_DIM;
    private static final int DANGER_COLOR = UiTheme.RED;
    private static final int GOLD_COLOR = UiTheme.LIME;
    private static final int EXPORT_COLOR = UiTheme.CYAN;
    private static final int TECHNICAL_COLOR = UiTheme.CYAN;
    private static final int SPECIAL_COLOR = UiTheme.CYAN;

    private final PressAction onPress;
    private final UiAction action;
    private final Tone legacyTone;
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
        UiAction action,
        Tone legacyTone,
        boolean selected,
        BooleanSupplier visibleWhen,
        BooleanSupplier enabledWhen
    ) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.action = action;
        this.legacyTone = legacyTone;
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

    String actionId() {
        return action == null ? "" : action.id();
    }

    UiAction action() {
        if (action == null) return null;
        return new UiAction(
            action.id(),
            getMessage().getString(),
            action.kind(),
            active && enabledWhen.getAsBoolean(),
            selected,
            action.tooltip()
        );
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visibleWhen.getAsBoolean()) return;
        int x = getX();
        int y = getY();
        UiAction currentAction = action();
        boolean enabled = currentAction == null ? active && enabledWhen.getAsBoolean() : currentAction.enabled();
        boolean actionSelected = currentAction == null ? selected : currentAction.selected();
        UiActionPresentation presentation = currentAction == null
            ? UiActionPresentation.from(actionKind(legacyTone))
            : UiActionPresentation.from(currentAction);
        boolean hot = enabled && (isHovered() || isFocused());
        int right = x + getWidth();
        int bottom = y + getHeight();
        int accent = presentation.accentColor();
        int bg = enabled ? (hot ? BG_HOVER : BG) : BG_DISABLED;
        int textColor = enabled ? (!presentation.decorated() && !actionSelected ? TEXT : accent) : TEXT_DISABLED;

        context.fill(x, y, right, bottom, bg);
        context.fill(x, y, right, y + 1, BORDER);
        context.fill(x, bottom - 1, right, bottom, BORDER);
        context.fill(x, y, x + 1, bottom, BORDER);
        context.fill(right - 1, y, right, bottom, BORDER);
        if (isFocused() && enabled) {
            context.fill(x - 1, y - 1, right + 1, y, BORDER_FOCUS);
            context.fill(x - 1, bottom, right + 1, bottom + 1, BORDER_FOCUS);
            context.fill(x - 1, y, x, bottom, BORDER_FOCUS);
            context.fill(right, y, right + 1, bottom, BORDER_FOCUS);
        }
        if (actionSelected) {
            context.fill(x, y + 3, x + 2, bottom - 3, UiTheme.LIME);
            context.fill(x + 4, bottom - 2, right - 4, bottom - 1, UiTheme.LIME);
        } else if (presentation.decorated() && enabled) {
            context.fill(x + 4, y + getHeight() / 2 - 1, x + 6, y + getHeight() / 2 + 1, accent);
        }

        Font renderer = Minecraft.getInstance().font;
        String label = currentAction == null ? getMessage().getString() : currentAction.label();
        String clipped = clip(renderer, CompanionI18n.translate(label), Math.max(0, getWidth() - 10));
        context.centeredText(renderer, MapKlussText.text(clipped), x + getWidth() / 2, y + (getHeight() - renderer.lineHeight) / 2 + 1, textColor);
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

    private static UiAction.Kind actionKind(Tone tone) {
        return switch (tone) {
            case GOLD -> UiAction.Kind.PRIMARY;
            case DANGER -> UiAction.Kind.DANGER;
            case EXPORT, TECHNICAL, SPECIAL -> UiAction.Kind.TECHNICAL;
            default -> UiAction.Kind.DEFAULT;
        };
    }

    interface PressAction {
        void onPress(MapKlussButton button);
    }

    enum Tone {
        DEFAULT(BG, BG_HOVER, BORDER, UiTheme.LIME, UiTheme.LIME),
        GOLD(BG, BG_HOVER, BORDER, GOLD_COLOR, UiTheme.LIME),
        DANGER(BG, BG_HOVER, BORDER, DANGER_COLOR, UiTheme.RED),
        EXPORT(BG, BG_HOVER, BORDER, EXPORT_COLOR, UiTheme.CYAN),
        TECHNICAL(BG, BG_HOVER, BORDER, TECHNICAL_COLOR, UiTheme.CYAN),
        SPECIAL(BG, BG_HOVER, BORDER, SPECIAL_COLOR, UiTheme.CYAN);

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
        private UiAction action;
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

        Builder action(String actionId) {
            this.action = CompanionActionInventory.action(actionId, message.getString());
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
            if (action != null) {
                action = new UiAction(
                    action.id(),
                    message.getString(),
                    actionKind(tone),
                    enabledWhen.getAsBoolean(),
                    selected,
                    tooltip == null ? "" : tooltip.getString()
                );
            }
            MapKlussButton button = new MapKlussButton(x, y, width, height, message, onPress, action, tone, selected, visibleWhen, enabledWhen);
            button.setTabOrderGroup(navigationOrder);
            if (tooltip != null) button.setTooltip(Tooltip.create(tooltip));
            return button;
        }

    }
}
