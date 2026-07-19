package art.mapkluss.companion;

public record DeviceStartResponse(
    String deviceCode,
    String userCode,
    String verificationUri,
    int expiresIn,
    int interval
) {
}
