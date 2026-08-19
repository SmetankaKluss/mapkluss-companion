package art.mapkluss.companion;

public enum CompanionTelemetryEvent {
    LAUNCH("launch"),
    LOGIN_COMPLETED("login_completed"),
    LIBRARY_OPENED("library_opened"),
    SCHEMATIC_INSTALLED("schematic_installed"),
    LENS_STARTED("lens_started"),
    TRACKER_CREATED("tracker_created");

    private final String wireName;

    CompanionTelemetryEvent(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
