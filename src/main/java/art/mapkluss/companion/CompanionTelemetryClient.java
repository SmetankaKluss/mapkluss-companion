package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CompanionTelemetryClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final CompanionConfig config;
    private final CompanionTelemetrySettings settings;
    private final String modVersion;
    private final String minecraftVersion;

    public CompanionTelemetryClient(
        CompanionConfig config,
        CompanionTelemetrySettings settings,
        String modVersion,
        String minecraftVersion
    ) {
        this.config = config;
        this.settings = settings;
        this.modVersion = safeVersion(modVersion);
        this.minecraftVersion = safeVersion(minecraftVersion);
    }

    public CompletableFuture<Void> record(CompanionTelemetryEvent event) {
        if (!settings.enabled()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            String installationId = settings.installationId();
            if (installationId == null) return;
            try {
                JsonObject body = new JsonObject();
                body.addProperty("installation_id", installationId);
                body.addProperty("event", event.wireName());
                body.addProperty("mod_version", modVersion);
                body.addProperty("minecraft_version", minecraftVersion);
                body.addProperty("language", config.language());
                body.addProperty("os_family", osFamily(System.getProperty("os.name", "")));

                URI endpoint = URI.create(CompanionBackendRouter.select(config) + "/functions/v1/companion-telemetry");
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", config.supabaseAnonKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
                HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {
                // Telemetry must never affect gameplay or expose error details.
            }
        });
    }

    static String osFamily(String osName) {
        String value = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (value.contains("win")) return "windows";
        if (value.contains("mac") || value.contains("darwin")) return "macos";
        if (value.contains("linux")) return "linux";
        return "other";
    }

    private static String safeVersion(String value) {
        String normalized = value == null ? "unknown" : value.replaceAll("[^0-9A-Za-z.+_-]", "");
        return normalized.isBlank() ? "unknown" : normalized.substring(0, Math.min(32, normalized.length()));
    }
}
