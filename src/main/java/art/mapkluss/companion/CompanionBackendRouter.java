package art.mapkluss.companion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class CompanionBackendRouter {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private static final Map<RouteKey, String> CACHE = new ConcurrentHashMap<>();

    private CompanionBackendRouter() {
    }

    static String select(CompanionConfig config) {
        String gatewayUrl = stripTrailingSlash(config.gatewayUrl());
        String directUrl = stripTrailingSlash(config.supabaseUrl());
        return CACHE.computeIfAbsent(new RouteKey(gatewayUrl, directUrl), ignored ->
            select(gatewayUrl, directUrl, CompanionBackendRouter::isReady)
        );
    }

    static String select(String gatewayUrl, String directUrl, Predicate<URI> readinessProbe) {
        String gateway = stripTrailingSlash(gatewayUrl);
        String direct = stripTrailingSlash(directUrl);
        if (gateway.isBlank() || gateway.equals(direct)) return direct;
        URI readinessUri = URI.create(gateway + "/readyz");
        return readinessProbe.test(readinessUri) ? gateway : direct;
    }

    private static boolean isReady(URI readinessUri) {
        HttpRequest request = HttpRequest.newBuilder(readinessUri)
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        try {
            HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception error) {
            return false;
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record RouteKey(String gatewayUrl, String directUrl) {
    }
}
