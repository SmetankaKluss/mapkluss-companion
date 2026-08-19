package art.mapkluss.companion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class CompanionActionInventory {
    private static final Map<String, Set<String>> ACTIONS = build();

    private CompanionActionInventory() {
    }

    static Set<String> screens() {
        return ACTIONS.keySet();
    }

    static Set<String> actionsFor(String screen) {
        return ACTIONS.getOrDefault(screen, Set.of());
    }

    static Set<String> allActions() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ACTIONS.values().forEach(result::addAll);
        return Collections.unmodifiableSet(result);
    }

    static boolean contains(String actionId) {
        return actionId != null && allActions().contains(actionId);
    }

    static String requireKnown(String actionId) {
        if (!contains(actionId)) {
            throw new IllegalArgumentException("Unknown Companion action: " + actionId);
        }
        return actionId;
    }

    static UiAction action(String actionId, String label) {
        return new UiAction(requireKnown(actionId), label, UiAction.Kind.DEFAULT, true, false, "");
    }

    static String navigationAction(CompanionUiLayout.Destination destination) {
        return switch (destination) {
            case LIBRARY -> "nav.library";
            case LENS -> "nav.lens";
            case SCAN -> "nav.scan";
            case TRACKER -> "nav.tracker";
            case ACCOUNT -> "nav.account";
            case ART, TWO_LAYER -> throw new IllegalArgumentException("No top-level navigation action for " + destination);
        };
    }

    static String artTabAction(int index) {
        return switch (index) {
            case 0 -> "art.tab_files";
            case 1 -> "art.tab_cloud";
            case 2 -> "art.tab_build";
            case 3 -> "art.tab_more";
            default -> throw new IllegalArgumentException("Unknown art tab: " + index);
        };
    }

    static String scanTabAction(int index) {
        return switch (index) {
            case 0 -> "scan.tab_result";
            case 1 -> "scan.tab_history";
            case 2 -> "scan.tab_open";
            default -> throw new IllegalArgumentException("Unknown scan tab: " + index);
        };
    }

    private static Map<String, Set<String>> build() {
        LinkedHashMap<String, Set<String>> result = new LinkedHashMap<>();
        result.put("library", ids(
            "library.my_arts", "library.favorites", "library.recent", "library.collections",
            "library.search", "library.search_clear", "library.refresh", "library.sync",
            "library.page_next",
            "library.select_art", "library.toggle_favorite", "library.install", "library.open_tracker",
            "library.open_site", "library.open_editor", "nav.library", "nav.lens", "nav.scan", "nav.tracker",
            "nav.account", "library.import_two_layer", "library.toggle_actions", "global.back", "global.language"
        ));
        result.put("art", ids(
            "art.install", "art.install_tiles", "art.remove_install", "art.refresh",
            "art.privacy", "art.save", "art.delete", "art.favorite", "art.collections",
            "art.open_page", "art.open_editor", "art.track", "art.download_png",
            "art.download_materials", "art.download_commands", "art.download_datapack",
            "art.import_mapdat", "art.download_mapdat", "art.download_project", "art.files_folder",
            "art.schematics_folder", "art.two_layer", "global.back", "global.language"
            , "art.prepare_autoframe", "art.tab_files", "art.tab_cloud", "art.tab_build", "art.tab_more"
        ));
        result.put("lens", ids(
            "lens.join", "lens.leave", "lens.refresh", "lens.place",
            "lens.remove_placement", "lens.visibility_private", "lens.visibility_group",
            "lens.hide_author", "lens.report", "global.back", "global.language"
            , "lens.select_session", "lens.select_placement", "lens.next_session_page",
            "lens.next_placement_page", "lens.hide_placement"
        ));
        result.put("scan", ids(
            "scan.hand", "scan.frame", "scan.wall", "scan.corners", "scan.save_png",
            "scan.upload_cloud", "global.back", "global.language"
            , "scan.title_apply", "scan.title_reset", "scan.corner_a", "scan.corner_b",
            "scan.import_refresh", "scan.history_previous", "scan.history_next", "scan.history_load",
            "scan.history_delete", "scan.open_folder", "scan.open_art", "scan.open_editor", "scan.open_cloud",
            "scan.tab_result", "scan.tab_history", "scan.tab_open"
        ));
        result.put("tracker", ids(
            "tracker.open_session", "tracker.refresh", "tracker.set_count",
            "tracker.add_one", "tracker.clear_count", "tracker.complete_all",
            "tracker.status_gathering", "tracker.status_building",
            "global.back", "global.language"
            , "tracker.history_page", "tracker.open_history_art", "tracker.retry",
            "tracker.change_session", "tracker.search", "tracker.search_clear", "tracker.hide_completed",
            "tracker.open_site", "tracker.open_art", "tracker.step_cycle", "tracker.undo", "tracker.decrement"
        ));
        result.put("account", ids(
            "account.login_start", "account.login_poll", "account.copy_code", "account.open_site",
            "account.sync", "account.update", "account.logout", "account.details",
            "account.telemetry", "global.back", "global.language"
            , "account.login_auto_poll"
        ));
        result.put("telemetry", ids("telemetry.enable", "telemetry.disable"));
        result.put("collections", ids(
            "collections.create", "collections.rename", "collections.delete", "collections.open",
            "collections.add_art", "collections.remove_art", "collections.refresh", "global.back",
            "global.language", "collections.search", "collections.search_clear", "collections.page_next",
            "collections.open_site", "collections.open_art",
            "collections.open_tracker", "collections.open_editor"
        ));
        result.put("two_layer", ids(
            "two_layer.select_part", "two_layer.resume", "global.back", "global.language"
            , "two_layer.start_cloud", "two_layer.import_zip", "two_layer.stop",
            "two_layer.tile_previous_page", "two_layer.tile_next_page"
        ));
        result.put("update", ids("update.telegram", "update.download", "update.dismiss"));
        result.put("inventory", ids("inventory.recognize_maps"));
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> ids(String... values) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Collections.addAll(ids, values);
        return Collections.unmodifiableSet(ids);
    }
}
