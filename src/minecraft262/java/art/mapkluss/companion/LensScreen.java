package art.mapkluss.companion;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LensScreen extends Screen {
    private static final int PANEL_WIDTH = 540;
    private static final int INPUT_Y = 88;
    private static final int TABS_Y = 118;
    private static final int LIST_Y = 155;
    private static final int ROW_HEIGHT = 23;

    private final Screen parent;
    private final LensManager manager = LensManager.instance();
    private final CompanionConfirmation deleteConfirmation = new CompanionConfirmation();

    private EditBox codeInput;
    private MapKlussButton deleteButton;
    private String code = "";
    private String selectedSessionId;
    private String selectedPlacementId;
    private String visibility = "personal";
    private String fingerprint = "";
    private boolean placementsTab;
    private int sessionPage;
    private int placementPage;

    public LensScreen(Screen parent) {
        super(Component.literal("MapKluss Lens"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        manager.screenOpened();
        rebuildControls();
        manager.refreshSessions(client());
    }

    @Override
    public void removed() {
        manager.screenClosed();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        String next = fingerprint();
        if (!next.equals(fingerprint) && (codeInput == null || !codeInput.isFocused())) {
            rememberCode();
            rebuildControls();
        }
    }

    private void rebuildControls() {
        clearWidgets();
        deleteButton = null;
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        int gap = 4;

        int joinWidth = Math.min(112, Math.max(84, panelWidth / 4));
        int refreshWidth = Math.min(70, Math.max(58, panelWidth / 7));
        int codeWidth = Math.max(76, panelWidth - joinWidth - refreshWidth - gap * 2);
        codeInput = new EditBox(font, left, INPUT_Y, codeWidth, 20, CompanionI18n.text("Код Lens"));
        codeInput.setMaxLength(20);
        codeInput.setValue(code);
        addRenderableWidget(codeInput);
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Войти по коду"), button -> {
                rememberCode();
                manager.join(client(), code);
            }).dimensions(left + codeWidth + gap, INPUT_Y, joinWidth, 20).build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Обновить"), button -> manager.refreshSessions(client()))
            .dimensions(left + codeWidth + joinWidth + gap * 2, INPUT_Y, refreshWidth, 20).build());

        int half = Math.max(80, (panelWidth - gap) / 2);
        addRenderableWidget(MapKlussButton.builder(Component.literal(sessionTabLabel()), button -> openOrAdvance(false))
            .selected(!placementsTab).dimensions(left, TABS_Y, half, 20).build());
        addRenderableWidget(MapKlussButton.builder(Component.literal(placementTabLabel()), button -> openOrAdvance(true))
            .selected(placementsTab).dimensions(left + half + gap, TABS_Y, panelWidth - half - gap, 20).build());

        reconcileSelection();
        if (placementsTab) {
            addPlacementControls(left, panelWidth);
        } else {
            addSessionControls(left, panelWidth);
        }

        addRenderableWidget(MapKlussUi.languageButton(this));
        addRenderableWidget(MapKlussUi.backButton(this, parent, left));
        setFocused(null);
        codeInput.setFocused(false);
        fingerprint = fingerprint();
    }

    private void addSessionControls(int left, int panelWidth) {
        int gap = 4;
        int contentBottom = Math.max(LIST_Y, height - 58);
        boolean showActions = height >= 260;
        int controlsY = showActions ? Math.max(LIST_Y, contentBottom - 48) : contentBottom;
        int rows = Math.max(0, Math.min(4, (controlsY - LIST_Y - 6) / ROW_HEIGHT));
        int pageSize = CompanionLayout.pageSize(rows);
        List<LensDtos.Session> sessions = manager.sessions();
        sessionPage = clampPage(sessionPage, sessions.size(), pageSize);
        int start = sessionPage * pageSize;
        int end = Math.min(start + rows, sessions.size());
        for (int i = start; i < end; i++) {
            LensDtos.Session session = sessions.get(i);
            int rowY = LIST_Y + (i - start) * ROW_HEIGHT;
            String label = session.title() + "  r" + session.revision() + "  " + session.grid().wide() + "x" + session.grid().tall();
            int leaveWidth = 68;
            addRenderableWidget(MapKlussButton.builder(Component.literal(label), button -> {
                    rememberCode();
                    selectedSessionId = session.sessionId();
                    deleteConfirmation.reset();
                    rebuildControls();
                }).selected(session.sessionId().equals(selectedSessionId))
                .tooltip(Component.literal(label))
                .dimensions(left, rowY, panelWidth - leaveWidth - gap, 20).build());
            MapKlussButton leave = MapKlussButton.builder(CompanionI18n.text("Выйти"), button -> manager.leave(client(), session.sessionId()))
                .danger().tooltip(CompanionI18n.text(session.ownedByUser() ? "Личную сессию закрывают в редакторе" : "Выйти из группы Lens"))
                .dimensions(left + panelWidth - leaveWidth, rowY, leaveWidth, 20).build();
            leave.active = !session.ownedByUser();
            addRenderableWidget(leave);
        }

        if (!showActions) return;
        int half = Math.max(80, (panelWidth - gap) / 2);
        addVisibilityButton(left, controlsY, half, "personal", "Личное");
        addVisibilityButton(left + half + gap, controlsY, panelWidth - half - gap, "group", "Группа");
        LensDtos.Session selected = selectedSession();
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Закрепить по левой нижней рамке"), button ->
                manager.anchorTarget(client(), selectedSessionId, visibility))
            .gold()
            .tooltip(CompanionI18n.text(selected == null ? "Сначала выберите сессию Lens" : "Закрепить по левой нижней рамке"))
            .dimensions(left, controlsY + 26, panelWidth, 20)
            .enabledWhen(() -> selectedSession() != null && selectedSession().ownedByUser())
            .build());
    }

    private void addPlacementControls(int left, int panelWidth) {
        int gap = 4;
        boolean showActions = height >= 240;
        int actionsY = showActions ? Math.max(LIST_Y, height - 80) : Math.max(LIST_Y, height - 58);
        int rows = Math.max(0, Math.min(5, (actionsY - LIST_Y - 6) / ROW_HEIGHT));
        int pageSize = CompanionLayout.pageSize(rows);
        List<LensDtos.Placement> placements = manager.placements();
        placementPage = clampPage(placementPage, placements.size(), pageSize);
        int start = placementPage * pageSize;
        int end = Math.min(start + rows, placements.size());
        for (int i = start; i < end; i++) {
            LensDtos.Placement placement = placements.get(i);
            int rowY = LIST_Y + (i - start) * ROW_HEIGHT;
            String label = placement.title() + "  " + visibilityLabel(placement.visibility()) + "  r" + placement.revision();
            addRenderableWidget(MapKlussButton.builder(Component.literal(label), button -> {
                    rememberCode();
                    selectedPlacementId = placement.placementId();
                    deleteConfirmation.reset();
                    rebuildControls();
                }).selected(placement.placementId().equals(selectedPlacementId))
                .tooltip(Component.literal(label))
                .dimensions(left, rowY, panelWidth, 20).build());
        }

        if (!showActions) return;
        int actionWidth = Math.max(54, (panelWidth - gap * 3) / 4);
        LensDtos.Placement selected = selectedPlacement();
        boolean ownPlacement = selected != null && ownsSession(selected.sessionId());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Скрыть"), button -> manager.hidePlacement(selectedPlacementId))
            .tooltip(CompanionI18n.text(selected == null ? "Сначала выберите размещение Lens" : "Скрыть размещение локально"))
            .dimensions(left, actionsY, actionWidth, 20)
            .enabledWhen(() -> selectedPlacement() != null && !ownsSelectedPlacement())
            .build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Скрыть автора"), button -> {
                LensDtos.Placement placement = selectedPlacement();
                if (placement != null) manager.blockOwner(placement.ownerKey());
            }).tooltip(CompanionI18n.text(selected == null ? "Сначала выберите размещение Lens" : "Скрыть все размещения этого автора локально"))
            .dimensions(left + actionWidth + gap, actionsY, actionWidth, 20)
            .enabledWhen(() -> selectedPlacement() != null && !ownsSelectedPlacement())
            .build());
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text("Жалоба"), button -> manager.reportPlacement(client(), selectedPlacement(), "other"))
            .danger().tooltip(CompanionI18n.text(selected == null ? "Сначала выберите размещение Lens" : "Отправить жалобу и скрыть размещение"))
            .dimensions(left + (actionWidth + gap) * 2, actionsY, actionWidth, 20).navigationOrder(900)
            .enabledWhen(() -> selectedPlacement() != null && !ownsSelectedPlacement())
            .build());
        deleteButton = addRenderableWidget(MapKlussButton.builder(Component.literal(deleteConfirmation.armed() ? "Подтвердить удаление" : "Удалить"), button -> deleteSelectedPlacement())
            .danger().tooltip(CompanionI18n.text("Удалить своё размещение Lens"))
            .dimensions(left + (actionWidth + gap) * 3, actionsY, panelWidth - (actionWidth + gap) * 3, 20).navigationOrder(1000)
            .enabledWhen(() -> selectedPlacement() != null && selectedPlacement().ownedByDevice())
            .build());
    }

    private void deleteSelectedPlacement() {
        if (selectedPlacement() == null || !selectedPlacement().ownedByDevice()) return;
        if (!deleteConfirmation.confirmOrArm()) {
            manager.setLocalStatus(CompanionI18n.translate("Нажмите ещё раз для подтверждения"));
            if (deleteButton != null) deleteButton.setMessage(CompanionI18n.text("Подтвердить удаление"));
            return;
        }
        manager.deletePlacement(client(), selectedPlacementId);
    }

    private void openOrAdvance(boolean nextPlacementsTab) {
        rememberCode();
        deleteConfirmation.reset();
        if (placementsTab == nextPlacementsTab) {
            if (placementsTab) placementPage = advancePage(placementPage, manager.placements().size(), placementRowCapacity());
            else sessionPage = advancePage(sessionPage, manager.sessions().size(), sessionRowCapacity());
        } else {
            placementsTab = nextPlacementsTab;
        }
        rebuildControls();
    }

    private int sessionRowCapacity() {
        int controlsY = height >= 260
            ? Math.max(LIST_Y, Math.max(LIST_Y, height - 58) - 48)
            : Math.max(LIST_Y, height - 58);
        return Math.max(0, Math.min(4, (controlsY - LIST_Y - 6) / ROW_HEIGHT));
    }

    private int placementRowCapacity() {
        int actionsY = height >= 240 ? Math.max(LIST_Y, height - 80) : Math.max(LIST_Y, height - 58);
        return Math.max(0, Math.min(5, (actionsY - LIST_Y - 6) / ROW_HEIGHT));
    }

    private int advancePage(int current, int totalItems, int visibleRows) {
        int pages = totalPages(totalItems, CompanionLayout.pageSize(visibleRows));
        return pages <= 1 ? 0 : (current + 1) % pages;
    }

    private String sessionTabLabel() {
        int rows = CompanionLayout.pageSize(sessionRowCapacity());
        return CompanionI18n.translate("Сессии") + " · " + (sessionPage + 1) + "/" + totalPages(manager.sessions().size(), rows);
    }

    private String placementTabLabel() {
        int rows = CompanionLayout.pageSize(placementRowCapacity());
        return CompanionI18n.translate("Размещения рядом") + " · " + (placementPage + 1) + "/" + totalPages(manager.placements().size(), rows);
    }

    private static int totalPages(int totalItems, int pageSize) {
        return Math.max(1, (totalItems + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
    }

    private static int clampPage(int current, int totalItems, int pageSize) {
        return Math.max(0, Math.min(current, totalPages(totalItems, pageSize) - 1));
    }

    private void addVisibilityButton(int x, int y, int buttonWidth, String value, String label) {
        addRenderableWidget(MapKlussButton.builder(CompanionI18n.text(label), button -> {
                rememberCode();
                visibility = value;
                rebuildControls();
            }).selected(value.equals(visibility)).dimensions(x, y, buttonWidth, 20).build());
    }

    private void reconcileSelection() {
        List<LensDtos.Session> sessions = manager.sessions();
        if (selectedSessionId == null || sessions.stream().noneMatch(session -> session.sessionId().equals(selectedSessionId))) {
            selectedSessionId = sessions.isEmpty() ? null : sessions.get(0).sessionId();
        }
        List<LensDtos.Placement> placements = manager.placements();
        if (selectedPlacementId == null || placements.stream().noneMatch(placement -> placement.placementId().equals(selectedPlacementId))) {
            selectedPlacementId = placements.isEmpty() ? null : placements.get(0).placementId();
        }
    }

    private LensDtos.Placement selectedPlacement() {
        return manager.placements().stream()
            .filter(placement -> placement.placementId().equals(selectedPlacementId))
            .findFirst().orElse(null);
    }

    private LensDtos.Session selectedSession() {
        return manager.sessions().stream()
            .filter(session -> session.sessionId().equals(selectedSessionId))
            .findFirst().orElse(null);
    }

    private boolean ownsSession(String sessionId) {
        return manager.sessions().stream()
            .anyMatch(session -> session.sessionId().equals(sessionId) && session.ownedByUser());
    }

    private boolean ownsSelectedPlacement() {
        LensDtos.Placement placement = selectedPlacement();
        return placement != null && ownsSession(placement.sessionId());
    }

    private String visibilityLabel(String value) {
        return switch (value) {
            case "personal" -> CompanionI18n.translate("Личное");
            case "group" -> CompanionI18n.translate("Группа");
            default -> value;
        };
    }

    private String fingerprint() {
        return manager.sessions().stream().map(session -> session.sessionId() + ":" + session.revision()).collect(Collectors.joining("|"))
            + "/" + manager.placements().stream().map(placement -> placement.placementId() + ":" + placement.revision()).collect(Collectors.joining("|"));
    }

    private void rememberCode() {
        if (codeInput != null) code = codeInput.getValue();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int panelWidth = MapKlussUi.panelWidth(width, PANEL_WIDTH);
        int left = MapKlussUi.centeredLeft(width, panelWidth);
        MapKlussUi.drawPanel(context, width, PANEL_WIDTH, 18, MapKlussUi.panelBottom(height));
        MapKlussUi.drawHeader(context, font, "MapKluss Lens", "", width, 28);
        MapKlussUi.drawFieldLabel(context, font, "Код Lens", left, INPUT_Y, panelWidth);

        if (!placementsTab && manager.sessions().isEmpty()) {
            MapKlussUi.drawEmptyState(context, font, "Нет активных сессий", "", left, LIST_Y, panelWidth, Math.max(42, height - LIST_Y - 112));
        }
        if (placementsTab && manager.placements().isEmpty()) {
            MapKlussUi.drawEmptyState(context, font, "Размещений пока нет", "", left, LIST_Y, panelWidth, Math.max(42, height - LIST_Y - 92));
        }
        MapKlussUi.drawStatusIn(context, font, manager.status(), left + panelWidth / 2, 48, panelWidth - 90);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private Minecraft client() {
        return Minecraft.getInstance();
    }
}
