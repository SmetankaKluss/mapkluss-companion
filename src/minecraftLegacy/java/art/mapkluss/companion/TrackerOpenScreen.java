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
        CompanionUiLayout.Shell shell = trackerShell();
        CompanionUiLayout.Rect work = trackerWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        int gap = 6;
        int openWidth = Math.min(88, Math.max(68, panelWidth / 5));
        int inputWidth = panelWidth - openWidth - gap;
        sessionInput = new TextFieldWidget(textRenderer, left, work.y(), inputWidth, 22, CompanionI18n.text("UUID сборки"));
        sessionInput.setPlaceholder(CompanionI18n.text("UUID сборки"));
        sessionInput.setMaxLength(64);
        addDrawableChild(sessionInput);
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Открыть"), button -> openSession(sessionInput.getText().trim()))
            .action("tracker.open_session")
            .selected(true).dimensions(left + inputWidth + gap, work.y(), openWidth, 22).build());

        rebuildHistoryButtons(left, panelWidth);
        addNavigationControls(shell);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
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
            int rowY = historyY() + (i - start) * HISTORY_ROW_HEIGHT;
            String label = historyLabel(entry);
            addDrawableChild(MapKlussButton.builder(Text.literal(label), button -> openSession(entry.sessionId()))
                .action("tracker.open_session")
                .tooltip(Text.literal(label))
                .dimensions(left, rowY, panelWidth - artWidth - gap, 20).build());
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Арт"), button -> openHistoryArt(entry))
                .action("tracker.open_history_art")
                .special()
                .tooltip(CompanionI18n.text("Открыть связанный арт"))
                .dimensions(left + panelWidth - artWidth, rowY, artWidth, 20)
                .enabledWhen(() -> entry.artId() != null && !entry.artId().isBlank()).build());
        }
        if (totalPages(pageSize) > 1) {
            addDrawableChild(MapKlussButton.builder(Text.literal("Стр " + (historyPage + 1) + "/" + totalPages(pageSize)), button -> {
                    historyPage = (historyPage + 1) % totalPages(CompanionLayout.pageSize(historyRowCapacity()));
                    init();
                }).action("tracker.history_page").dimensions(left + panelWidth - 82, historyY() - 28, 82, 20).build());
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
        CompanionUiLayout.Rect work = trackerWork(trackerShell());
        return Math.max(0, Math.min(10, (work.bottom() - historyY() - 8) / HISTORY_ROW_HEIGHT));
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
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.TRACKER, CompanionI18n.translate("Трекер"), status), false
        );
        CompanionUiLayout.Rect work = trackerWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        MapKlussUi.drawLeft(context, textRenderer, "Недавние сессии", left, historyY() - 18, panelWidth, MapKlussUi.MUTED);
        if (history.isEmpty()) {
            MapKlussUi.drawEmptyState(context, textRenderer, "Недавних сессий пока нет", "Открой трекер из сохранённого арта или вставь UUID", left, historyY(), panelWidth, Math.max(42, work.bottom() - historyY()));
        }
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.TRACKER);
    }

    private CompanionUiLayout.Shell trackerShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect trackerWork(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 14, content.y() + 12, Math.max(1, content.width() - 28), Math.max(1, content.height() - 24));
    }

    private int historyY() {
        return trackerWork(trackerShell()).y() + 58;
    }

    private void addNavigationControls(CompanionUiLayout.Shell shell) {
        for (int i = 0; i <= CompanionUiLayout.Destination.ACCOUNT.ordinal(); i++) {
            CompanionUiLayout.Destination destination = CompanionUiLayout.Destination.values()[i];
            CompanionUiLayout.Rect rect = CompanionUiLayout.navigationButton(shell, i);
            addDrawableChild(MapKlussButton.builder(Text.literal(""), button -> openDestination(destination))
                .action(CompanionActionInventory.navigationAction(destination))
                .tooltip(CompanionI18n.text(destinationLabel(destination)))
                .dimensions(rect.x(), rect.y(), rect.width(), rect.height()).build());
        }
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().setScreen(new CompanionLibraryScreen(this));
            case LENS -> client().setScreen(new LensScreen(this));
            case SCAN -> client().setScreen(new ScanScreen(this));
            case TRACKER -> { }
            case ACCOUNT -> client().setScreen(new CompanionAccountScreen(this));
            default -> { }
        }
    }

    private String destinationLabel(CompanionUiLayout.Destination destination) {
        return switch (destination) {
            case LIBRARY -> "Библиотека";
            case LENS -> "Lens";
            case SCAN -> "Скан";
            case TRACKER -> "Трекер";
            case ACCOUNT -> "Аккаунт";
            default -> destination.name();
        };
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
