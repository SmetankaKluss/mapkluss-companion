package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.stream.Collectors;

public final class LensScreen extends Screen {
    private static final int ROW_HEIGHT = 23;

    private final Screen parent;
    private final LensManager manager = LensManager.instance();
    private final CompanionConfirmation deleteConfirmation = new CompanionConfirmation();

    private TextFieldWidget codeInput;
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
        super(Text.literal("MapKluss Lens"));
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
        clearChildren();
        deleteButton = null;
        CompanionUiLayout.Shell shell = lensShell();
        CompanionUiLayout.Rect work = lensWork(shell);
        int panelWidth = work.width();
        int left = work.x();
        int gap = 6;
        int inputY = joinY(work);
        int tabsY = tabsY(work);

        int joinWidth = Math.min(112, Math.max(84, panelWidth / 4));
        int refreshWidth = Math.min(72, Math.max(54, panelWidth / 7));
        int codeWidth = Math.max(76, panelWidth - joinWidth - refreshWidth - gap * 2);
        codeInput = new TextFieldWidget(textRenderer, left, inputY, codeWidth, 22, CompanionI18n.text("Код Lens"));
        codeInput.setMaxLength(20);
        codeInput.setText(code);
        addDrawableChild(codeInput);
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Войти по коду"), button -> {
                rememberCode();
                manager.join(client(), code);
            }).action("lens.join").selected(true).dimensions(left + codeWidth + gap, inputY, joinWidth, 22).build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Обновить"), button -> manager.refreshSessions(client()))
            .action("lens.refresh")
            .technical().dimensions(left + codeWidth + joinWidth + gap * 2, inputY, refreshWidth, 22).build());

        int half = Math.max(80, (panelWidth - gap) / 2);
        addDrawableChild(MapKlussButton.builder(Text.literal(sessionTabLabel()), button -> openOrAdvance(false))
            .action("lens.next_session_page")
            .selected(!placementsTab).dimensions(left, tabsY, half, 22).build());
        addDrawableChild(MapKlussButton.builder(Text.literal(placementTabLabel()), button -> openOrAdvance(true))
            .action("lens.next_placement_page")
            .selected(placementsTab).dimensions(left + half + gap, tabsY, panelWidth - half - gap, 22).build());

        reconcileSelection();
        if (placementsTab) {
            addPlacementControls(left, panelWidth);
        } else {
            addSessionControls(left, panelWidth);
        }

        addNavigationControls(shell);
        addDrawableChild(MapKlussUi.languageButtonAt(this, shell.topBar().right() - 38, shell.topBar().y() + 9));
        setFocused(null);
        codeInput.setFocused(false);
        fingerprint = fingerprint();
    }

    private void addSessionControls(int left, int panelWidth) {
        CompanionUiLayout.Rect work = lensWork(lensShell());
        int listY = listY(work);
        int gap = 6;
        boolean compact = work.height() < 210;
        int controlsY = work.bottom() - (compact ? 22 : 48);
        int rows = Math.max(0, Math.min(5, (controlsY - listY - 6) / ROW_HEIGHT));
        int pageSize = CompanionLayout.pageSize(rows);
        List<LensDtos.Session> sessions = manager.sessions();
        sessionPage = clampPage(sessionPage, sessions.size(), pageSize);
        int start = sessionPage * pageSize;
        int end = Math.min(start + rows, sessions.size());
        for (int i = start; i < end; i++) {
            LensDtos.Session session = sessions.get(i);
            int rowY = listY + (i - start) * ROW_HEIGHT;
            String label = session.title() + "  r" + session.revision() + "  " + session.grid().wide() + "x" + session.grid().tall();
            int leaveWidth = 68;
            addDrawableChild(MapKlussButton.builder(Text.literal(label), button -> {
                    rememberCode();
                    selectedSessionId = session.sessionId();
                    deleteConfirmation.reset();
                    rebuildControls();
                }).action("lens.select_session").selected(session.sessionId().equals(selectedSessionId))
                .tooltip(Text.literal(label))
                .dimensions(left, rowY, panelWidth - leaveWidth - gap, 20).build());
            MapKlussButton leave = MapKlussButton.builder(CompanionI18n.text("Выйти"), button -> manager.leave(client(), session.sessionId()))
                .action("lens.leave")
                .danger().tooltip(CompanionI18n.text(session.ownedByUser() ? "Личную сессию закрывают в редакторе" : "Выйти из группы Lens"))
                .dimensions(left + panelWidth - leaveWidth, rowY, leaveWidth, 20).build();
            leave.active = !session.ownedByUser();
            addDrawableChild(leave);
        }

        if (compact) {
            int visibilityWidth = Math.max(64, panelWidth / 3);
            addDrawableChild(MapKlussButton.builder(Text.literal(CompanionI18n.translate("Режим: ") + visibilityLabel(visibility)), button -> {
                    visibility = "personal".equals(visibility) ? "group" : "personal";
                    rebuildControls();
                }).action("lens.visibility_group").dimensions(left, controlsY, visibilityWidth, 22).build());
            LensDtos.Session selected = selectedSession();
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Закрепить по рамке"), button ->
                    manager.anchorTarget(client(), selectedSessionId, visibility))
                .action("lens.place").special().tooltip(CompanionI18n.text(selected == null ? "Сначала выберите сессию Lens" : "Закрепить по угловой рамке"))
                .dimensions(left + visibilityWidth + gap, controlsY, panelWidth - visibilityWidth - gap, 22)
                .enabledWhen(() -> selectedSession() != null && selectedSession().ownedByUser()).build());
            return;
        }
        int half = Math.max(80, (panelWidth - gap) / 2);
        addVisibilityButton(left, controlsY, half, "personal", "Личное");
        addVisibilityButton(left + half + gap, controlsY, panelWidth - half - gap, "group", "Группа");
        LensDtos.Session selected = selectedSession();
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Закрепить по угловой рамке"), button ->
                manager.anchorTarget(client(), selectedSessionId, visibility))
            .action("lens.place").special()
            .tooltip(CompanionI18n.text(selected == null ? "Сначала выберите сессию Lens" : "Закрепить по угловой рамке"))
            .dimensions(left, controlsY + 26, panelWidth, 22)
            .enabledWhen(() -> selectedSession() != null && selectedSession().ownedByUser())
            .build());
    }

    private void addPlacementControls(int left, int panelWidth) {
        CompanionUiLayout.Rect work = lensWork(lensShell());
        int listY = listY(work);
        int gap = 6;
        int actionsY = work.bottom() - 22;
        int rows = Math.max(0, Math.min(6, (actionsY - listY - 6) / ROW_HEIGHT));
        int pageSize = CompanionLayout.pageSize(rows);
        List<LensDtos.Placement> placements = manager.placements();
        placementPage = clampPage(placementPage, placements.size(), pageSize);
        int start = placementPage * pageSize;
        int end = Math.min(start + rows, placements.size());
        for (int i = start; i < end; i++) {
            LensDtos.Placement placement = placements.get(i);
            int rowY = listY + (i - start) * ROW_HEIGHT;
            String label = placement.title() + "  " + visibilityLabel(placement.visibility()) + "  r" + placement.revision();
            addDrawableChild(MapKlussButton.builder(Text.literal(label), button -> {
                    rememberCode();
                    selectedPlacementId = placement.placementId();
                    deleteConfirmation.reset();
                    rebuildControls();
                }).action("lens.select_placement").selected(placement.placementId().equals(selectedPlacementId))
                .tooltip(Text.literal(label))
                .dimensions(left, rowY, panelWidth, 20).build());
        }

        int actionWidth = Math.max(54, (panelWidth - gap * 3) / 4);
        LensDtos.Placement selected = selectedPlacement();
        boolean ownPlacement = selected != null && ownsSession(selected.sessionId());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Скрыть"), button -> manager.hidePlacement(selectedPlacementId))
            .action("lens.hide_placement")
            .tooltip(CompanionI18n.text(selected == null ? "Сначала выберите размещение Lens" : "Скрыть размещение локально"))
            .dimensions(left, actionsY, actionWidth, 20)
            .enabledWhen(() -> selectedPlacement() != null && !ownsSelectedPlacement())
            .build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Скрыть автора"), button -> {
                LensDtos.Placement placement = selectedPlacement();
                if (placement != null) manager.blockOwner(placement.ownerKey());
            }).action("lens.hide_author").tooltip(CompanionI18n.text(selected == null ? "Сначала выберите размещение Lens" : "Скрыть все размещения этого автора локально"))
            .dimensions(left + actionWidth + gap, actionsY, actionWidth, 20)
            .enabledWhen(() -> selectedPlacement() != null && !ownsSelectedPlacement())
            .build());
        addDrawableChild(MapKlussButton.builder(CompanionI18n.text("Жалоба"), button -> manager.reportPlacement(client(), selectedPlacement(), "other"))
            .action("lens.report")
            .danger().tooltip(CompanionI18n.text(selected == null ? "Сначала выберите размещение Lens" : "Отправить жалобу и скрыть размещение"))
            .dimensions(left + (actionWidth + gap) * 2, actionsY, actionWidth, 20).navigationOrder(900)
            .enabledWhen(() -> selectedPlacement() != null && !ownsSelectedPlacement())
            .build());
        deleteButton = addDrawableChild(MapKlussButton.builder(Text.literal(deleteConfirmation.armed() ? "Подтвердить удаление" : "Удалить"), button -> deleteSelectedPlacement())
            .action("lens.remove_placement")
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
        CompanionUiLayout.Rect work = lensWork(lensShell());
        int controlsY = work.bottom() - (work.height() < 210 ? 22 : 48);
        return Math.max(0, Math.min(5, (controlsY - listY(work) - 6) / ROW_HEIGHT));
    }

    private int placementRowCapacity() {
        CompanionUiLayout.Rect work = lensWork(lensShell());
        int actionsY = work.bottom() - 22;
        return Math.max(0, Math.min(6, (actionsY - listY(work) - 6) / ROW_HEIGHT));
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
            addDrawableChild(MapKlussButton.builder(CompanionI18n.text(label), button -> {
                rememberCode();
                visibility = value;
                rebuildControls();
            }).action("personal".equals(value) ? "lens.visibility_private" : "lens.visibility_group")
                .selected(value.equals(visibility)).dimensions(x, y, buttonWidth, 20).build());
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
        if (codeInput != null) code = codeInput.getText();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CompanionUiLayout.Shell shell = MapKlussUi.drawShell(
            context, textRenderer, width, height,
            ScreenViewModel.shell(CompanionUiLayout.Destination.LENS, "MapKluss Lens", manager.status()), false
        );
        CompanionUiLayout.Rect work = lensWork(shell);
        drawLiveSummary(context, work);
        int listTop = listY(work);

        if (!placementsTab && manager.sessions().isEmpty()) {
            drawLensEmptyState(context, work, listTop, "Нет активных сессий", "Запусти Lens в редакторе или войди по коду", work.height() < 210 ? 26 : 54);
        }
        if (placementsTab && manager.placements().isEmpty()) {
            drawLensEmptyState(context, work, listTop, "Размещений пока нет", "Выбери сессию и закрепи её по угловой рамке", 32);
        }
        super.render(context, mouseX, mouseY, delta);
        MapKlussUi.drawNavigation(context, shell, CompanionUiLayout.Destination.LENS);
    }

    private void drawLensEmptyState(
        DrawContext context,
        CompanionUiLayout.Rect work,
        int listTop,
        String title,
        String detail,
        int reservedBottom
    ) {
        int availableHeight = Math.max(0, work.bottom() - listTop - reservedBottom);
        if (availableHeight >= 42) {
            MapKlussUi.drawEmptyState(context, textRenderer, title, detail, work.x(), listTop, work.width(), availableHeight);
        } else if (availableHeight >= textRenderer.fontHeight) {
            MapKlussUi.drawCenteredIn(context, textRenderer, title, work.x() + work.width() / 2,
                listTop + Math.max(0, (availableHeight - textRenderer.fontHeight) / 2), work.width() - 20, MapKlussUi.ACCENT);
        }
    }

    private void drawLiveSummary(DrawContext context, CompanionUiLayout.Rect work) {
        if (work.height() < 170) return;
        int top = work.y();
        int height = Math.min(58, Math.max(38, joinY(work) - top - 8));
        context.fill(work.x(), top, work.right(), top + height, UiTheme.SURFACE_RAISED);
        LensDtos.Session selected = selectedSession();
        int accent = selected == null ? UiTheme.AMBER : "active".equals(selected.status()) ? UiTheme.LIME : UiTheme.CYAN;
        context.fill(work.x(), top, work.x() + 3, top + height, accent);
        String title = selected == null ? "Lens не подключён" : selected.title();
        String detail = selected == null
            ? "Ожидаю live-сессию редактора"
            : "revision " + selected.revision() + "  ·  " + selected.viewerCount() + " зр.  ·  "
                + selected.grid().wide() + "×" + selected.grid().tall() + "  ·  " + selected.tileResolution() + "px/карта";
        MapKlussUi.drawLeft(context, textRenderer, title, work.x() + 14, top + 11, work.width() - 28, MapKlussUi.WHITE);
        MapKlussUi.drawLeft(context, textRenderer, detail, work.x() + 14, top + 29, work.width() - 28, MapKlussUi.MUTED);
    }

    private CompanionUiLayout.Shell lensShell() {
        return CompanionUiLayout.shell(width, height, false);
    }

    private CompanionUiLayout.Rect lensWork(CompanionUiLayout.Shell shell) {
        CompanionUiLayout.Rect content = shell.content();
        return new CompanionUiLayout.Rect(content.x() + 14, content.y() + 12, Math.max(1, content.width() - 28), Math.max(1, content.height() - 24));
    }

    private int joinY(CompanionUiLayout.Rect work) {
        return work.y() + (work.height() < 170 ? 0 : work.height() < 210 ? 46 : 70);
    }

    private int tabsY(CompanionUiLayout.Rect work) {
        return joinY(work) + 30;
    }

    private int listY(CompanionUiLayout.Rect work) {
        return tabsY(work) + 30;
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
            case LENS -> { }
            case SCAN -> client().setScreen(new ScanScreen(this));
            case TRACKER -> client().setScreen(new TrackerOpenScreen(this));
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

    private MinecraftClient client() {
        return MinecraftClient.getInstance();
    }
}
