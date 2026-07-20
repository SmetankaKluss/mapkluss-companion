package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

final class MapKlussUi {
    private static final String ELLIPSIS = "...";
    static final int WHITE = 0xFFFFFFFF;
    static final int MUTED = 0xFFAAAAAA;
    static final int DIM = 0xFF686868;
    static final int ACCENT = 0xFF57FF6E;
    static final int CYAN = 0xFF8FC7FF;
    static final int GOLD = 0xFFD9C27A;
    static final int DANGER = 0xFFFF6677;
    static final int WARNING = 0xFFFFC46B;
    static final int SUCCESS = 0xFF8FE388;

    private static final int PANEL_BG = 0xD00B0B11;
    private static final int PANEL_INNER = 0x70151520;
    private static final int PANEL_BORDER = 0xCC57FF6E;
    private static final int PANEL_BORDER_SOFT = 0x5557FF6E;
    private static final int SECTION_BG = 0x60101018;
    private static final int SECTION_BORDER = 0x6650505D;
    private static final int LANGUAGE_WIDTH = 34;
    private static final int BACK_WIDTH = 84;

    private MapKlussUi() {
    }

    static int panelWidth(int screenWidth, int desiredWidth) {
        return Math.max(220, Math.min(desiredWidth, screenWidth - 24));
    }

    static int centeredLeft(int screenWidth, int width) {
        return (screenWidth - width) / 2;
    }

    static boolean rightRailFits(int screenWidth, int panelWidth, int railWidth, int railGap) {
        return panelWidth + railGap + railWidth <= screenWidth - 24;
    }

    static int leftWithRightRail(int screenWidth, int panelWidth, int railWidth, int railGap) {
        if (!rightRailFits(screenWidth, panelWidth, railWidth, railGap)) {
            return centeredLeft(screenWidth, panelWidth);
        }
        return Math.max(12, (screenWidth - panelWidth - railGap - railWidth) / 2);
    }

    static int visibleRows(int screenHeight, int firstRowY, int bottomReserve, int rowHeight, int maxRows) {
        return CompanionLayout.visibleRows(screenHeight, firstRowY, bottomReserve, rowHeight, maxRows);
    }

    static void drawPanel(DrawContext context, int screenWidth, int desiredWidth, int top, int bottom) {
        int panelWidth = panelWidth(screenWidth, desiredWidth);
        int left = centeredLeft(screenWidth, panelWidth) - 10;
        int right = left + panelWidth + 20;
        drawPanelAt(context, left, right, top, bottom);
    }

    static void drawPanelAt(DrawContext context, int left, int right, int top, int bottom) {
        context.fill(left, top, right, bottom, PANEL_BG);
        context.fill(left + 3, top + 3, right - 3, bottom - 3, PANEL_INNER);
        context.fill(left, top, right, top + 1, PANEL_BORDER);
        context.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        context.fill(left, top, left + 1, bottom, PANEL_BORDER);
        context.fill(right - 1, top, right, bottom, PANEL_BORDER);
        context.fill(left + 2, top + 2, right - 2, top + 3, PANEL_BORDER_SOFT);
    }

    static void drawHeader(DrawContext context, TextRenderer renderer, String title, String subtitle, int screenWidth, int y) {
        title = CompanionI18n.translate(title);
        subtitle = CompanionI18n.translate(subtitle);
        drawCentered(context, renderer, title, screenWidth, y, WHITE);
        if (subtitle != null && !subtitle.isBlank()) {
            drawCentered(context, renderer, subtitle, screenWidth, y + 16, MUTED);
        }
    }

    static void drawStatus(DrawContext context, TextRenderer renderer, String status, int screenWidth, int y, int color) {
        status = CompanionI18n.translate(status);
        drawWrappedCentered(context, renderer, status, screenWidth, y, panelWidth(screenWidth, 420), 2, color);
    }

    static void drawStatus(DrawContext context, TextRenderer renderer, String status, int screenWidth, int y) {
        drawStatus(context, renderer, status, screenWidth, y, statusColor(status));
    }

    static void drawStatusIn(
        DrawContext context,
        TextRenderer renderer,
        String status,
        int centerX,
        int y,
        int maxWidth
    ) {
        if (status == null || status.isBlank()) return;
        status = CompanionI18n.translate(status);
        drawWrappedCenteredIn(context, renderer, status, centerX, y, maxWidth, 2, statusColor(status));
    }

