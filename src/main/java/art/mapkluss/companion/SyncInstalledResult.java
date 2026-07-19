package art.mapkluss.companion;

public record SyncInstalledResult(
    int checked,
    int refreshed,
    int removedMissing,
    int failed
) {
}
