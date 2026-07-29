package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class CompanionBackendRouterTest {
    @Test
    void selectsGatewayWhenReadinessSucceeds() {
        AtomicReference<URI> probed = new AtomicReference<>();
        String selected = CompanionBackendRouter.select(
            "https://api.mapkluss.art/",
            "https://project.supabase.co/",
            uri -> {
                probed.set(uri);
                return true;
            }
        );

        assertEquals("https://api.mapkluss.art", selected);
        assertEquals(URI.create("https://api.mapkluss.art/readyz"), probed.get());
    }

    @Test
    void selectsDirectEndpointWhenGatewayIsUnavailable() {
        String selected = CompanionBackendRouter.select(
            "https://api.mapkluss.art",
            "https://project.supabase.co",
            ignored -> false
        );

        assertEquals("https://project.supabase.co", selected);
    }

    @Test
    void keepsGatewayChecksBounded() {
        assertFalse(CompanionBackendRouter.CONNECT_TIMEOUT.isZero());
        assertFalse(CompanionBackendRouter.REQUEST_TIMEOUT.isZero());
    }

    @Test
    void retriesDirectFallbackAfterShortTtl() {
        CompanionBackendRouter.clearCacheForTests();
        AtomicLong clock = new AtomicLong(100);
        AtomicInteger probes = new AtomicInteger();
        java.util.function.Predicate<URI> probe = ignored -> probes.incrementAndGet() > 1;

        assertEquals("https://direct.example", CompanionBackendRouter.selectCached(
            "https://gateway.example", "https://direct.example", probe, clock::get));
        assertEquals("https://direct.example", CompanionBackendRouter.selectCached(
            "https://gateway.example", "https://direct.example", probe, clock::get));
        assertEquals(1, probes.get());

        clock.addAndGet(CompanionBackendRouter.DIRECT_CACHE_TTL.toNanos() + 1);
        assertEquals("https://gateway.example", CompanionBackendRouter.selectCached(
            "https://gateway.example", "https://direct.example", probe, clock::get));
        assertEquals(2, probes.get());
    }

    @Test
    void gatewayFailureImmediatelySelectsDirectFallback() {
        CompanionBackendRouter.clearCacheForTests();
        CompanionConfig config = new CompanionConfig(
            "https://direct.example", "anon", "https://mapkluss.art", "ru", "https://gateway.example"
        );
        assertEquals("https://gateway.example", CompanionBackendRouter.select(
            config.gatewayUrl(), config.supabaseUrl(), ignored -> true
        ));

        CompanionBackendRouter.reportFailure(config, URI.create("https://gateway.example/functions/v1/test"));

        assertEquals("https://direct.example", CompanionBackendRouter.select(config));
    }
}
