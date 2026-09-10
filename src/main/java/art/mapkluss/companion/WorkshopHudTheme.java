package art.mapkluss.companion;

/** Rendering consumes an in-memory theme; never reads configuration per frame. */
final class WorkshopHudTheme {
    private static volatile WorkshopTheme current = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    static WorkshopTheme current() { return current; }
    static void select(String id) { current = WorkshopTheme.of(id); }
}
