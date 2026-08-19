package art.mapkluss.companion;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

    static void drawBackdrop(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        context.fill(0, 0, screenWidth, screenHeight, BACKDROP);
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

    static CompanionUiLayout.Shell drawShell(
        GuiGraphicsExtractor context,
        Font renderer,
        int screenWidth,
        int screenHeight,
        CompanionUiLayout.Destination current,
        String title,
        String status,
        boolean inspector
    ) {
        return drawShell(context, renderer, screenWidth, screenHeight,
            ScreenViewModel.shell(current, title, status), inspector, 0);
    }

    static CompanionUiLayout.Shell drawShell(
        GuiGraphicsExtractor context, Font renderer, int screenWidth, int screenHeight,
        ScreenViewModel model, boolean inspector
    ) {
        return drawShell(context, renderer, screenWidth, screenHeight, model, inspector, 0);
    }

    static CompanionUiLayout.Shell drawShell(
        GuiGraphicsExtractor context,
        Font renderer,
        int screenWidth,
        int screenHeight,
        CompanionUiLayout.Destination current,
        String title,
        String status,
        boolean inspector,
        int leadingInset
    ) {
        return drawShell(context, renderer, screenWidth, screenHeight,
            ScreenViewModel.shell(current, title, status), inspector, leadingInset);
    }

    static CompanionUiLayout.Shell drawShell(
        GuiGraphicsExtractor context,
        Font renderer,
        int screenWidth,
        int screenHeight,
        ScreenViewModel model,
        boolean inspector,
        int leadingInset
    ) {
        drawBackdrop(context, screenWidth, screenHeight);
        CompanionUiLayout.Shell shell = CompanionUiLayout.shell(screenWidth, screenHeight, inspector);
        CompanionUiLayout.Rect app = shell.app();
        context.fill(app.x(), app.y(), app.right(), app.bottom(), CHASSIS);
        context.fill(app.x(), app.y(), app.right(), app.y() + 1, EDGE_MID);
        CompanionUiLayout.Rect navigation = shell.navigation();
        context.fill(navigation.x(), navigation.y(), navigation.right(), navigation.bottom(), PANEL_BG);
        CompanionUiLayout.Rect topBar = shell.topBar();
        context.fill(topBar.x(), topBar.y(), topBar.right(), topBar.bottom(), PANEL_BG);
        context.fill(topBar.x(), topBar.bottom() - 1, topBar.right(), topBar.bottom(), EDGE_MID);
        CompanionUiLayout.Rect content = shell.content();
        context.fill(content.x(), content.y(), content.right(), content.bottom(), SECTION_BG);
        if (shell.hasInspector()) {
            CompanionUiLayout.Rect inspectorRect = shell.inspector();
            context.fill(inspectorRect.x(), inspectorRect.y(), inspectorRect.right(), inspectorRect.bottom(), PANEL_RAISED);
            context.fill(inspectorRect.x(), inspectorRect.y(), inspectorRect.x() + 1, inspectorRect.bottom(), EDGE_MID);
        }
        int textLeft = topBar.x() + 12 + Math.max(0, leadingInset);
        int textRight = topBar.right() - 48;
        String status = model.status();
        int statusWidth = status.isBlank()
            ? 0
            : CompanionUiLayout.clamp(topBar.width() * 30 / 100, 72, Math.max(72, (textRight - textLeft) / 2));
        int titleWidth = Math.max(40, textRight - textLeft - statusWidth - (statusWidth > 0 ? 10 : 0));
        drawLeft(context, renderer, model.heading(), textLeft, topBar.y() + 10, titleWidth, WHITE);
        if (!status.isBlank()) {
            drawRight(context, renderer, status, textRight, topBar.y() + 10, statusWidth, statusColor(model.statusKind()));
        }
        drawNavigation(context, shell, model.destination());
        return shell;
    }

    static void drawNavigation(GuiGraphicsExtractor context, CompanionUiLayout.Shell shell, CompanionUiLayout.Destination current) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect button = CompanionUiLayout.navigationButton(shell, i);
            boolean selected = destination == current
                || destination == CompanionUiLayout.Destination.LIBRARY
                && (current == CompanionUiLayout.Destination.ART || current == CompanionUiLayout.Destination.TWO_LAYER);
            if (selected) {
                context.fill(button.x(), button.y(), button.right(), button.bottom(), PANEL_RAISED);
                if (shell.mode() == CompanionUiLayout.Mode.COMPACT) {
                    context.fill(button.x() + 5, button.y(), button.right() - 5, button.y() + 2, ACCENT);
                } else {
                    context.fill(button.x(), button.y() + 6, button.x() + 2, button.bottom() - 6, ACCENT);
                }
            }
            int iconX = button.x() + (button.width() - 16) / 2;
            int iconY = button.y() + (button.height() - 16) / 2;
            drawIcon(context, iconFor(destination), iconX, iconY, selected ? ACCENT : MUTED);
        }
    }

    static void drawIcon(GuiGraphicsExtractor context, MapKlussIcon icon, int x, int y, int color) {
        for (int row = 0; row < 16; row++) {
            int start = -1;
            for (int column = 0; column <= 16; column++) {
                boolean filled = column < 16 && icon.pixel(column, row);
                if (filled && start < 0) start = column;
                if (!filled && start >= 0) {
                    context.fill(x + start, y + row, x + column, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    private static MapKlussIcon iconFor(CompanionUiLayout.Destination destination) {
        return switch (destination) {
            case LIBRARY, ART, TWO_LAYER -> MapKlussIcon.LIBRARY;
            case LENS -> MapKlussIcon.LENS;
            case SCAN -> MapKlussIcon.SCAN;
            case TRACKER -> MapKlussIcon.TRACKER;
            case ACCOUNT -> MapKlussIcon.ACCOUNT;
        };
    }

    static void drawPanel(GuiGraphicsExtractor context, int screenWidth, int desiredWidth, int top, int bottom) {
        int panelWidth = panelWidth(screenWidth, desiredWidth);
        int left = centeredLeft(screenWidth, panelWidth) - 10;
        int right = left + panelWidth + 20;
        drawPanelAt(context, left, right, top, bottom);
    }

    static void drawPanelAt(GuiGraphicsExtractor context, int left, int right, int top, int bottom) {
        if (right - left < 12 || bottom - top < 12) return;
        context.fill(left, top, right, bottom, PANEL_BG);
        context.fill(left, top, right, top + 1, EDGE_MID);
        context.fill(left, bottom - 1, right, bottom, EDGE_MID);
        context.fill(left, top, left + 1, bottom, EDGE_MID);
        context.fill(right - 1, top, right, bottom, EDGE_MID);
    }

    static void drawHeader(GuiGraphicsExtractor context, Font renderer, String title, String subtitle, int screenWidth, int y) {
        title = CompanionI18n.translate(title);
        subtitle = CompanionI18n.translate(subtitle);
        int left = HEADER_INSET;
        int right = Math.max(left + 80, screenWidth - HEADER_INSET);
        int top = Math.max(4, y - HEADER_TITLE_Y_OFFSET);
        int bottom = y + (subtitle == null || subtitle.isBlank() ? 17 : 29);
        context.fill(left, top, right, bottom, PANEL_BG);
        context.fill(left, bottom - 1, right, bottom, EDGE_MID);
        int titleWidth = Math.max(80, right - left - LANGUAGE_WIDTH - 28);
        String clippedTitle = clip(renderer, title, titleWidth);
        context.fill(left + 10, y + 2, left + 12, y + 10, ACCENT);
        context.text(renderer, MapKlussText.text(clippedTitle), left + 18, y, WHITE);
        if (subtitle != null && !subtitle.isBlank()) {
            drawRight(context, renderer, subtitle, right - LANGUAGE_WIDTH - 10, y, Math.max(40, titleWidth / 2), MUTED);
        }
    }

    static void drawLocalHeader(
        GuiGraphicsExtractor context,
        Font renderer,
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
        context.fill(left, top, safeRight, bottom, PANEL_BG);
        context.fill(left, bottom - 1, safeRight, bottom, EDGE_MID);
        int titleWidth = Math.max(40, safeRight - left - 32);
        String clippedTitle = clip(renderer, title, titleWidth);
        context.fill(left + 8, y + 2, left + 10, y + 10, ACCENT);
        context.text(renderer, MapKlussText.text(clippedTitle), left + 16, y, WHITE);
        if (subtitle != null && !subtitle.isBlank()) {
            drawRight(context, renderer, subtitle, safeRight - 10, y, Math.max(40, titleWidth / 2), MUTED);
        }
    }

    static void drawStatus(GuiGraphicsExtractor context, Font renderer, String status, int screenWidth, int y, int color) {
        status = CompanionI18n.translate(status);
        drawWrappedCentered(context, renderer, status, screenWidth, y, panelWidth(screenWidth, 420), 2, color);
    }

    static void drawStatus(GuiGraphicsExtractor context, Font renderer, String status, int screenWidth, int y) {
        drawStatus(context, renderer, status, screenWidth, y, statusColor(status));
    }

    static void drawStatusIn(
        GuiGraphicsExtractor context,
        Font renderer,
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

    static int statusColor(ScreenViewModel.StatusKind kind) {
        return switch (kind) {
            case ERROR -> DANGER;
            case WARNING -> WARNING;
            case SUCCESS -> SUCCESS;
            case LOADING -> GOLD;
            case IDLE -> MUTED;
        };
    }

    static void drawSection(GuiGraphicsExtractor context, Font renderer, String label, int screenWidth, int desiredWidth, int y, int height) {
        int panelWidth = panelWidth(screenWidth, desiredWidth);
        int left = centeredLeft(screenWidth, panelWidth);
        drawSectionAt(context, renderer, label, left, panelWidth, y, height);
    }

    static void drawSectionAt(GuiGraphicsExtractor context, Font renderer, String label, int left, int panelWidth, int y, int height) {
        if (height <= 0) return;
        int right = left + panelWidth;
        context.fill(left, y, right, y + height, SECTION_BG);
        context.fill(left, y, right, y + 1, SECTION_BORDER);
        if (label != null && !label.isBlank()) {
            label = CompanionI18n.translate(label);
            drawLeft(context, renderer, label.toUpperCase(), left + SECTION_LABEL_X, y + 6, panelWidth - 20, MUTED);
        }
    }

    static void drawActionGroupLabel(
        GuiGraphicsExtractor context,
        Font renderer,
        String label,
        int left,
        int panelWidth,
        int rowY,
        int buttonHeight
    ) {
        if (label != null && !label.isBlank()) {
            label = CompanionI18n.translate(label);
            context.fill(left, rowY - 4, left + panelWidth, rowY - 3, SECTION_BORDER);
            drawLeft(context, renderer, label.toUpperCase(), left, rowY - 14, panelWidth, MUTED);
        }
    }

    static Component clippedText(Font renderer, String value, int maxWidth) {
        value = CompanionI18n.translate(value);
        return MapKlussText.text(clip(renderer, value, maxWidth));
    }

    static void drawCentered(GuiGraphicsExtractor context, Font renderer, Component text, int screenWidth, int y, int color) {
        drawCentered(context, renderer, text.getString(), screenWidth, y, color);
    }

    static void drawCentered(GuiGraphicsExtractor context, Font renderer, String value, int screenWidth, int y, int color) {
        int maxWidth = panelWidth(screenWidth, 520);
        context.centeredText(renderer, clippedText(renderer, value, maxWidth), screenWidth / 2, y, color);
    }

    static void drawCenteredIn(GuiGraphicsExtractor context, Font renderer, String value, int centerX, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        context.centeredText(renderer, clippedText(renderer, value, maxWidth), centerX, y, color);
    }

    static void drawEmptyState(
        GuiGraphicsExtractor context,
        Font renderer,
        String title,
        String detail,
        int left,
        int top,
        int width,
        int height
    ) {
        drawEmptyState(context, renderer, title, detail, left, top, width, height, ACCENT);
    }

    static void drawEmptyState(
        GuiGraphicsExtractor context,
        Font renderer,
        String title,
        String detail,
        int left,
        int top,
        int width,
        int height,
        int titleColor
    ) {
        if (height < 42) return;
        title = CompanionI18n.translate(title);
        detail = CompanionI18n.translate(detail);
        int cardWidth = Math.min(width - 28, 310);
        int cardHeight = detail == null || detail.isBlank() ? 34 : 48;
        int cardLeft = left + (width - cardWidth) / 2;
        int cardTop = top + Math.max(18, (height - cardHeight) / 3);
        drawInsetWell(context, cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
        drawCenteredIn(context, renderer, title, cardLeft + cardWidth / 2, cardTop + 9, cardWidth - 14, titleColor);
        if (detail != null && !detail.isBlank()) {
            drawCenteredIn(context, renderer, detail, cardLeft + cardWidth / 2, cardTop + 25, cardWidth - 14, MUTED);
        }
    }

    static void drawLeft(GuiGraphicsExtractor context, Font renderer, String value, int x, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        context.text(renderer, clippedText(renderer, value, maxWidth), x, y, color);
    }

    static void drawRight(GuiGraphicsExtractor context, Font renderer, String value, int right, int y, int maxWidth, int color) {
        value = CompanionI18n.translate(value);
        String clipped = clip(renderer, value, maxWidth);
        Component text = MapKlussText.text(clipped);
        context.text(renderer, text, right - renderer.width(text), y, color);
    }

    static void drawFieldLabel(GuiGraphicsExtractor context, Font renderer, String value, int x, int inputY, int maxWidth) {
        drawInsetWell(context, x - 2, inputY - 2, x + maxWidth + 2, inputY + 22);
        drawLeft(context, renderer, value, x + 2, inputY - 10, Math.max(0, maxWidth - 4), CYAN);
    }

    static void drawPreviewWell(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        if (right - left < 12 || bottom - top < 12) return;
        context.fill(left, top, right, bottom, PANEL_INSET);
        context.fill(left, top, right, top + 1, EDGE_MID);
        context.fill(left, bottom - 1, right, bottom, EDGE_MID);
        context.fill(left, top, left + 1, bottom, EDGE_MID);
        context.fill(right - 1, top, right, bottom, EDGE_MID);
    }

    static void drawDataStrip(GuiGraphicsExtractor context, int left, int right, int top, int bottom) {
        if (right - left < 8 || bottom - top < 8) return;
        context.fill(left, top, right, bottom, PANEL_RAISED);
        context.fill(left, top, right, top + 1, EDGE_MID);
    }

    static AbstractWidget languageButton(Screen screen) {
        return languageButtonAt(screen, CompanionLayout.topRightX(screen.width, LANGUAGE_WIDTH), CompanionLayout.EDGE_MARGIN);
    }

    static AbstractWidget languageButtonAt(Screen screen, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        return MapKlussButton.builder(Component.literal(CompanionI18n.toggleLabel(client)), button -> {
                try {
                    CompanionI18n.toggle(client);
                    client.gui.setScreen(screen);
                } catch (Exception e) {
                    MapKlussCompanionClient.LOGGER.warn("Failed to toggle MapKluss Companion language.", e);
                }
            })
            .action("global.language")
            .dimensions(x, y, LANGUAGE_WIDTH, 18)
            .build();
    }

    static AbstractWidget backButton(Screen screen, Screen parent, int pageLeft) {
        return backButton(screen, parent, pageLeft, CompanionLayout.panelBottom(screen.height), () -> true);
    }

    static AbstractWidget backButton(Screen screen, Screen parent, int pageLeft, BooleanSupplier enabledWhen) {
        return backButton(screen, parent, pageLeft, CompanionLayout.panelBottom(screen.height), enabledWhen);
    }

    static AbstractWidget backButton(Screen screen, Screen parent, int pageLeft, int panelBottom, BooleanSupplier enabledWhen) {
        return MapKlussButton.builder(CompanionI18n.text("Назад"), button -> Minecraft.getInstance().gui.setScreen(parent))
            .action("global.back")
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
        GuiGraphicsExtractor context,
        Font renderer,
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
            context.centeredText(renderer, MapKlussText.text(next.text()), screenWidth / 2, y + line * 11, color);
            remaining = remaining.substring(Math.min(remaining.length(), next.consumedChars())).trim();
        }
    }

    static void drawWrappedCenteredIn(
        GuiGraphicsExtractor context,
        Font renderer,
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
            context.centeredText(renderer, MapKlussText.text(next.text()), centerX, y + line * 11, color);
            remaining = remaining.substring(Math.min(remaining.length(), next.consumedChars())).trim();
        }
    }

    private static String clip(Font renderer, String value, int maxWidth) {
        if (value == null || value.isEmpty()) return "";
        if (renderer.width(value) <= maxWidth) return value;
        int ellipsisWidth = renderer.width(ELLIPSIS);
        if (maxWidth <= ellipsisWidth) return "";
        return renderer.plainSubstrByWidth(value, maxWidth - ellipsisWidth) + ELLIPSIS;
    }

    private static WrappedLine takeLine(Font renderer, String value, int maxWidth, boolean lastLine) {
        if (renderer.width(value) <= maxWidth) return new WrappedLine(value, value.length());
        int trimWidth = lastLine ? maxWidth - renderer.width(ELLIPSIS) : maxWidth;
        if (trimWidth <= 0) {
            return lastLine && maxWidth >= renderer.width(ELLIPSIS)
                ? new WrappedLine(ELLIPSIS, Math.min(value.length(), 1))
                : new WrappedLine("", 0);
        }
        int end = renderer.plainSubstrByWidth(value, trimWidth).length();
        if (end <= 0) return new WrappedLine("", 0);
        int space = value.lastIndexOf(' ', end);
        if (space > 10) end = space;
        String line = value.substring(0, end).trim();
        return new WrappedLine(lastLine ? line + ELLIPSIS : line, Math.max(1, end));
    }

    private record WrappedLine(String text, int consumedChars) {
    }

    private static void drawRaisedPlate(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, PANEL_RAISED);
        context.fill(left, bottom - 1, right, bottom, EDGE_MID);
    }

    private static void drawInsetWell(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        if (right - left < 4 || bottom - top < 4) return;
        context.fill(left, top, right, bottom, SECTION_BG);
        context.fill(left, top, right, top + 1, SECTION_BORDER);
        context.fill(left, bottom - 1, right, bottom, SECTION_BORDER);
    }

    private static void drawCornerPins(GuiGraphicsExtractor context, int left, int right, int top, int bottom) {
        // Intentionally empty. UI 0.12 removes decorative corner hardware.
    }
}