    static int statusColor(String status) {
        if (status == null) return MUTED;
        String value = status.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("ошиб") || value.contains("не удалось") || value.contains("error") || value.contains("failed")) {
            return DANGER;
        }
        if (value.contains("предуп") || value.contains("истек") || value.contains("недоступ") || value.contains("warning")) {
            return WARNING;
        }
        if (value.contains("готов") || value.contains("загружено") || value.contains("сохран") || value.contains("подтверж")
            || value.contains("ready") || value.contains("loaded") || value.contains("saved") || value.contains("complete")) {
            return SUCCESS;
        }
        if (value.contains("загруз") || value.contains("синхрон") || value.contains("провер") || value.contains("loading")) {
            return GOLD;
        }
        return MUTED;
    }

    static void drawSection(DrawContext context, TextRenderer renderer, String label, int screenWidth, int desiredWidth, int y, int height) {
        int panelWidth = panelWidth(screenWidth, desiredWidth);
        int left = centeredLeft(screenWidth, panelWidth);
        drawSectionAt(context, renderer, label, left, panelWidth, y, height);
    }

    static void drawSectionAt(DrawContext context, TextRenderer renderer, String label, int left, int panelWidth, int y, int height) {
        if (height <= 0) return;
        int right = left + panelWidth;
        context.fill(left - 2, y, right + 2, y + height, SECTION_BG);
        context.fill(left - 2, y, right + 2, y + 1, SECTION_BORDER);
        context.fill(left - 2, y + height - 1, right + 2, y + height, SECTION_BORDER);
        context.fill(left - 2, y, left - 1, y + height, SECTION_BORDER);
        context.fill(right + 1, y, right + 2, y + height, SECTION_BORDER);
        if (label != null && !label.isBlank()) {
            label = CompanionI18n.translate(label);
            drawLeft(context, renderer, label.toUpperCase(), left + 6, y + 7, panelWidth - 12, CYAN);
        }
    }

    static void drawActionGroupLabel(
        DrawContext context,
        TextRenderer renderer,
        String label,
        int left,
        int panelWidth,
        int rowY,
        int buttonHeight
    ) {
        if (label != null && !label.isBlank()) {
            label = CompanionI18n.translate(label);
            drawLeft(context, renderer, label.toUpperCase(), left + 2, rowY - 12, panelWidth - 4, CYAN);
        }
    }

    static Text clippedText(TextRenderer renderer, String value, int maxWidth) {
        value = CompanionI18n.translate(value);
        return Text.literal(clip(renderer, value, maxWidth));
    }

    static void drawCentered(DrawContext context, TextRenderer renderer, Text text, int screenWidth, int y, int color) {
        drawCentered(context, renderer, text.getString(), screenWidth, y, color);
    }

    static void drawCentered(DrawContext context, TextRenderer renderer, String value, int screenWidth, int y, int color) {
        int maxWidth = panelWidth(screenWidth, 520);
        context.drawCenteredTextWithShadow(renderer, clippedText(renderer, value, maxWidth), screenWidth / 2, y, color);
    }

    static void drawCenteredIn(DrawContext context, TextRenderer renderer, String value, int centerX, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        context.drawCenteredTextWithShadow(renderer, clippedText(renderer, value, maxWidth), centerX, y, color);
    }

    static void drawEmptyState(
        DrawContext context,
        TextRenderer renderer,
        String title,
        String detail,
        int left,
        int top,
        int width,
        int height
    ) {
        if (height < 42) return;
        title = CompanionI18n.translate(title);
        detail = CompanionI18n.translate(detail);
        int cardWidth = Math.min(width - 28, 310);
        int cardHeight = detail == null || detail.isBlank() ? 34 : 48;
        int cardLeft = left + (width - cardWidth) / 2;
        int cardTop = top + Math.max(18, (height - cardHeight) / 3);
        context.fill(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight, 0x50101018);
        context.fill(cardLeft, cardTop, cardLeft + cardWidth, cardTop + 1, SECTION_BORDER);
        context.fill(cardLeft, cardTop + cardHeight - 1, cardLeft + cardWidth, cardTop + cardHeight, SECTION_BORDER);
        drawCenteredIn(context, renderer, title, cardLeft + cardWidth / 2, cardTop + 9, cardWidth - 14, ACCENT);
        if (detail != null && !detail.isBlank()) {
            drawCenteredIn(context, renderer, detail, cardLeft + cardWidth / 2, cardTop + 25, cardWidth - 14, MUTED);
        }
    }

    static void drawLeft(DrawContext context, TextRenderer renderer, String value, int x, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        context.drawTextWithShadow(renderer, clippedText(renderer, value, maxWidth), x, y, color);
    }

    static void drawFieldLabel(DrawContext context, TextRenderer renderer, String value, int x, int inputY, int maxWidth) {
        drawLeft(context, renderer, value, x + 2, inputY - 10, Math.max(0, maxWidth - 4), CYAN);
    }

    static ClickableWidget languageButton(Screen screen) {
        return languageButtonAt(screen, CompanionLayout.topRightX(screen.width, LANGUAGE_WIDTH), CompanionLayout.EDGE_MARGIN);
    }

    static ClickableWidget languageButtonAt(Screen screen, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        return MapKlussButton.builder(Text.literal(CompanionI18n.toggleLabel(client)), button -> {
                try {
                    CompanionI18n.toggle(client);
                    client.setScreen(screen);
                } catch (Exception e) {
                    MapKlussCompanionClient.LOGGER.warn("Failed to toggle MapKluss Companion language.", e);
                }
            })
            .dimensions(x, y, 34, 18)
            .build();
    }

    static ClickableWidget backButton(Screen screen, Screen parent, int pageLeft) {
        return backButton(screen, parent, pageLeft, CompanionLayout.panelBottom(screen.height), () -> true);
    }

    static ClickableWidget backButton(Screen screen, Screen parent, int pageLeft, BooleanSupplier enabledWhen) {
        return backButton(screen, parent, pageLeft, CompanionLayout.panelBottom(screen.height), enabledWhen);
    }

    static ClickableWidget backButton(Screen screen, Screen parent, int pageLeft, int panelBottom, BooleanSupplier enabledWhen) {
        return MapKlussButton.builder(CompanionI18n.text("Назад"), button -> MinecraftClient.getInstance().setScreen(parent))
            .dimensions(
                pageLeft,
                CompanionLayout.insidePanelButtonY(panelBottom, CompanionLayout.NAV_BUTTON_HEIGHT),
                BACK_WIDTH,
                CompanionLayout.NAV_BUTTON_HEIGHT
            )
            .navigationOrder(800)
            .enabledWhen(enabledWhen)
            .build();
    }

    static int contentBottom(int screenHeight) {
        return CompanionLayout.contentBottom(screenHeight);
    }

    static int panelBottom(int screenHeight) {
        return CompanionLayout.panelBottom(screenHeight);
    }

    static void drawWrappedCentered(
        DrawContext context,
        TextRenderer renderer,
        String value,
        int screenWidth,
        int y,
        int maxWidth,
        int maxLines,
        int color
    ) {
        if (value == null || value.isBlank() || maxLines <= 0) return;
        value = CompanionI18n.translate(value);
        String remaining = value.trim();
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            boolean lastLine = line == maxLines - 1;
            WrappedLine next = takeLine(renderer, remaining, maxWidth, lastLine);
            if (next.text().isEmpty() || next.consumedChars() <= 0) return;
            context.drawCenteredTextWithShadow(renderer, Text.literal(next.text()), screenWidth / 2, y + line * 11, color);
            remaining = remaining.substring(Math.min(remaining.length(), next.consumedChars())).trim();
        }
    }

    static void drawWrappedCenteredIn(
        DrawContext context,
        TextRenderer renderer,
        String value,
        int centerX,
        int y,
        int maxWidth,
        int maxLines,
        int color
    ) {
        if (value == null || value.isBlank() || maxLines <= 0) return;
        value = CompanionI18n.translate(value);
        String remaining = value.trim();
        for (int line = 0; line < maxLines && !remaining.isEmpty(); line++) {
            boolean lastLine = line == maxLines - 1;
            WrappedLine next = takeLine(renderer, remaining, maxWidth, lastLine);
            if (next.text().isEmpty() || next.consumedChars() <= 0) return;
            context.drawCenteredTextWithShadow(renderer, Text.literal(next.text()), centerX, y + line * 11, color);
            remaining = remaining.substring(Math.min(remaining.length(), next.consumedChars())).trim();
        }
    }

    private static String clip(TextRenderer renderer, String value, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (renderer.getWidth(value) <= maxWidth) return value;
        int ellipsisWidth = renderer.getWidth(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) return "";
        return renderer.trimToWidth(value, maxWidth - ellipsisWidth) + ELLIPSIS;
    }

    private static WrappedLine takeLine(TextRenderer renderer, String value, int maxWidth, boolean lastLine) {
        if (renderer.getWidth(value) <= maxWidth) return new WrappedLine(value, value.length());
        int trimWidth = lastLine ? maxWidth - renderer.getWidth(ELLIPSIS) : maxWidth;
        if (trimWidth <= 0) {
            return lastLine && maxWidth >= renderer.getWidth(ELLIPSIS)
                ? new WrappedLine(ELLIPSIS, Math.min(value.length(), 1))
                : new WrappedLine("", 0);
        }
        int end = renderer.trimToWidth(value, trimWidth).length();
        if (end <= 0) return new WrappedLine("", 0);
        int space = value.lastIndexOf(' ', end);
        if (space > 10) end = space;
        String line = value.substring(0, end).trim();
        return new WrappedLine(lastLine ? line + ELLIPSIS : line, Math.max(1, end));
    }

    private record WrappedLine(String text, int consumedChars) {
    }
}
