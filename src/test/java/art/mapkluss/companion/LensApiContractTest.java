package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class LensApiContractTest {
    private static final Gson GSON = new Gson();

    @Test
    void everyDeviceActionDeclaresItsPrincipalKind() throws Exception {
        Method action = LensApiClient.class.getDeclaredMethod("action", String.class);
        action.setAccessible(true);
        JsonObject payload = (JsonObject) action.invoke(null, "session_poll");
        assertEquals("session_poll", payload.get("action").getAsString());
        assertEquals("device", payload.get("principalKind").getAsString());
    }

    @Test
    void readsNestedCapabilitiesAndOwnershipFlags() {
        LensDtos.Capabilities capabilities = GSON.fromJson("""
            {"apiVersion":1,"enabled":true,"limits":{"maxPlacements":8,"tileResolutions":[128,64,32,16]},
             "timing":{"recoveryPollMs":5000}}
            """, LensDtos.Capabilities.class);
        assertEquals(8, capabilities.limits().maxPlacements());
        assertEquals(5000, capabilities.timing().recoveryPollMs());

        LensDtos.SessionResult session = GSON.fromJson("""
            {"apiVersion":1,"session":{"sessionId":"s","title":"art","status":"active",
             "grid":{"wide":2,"tall":3},"mapMode":"2d","revision":1,"tileResolution":128,
             "previewWidth":256,"previewHeight":384,"viewerCount":1,"ownedByUser":true}}
            """, LensDtos.SessionResult.class);
        assertNotNull(session.session());
        assertEquals(true, session.session().ownedByUser());
    }

    @Test
    void hasNoAutomaticPublicDiscoveryEndpoint() {
        assertFalse(Arrays.stream(LensApiClient.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("discover")));
    }

    @Test
    void everyLensNetworkPathHasABoundedTimeout() throws Exception {
        LensApiClient client = new LensApiClient("https://example.invalid", "anon", "device");
        Method action = LensApiClient.class.getDeclaredMethod("action", String.class);
        action.setAccessible(true);
        HttpRequest request = client.request((JsonObject) action.invoke(null, "session_poll"));

        assertEquals(LensApiClient.REQUEST_TIMEOUT, request.timeout().orElseThrow());
        assertFalse(LensApiClient.CONNECT_TIMEOUT.isZero());
        assertFalse(LensTextureAtlas.DOWNLOAD_TIMEOUT.isZero());
        assertFalse(LensRealtimeWakeup.CONNECT_TIMEOUT.isZero());
        assertEquals(Duration.ofSeconds(12), LensApiClient.REQUEST_TIMEOUT);
    }

    @Test
    void routesRealtimeThroughTheSelectedBackend() {
        assertEquals(
            "wss://api.mapkluss.art/realtime/v1/websocket",
            LensRealtimeWakeup.routeWebsocketUrl(
                "wss://project.supabase.co/realtime/v1/websocket",
                "https://api.mapkluss.art"
            )
        );
        assertEquals(
            "wss://project.supabase.co/realtime/v1/websocket",
            LensRealtimeWakeup.routeWebsocketUrl(
                "wss://project.supabase.co/realtime/v1/websocket",
                "https://project.supabase.co"
            )
        );
    }
}
