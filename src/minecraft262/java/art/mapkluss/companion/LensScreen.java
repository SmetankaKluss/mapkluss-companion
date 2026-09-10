package art.mapkluss.companion;

import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class LensScreen extends Screen {
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


    private final boolean fixture;
    private boolean opened;
    private boolean fixtureApplied;
    private boolean showPreview;
    private WorkshopTheme workshopTheme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private List<LensDtos.Session> fixtureSessions = List.of();
    private List<LensDtos.Placement> fixturePlacements = List.of();
    private String fixtureStatus = "";

    public LensScreen(Screen parent) { this(parent, false); }

    LensScreen(Screen parent, boolean fixture) {
        super(Component.literal("MapKluss Lens"));
        this.parent = parent;
        this.fixture = fixture;
    }

    void applyDevelopmentData(List<LensDtos.Session> sessions, List<LensDtos.Placement> placements, String status) {
        if (!fixture) return;
        fixtureSessions = List.copyOf(sessions);
        fixturePlacements = List.copyOf(placements);
        fixtureStatus = status;
    }

    private List<LensDtos.Session> sessions() { return fixture ? fixtureSessions : manager.sessions(); }
    private List<LensDtos.Placement> placements() { return fixture ? fixturePlacements : manager.placements(); }
    private String status() { return fixture ? fixtureStatus : manager.status(); }

    @Override
    protected void init() {
        if (fixture && !fixtureApplied) {
            fixtureApplied = true;
            try {
                Class.forName("art.mapkluss.companion.CompanionLibraryDevFixture")
                    .getMethod("applyLens", Object.class).invoke(null, this);
            } catch (ReflectiveOperationException ignored) { }
        }
        if (!fixture && !opened) {
            opened = true;
            manager.screenOpened();
            manager.refreshSessions(client());
        }
        rebuildControls();
    }

    @Override
    public void removed() {
        rememberCode();
        deleteConfirmation.reset();
        if (opened) { manager.screenClosed(); opened = false; }
        super.removed();
    }

    @Override
    public void onClose() {
        deleteConfirmation.reset();
        client().gui.setScreen(parent);
    }

    @Override
    public void tick() {
        super.tick();
        String next = fingerprint();
        if (!next.equals(fingerprint) && (codeInput == null || !codeInput.isFocused())) rebuildControls();
    }

    private MapKlussButton lensButton(String id, String label, WorkshopIcon icon, WorkshopLayout.Rect r,
        boolean enabled, boolean selected, boolean local, Runnable action) {
        var builder = MapKlussButton.builder(CompanionI18n.text(label), button -> {
            if (enabled && (!fixture || local) && LensUiPermissions.allowed(id, selectedSession(), selectedPlacement(), ownsSelectedPlacement())) action.run();
        }).action(id).tooltip(CompanionI18n.text(label)).selected(selected)
            .dimensions(r.x(), r.y(), r.width(), r.height()).enabledWhen(() -> enabled && (!fixture || local)
                && LensUiPermissions.allowed(id, selectedSession(), selectedPlacement(), ownsSelectedPlacement()));
        if (id.equals("lens.place")) builder.gold();
        if (id.equals("lens.remove_placement") || id.equals("lens.report")) builder.danger();
        return addRenderableWidget(builder.build().workshop(workshopTheme, icon));
    }

    private WorkshopLayout.Rect part(WorkshopLayout.Rect r, int index, int count) {
        int w = (r.width() - (count - 1) * 4) / count;
        return new WorkshopLayout.Rect(r.x() + index * (w + 4), r.y(), w, r.height());
    }

    private void rebuildControls() {
        rememberCode();
        clearWidgets();
        codeInput = null;
        deleteButton = null;
        try { workshopTheme = WorkshopTheme.of(CompanionConfig.load(client().gameDirectory.toPath()).theme()); }
        catch (Exception ignored) { }
        reconcileSelection();
        var s = WorkshopLensLayout.at(width, height);
        int slots = (s.navigation().width() - 60) / 5;
        WorkshopIcon[] icons = {WorkshopIcon.LIBRARY, WorkshopIcon.LENS, WorkshopIcon.SCAN, WorkshopIcon.TRACKER, WorkshopIcon.ACCOUNT};
        for (int i = 0; i < 5; i++) {
            var d = CompanionUiLayout.Destination.values()[i];
            lensButton(CompanionActionInventory.navigationAction(d), destinationLabel(d), icons[i],
                new WorkshopLayout.Rect(s.navigation().x() + i * slots, s.navigation().y(), slots - 4, 28),
                true, d == CompanionUiLayout.Destination.LENS, true, () -> openDestination(d));
        }
        lensButton("account.theme", "Оформление", WorkshopIcon.LAYERS,
            new WorkshopLayout.Rect(s.navigation().right() - 56, s.navigation().y(), 24, 28), true, false, true,
            () -> client().gui.setScreen(new WorkshopAppearanceScreen(this)));
        lensButton("global.back", "Назад", WorkshopIcon.BACK,
            new WorkshopLayout.Rect(s.navigation().right() - 24, s.navigation().y(), 24, 28), true, false, true, this::onClose);
        lensButton("global.language", CompanionI18n.toggleLabel(client()), null,
            new WorkshopLayout.Rect(s.status().right() - 68, s.status().y(), 32, 20), true, false, true, () -> {
                try { CompanionI18n.toggle(client()); rebuildControls(); } catch (Exception ignored) { }
            });
        lensButton("lens.refresh", "Обновить", WorkshopIcon.REFRESH,
            new WorkshopLayout.Rect(s.status().right() - 32, s.status().y(), 32, 20), true, false, false,
            () -> manager.refreshSessions(client()));

        if (!s.split()) lensButton("lens.preview", showPreview ? "Сессии" : "Превью", WorkshopIcon.LENS,
            new WorkshopLayout.Rect(s.footer().right() - 28, s.footer().y(), 28, 20), true, showPreview, true, () -> {
                showPreview = !showPreview; rebuildControls();
            });

        if (s.split() || !showPreview) {
            var join = s.join();
            codeInput = new EditBox(font, join.x(), join.y(), join.width() - 32, 24, CompanionI18n.text("Код Lens"));
            codeInput.setMaxLength(20);
            codeInput.setHint(CompanionI18n.text("Код Lens"));
            codeInput.setValue(code);
            codeInput.setResponder(value -> code = value);
            addRenderableWidget(codeInput);
            lensButton("lens.join", "Войти по коду", WorkshopIcon.LINK,
                new WorkshopLayout.Rect(join.right() - 28, join.y(), 28, 24), true, false, false,
                () -> { rememberCode(); manager.join(client(), code); });

            sessionPage = clampPage(sessionPage, sessions().size(), s.rows());
            placementPage = clampPage(placementPage, placements().size(), s.rows());
            lensButton("lens.next_session_page", sessionTabLabel(), WorkshopIcon.LENS, part(s.tabs(), 0, 2),
                true, !placementsTab, true, () -> openOrAdvance(false));
            lensButton("lens.next_placement_page", placementTabLabel(), WorkshopIcon.LAYERS, part(s.tabs(), 1, 2),
                true, placementsTab, true, () -> openOrAdvance(true));
            if (placementsTab) addPlacementControls(s); else addSessionControls(s);
            lensButton("lens.visibility_private", "Личное", WorkshopIcon.ACCOUNT, part(s.visibility(), 0, 2),
                !placementsTab, displayedVisibility().equals("personal"), true, () -> { visibility = "personal"; rebuildControls(); });
            lensButton("lens.visibility_group", "Группа", WorkshopIcon.LINK, part(s.visibility(), 1, 2),
                !placementsTab, displayedVisibility().equals("group"), true, () -> { visibility = "group"; rebuildControls(); });
        }
        addActions(s.actions());
        fingerprint = fingerprint();
    }

    private void addSessionControls(WorkshopLensLayout.Layout layout) {
        int start = sessionPage * layout.rows();
        var sessions = sessions();
        for (int i = start; i < Math.min(sessions.size(), start + layout.rows()); i++) {
            var session = sessions.get(i);
            lensButton("lens.select_session", session.title(), WorkshopIcon.LENS, layout.row(i - start),
                true, session.sessionId().equals(selectedSessionId), true, () -> {
                    selectedSessionId = session.sessionId(); deleteConfirmation.reset(); rebuildControls();
                });
        }
    }

    private void addPlacementControls(WorkshopLensLayout.Layout layout) {
        int start = placementPage * layout.rows();
        var placements = placements();
        for (int i = start; i < Math.min(placements.size(), start + layout.rows()); i++) {
            var placement = placements.get(i);
            lensButton("lens.select_placement", placement.title(), WorkshopIcon.LAYERS, layout.row(i - start),
                true, placement.placementId().equals(selectedPlacementId), true, () -> {
                    selectedPlacementId = placement.placementId(); deleteConfirmation.reset(); rebuildControls();
                });
        }
    }

    private void addActions(WorkshopLayout.Rect r) {
        if (!placementsTab) {
            var selected = selectedSession();
            lensButton("lens.place", "Закрепить по рамке", WorkshopIcon.INSTALL, part(r, 0, 2),
                selected != null && selected.ownedByUser(), false, false,
                () -> manager.anchorTarget(client(), selectedSessionId, visibility));
            if (selected != null && selected.ownedByUser()) {
                lensButton("lens.open_editor", "Редактор", WorkshopIcon.LINK, part(r, 1, 2),
                    true, false, false, () -> manager.openCloudEditor(client(), selectedSessionId));
            } else {
                lensButton("lens.leave", "Выйти", WorkshopIcon.LINK, part(r, 1, 2),
                    selected != null, false, false, () -> manager.leave(client(), selectedSessionId));
            }
        } else {
            boolean selected = selectedPlacement() != null;
            boolean other = selected && !ownsSelectedPlacement();
            lensButton("lens.hide_placement", "Скрыть", WorkshopIcon.CLOSE, part(r, 0, 4), other, false, false,
                () -> manager.hidePlacement(selectedPlacementId));
            lensButton("lens.hide_author", "Скрыть автора", WorkshopIcon.ACCOUNT, part(r, 1, 4), other, false, false,
                () -> { var p = selectedPlacement(); if (p != null) manager.blockOwner(p.ownerKey()); });
            lensButton("lens.report", "Жалоба", WorkshopIcon.MORE, part(r, 2, 4), other, false, false,
                () -> manager.reportPlacement(client(), selectedPlacement(), "other"));
            deleteButton = lensButton("lens.remove_placement", deleteConfirmation.armed() ? "Подтвердить удаление" : "Удалить",
                WorkshopIcon.DELETE, part(r, 3, 4), selected && selectedPlacement().ownedByDevice(),
                false, false, this::deleteSelectedPlacement);
        }
    }

    private int sessionRowCapacity() { return WorkshopLensLayout.at(width, height).rows(); }
    private String displayedVisibility() {
        return placementsTab && selectedPlacement() != null ? selectedPlacement().visibility() : visibility;
    }
    private int placementRowCapacity() { return WorkshopLensLayout.at(width, height).rows(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        var s = WorkshopLensLayout.at(width, height);
        WorkshopChrome.frame(context::fill, s.frame(), workshopTheme);
        context.fill(s.status().x(), s.status().bottom() + 1, s.status().right(), s.status().bottom() + 2, workshopTheme.color("border-subtle"));
        var session = previewSession();
        String summary = session == null ? CompanionI18n.translate("Нет активных сессий")
            : CompanionI18n.translate("active".equals(session.status()) ? "Сессия активна" : "Редактор не в сети")
                + "  r" + session.revision() + "  " + session.viewerCount() + " " + CompanionI18n.translate("Участники");
        WorkshopDraw.text(context, font, summary, s.status().x() + 4, s.status().y() + 5,
            s.status().width() - 80, workshopTheme.color("text-primary"));
        if (s.split() || showPreview) {
            var p = s.preview();
            context.fill(p.x(), p.y(), p.right(), p.bottom(), workshopTheme.color("field-bg"));
            drawPreview(context, p, session);
            String title = placementsTab && selectedPlacement() != null ? selectedPlacement().title() : session == null ? "" : session.title();
            if (session != null) title = session.grid().wide() + "x" + session.grid().tall() + "  " + title;
            WorkshopDraw.text(context, font, title, s.metadata().x() + 4, s.metadata().y() + 6,
                s.metadata().width() - 8, workshopTheme.color("text-primary"));
        }
        if ((s.split() || !showPreview) && (placementsTab ? placements().isEmpty() : sessions().isEmpty()))
            WorkshopDraw.text(context, font, CompanionI18n.translate(placementsTab ? "Размещений пока нет" : "Нет активных сессий"),
                s.list().x() + 4, s.list().y() + 6, s.list().width() - 8, workshopTheme.color("text-secondary"));
        String footer = status();
        if (footer.isBlank() && session != null && session.sessionCode() != null && !session.sessionCode().isBlank())
            footer = CompanionI18n.translate("Код Lens") + ": " + session.sessionCode();
        WorkshopDraw.text(context, font, CompanionI18n.translate(footer), s.footer().x() + 4,
            s.footer().y() + 6, s.footer().width() - 40, workshopTheme.color("text-secondary"));
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private LensDtos.Session previewSession() {
        if (!placementsTab) return selectedSession();
        var p = selectedPlacement();
        return p == null ? null : sessions().stream().filter(s -> s.sessionId().equals(p.sessionId())).findFirst().orElse(null);
    }

    private void drawPreview(GuiGraphicsExtractor context, WorkshopLayout.Rect p, LensDtos.Session session) {
        if (fixture && session != null) {
            var texture = net.minecraft.resources.Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "textures/dev/library/great-wave.png");
            WorkshopDraw.image(context, texture, p, 384, 256);
        } else if (session != null && manager.previewAtlas(session.sessionId(), session.revision()) != null) {
            WorkshopDraw.image(context, manager.previewAtlas(session.sessionId(), session.revision()), p,
                session.previewWidth(), session.previewHeight());
        } else {
            WorkshopDraw.icon(context, WorkshopIcon.LENS, p.x() + Math.max(0, (p.width() - 16) / 2),
                p.y() + Math.max(0, (p.height() - 16) / 2), workshopTheme.color("text-disabled"));
        }
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
            if (placementsTab) placementPage = advancePage(placementPage, placements().size(), placementRowCapacity());
            else sessionPage = advancePage(sessionPage, sessions().size(), sessionRowCapacity());
        } else {
            placementsTab = nextPlacementsTab;
        }
        rebuildControls();
    }

    private int advancePage(int current, int totalItems, int visibleRows) {
        int pages = totalPages(totalItems, CompanionLayout.pageSize(visibleRows));
        return pages <= 1 ? 0 : (current + 1) % pages;
    }

    private String sessionTabLabel() {
        int rows = CompanionLayout.pageSize(sessionRowCapacity());
        return CompanionI18n.translate("Сессии") + " · " + (sessionPage + 1) + "/" + totalPages(sessions().size(), rows);
    }

    private String placementTabLabel() {
        int rows = CompanionLayout.pageSize(placementRowCapacity());
        return CompanionI18n.translate("Размещения рядом") + " · " + (placementPage + 1) + "/" + totalPages(placements().size(), rows);
    }

    private static int totalPages(int totalItems, int pageSize) {
        return Math.max(1, (totalItems + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
    }

    private static int clampPage(int current, int totalItems, int pageSize) {
        return Math.max(0, Math.min(current, totalPages(totalItems, pageSize) - 1));
    }

    private void reconcileSelection() {
        List<LensDtos.Session> sessions = sessions();
        if (selectedSessionId == null || sessions.stream().noneMatch(session -> session.sessionId().equals(selectedSessionId))) {
            selectedSessionId = sessions.isEmpty() ? null : sessions.get(0).sessionId();
        }
        List<LensDtos.Placement> placements = placements();
        if (selectedPlacementId == null || placements.stream().noneMatch(placement -> placement.placementId().equals(selectedPlacementId))) {
            selectedPlacementId = placements.isEmpty() ? null : placements.get(0).placementId();
        }
    }

    private LensDtos.Placement selectedPlacement() {
        return placements().stream()
            .filter(placement -> placement.placementId().equals(selectedPlacementId))
            .findFirst().orElse(null);
    }

    private LensDtos.Session selectedSession() {
        return sessions().stream()
            .filter(session -> session.sessionId().equals(selectedSessionId))
            .findFirst().orElse(null);
    }

    private boolean ownsSession(String sessionId) {
        return sessions().stream()
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
        return sessions().toString() + "/" + placements().toString();
    }

    private void rememberCode() {
        if (codeInput != null) code = codeInput.getValue();
    }

    private void openDestination(CompanionUiLayout.Destination destination) {
        switch (destination) {
            case LIBRARY -> client().gui.setScreen(new CompanionLibraryScreen(this));
            case LENS -> { }
            case SCAN -> client().gui.setScreen(new ScanScreen(this, fixture));
            case TRACKER -> client().gui.setScreen(new TrackerOpenScreen(this, fixture));
            case ACCOUNT -> client().gui.setScreen(new CompanionAccountScreen(this, fixture));
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

    private Minecraft client() {
        return Minecraft.getInstance();
    }
}
