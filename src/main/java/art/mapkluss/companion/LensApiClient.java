package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;

public final class LensApiClient {
    private static final int MAX_API_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final URI endpoint;
    private final String anonKey;
    private final String bearerToken;
    private final CompanionConfig routingConfig;

    public LensApiClient(String supabaseUrl, String anonKey, String bearerToken) {
        this(supabaseUrl, anonKey, bearerToken, null);
    }

    public LensApiClient(CompanionConfig config, String bearerToken) {
        this(config.supabaseUrl(), config.supabaseAnonKey(), bearerToken, config);
    }

    private LensApiClient(String supabaseUrl, String anonKey, String bearerToken, CompanionConfig routingConfig) {
        this.endpoint = URI.create(stripTrailingSlash(supabaseUrl) + "/functions/v1/companion-lens");
        this.anonKey = anonKey;
        this.bearerToken = bearerToken;
        this.routingConfig = routingConfig;
    }

    public LensDtos.Capabilities capabilities() throws IOException, InterruptedException {
        return post(action("capabilities"), LensDtos.Capabilities.class);
    }
    private record Started(LensDtos.Session session,String publisherLease) { }
    public LensDtos.Session start(CompanionManifest manifest,byte[] preview)throws IOException,InterruptedException {
        var body=action("session_start");
        body.addProperty("title",manifest.title());body.add("grid",GSON.toJsonTree(manifest.grid()));
        body.addProperty("mapMode",manifest.mode());
        var started=post(body,Started.class);
        var publish=action("session_seed");
        publish.addProperty("sessionId",started.session().sessionId());
        publish.addProperty("baseRevision",started.session().revision());
        publish.addProperty("publisherLease",started.publisherLease());
        publish.addProperty("title",manifest.title());publish.add("grid",GSON.toJsonTree(manifest.grid()));
        publish.addProperty("mapMode",manifest.mode());publish.addProperty("tileResolution",128);
        publish.addProperty("sha256",SuppressionHashes.sha256(preview));
        publish.addProperty("previewBase64",java.util.Base64.getEncoder().encodeToString(preview));
        var s=post(publish,LensDtos.SessionResult.class).session();
        return new LensDtos.Session(s.sessionId(),s.title(),s.status(),s.grid(),s.mapMode(),s.revision(),
            s.tileResolution(),s.previewWidth(),s.previewHeight(),s.viewerCount(),s.editorLastSeenAt(),s.expiresAt(),
            started.session().sessionCode(),s.ownedByUser(),s.realtime());
    }

    public LensDtos.SessionList sessionList() throws IOException, InterruptedException {
        return post(action("session_list"), LensDtos.SessionList.class);
    }

    public LensDtos.Session join(String sessionCode) throws IOException, InterruptedException {
        JsonObject body = action("session_join");
        body.addProperty("sessionCode", sessionCode);
        return post(body, LensDtos.SessionResult.class).session();
    }

    public void leave(String sessionId) throws IOException, InterruptedException {
        JsonObject body = action("session_leave");
        body.addProperty("sessionId", sessionId);
        post(body, LensDtos.ActionResult.class);
    }

    public LensDtos.PollResult poll(
        String sessionId,
        long knownRevision,
        boolean needsPreview,
        String serverHash,
        String dimensionId
    ) throws IOException, InterruptedException {
        JsonObject body = action("session_poll");
        body.addProperty("sessionId", sessionId);
        body.addProperty("knownRevision", knownRevision);
        body.addProperty("needsPreview", needsPreview);
        addOptional(body, "serverHash", serverHash);
        addOptional(body, "dimensionId", dimensionId);
        return post(body, LensDtos.PollResult.class);
    }

    public LensDtos.Placement upsertPlacement(
        String placementId,
        String sessionId,
        String visibility,
        String serverHash,
        String dimensionId,
        LensDtos.Anchor anchor,
        String facing
    ) throws IOException, InterruptedException {
        JsonObject body = action("placement_upsert");
        addOptional(body, "placementId", placementId);
        body.addProperty("sessionId", sessionId);
        body.addProperty("visibility", visibility);
        addOptional(body, "serverHash", serverHash);
        addOptional(body, "dimensionId", dimensionId);
        body.add("anchor", GSON.toJsonTree(anchor));
        body.addProperty("facing", facing);
        return post(body, LensDtos.PlacementResult.class).placement();
    }

    public void deletePlacement(String placementId) throws IOException, InterruptedException {
        JsonObject body = action("placement_delete");
        body.addProperty("placementId", placementId);
        post(body, LensDtos.ActionResult.class);
    }

