package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompanionApiClient {
    private static final int MAX_API_RESPONSE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ARTIFACT_BYTES = 128 * 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private static final Gson GSON = new Gson();
    private static final Type LIBRARY_LIST = new TypeToken<ItemListResponse<CompanionLibraryItem>>() { }.getType();
    private static final Type COLLECTION_LIST = new TypeToken<ItemListResponse<CompanionCollection>>() { }.getType();

    private final HttpClient http;
    private final URI deviceFunctionsBase;
    private final URI modFunctionsBase;
    private final String anonKey;
    private final CompanionConfig routingConfig;
    private String bearerToken;

    public CompanionApiClient(String supabaseUrl, String anonKey) {
        this(supabaseUrl, anonKey, null);
    }

    public CompanionApiClient(CompanionConfig config) {
        this(config.supabaseUrl(), config.supabaseAnonKey(), config);
    }

    private CompanionApiClient(String supabaseUrl, String anonKey, CompanionConfig routingConfig) {
        this.http = HTTP;
        this.deviceFunctionsBase = URI.create(stripTrailingSlash(supabaseUrl) + "/functions/v1/companion-device");
        this.modFunctionsBase = URI.create(stripTrailingSlash(supabaseUrl) + "/functions/v1/companion-mod");
        this.anonKey = anonKey;
        this.routingConfig = routingConfig;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    public DeviceStartResponse startDeviceLogin() throws IOException, InterruptedException {
        return post(deviceFunctionsBase, newAction("device_start"), DeviceStartResponse.class, false);
    }

    public DevicePollResponse pollDeviceLogin(String deviceCode) throws IOException, InterruptedException {
        JsonObject payload = newAction("device_poll");
        payload.addProperty("device_code", deviceCode);
        return post(deviceFunctionsBase, payload, DevicePollResponse.class, false);
    }

    public void logoutDeviceSession() throws IOException, InterruptedException {
        post(modFunctionsBase, newAction("device_logout"), JsonObject.class, true);
    }

    public CompanionManifest manifest(String artId) throws IOException, InterruptedException {
        return manifest(artId, null);
    }

    public CompanionManifest manifest(String artId, String versionId) throws IOException, InterruptedException {
        JsonObject payload = newAction("manifest");
        payload.addProperty("art_id", artId);
        if (versionId != null && !versionId.isBlank()) payload.addProperty("version_id", versionId);
        return post(modFunctionsBase, payload, CompanionManifest.class, true);
    }

    public ItemListResponse<CompanionLibraryItem> library() throws IOException, InterruptedException {
        return post(modFunctionsBase, newAction("library"), LIBRARY_LIST, true);
    }

    public ItemListResponse<CompanionLibraryItem> favorites() throws IOException, InterruptedException {
        return post(modFunctionsBase, newAction("favorites"), LIBRARY_LIST, true);
    }

    public ItemListResponse<CompanionLibraryItem> recent() throws IOException, InterruptedException {
        return post(modFunctionsBase, newAction("recent"), LIBRARY_LIST, true);
    }

    public void setFavorite(String artId, boolean favorite) throws IOException, InterruptedException {
        JsonObject payload = newAction("favorite_set");
        payload.addProperty("art_id", artId);
        payload.addProperty("favorite", favorite);
        post(modFunctionsBase, payload, JsonObject.class, true);
    }

    public ArtUpdateResponse updateArt(String artId, String title, String privacy) throws IOException, InterruptedException {
        JsonObject payload = newAction("art_update");
        payload.addProperty("art_id", artId);
        payload.addProperty("title", title);
        payload.addProperty("privacy", privacy);
        return post(modFunctionsBase, payload, ArtUpdateResponse.class, true);
    }

    public void deleteArt(String artId) throws IOException, InterruptedException {
        JsonObject payload = newAction("art_delete");
        payload.addProperty("art_id", artId);
        post(modFunctionsBase, payload, JsonObject.class, true);
    }

    public ItemListResponse<CompanionCollection> collections() throws IOException, InterruptedException {
        return post(modFunctionsBase, newAction("collections"), COLLECTION_LIST, true);
    }

    public CollectionCreateResponse createCollection(String name) throws IOException, InterruptedException {
        JsonObject payload = newAction("collection_create");
        payload.addProperty("name", name);
        return post(modFunctionsBase, payload, CollectionCreateResponse.class, true);
    }

    public CollectionCreateResponse updateCollection(String collectionId, String name) throws IOException, InterruptedException {
        JsonObject payload = newAction("collection_update");
        payload.addProperty("collection_id", collectionId);
        payload.addProperty("name", name);
        return post(modFunctionsBase, payload, CollectionCreateResponse.class, true);
    }

    public void deleteCollection(String collectionId) throws IOException, InterruptedException {
        JsonObject payload = newAction("collection_delete");
        payload.addProperty("collection_id", collectionId);
        post(modFunctionsBase, payload, JsonObject.class, true);
    }

    public ItemListResponse<CompanionLibraryItem> collectionItems(String collectionId) throws IOException, InterruptedException {
        JsonObject payload = newAction("collection_items");
        payload.addProperty("collection_id", collectionId);
        return post(modFunctionsBase, payload, LIBRARY_LIST, true);
    }

    public void setCollectionItem(String collectionId, String artId, boolean selected) throws IOException, InterruptedException {
        JsonObject payload = newAction("collection_item_set");
        payload.addProperty("collection_id", collectionId);
        payload.addProperty("art_id", artId);
        payload.addProperty("selected", selected);
        post(modFunctionsBase, payload, JsonObject.class, true);
    }

    public ScanUploadResponse uploadScan(MapScanDraft draft) throws IOException, InterruptedException {
        JsonObject payload = newAction("scan_upload");
        payload.addProperty("title", draft.title());
        payload.addProperty("source", draft.source());
        JsonObject grid = new JsonObject();
        grid.addProperty("wide", draft.wide());
        grid.addProperty("tall", draft.tall());
        payload.add("map_grid", grid);
        payload.addProperty("missing_maps", draft.missingMaps());
        payload.addProperty("image_base64", draft.dataUrl());
        return post(modFunctionsBase, payload, ScanUploadResponse.class, true);
    }

    public ScanImportDetails scanImport(String importId) throws IOException, InterruptedException {
        JsonObject payload = newAction("scan_get");
        payload.addProperty("import_id", importId);
        return post(modFunctionsBase, payload, ScanImportDetails.class, true);
    }

    public BuildSessionState tracker(String sessionId) throws IOException, InterruptedException {
        JsonObject payload = newAction("tracker_get");
        payload.addProperty("session_id", sessionId);
        return post(modFunctionsBase, payload, BuildSessionResponse.class, true).session();
    }

    public BuildSessionState trackerForArt(String artId) throws IOException, InterruptedException {
        return trackerForArtResult(artId).session();
    }

    public TrackerForArtResult trackerForArtResult(String artId) throws IOException, InterruptedException {
        JsonObject payload = newAction("tracker_for_art");
        payload.addProperty("art_id", artId);
        BuildSessionResponse response = post(modFunctionsBase, payload, BuildSessionResponse.class, true);
        return new TrackerForArtResult(response.session(), response.created());
    }

    public void updateTracker(String sessionId, JsonObject gathered, JsonObject placed) throws IOException, InterruptedException {
        JsonObject payload = newAction("tracker_update");
        payload.addProperty("session_id", sessionId);
        if (gathered != null) payload.add("gathered", gathered);
        if (placed != null) payload.add("placed", placed);
        post(modFunctionsBase, payload, JsonObject.class, true);
    }

    public void switchTrackerMode(String sessionId, String mode) throws IOException, InterruptedException {
        JsonObject payload = newAction("tracker_switch");
        payload.addProperty("session_id", sessionId);
        payload.addProperty("mode", mode);
        post(modFunctionsBase, payload, JsonObject.class, true);
    }

    public byte[] downloadArtifact(CompanionArtifact artifact) throws IOException, InterruptedException {
        return downloadArtifactBounded(artifact, MAX_ARTIFACT_BYTES);
    }

    public byte[] downloadArtifactBounded(CompanionArtifact artifact, int maxBytes) throws IOException, InterruptedException {
        if (maxBytes < 1 || artifact.sizeBytes() < 1 || artifact.sizeBytes() > maxBytes) {
            throw new IOException("Artifact size is outside the safe download limit");
        }
        URI uri = requireSignedArtifactUri(artifact);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .GET()
            .build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                byte[] errorBody = readBounded(body, 64 * 1024);
                requireSuccess(response.statusCode(), new String(errorBody, java.nio.charset.StandardCharsets.UTF_8));
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > maxBytes || (contentLength >= 0 && contentLength != artifact.sizeBytes())) {
                throw new IOException("Artifact Content-Length does not match its pinned metadata");
            }
            return readBounded(body, maxBytes);
        }
    }

    static URI requireSignedArtifactUri(CompanionArtifact artifact) throws IOException {
        if (artifact == null || artifact.signedUrl() == null || artifact.signedUrl().isBlank()) {
            throw new IOException("Artifact has no signed download URL");
        }
        return requireTrustedDownloadUri(artifact.signedUrl());
    }

    static URI requireTrustedDownloadUri(String value) throws IOException {
        if (value == null || value.isBlank()) throw new IOException("Download URL is missing");
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException error) {
            throw new IOException("Download URL is invalid", error);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(java.util.Locale.ROOT);
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        boolean trustedHost = loopback || "api.mapkluss.art".equals(host) || host.endsWith(".supabase.co")
            // Yandex Object Storage signs URLs with both the root endpoint and virtual-host bucket URLs.
            || "storage.yandexcloud.net".equals(host) || host.endsWith(".storage.yandexcloud.net");
        boolean secureScheme = "https".equalsIgnoreCase(uri.getScheme())
            || (loopback && "http".equalsIgnoreCase(uri.getScheme()));
        if (!uri.isAbsolute() || !secureScheme || !trustedHost || uri.getUserInfo() != null) {
            throw new IOException("Download URL is outside trusted storage hosts");
        }
        return uri;
    }

    static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maxBytes) throw new IOException("Artifact response exceeds the safe download limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public void downloadArtifactTo(CompanionArtifact artifact, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Files.write(target, downloadArtifact(artifact));
    }

    private <T> T post(URI uri, JsonObject payload, Class<T> type, boolean auth) throws IOException, InterruptedException {
        return post(uri, payload, (Type) type, auth);
    }

    private <T> T post(URI uri, JsonObject payload, Type type, boolean auth) throws IOException, InterruptedException {
        URI selectedUri = routedEndpoint(uri);
        ApiResponse response;
        try {
            response = sendPost(selectedUri, payload, auth);
        } catch (IOException error) {
            if (!canFallback(selectedUri)) throw error;
            CompanionBackendRouter.reportFailure(routingConfig, selectedUri);
            if (!safeToRetry(payload)) throw error;
            response = sendPost(CompanionBackendRouter.directEndpoint(routingConfig, uri.getPath()), payload, auth);
        }
        if (retryableGatewayStatus(selectedUri, response.status()) && safeToRetry(payload)) {
            CompanionBackendRouter.reportFailure(routingConfig, selectedUri);
            response = sendPost(CompanionBackendRouter.directEndpoint(routingConfig, uri.getPath()), payload, auth);
        } else if (retryableGatewayStatus(selectedUri, response.status())) {
            CompanionBackendRouter.reportFailure(routingConfig, selectedUri);
        }
        requireSuccess(response.status(), response.body());
        return GSON.fromJson(response.body(), type);
    }

    private ApiResponse sendPost(URI uri, JsonObject payload, boolean auth) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)));
        if (auth && bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] responseBytes;
        try (InputStream body = response.body()) {
            responseBytes = readBounded(body, response.statusCode() >= 200 && response.statusCode() < 300
                ? MAX_API_RESPONSE_BYTES
                : 64 * 1024);
        }
        String responseBody = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
        return new ApiResponse(response.statusCode(), responseBody);
    }

    private URI routedEndpoint(URI endpoint) {
        if (routingConfig == null) return endpoint;
        return URI.create(CompanionBackendRouter.select(routingConfig) + endpoint.getPath());
    }

    private boolean canFallback(URI attemptedUri) {
        return routingConfig != null
            && !CompanionBackendRouter.directEndpoint(routingConfig, attemptedUri.getPath()).equals(attemptedUri);
    }

    private boolean retryableGatewayStatus(URI attemptedUri, int status) {
        return canFallback(attemptedUri) && (status == 502 || status == 503 || status == 504);
    }

    private static boolean safeToRetry(JsonObject payload) {
        String action = payload != null && payload.has("action") ? payload.get("action").getAsString() : "";
        return switch (action) {
            case "device_poll", "manifest", "library", "favorites", "recent", "collections",
                "collection_items", "scan_get", "tracker_get", "tracker_for_art" -> true;
            default -> false;
        };
    }

    private record ApiResponse(int status, String body) {
    }

    private static JsonObject newAction(String action) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        return payload;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static void requireSuccess(int status, String body) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("MapKluss API failed with HTTP " + status + ": " + body);
        }
    }
}
