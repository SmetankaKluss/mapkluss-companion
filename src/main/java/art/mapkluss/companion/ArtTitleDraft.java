package art.mapkluss.companion;

/** Keeps a local rename across view rebuilds and unrelated manifest updates. */
final class ArtTitleDraft {
    private String server;
    private String value;
    ArtTitleDraft(String title) { server = value = title == null ? "" : title; }
    String value() { return value; }
    void edit(String title) { value = title == null ? "" : title; }
    void receive(String title) {
        String next = title == null ? "" : title;
        if (value.equals(server)) value = next;
        server = next;
    }
    void saved(String submitted, String confirmed) {
        if (value.equals(submitted)) value = confirmed;
        server = confirmed;
    }
}