    public void heartbeat(Collection<String> sessionIds, Collection<String> placementIds)
        throws IOException, InterruptedException {
        if ((sessionIds == null || sessionIds.isEmpty()) && (placementIds == null || placementIds.isEmpty())) return;
        JsonObject body = action("presence_heartbeat");
        body.add("sessionIds", GSON.toJsonTree(sessionIds == null ? java.util.List.of() : sessionIds));
        body.add("placementIds", GSON.toJsonTree(placementIds == null ? java.util.List.of() : placementIds));
        post(body, LensDtos.ActionResult.class);
    }

    public void reportPlacement(String placementId, String reason) throws IOException, InterruptedException {
        JsonObject body = action("placement_report");
        body.addProperty("placementId", placementId);
        body.addProperty("reason", reason);
        post(body, LensDtos.ActionResult.class);
    }

    private <T> T post(JsonObject payload, Type type) throws IOException, InterruptedException {
        URI selectedEndpoint = routedEndpoint();
        ApiResponse response;
        try {
            response = sendPost(selectedEndpoint, payload);
        } catch (IOException error) {
            if (!canFallback(selectedEndpoint)) throw error;
            CompanionBackendRouter.reportFailure(routingConfig, selectedEndpoint);
            if (!safeToRetry(payload)) throw error;
            response = sendPost(CompanionBackendRouter.directEndpoint(routingConfig, endpoint.getPath()), payload);
        }
        if (retryableGatewayStatus(selectedEndpoint, response.status()) && safeToRetry(payload)) {
            CompanionBackendRouter.reportFailure(routingConfig, selectedEndpoint);
            response = sendPost(CompanionBackendRouter.directEndpoint(routingConfig, endpoint.getPath()), payload);
        } else if (retryableGatewayStatus(selectedEndpoint, response.status())) {
            CompanionBackendRouter.reportFailure(routingConfig, selectedEndpoint);
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw parseError(response.status(), response.body());
        }
        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("apiVersion") || json.get("apiVersion").getAsInt() != LensDtos.API_VERSION) {
            throw new IOException("Unsupported Lens API response version");
        }
        T result = GSON.fromJson(json, type);
        if (result == null) throw new IOException("Lens API returned an empty response");
        return result;
    }

    private ApiResponse sendPost(URI target, JsonObject payload) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = HTTP.send(request(target, payload), HttpResponse.BodyHandlers.ofInputStream());
        byte[] responseBytes;
        try (InputStream body = response.body()) {
            responseBytes = CompanionApiClient.readBounded(
                body,
                response.statusCode() >= 200 && response.statusCode() < 300 ? MAX_API_RESPONSE_BYTES : 64 * 1024
            );
        }
        String responseBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
        return new ApiResponse(response.statusCode(), responseBody);
    }

    HttpRequest request(JsonObject payload) {
        return request(routedEndpoint(), payload);
    }

    private HttpRequest request(URI target, JsonObject payload) {
        return HttpRequest.newBuilder(target)
            .timeout(REQUEST_TIMEOUT)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer " + bearerToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
            .build();
    }

    private URI routedEndpoint() {
        if (routingConfig == null) return endpoint;
        return URI.create(CompanionBackendRouter.select(routingConfig) + endpoint.getPath());
    }

    private boolean canFallback(URI attemptedUri) {
        return routingConfig != null
            && !CompanionBackendRouter.directEndpoint(routingConfig, endpoint.getPath()).equals(attemptedUri);
    }

    private boolean retryableGatewayStatus(URI attemptedUri, int status) {
        return canFallback(attemptedUri) && (status == 502 || status == 503 || status == 504);
    }

    private static boolean safeToRetry(JsonObject payload) {
        String action = payload != null && payload.has("action") ? payload.get("action").getAsString() : "";
        return switch (action) {
            case "capabilities", "session_list", "session_poll", "presence_heartbeat" -> true;
            default -> false;
        };
    }

    private record ApiResponse(int status, String body) {
    }

    private <T> T post(JsonObject payload, Class<T> type) throws IOException, InterruptedException {
        return post(payload, (Type) type);
    }

    private static LensApiException parseError(int status, String body) {
        try {
            ErrorBody error = GSON.fromJson(body, ErrorBody.class);
            if (error != null) return new LensApiException(status, error.error(), error.message(), error.retryAfterMs());
        } catch (Exception ignored) {
        }
        return new LensApiException(status, "invalid_request", "Lens API failed with HTTP " + status, null);
    }

    private static JsonObject action(String action) {
        JsonObject body = new JsonObject();
        body.addProperty("action", action);
        body.addProperty("principalKind", "device");
        return body;
    }

    private static void addOptional(JsonObject body, String key, String value) {
        if (value != null && !value.isBlank()) body.addProperty(key, value);
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) result = result.substring(0, result.length() - 1);
        return result;
    }

    private record ErrorBody(int apiVersion, String error, String message, Integer retryAfterMs) {
    }
}
