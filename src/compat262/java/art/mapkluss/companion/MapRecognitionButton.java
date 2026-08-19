package art.mapkluss.companion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public final class MapRecognitionButton extends MapKlussButton {
    private static final int BG = 0xF00D1B22;
    private static final int BG_HOVER = 0xF0122730;
    private static final int BORDER = 0xFF2E6670;
    private static final int CYAN = 0xFF31D8E8;
    private static final int PAPER = 0xFFF0E7D2;
    private static final int EDGE_LIGHT = 0xFF6C7887;
    private static final int EDGE_DARK = 0xFF050609;

    public MapRecognitionButton(int x, int y, int size, Runnable onPress) {
        super(
            x, y, size, size,
            Component.translatable("button.mapkluss-companion.recognize_maps"),
            button -> onPress.run(),
            CompanionActionInventory.action("inventory.recognize_maps", "button.mapkluss-companion.recognize_maps"),
            Tone.TECHNICAL,
            false,
            () -> true,
            () -> true
        );
        setTooltip(Tooltip.create(Component.translatable("button.mapkluss-companion.recognize_maps")));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int right = x + getWidth();
        int bottom = y + getHeight();
        boolean hot = active && (isHovered() || isFocused());

        context.fill(x, y, right, bottom, hot ? BG_HOVER : BG);
        context.fill(x, y, right, y + 1, hot ? CYAN : EDGE_LIGHT);
        context.fill(x, y, x + 1, bottom, hot ? CYAN : EDGE_LIGHT);
        context.fill(x, bottom - 2, right, bottom, EDGE_DARK);
        context.fill(right - 2, y, right, bottom, EDGE_DARK);
        context.fill(x + 1, y + 1, right - 1, y + 2, hot ? CYAN : BORDER);
        context.fill(x + 1, y + 1, x + 2, bottom - 1, hot ? CYAN : BORDER);
        if (isFocused()) {
            context.fill(x - 1, y - 1, right + 1, y, CYAN);
            context.fill(x - 1, bottom, right + 1, bottom + 1, CYAN);
            context.fill(x - 1, y, x, bottom, CYAN);
            context.fill(right, y, right + 1, bottom, CYAN);
        }

        drawMapGrid(context, x, y, hot);
    }

    private static void drawMapGrid(GuiGraphicsExtractor context, int x, int y, boolean hot) {
        int primary = hot ? CYAN : PAPER;
        context.fill(x + 5, y + 5, x + 8, y + 8, primary);
        context.fill(x + 10, y + 5, x + 13, y + 8, CYAN);
        context.fill(x + 5, y + 10, x + 8, y + 13, CYAN);
        context.fill(x + 10, y + 10, x + 13, y + 13, primary);
    }
}
