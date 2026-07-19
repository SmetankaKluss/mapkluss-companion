package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

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
}
