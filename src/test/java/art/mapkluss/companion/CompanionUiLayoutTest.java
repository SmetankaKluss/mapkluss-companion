package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionUiLayoutTest {
    @Test
    void selectsResponsiveModesAtDocumentedBreakpoints() {
        assertEquals(CompanionUiLayout.Mode.COMPACT, CompanionUiLayout.modeFor(320, 240));
        assertEquals(CompanionUiLayout.Mode.MEDIUM, CompanionUiLayout.modeFor(640, 360));
        assertEquals(CompanionUiLayout.Mode.WIDE, CompanionUiLayout.modeFor(960, 540));
    }

    @Test
    void shellRegionsNeverOverlap() {
        int[][] sizes = {{320, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};
        for (int[] size : sizes) {
            CompanionUiLayout.Shell shell = CompanionUiLayout.shell(size[0], size[1], true);
            assertFalse(shell.navigation().intersects(shell.topBar()));
            assertFalse(shell.navigation().intersects(shell.content()));
            assertFalse(shell.content().intersects(shell.inspector()));
            assertTrue(shell.app().width() > 0);
            assertTrue(shell.app().height() > 0);
        }
    }

    @Test
    void wideInspectorReservesEnoughRoomForLocalizedTabs() {
        CompanionUiLayout.Shell shell = CompanionUiLayout.shell(960, 540, true);
        assertTrue(shell.inspector().width() >= 300);
        assertTrue(shell.content().width() >= 500);
        assertFalse(shell.content().intersects(shell.inspector()));
    }

    @Test
    void focusedWorkflowPanelStaysInsideEverySupportedViewport() {
        int[][] sizes = {{320, 240}, {480, 270}, {640, 360}, {960, 540}, {1280, 720}};
        for (int[] size : sizes) {
            CompanionUiLayout.Shell shell = CompanionUiLayout.shell(size[0], size[1], false);
            CompanionUiLayout.Rect panel = CompanionUiLayout.focusedPanel(shell.content(), 520, 220);
            assertTrue(panel.x() >= shell.content().x());
            assertTrue(panel.y() >= shell.content().y());
            assertTrue(panel.right() <= shell.content().right());
            assertTrue(panel.bottom() <= shell.content().bottom());
        }
    }

    @Test
    void actionInventoryKeepsEveryLegacySurfaceReachable() {
        assertTrue(CompanionActionInventory.actionsFor("library").contains("library.install"));
        assertTrue(CompanionActionInventory.actionsFor("art").contains("art.install_tiles"));
        assertTrue(CompanionActionInventory.actionsFor("lens").contains("lens.place"));
        assertTrue(CompanionActionInventory.actionsFor("scan").contains("scan.corners"));
        assertTrue(CompanionActionInventory.actionsFor("tracker").contains("tracker.set_count"));
        assertTrue(CompanionActionInventory.actionsFor("account").contains("account.login_start"));
        assertTrue(CompanionActionInventory.actionsFor("two_layer").contains("two_layer.select_part"));
        assertTrue(CompanionActionInventory.actionsFor("library").contains("library.import_two_layer"));
        assertTrue(CompanionActionInventory.actionsFor("art").contains("art.prepare_autoframe"));
        assertTrue(CompanionActionInventory.actionsFor("lens").contains("lens.hide_placement"));
        assertTrue(CompanionActionInventory.actionsFor("scan").contains("scan.history_delete"));
        assertTrue(CompanionActionInventory.actionsFor("tracker").contains("tracker.undo"));
        assertTrue(CompanionActionInventory.actionsFor("collections").contains("collections.open_editor"));
        assertTrue(CompanionActionInventory.actionsFor("update").contains("update.download"));
        assertFalse(CompanionActionInventory.allActions().isEmpty());
    }

    @Test
    void rejectsUnknownProductionActionIds() {
        assertThrows(IllegalArgumentException.class,
            () -> CompanionActionInventory.requireKnown("library.not_a_real_action"));
        UiAction action = CompanionActionInventory.action("library.install", "Install");
        assertEquals("library.install", action.id());
        assertEquals("Install", action.label());
    }

    @Test
    void actionPresentationUsesTheSemanticActionKind() {
        assertEquals(UiTheme.LIME, presentation(UiAction.Kind.DEFAULT).accentColor());
        assertFalse(presentation(UiAction.Kind.DEFAULT).decorated());
        assertEquals(UiTheme.LIME, presentation(UiAction.Kind.PRIMARY).accentColor());
        assertEquals(UiTheme.CYAN, presentation(UiAction.Kind.TECHNICAL).accentColor());
        assertEquals(UiTheme.AMBER, presentation(UiAction.Kind.WARNING).accentColor());
        assertEquals(UiTheme.RED, presentation(UiAction.Kind.DANGER).accentColor());
        assertTrue(presentation(UiAction.Kind.DANGER).decorated());
    }

    @Test
    void activeRenderBridgeUsesTheActionPresentationContract() throws IOException {
        String bridge = Files.readString(Path.of(activeButtonBridge()));
        assertTrue(bridge.contains("UiAction currentAction = action()"));
        assertTrue(bridge.contains("UiActionPresentation.from(currentAction)"));
        assertTrue(bridge.contains("currentAction.enabled()"));
        assertTrue(bridge.contains("currentAction.selected()"));
        assertTrue(bridge.contains("currentAction.label()"));
        assertFalse(bridge.contains("int accent = tone.accentColor()"));
    }

    @Test
    void screenViewModelOwnsHeadingStatusAndShellIdentity() {
        ScreenViewModel model = ScreenViewModel.shell(
            CompanionUiLayout.Destination.ART,
            "Library",
            List.of("My Art"),
            "Saved"
        );
        assertEquals("Library / My Art", model.heading());
        assertEquals(ScreenViewModel.StatusKind.SUCCESS, model.statusKind());
        assertEquals(UiNode.Type.SHELL, model.root().type());
        assertEquals("art", model.root().metadata().get("destination"));
    }

    @Test
    void screenStatusClassificationIsLanguageSafeAndExplicit() {
        assertEquals(ScreenViewModel.StatusKind.IDLE, ScreenViewModel.classifyStatus(""));
        assertEquals(ScreenViewModel.StatusKind.LOADING, ScreenViewModel.classifyStatus("Синхронизация..."));
        assertEquals(ScreenViewModel.StatusKind.SUCCESS, ScreenViewModel.classifyStatus("Saved"));
        assertEquals(ScreenViewModel.StatusKind.WARNING, ScreenViewModel.classifyStatus("Сессия истекла"));
        assertEquals(ScreenViewModel.StatusKind.ERROR, ScreenViewModel.classifyStatus("Failed"));
    }

    @Test
    void uiLabStatesKeepSemanticStatusColors() throws IOException {
        String model = Files.readString(Path.of("src/uiLabShared/java/art/mapkluss/companion/MapKlussUiLabModel.java"));
        assertTrue(model.contains("case ERROR -> ScreenViewModel.StatusKind.ERROR"));
        assertTrue(model.contains("case WARNING -> ScreenViewModel.StatusKind.WARNING"));
        assertTrue(model.contains("case LOADING -> ScreenViewModel.StatusKind.LOADING"));
    }

    @Test
    void libraryAndArtUseWorkshopLayouts() throws IOException {
        String production = readProductionSources(
            activeScreenSource("CompanionLibraryScreen.java"),
            activeScreenSource("CompanionArtScreen.java")
        );
        assertTrue(production.contains("WorkshopLayout.library(width, height)"));
        assertTrue(production.contains("WorkshopArtLayout.at(width,height)"));
        assertEquals(2, count(production, "WorkshopChrome.frame("));
        assertFalse(production.contains("private void legacyRender("));
    }

    @Test
    void twoLayerWorkflowConstructsExplicitProductionModels() throws IOException {
        String production = readProductionSources(
            activeWorkflowSource("SuppressionStartScreen.java"),
            activeWorkflowSource("SuppressionTileSelectScreen.java")
        );
        assertEquals(2, count(production, "private ScreenViewModel screenModel()"));
        assertTrue(production.contains("CompanionUiLayout.Destination.TWO_LAYER"));
        assertTrue(production.contains("ScreenViewModel.StatusKind.LOADING"));
        assertTrue(production.contains("ScreenViewModel.StatusKind.SUCCESS"));
    }

    @Test
    void everyPrimaryProductionScreenUsesSharedLayoutOrScreenModel() throws IOException {
        String[] screens = {
            activeScreenSource("CompanionLibraryScreen.java"),
            activeScreenSource("CompanionArtScreen.java"),
            activeWorkflowSource("CompanionCollectionsScreen.java"),
            activeWorkflowSource("CompanionCollectionItemsScreen.java"),
            activeWorkflowSource("CompanionArtCollectionsScreen.java"),
            activeWorkflowSource("CompanionAccountScreen.java"),
            activeWorkflowSource("DeviceLoginScreen.java"),
            activeWorkflowSource("LensScreen.java"),
            activeWorkflowSource("ScanScreen.java"),
            activeWorkflowSource("TrackerOpenScreen.java"),
            activeWorkflowSource("TrackerSessionScreen.java"),
            activeWorkflowSource("SuppressionStartScreen.java"),
            activeWorkflowSource("SuppressionTileSelectScreen.java")
        };
        for (String screen : screens) {
            String source = Files.readString(Path.of(screen));
            boolean workshopCollection = source.contains("WorkshopCollectionLayout.at(width,height)")
                && source.contains("WorkshopChrome.frame(");
            boolean workshopLens = source.contains("WorkshopLensLayout.at(width, height)") && source.contains("WorkshopChrome.frame(");
            boolean workshopScan = source.contains("WorkshopScanLayout.at(width, height)") && source.contains("WorkshopChrome.frame(");
            boolean workshopTracker = source.contains("WorkshopTrackerLayout.at(width,height)") && source.contains("WorkshopChrome.frame(");
            boolean workshopCore = (source.contains("WorkshopLayout.library(width, height)")
                || source.contains("WorkshopArtLayout.at(width,height)")
                || source.contains("WorkshopLayout.account(width,height)"))
                && source.contains("WorkshopChrome.frame(");
            assertTrue(workshopCore || workshopCollection || workshopLens || workshopScan || workshopTracker || source.contains("ScreenViewModel.shell(") || source.contains("screenModel()"), screen);
        }
    }

    @Test
    void compactLensEmptyStateRespectsItsActionRow() throws IOException {
        String production = readProductionSources(
            "src/minecraft262/java/art/mapkluss/companion/LensScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/LensScreen.java"
        );
        assertTrue(production.contains("WorkshopLensLayout.at(width, height)"));
        assertTrue(production.contains("s.list().width() - 8"));
        assertFalse(production.contains("drawLensEmptyState("));
    }

    @Test
    void accountDetailsReplaceActionRows() throws IOException {
        String production = readProductionSources(
            "src/minecraft262/java/art/mapkluss/companion/CompanionAccountScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/CompanionAccountScreen.java"
        );
        assertEquals(2, count(production, "if (detailsVisible) return;"));
        assertEquals(2, count(production, "int y=shell.preview().y()+8;"));
        assertFalse(production.contains("private void legacyRender("));
        assertFalse(production.contains("content.y() + Math.min(74"));
    }

    private static UiActionPresentation presentation(UiAction.Kind kind) {
        return UiActionPresentation.from(new UiAction("test.action", "Test", kind, true, false, ""));
    }

    private static String activeButtonBridge() {
        String minecraftVersion = System.getProperty("mapkluss.test.minecraftVersion", "1.21.11");
        return switch (minecraftVersion) {
            case "26.2" -> "src/compat262/java/art/mapkluss/companion/MapKlussButton.java";
            case "1.21.4" -> "src/compat1214/java/art/mapkluss/companion/MapKlussButton.java";
            case "1.21.8" -> "src/compat1218/java/art/mapkluss/companion/MapKlussButton.java";
            default -> "src/compat12111/java/art/mapkluss/companion/MapKlussButton.java";
        };
    }

    private static String activeScreenSource(String filename) {
        String minecraftVersion = System.getProperty("mapkluss.test.minecraftVersion", "1.21.11");
        return switch (minecraftVersion) {
            case "26.2" -> "src/minecraft262/java/art/mapkluss/companion/" + filename;
            case "1.21.4" -> "src/minecraft1214/java/art/mapkluss/companion/" + filename;
            default -> "src/minecraft1218plus/java/art/mapkluss/companion/" + filename;
        };
    }

    private static String activeWorkflowSource(String filename) {
        String minecraftVersion = System.getProperty("mapkluss.test.minecraftVersion", "1.21.11");
        String root = minecraftVersion.equals("26.2") ? "src/minecraft262/java/" : "src/minecraftLegacy/java/";
        return root + "art/mapkluss/companion/" + filename;
    }

    private static int count(String value, String needle) {
        int total = 0;
        int from = 0;
        while ((from = value.indexOf(needle, from)) >= 0) {
            total++;
            from += needle.length();
        }
        return total;
    }

    @Test
    void productionLibraryAndTwoLayerDeclareTheirCriticalActions() throws IOException {
        String production = readProductionSources(
            "src/minecraft262/java/art/mapkluss/companion/CompanionLibraryScreen.java",
            "src/minecraft1218plus/java/art/mapkluss/companion/CompanionLibraryScreen.java",
            "src/minecraft1214/java/art/mapkluss/companion/CompanionLibraryScreen.java",
            "src/minecraft262/java/art/mapkluss/companion/SuppressionStartScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/SuppressionStartScreen.java",
            "src/minecraft262/java/art/mapkluss/companion/SuppressionTileSelectScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/SuppressionTileSelectScreen.java",
            "src/minecraft262/java/art/mapkluss/companion/CompanionAccountScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/CompanionAccountScreen.java",
            "src/minecraft262/java/art/mapkluss/companion/DeviceLoginScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/DeviceLoginScreen.java",
            "src/minecraft262/java/art/mapkluss/companion/CompanionUpdateScreen.java",
            "src/minecraftLegacy/java/art/mapkluss/companion/CompanionUpdateScreen.java"
        );
        Set<String> required = Set.of(
            "library.my_arts", "library.favorites", "library.recent", "library.collections",
            "library.search", "library.search_clear", "library.page_next", "library.refresh",
            "library.select_art", "library.install", "library.toggle_favorite", "library.open_tracker",
            "library.open_site", "library.open_editor", "library.sync", "library.import_two_layer",
            "two_layer.start_cloud", "two_layer.import_zip", "two_layer.resume", "two_layer.stop",
            "two_layer.select_part", "two_layer.tile_previous_page", "two_layer.tile_next_page",
            "account.login_start", "account.login_auto_poll", "account.login_poll", "account.copy_code",
            "account.open_site", "account.sync", "account.update", "account.logout", "account.details",
            "update.telegram", "update.download", "update.dismiss"
        );
        for (String action : required) {
            boolean direct = production.contains(".action(\"" + action + "\")")
                || production.contains("layerButton(\"" + action + "\"")
                || production.contains("workshopButton(\"" + action + "\"")
                || production.contains("accountButton(\"" + action + "\"")
                || production.contains("loginButton(\"" + action + "\"")
                || libraryArrayActions(production).contains(action);
            boolean dynamicAccountAction = (action.equals("account.login_start") || action.equals("account.logout"))
                && production.contains("accountButton(isSignedIn() ? \"account.logout\" : \"account.login_start\"");
            assertTrue(direct || dynamicAccountAction, action);
        }
    }

    @Test
    void everyDeclaredActionHasAProductionRegistration() throws IOException {
        String production = readAllProductionSources();
        Set<String> reachable = new HashSet<>();
        reachable.addAll(libraryArrayActions(production));
        Matcher accountButtons = Pattern.compile("(?:accountButton|loginButton)\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (accountButtons.find()) reachable.add(accountButtons.group(1));
        Matcher direct = Pattern.compile("\\.action\\(\\\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\\\"").matcher(production);
        while (direct.find()) reachable.add(direct.group(1));
        Matcher workshop = Pattern.compile("workshopButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (workshop.find()) reachable.add(workshop.group(1));
        Matcher artWorkshop = Pattern.compile("artWorkshopButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (artWorkshop.find()) reachable.add(artWorkshop.group(1));
        Matcher collections = Pattern.compile("collectionButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (collections.find()) reachable.add(collections.group(1));
        Matcher lens = Pattern.compile("lensButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (lens.find()) reachable.add(lens.group(1));
        Matcher layers = Pattern.compile("layerButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (layers.find()) reachable.add(layers.group(1));
        Matcher scan = Pattern.compile("scanButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (scan.find()) reachable.add(scan.group(1));
        Matcher tracker = Pattern.compile("trackerButton\\(\"([a-z][a-z0-9_]*(?:\\.[a-z0-9_]+)+)\"").matcher(production);
        while (tracker.find()) reachable.add(tracker.group(1));
        Matcher build = Pattern.compile("button\\(\"(tracker\\.(?:build|group)\\.[a-z_]+)\"").matcher(production);
        while (build.find()) reachable.add(build.group(1));
        Matcher workshopRows = Pattern.compile("String\\[\\] ids = secondaryActions([\\s\\S]*?);").matcher(production);
        while (workshopRows.find()) {
            Matcher id = Pattern.compile("\"(library\\.[a-z_]+|global\\.[a-z_]+)\"").matcher(workshopRows.group(1));
            while (id.find()) reachable.add(id.group(1));
        }

        // These commands use runtime state or a destination enum instead of a fixed literal.
        reachable.addAll(Set.of(
            "account.login_start", "account.logout",
            "collections.add_art", "collections.remove_art",
            "lens.visibility_private", "lens.visibility_group",
            "nav.library", "nav.lens", "nav.scan", "nav.tracker", "nav.account",
            "art.tab_files", "art.tab_cloud", "art.tab_build", "art.tab_more",
            "scan.tab_result", "scan.tab_history", "scan.tab_open"
        ));

        assertEquals(Set.of(), CompanionActionInventory.allActions().stream()
            .filter(action -> !reachable.contains(action))
            .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(), reachable.stream()
            .filter(action -> !CompanionActionInventory.contains(action))
            .collect(java.util.stream.Collectors.toSet()));
    }

    private static Set<String> libraryArrayActions(String source) {
        Set<String> actions = new HashSet<>();
        Matcher arrays = Pattern.compile("String\\[\\] ids = (?:secondaryActions[\\s\\S]*?|\\{[^;]*?\\});").matcher(source);
        while (arrays.find()) {
            Matcher ids = Pattern.compile("\"(library\\.[a-z_]+|global\\.[a-z_]+)\"").matcher(arrays.group());
            while (ids.find()) actions.add(ids.group(1));
        }
        return actions;
    }

    private static String readProductionSources(String... paths) throws IOException {
        StringBuilder result = new StringBuilder();
        for (String path : paths) result.append(Files.readString(Path.of(path)));
        return result.toString();
    }

    private static String readAllProductionSources() throws IOException {
        StringBuilder result = new StringBuilder();
        for (String sourceRoot : activeProductionSourceRoots()) {
            Path root = Path.of(sourceRoot);
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    result.append(Files.readString(path));
                }
            }
        }
        return result.toString();
    }

    private static Set<String> activeProductionSourceRoots() {
        String minecraftVersion = System.getProperty("mapkluss.test.minecraftVersion", "1.21.11");
        return switch (minecraftVersion) {
            case "26.2" -> Set.of("src/main/java", "src/minecraft262/java", "src/compat262/java");
            case "1.21.4" -> Set.of("src/main/java", "src/minecraftLegacy/java", "src/minecraft1214/java", "src/compat1214/java");
            case "1.21.8" -> Set.of("src/main/java", "src/minecraftLegacy/java", "src/minecraft1218plus/java", "src/compat1218/java");
            default -> Set.of("src/main/java", "src/minecraftLegacy/java", "src/minecraft1218plus/java", "src/compat12111/java");
        };
    }

    @Test
    void compactLensKeepsRoomForListAndContextActions() {
        CompanionUiLayout.Shell shell = CompanionUiLayout.shell(320, 240, false);
        CompanionUiLayout.Rect work = new CompanionUiLayout.Rect(
            shell.content().x() + 14,
            shell.content().y() + 12,
            shell.content().width() - 28,
            shell.content().height() - 24
        );
        int joinY = work.y();
        int tabsY = joinY + 30;
        int listY = tabsY + 30;
        int actionsY = work.bottom() - 22;
        assertTrue(actionsY > listY);
        assertTrue(actionsY + 22 <= work.bottom());
    }

    @Test
    void iconAtlasLoadsEveryOriginalGlyph() {
        for (MapKlussIcon icon : MapKlussIcon.values()) {
            assertFalse(icon.name().isBlank());
            icon.pixel(0, 0);
            icon.pixel(15, 15);
        }
    }
}
