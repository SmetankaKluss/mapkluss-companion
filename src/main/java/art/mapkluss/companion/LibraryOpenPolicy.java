package art.mapkluss.companion;

/** Separates screen reflow from entering the library after navigation. */
final class LibraryOpenPolicy {
    enum Load { NONE, REFRESH, SYNC }

    private boolean attached;
    private boolean firstOpen = true;

    Load enter(boolean syncOnOpen) {
        if (attached) return Load.NONE;
        attached = true;
        boolean sync = firstOpen && syncOnOpen;
        firstOpen = false;
        return sync ? Load.SYNC : Load.REFRESH;
    }

    void leave() {
        attached = false;
    }
}
