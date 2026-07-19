package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class TrackerOpenScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int INPUT_Y = 78;
    private static final int HISTORY_Y = 136;
    private static final int HISTORY_ROW_HEIGHT = 23;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final List<TrackerHistoryEntry> history = new ArrayList<>();
    private TextFieldWidget sessionInput;
    private String status = "";
    private int historyPage;

    public TrackerOpenScreen(Screen parent) {
        super(Text.literal("Трекер сборки MapKluss"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearChildren();
        loadHistory();
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int gap = 4;
        int openWidth = Math.min(88, Math.max(68, panelWidth / 5));
        int inputWidth = panelWidth - openWidth - gap;
        sessionInput = new TextFieldWidget(textRenderer, left, INPUT_Y, inputWidth, 20, CompanionI18n.text("UUID сборки"));
        sessionInput.setPlaceholder(CompanionI18n.text("UUID сборки"));
        sessionInput.setMaxLength(64);
        addDrawableChild(sessionInput);
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Открыть"), button -> openSession(sessionInput.getText().trim()))
            .gold().dimensions(left + inputWidth + gap, INPUT_Y, openWidth, 20).build());

        rebuildHistoryButtons(left, panelWidth);
        addDrawableChild(MapKlussUi.languageButton(this));
        addDrawableChild(MapKlussUi.backButton(this, parent, left));
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
            addDrawableChild(MapKlussButton.builder(Text.literal(label), button -> openSession(entry.sessionId()))
                .tooltip(Text.literal(label))
                .dimensions(left, rowY, panelWidth - artWidth - gap, 20).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Арт"), button -> openHistoryArt(entry))
                .tooltip(CompanionI18n.text("Открыть связанный арт"))
                .dimensions(left + panelWidth - artWidth, rowY, artWidth, 20)
                .enabledWhen(() -> entry.artId() != null && !entry.artId().isBlank()).build());
        }
        if (totalPages(pageSize) > 1) {
            addDrawableChild(MapKlussButton.builder(Text.literal("Стр " + (historyPage + 1) + "/" + totalPages(pageSize)), button -> {
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
        client().setScreen(new TrackerSessionScreen(this, trimmed));
    }

    private void loadHistory() {
        try {
            TrackerHistoryStore store = TrackerHistoryStore.load(LitematicaPaths.trackerHistoryPath(client().runDirectory.toPath()));
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
        client().setScreen(new CompanionArtScreen(this, entry.artId(), entry.title()));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        MapKlussUi.drawPanel(context, width, PANEL_WIDTH, 14, MapKlussUi.panelBottom(height));
        MapKlussUi.drawHeader(context, textRenderer, title.getString(), "", width, 20);
        MapKlussUi.drawStatusIn(context, textRenderer, status, left + panelWidth / 2, 38, panelWidth - 20);
        MapKlussUi.drawFieldLabel(context, textRenderer, "UUID сборки", left, INPUT_Y, panelWidth);
        MapKlussUi.drawLeft(context, textRenderer, "Недавние сессии трекера", left + 2, 116, panelWidth - 92, MapKlussUi.ACCENT);
        if (history.isEmpty()) {
            MapKlussUi.drawEmptyState(context, textRenderer, "Недавних сессий пока нет", "", left, HISTORY_Y, panelWidth, Math.max(42, height - HISTORY_Y - 50));
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private String formatInstant(String value) {
        try {
            return TIME_FORMAT.format(Instant.parse(value));
        } catch (Exception ignored) {
            return value == null ? CompanionI18n.translate("неизвестно") : value;
        }
    }

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
