package art.mapkluss.companion;

import java.io.IOException;

public final class CompanionAuthSupport {
    private CompanionAuthSupport() {
    }

    public static boolean isAuthFailure(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null) return false;
        return message.contains("HTTP 401") || message.contains("HTTP 403");
    }

    public static void clearSessionQuietly(CompanionRuntime runtime) {
        if (runtime == null) return;
        try {
            runtime.clearSession();
        } catch (IOException ignored) {
            // Best effort only; UI should still fall back to signed-out messaging.
        }
    }

    public static String expiredMessage() {
        return "Сессия истекла. Войдите заново.";
    }
}
