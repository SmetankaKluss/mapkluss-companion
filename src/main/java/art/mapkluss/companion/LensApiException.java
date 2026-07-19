package art.mapkluss.companion;

import java.io.IOException;

public final class LensApiException extends IOException {
    private final int statusCode;
    private final String errorCode;
    private final Integer retryAfterMs;

    LensApiException(int statusCode, String errorCode, String message, Integer retryAfterMs) {
        super(message == null || message.isBlank() ? errorCode : message);
        this.statusCode = statusCode;
        this.errorCode = errorCode == null ? "invalid_request" : errorCode;
        this.retryAfterMs = retryAfterMs;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public Integer retryAfterMs() {
        return retryAfterMs;
    }
}
