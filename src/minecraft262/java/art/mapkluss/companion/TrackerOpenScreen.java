package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TrackerOpenScreen extends Screen {
    private static final int PANEL_WIDTH = 1120;
    private static final int INPUT_Y = 78;
    private static final int HISTORY_Y = 136;
    private static final int HISTORY_ROW_HEIGHT = 23;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final List<TrackerHistoryEntry> history = new ArrayList<>();
    private EditBox sessionInput;
    private String status = "";
    private int historyPage;

    public TrackerOpenScreen(Screen parent) {
        super(Component.literal("Трекер сборки MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        loadHistory();
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int gap = 4;
        int openWidth = Math.min(88, Math.max(68, panelWidth / 5));
        int inputWidth = panelWidth - openWidth - gap;
        sessionInput = new EditBox(font, left, INPUT_Y, inputWidth, 20, CompanionI18n.text("UUID сборки"));
        sessionInput.setHint(CompanionI18n.text("UUID сборки"));
        sessionInput.setMaxLength(64);
        addRenderableWidget(sessionInput);
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Открыть"), button -> openSession(sessionInput.getValue().trim()))
            .gold().dimensions(left + inputWidth + gap, INPUT_Y, openWidth, 20).build());

        rebuildHistoryButtons(left, panelWidth);
        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left));
        setFocused(sessionInput);
        sessionInput.setFocused(true);
    }

    private void rebuildHistoryButtons(int left, int panelWidth) {
        int rows = historyRowCapacity();
        int pageSize = CompanionLayout.pageSize(rows);
        historyPage = Math.max(0, Math.min(historyPage, totalPages(pageSize) - 1));
        int start = historyPage * pageSize;
        int end = Math.min(start + rows, history.size());
        int gap = 4;
        int artWidth = 52;
        for (int i = start; i < end; i++) {
            TrackerHistoryEntry entry = history.get(i);
            int rowY = HISTORY_Y + (i - start) * HISTORY_ROW_HEIGHT;
            String label = historyLabel(entry);
            addRenderableWidget(MapKlussButton.builder(Component.literal(label), button -> openSession(entry.sessionId()))
                .tooltip(Component.literal(label))
                .dimensions(left, rowY, panelWidth - artWidth - gap, 20).build());
            addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Арт"), button -> openHistoryArt(entry))
                .special()
                .tooltip(CompanionI18n.text("Открыть связанный арт"))
                .dimensions(left + panelWidth - artWidth, rowY, artWidth, 20)
                .enabledWhen(() -> entry.artId() != null && !entry.artId().isBlank()).build());
        }
        if (totalPages(pageSize) > 1) {
            addRenderableWidget(MapKlussButton.builder(Component.literal("Стр " + (historyPage + 1) + "/" + totalPages(pageSize)), button -> {
                    historyPage = (historyPage + 1) % totalPages(CompanionLayout.pageSize(historyRowCapacity()));
                    init();
                }).dimensions(left + panelWidth - 82, 108, 82, 20).build());
        }
    }

    private void openSession(String sessionId) {
        String trimmed = sessionId == null ? "" : sessionId.trim();
        if (trimmed.isEmpty()) {
            status = "Сначала вставьте UUID трекера.";
            return;
        }
        client().gui.setScreen(new TrackerSessionScreen(this, trimmed));
    }

    private void loadHistory() {
        try {
            TrackerHistoryStore store = TrackerHistoryStore.load(LitematicaPaths.trackerHistoryPath(client().gameDirectory.toPath()));
            history.clear();
            history.addAll(store.entries());
        } catch (Exception error) {
            history.clear();
            status = CompanionUiErrors.message("tracker", error);
        }
    }

    private int historyRowCapacity() {
        return MapKlussUi.visibleRows(height, HISTORY_Y, 46, HISTORY_ROW_HEIGHT, 8);
    }

    private int totalPages(int pageSize) {
        return Math.max(1, (history.size() + pageSize - 1) / pageSize);
    }

    private String historyLabel(TrackerHistoryEntry entry) {
        String entryTitle = entry.title() == null || entry.title().isBlank() ? entry.sessionId() : entry.title();
        return entryTitle + " / " + readableMode(entry.mode()) + " / " + formatInstant(entry.openedAt());
    }

    private String readableMode(String mode) {
        if ("building".equals(mode)) return CompanionI18n.translate("Стройка");
        if ("gathering".equals(mode)) return CompanionI18n.translate("Сбор");
        return CompanionI18n.translate("Трекер");
    }

    private void openHistoryArt(TrackerHistoryEntry entry) {
        if (entry.artId() == null || entry.artId().isBlank()) return;
        client().gui.setScreen(new CompanionArtScreen(this, entry.artId(), entry.title()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        MapKlussUi.drawBackdrop(context, width, height);
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        MapKlussUi.drawPanel(context, width, PANEL_WIDTH, 46, MapKlussUi.panelBottom(height));
        MapKlussUi.drawSectionAt(context, font, null, left, panelWidth, INPUT_Y - 14, 38);
        int historyTop = HISTORY_Y - 24;
        int historyBottom = Math.min(MapKlussUi.panelBottom(height) - 4, MapKlussUi.contentBottom(height));
        MapKlussUi.drawSectionAt(context, font, "Недавние сессии",
            left, panelWidth, historyTop, Math.max(0, historyBottom - historyTop));
        MapKlussUi.drawHeader(context, font, title.getString(), "", width, 20);
        MapKlussUi.drawStatusIn(context, font, status, left + panelWidth / 2, 38, panelWidth - 20);
        MapKlussUi.drawFieldLabel(context, font, "UUID сборки", left, INPUT_Y, panelWidth);
        if (history.isEmpty()) {
            MapKlussUi.drawEmptyState(context, font, "Недавних сессий пока нет", "", left, HISTORY_Y, panelWidth, Math.max(42, height - HISTORY_Y - 50));
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private String formatInstant(String value) {
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return value == null ? CompanionI18n.translate("неизвестно") : value;
        }
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }
}
