package art.mapkluss.companion;

enum WorkshopIcon {
    LIBRARY, LENS, SCAN, TRACKER, ACCOUNT, SEARCH, REFRESH, INSTALL, DOWNLOAD,
    EDIT, LINK, BACK, MORE, DELETE, CLOSE, FAVORITE, LAYERS, CHECK, FOLDER, PAUSE;
    static final String TEXTURE = "textures/gui/workshop/icons.png";
    static final int SIZE = 16;
    int u() { return ordinal() * SIZE; }
    static int atlasWidth() { return values().length * SIZE; }
}
