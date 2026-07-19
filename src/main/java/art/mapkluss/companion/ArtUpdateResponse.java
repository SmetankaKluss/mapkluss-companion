package art.mapkluss.companion;

public record ArtUpdateResponse(
    boolean ok,
    String title,
    String privacy,
    String updatedAt
) {
}
