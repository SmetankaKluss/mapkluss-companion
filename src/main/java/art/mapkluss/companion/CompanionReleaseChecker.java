package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CompanionReleaseChecker {
    static final URI LATEST_RELEASE_URI =
        URI.create("https://api.github.com/repos/SmetankaKluss/mapkluss-companion/releases/latest");
    static final int MAX_RESPONSE_BYTES = 64 * 1024;

    private final HttpClient http;

    public CompanionReleaseChecker() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    CompanionReleaseChecker(HttpClient http) {
        this.http = http;
    }

    public CompletableFuture<Optional<Release>> findUpdate(String currentVersion) {
        Optional<CompanionVersion> current = CompanionVersion.parse(currentVersion);
        if (current.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());

        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_URI)
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MapKluss-Companion/" + current.get())
            .GET()
            .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(response -> {
                try (InputStream body = response.body()) {
                    byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                    return parseLatestRelease(response.statusCode(), bytes, current.get());
                } catch (Exception ignored) {
                    return Optional.<Release>empty();
                }
            })
            .exceptionally(error -> Optional.<Release>empty());
    }

    static Optional<Release> parseLatestRelease(int statusCode, byte[] body, CompanionVersion current) {
        if (statusCode != 200 || body == null || body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
            return Optional.empty();
        }
        try {
            JsonObject json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!json.has("tag_name") || !json.get("tag_name").isJsonPrimitive()) return Optional.empty();
            Optional<CompanionVersion> latest = CompanionVersion.parse(json.get("tag_name").getAsString());
            if (latest.isEmpty() || latest.get().compareTo(current) <= 0) return Optional.empty();
            return Optional.of(new Release(latest.get().toString()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public record Release(String version) {
    }
}
