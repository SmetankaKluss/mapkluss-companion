package art.mapkluss.companion;

/** Keeps fixture-only navigation separate from real Cloud mutations. */
final class LibraryActionPolicy {
    private LibraryActionPolicy() { }

    static boolean permits(boolean fixture, boolean local, String actionId) {
        if (!fixture || local) return true;
        return actionId.startsWith("nav.")
            || actionId.equals("library.select_art")
            || actionId.equals("library.open_lens")
            || actionId.equals("library.open_two_layer");
    }
}
