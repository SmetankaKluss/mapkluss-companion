package art.mapkluss.companion;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

final class CompanionBackendRouter {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
    static final Duration GATEWAY_CACHE_TTL = Duration.ofSeconds(60);
    static final Duration DIRECT_CACHE_TTL = Duration.ofSeconds(15);

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private static final Map<RouteKey, RouteState> CACHE = new ConcurrentHashMap<>();

    private CompanionBackendRouter() {
    }

    static String select(CompanionConfig config) {
        String gatewayUrl = stripTrailingSlash(config.gatewayUrl());
        String directUrl = stripTrailingSlash(config.supabaseUrl());
        return selectCached(gatewayUrl, directUrl, CompanionBackendRouter::isReady, System::nanoTime);
    }

    static void reportFailure(CompanionConfig config, URI attemptedUri) {
        if (config == null || attemptedUri == null) return;
        String gateway = stripTrailingSlash(config.gatewayUrl());
        String direct = stripTrailingSlash(config.supabaseUrl());
        if (gateway.isBlank() || gateway.equals(direct) || !sameOrigin(gateway, attemptedUri)) return;
        long now = System.nanoTime();
        CACHE.put(
            new RouteKey(gateway, direct),
            new RouteState(direct, saturatedAdd(now, DIRECT_CACHE_TTL.toNanos()))
        );
    }

    static URI directEndpoint(CompanionConfig config, String path) {
        return URI.create(stripTrailingSlash(config.supabaseUrl()) + normalizePath(path));
    }

    static String selectCached(
        String gatewayUrl,
        String directUrl,
        Predicate<URI> readinessProbe,
        LongSupplier clock
    ) {
        String gateway = stripTrailingSlash(gatewayUrl);
        String direct = stripTrailingSlash(directUrl);
        if (gateway.isBlank() || gateway.equals(direct)) return direct;
        RouteKey key = new RouteKey(gateway, direct);
        long now = clock.getAsLong();
        RouteState selected = CACHE.compute(key, (ignored, current) -> {
            if (current != null && now < current.expiresAtNanos()) return current;
            String url = select(gateway, direct, readinessProbe);
            long ttl = url.equals(gateway) ? GATEWAY_CACHE_TTL.toNanos() : DIRECT_CACHE_TTL.toNanos();
            return new RouteState(url, saturatedAdd(now, ttl));
        });
        return selected.url();
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

    private static boolean sameOrigin(String baseUrl, URI uri) {
        try {
            URI base = URI.create(baseUrl);
            int basePort = base.getPort() >= 0 ? base.getPort() : defaultPort(base.getScheme());
            int uriPort = uri.getPort() >= 0 ? uri.getPort() : defaultPort(uri.getScheme());
            return java.util.Objects.equals(base.getScheme(), uri.getScheme())
                && java.util.Objects.equals(base.getHost(), uri.getHost())
                && basePort == uriPort;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int defaultPort(String scheme) {
        return "http".equalsIgnoreCase(scheme) ? 80 : "https".equalsIgnoreCase(scheme) ? 443 : -1;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        return path.startsWith("/") ? path : "/" + path;
    }

    static void clearCacheForTests() {
        CACHE.clear();
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record RouteKey(String gatewayUrl, String directUrl) {
    }

    private record RouteState(String url, long expiresAtNanos) {
    }
}
