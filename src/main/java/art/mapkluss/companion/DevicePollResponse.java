package art.mapkluss.companion;

public record DevicePollResponse(
    String status,
    String accessToken,
    String refreshToken,
    String userId
) {
}
