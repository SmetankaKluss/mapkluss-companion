package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public record CompanionSessionInfo(
    String userId,
    String savedAt,
    String expiresAt
) {
    private static final Gson GSON = new Gson();

    public static CompanionSessionInfo from(String accessToken, String userId, String savedAt) {
        return new CompanionSessionInfo(userId, savedAt, parseJwtExpiry(accessToken));
    }

    public boolean isSignedIn() {
        return userId != null && !userId.isBlank();
    }

    public boolean isExpired() {
        if (expiresAt == null || expiresAt.isBlank()) return false;
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (Exception ignored) {
            return false;
        }
    }

    public String shortUserId() {
        if (userId == null || userId.isBlank()) return "anonymous";
        return userId.length() <= 12 ? userId : userId.substring(0, 8) + "...";
    }

    private static String parseJwtExpiry(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return null;
        String[] parts = accessToken.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            JsonObject payload = GSON.fromJson(new String(decoded, StandardCharsets.UTF_8), JsonObject.class);
            if (payload == null || !payload.has("exp") || payload.get("exp").isJsonNull()) return null;
            long exp = payload.get("exp").getAsLong();
            return Instant.ofEpochSecond(exp).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String padBase64(String input) {
        int remainder = input.length() % 4;
        if (remainder == 0) return input;
        return input + "=".repeat(4 - remainder);
    }
}
