package art.mapkluss.companion;

public record TrackerHistoryEntry(
    String sessionId,
    String title,
    String artId,
    String mode,
    String openedAt
) {
}
