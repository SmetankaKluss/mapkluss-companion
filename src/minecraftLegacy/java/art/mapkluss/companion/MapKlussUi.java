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
    static int WHITE;
    static int MUTED;
    static int DIM;
    static int ACCENT;
    static int CYAN;
    static int AMETHYST;
    static int GOLD;
    static int EXPORT;
    static int DANGER;
    static int WARNING;
    static int SUCCESS;

    private static int CHASSIS;
    private static int PANEL_BG;
    private static int PANEL_RAISED;
    private static int PANEL_INSET;
    private static int EDGE_HIGHLIGHT;
    private static int EDGE_MID;
    private static int EDGE_DARK;
    private static int BRASS;
    private static int SECTION_BG;
    private static int SECTION_BORDER;
    private static int PANEL_INNER_HIGHLIGHT;
    private static int PANEL_INNER_SHADOW;
    private static int PREVIEW_INNER_DARK;
    private static int PREVIEW_INNER_LIGHT;
    private static int WELL_INNER_DARK;
    private static int BACKDROP;
    private static int BACKDROP_EDGE;
    private static int BACKDROP_SIDE;
    private static int LANGUAGE_WIDTH;
    private static int BACK_WIDTH;
    private static int HEADER_INSET;
    private static int HEADER_TITLE_Y_OFFSET;
    private static int HEADER_BRAND_X;
    private static int SECTION_LABEL_X;

    static {
        applyResources(MapKlussUiResourceData.current());
    }

    private MapKlussUi() {
    }

    static void applyResources(MapKlussUiResourceData.Snapshot snapshot) {
        WHITE = snapshot.color("white");
        MUTED = snapshot.color("muted");
        DIM = snapshot.color("dim");
        ACCENT = snapshot.color("accent");
        CYAN = snapshot.color("cyan");
        AMETHYST = snapshot.color("amethyst");
        GOLD = snapshot.color("gold");
        EXPORT = snapshot.color("export");
        DANGER = snapshot.color("danger");
        WARNING = snapshot.color("warning");
        SUCCESS = snapshot.color("success");
        CHASSIS = snapshot.color("chassis");
        PANEL_BG = snapshot.color("panel_bg");
        PANEL_RAISED = snapshot.color("panel_raised");
        PANEL_INSET = snapshot.color("panel_inset");
        EDGE_HIGHLIGHT = snapshot.color("edge_highlight");
        EDGE_MID = snapshot.color("edge_mid");
        EDGE_DARK = snapshot.color("edge_dark");
        BRASS = snapshot.color("brass");
        SECTION_BG = snapshot.color("section_bg");
        SECTION_BORDER = snapshot.color("section_border");
        PANEL_INNER_HIGHLIGHT = snapshot.color("panel_inner_highlight");
        PANEL_INNER_SHADOW = snapshot.color("panel_inner_shadow");
        PREVIEW_INNER_DARK = snapshot.color("preview_inner_dark");
        PREVIEW_INNER_LIGHT = snapshot.color("preview_inner_light");
        WELL_INNER_DARK = snapshot.color("well_inner_dark");
        BACKDROP = snapshot.color("backdrop");
        BACKDROP_EDGE = snapshot.color("backdrop_edge");
        BACKDROP_SIDE = snapshot.color("backdrop_side");
        LANGUAGE_WIDTH = snapshot.metric("language_width");
        BACK_WIDTH = snapshot.metric("back_width");
        HEADER_INSET = snapshot.metric("header_inset");
        HEADER_TITLE_Y_OFFSET = snapshot.metric("header_title_y_offset");
        HEADER_BRAND_X = snapshot.metric("header_brand_x");
        SECTION_LABEL_X = snapshot.metric("section_label_x");
    }

    static void drawBackdrop(DrawContext context, int screenWidth, int screenHeight) {
        context.fill(0, 0, screenWidth, screenHeight, BACKDROP);
        context.fill(0, 0, screenWidth, 2, BACKDROP_EDGE);
        context.fill(0, screenHeight - 2, screenWidth, screenHeight, BACKDROP_EDGE);
        context.fill(0, 2, 2, screenHeight - 2, BACKDROP_SIDE);
        context.fill(screenWidth - 2, 2, screenWidth, screenHeight - 2, BACKDROP_SIDE);
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
        if (right - left < 12 || bottom - top < 12) return;
        context.fill(left, top, right, bottom, CHASSIS);
        context.fill(left, top, right, top + 1, EDGE_HIGHLIGHT);
        context.fill(left, top, left + 1, bottom, EDGE_HIGHLIGHT);
        context.fill(left, bottom - 2, right, bottom, EDGE_DARK);
        context.fill(right - 2, top, right, bottom, EDGE_DARK);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, EDGE_MID);
        context.fill(left + 3, top + 3, right - 3, bottom - 3, PANEL_BG);
        context.fill(left + 4, top + 4, right - 4, top + 5, PANEL_INNER_HIGHLIGHT);
        context.fill(left + 4, top + 4, left + 5, bottom - 4, PANEL_INNER_HIGHLIGHT);
        context.fill(left + 4, bottom - 5, right - 4, bottom - 4, PANEL_INNER_SHADOW);
        context.fill(right - 5, top + 4, right - 4, bottom - 4, PANEL_INNER_SHADOW);
        drawCornerPins(context, left, right, top, bottom);
    }

    static void drawHeader(DrawContext context, TextRenderer renderer, String title, String subtitle, int screenWidth, int y) {
        title = CompanionI18n.translate(title);
        subtitle = CompanionI18n.translate(subtitle);
        int left = HEADER_INSET;
        int right = Math.max(left + 80, screenWidth - HEADER_INSET);
        int top = Math.max(4, y - HEADER_TITLE_Y_OFFSET);
        int bottom = y + (subtitle == null || subtitle.isBlank() ? 17 : 29);
        drawRaisedPlate(context, left, top, right, bottom);
        if (screenWidth >= 420) {
            drawLeft(context, renderer, "MAPKLUSS", left + HEADER_BRAND_X, y, 96, WHITE);
        }
        int titleWidth = Math.max(80, screenWidth - (screenWidth >= 420 ? 250 : 100));
        String clippedTitle = clip(renderer, title, titleWidth);
        int clippedTitleWidth = renderer.getWidth(clippedTitle);
        int titleCenter = screenWidth / 2;
        int leftSignal = titleCenter - clippedTitleWidth / 2 - 12;
        int rightSignal = titleCenter + clippedTitleWidth / 2 + 8;
        if (leftSignal > left + 114) {
            context.fill(leftSignal, y + 4, leftSignal + 4, y + 7, CYAN);
        }
        if (rightSignal + 4 < right - LANGUAGE_WIDTH - 12) {
            context.fill(rightSignal, y + 4, rightSignal + 4, y + 7, CYAN);
        }
        context.drawCenteredTextWithShadow(renderer, Text.literal(clippedTitle), titleCenter, y, WHITE);
        if (subtitle != null && !subtitle.isBlank()) {
            drawCenteredIn(context, renderer, subtitle, screenWidth / 2, y + 14, titleWidth, CYAN);
        }
    }

    static void drawLocalHeader(
        DrawContext context,
        TextRenderer renderer,
        String title,
        String subtitle,
        int left,
        int right,
        int y
    ) {
        title = CompanionI18n.translate(title);
        subtitle = CompanionI18n.translate(subtitle);
        int safeRight = Math.max(left + 80, right);
        int top = Math.max(4, y - HEADER_TITLE_Y_OFFSET);
        int bottom = y + (subtitle == null || subtitle.isBlank() ? 17 : 29);
        drawRaisedPlate(context, left, top, safeRight, bottom);
        int titleCenter = (left + safeRight) / 2;
        int titleWidth = Math.max(40, safeRight - left - 44);
        String clippedTitle = clip(renderer, title, titleWidth);
        int clippedTitleWidth = renderer.getWidth(clippedTitle);
        int leftSignal = titleCenter - clippedTitleWidth / 2 - 12;
        int rightSignal = titleCenter + clippedTitleWidth / 2 + 8;
        if (leftSignal > left + 12) {
            context.fill(leftSignal, y + 4, leftSignal + 4, y + 7, CYAN);
        }
        if (rightSignal + 4 < safeRight - 12) {
            context.fill(rightSignal, y + 4, rightSignal + 4, y + 7, CYAN);
        }
        context.drawCenteredTextWithShadow(renderer, Text.literal(clippedTitle), titleCenter, y, WHITE);
        if (subtitle != null && !subtitle.isBlank()) {
            drawCenteredIn(context, renderer, subtitle, titleCenter, y + 14, titleWidth, CYAN);
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
        drawInsetWell(context, left - 2, y, right + 2, y + height);
        if (label != null && !label.isBlank()) {
            label = CompanionI18n.translate(label);
            context.fill(left + 5, y + 4, Math.min(right - 5, left + 12), y + 11, BRASS);
            drawLeft(context, renderer, label.toUpperCase(), left + SECTION_LABEL_X, y + 5, panelWidth - 26, CYAN);
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
            context.fill(left, rowY - 7, left + 5, rowY - 5, BRASS);
            drawLeft(context, renderer, label.toUpperCase(), left + 9, rowY - 12, panelWidth - 11, CYAN);
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
        drawInsetWell(context, cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
        drawCenteredIn(context, renderer, title, cardLeft + cardWidth / 2, cardTop + 9, cardWidth - 14, ACCENT);
        if (detail != null && !detail.isBlank()) {
            drawCenteredIn(context, renderer, detail, cardLeft + cardWidth / 2, cardTop + 25, cardWidth - 14, MUTED);
        }
    }

    static void drawLeft(DrawContext context, TextRenderer renderer, String value, int x, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        context.drawTextWithShadow(renderer, clippedText(renderer, value, maxWidth), x, y, color);
    }

    static void drawRight(DrawContext context, TextRenderer renderer, String value, int right, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        String clipped = clip(renderer, value, maxWidth);
        context.drawTextWithShadow(renderer, Text.literal(clipped), right - renderer.getWidth(clipped), y, color);
    }

    static void drawFieldLabel(DrawContext context, TextRenderer renderer, String value, int x, int inputY, int maxWidth) {
        drawInsetWell(context, x - 2, inputY - 2, x + maxWidth + 2, inputY + 22);
        drawLeft(context, renderer, value, x + 2, inputY - 10, Math.max(0, maxWidth - 4), CYAN);
    }

    static void drawPreviewWell(DrawContext context, int left, int top, int right, int bottom) {
        if (right - left < 12 || bottom - top < 12) return;
        context.fill(left, top, right, bottom, EDGE_DARK);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, EDGE_MID);
        context.fill(left + 3, top + 3, right - 3, bottom - 3, PANEL_INSET);
        context.fill(left + 3, top + 3, right - 3, top + 4, PREVIEW_INNER_DARK);
        context.fill(left + 3, top + 3, left + 4, bottom - 3, PREVIEW_INNER_DARK);
        context.fill(left + 3, bottom - 4, right - 3, bottom - 3, PREVIEW_INNER_LIGHT);
        context.fill(right - 4, top + 3, right - 3, bottom - 3, PREVIEW_INNER_LIGHT);
        drawCornerPins(context, left, right, top, bottom);
    }

    static void drawDataStrip(DrawContext context, int left, int right, int top, int bottom) {
        if (right - left < 8 || bottom - top < 8) return;
        drawInsetWell(context, left, top, right, bottom);
        context.fill(left + 6, top + 5, left + 9, top + 8, CYAN);
        context.fill(right - 9, bottom - 8, right - 6, bottom - 5, BRASS);
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
            .dimensions(x, y, LANGUAGE_WIDTH, 18)
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

    private static void drawRaisedPlate(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, PANEL_RAISED);
        context.fill(left, top, right, top + 1, EDGE_HIGHLIGHT);
        context.fill(left, top, left + 1, bottom, EDGE_HIGHLIGHT);
        context.fill(left, bottom - 2, right, bottom, EDGE_DARK);
        context.fill(right - 2, top, right, bottom, EDGE_DARK);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, PANEL_BG);
    }

    private static void drawInsetWell(DrawContext context, int left, int top, int right, int bottom) {
        if (right - left < 4 || bottom - top < 4) return;
        context.fill(left, top, right, bottom, EDGE_DARK);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, SECTION_BORDER);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, SECTION_BG);
        context.fill(left + 2, top + 2, right - 2, top + 3, WELL_INNER_DARK);
        context.fill(left + 2, top + 2, left + 3, bottom - 2, WELL_INNER_DARK);
        context.fill(left + 2, bottom - 3, right - 2, bottom - 2, PREVIEW_INNER_LIGHT);
        context.fill(right - 3, top + 2, right - 2, bottom - 2, PREVIEW_INNER_LIGHT);
    }

    private static void drawCornerPins(DrawContext context, int left, int right, int top, int bottom) {
        int pin = BRASS;
        context.fill(left + 5, top + 5, left + 7, top + 7, pin);
        context.fill(right - 7, top + 5, right - 5, top + 7, pin);
        context.fill(left + 5, bottom - 7, left + 7, bottom - 5, pin);
        context.fill(right - 7, bottom - 7, right - 5, bottom - 5, pin);
    }
}
