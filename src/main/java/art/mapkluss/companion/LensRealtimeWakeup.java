package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class LensRealtimeWakeup implements WebSocket.Listener, AutoCloseable {
    private static final Gson GSON = new Gson();
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_MESSAGE_CHARACTERS = 256 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private static final AtomicLong REFERENCES = new AtomicLong();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mapkluss-lens-realtime");
        thread.setDaemon(true);
        return thread;
    });

    private final LensDtos.Realtime capability;
    private final String websocketUrl;
    private final Runnable wakeup;
    private final BoundedTextMessageAccumulator messages = new BoundedTextMessageAccumulator(MAX_MESSAGE_CHARACTERS);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private volatile WebSocket socket;
    private volatile ScheduledFuture<?> heartbeat;
    private volatile ScheduledFuture<?> reconnect;
    private volatile String joinReference;
    private volatile boolean healthy;

    LensRealtimeWakeup(LensDtos.Realtime capability, String backendUrl, Runnable wakeup) {
        this.capability = capability;
        this.websocketUrl = routeWebsocketUrl(capability.websocketUrl(), backendUrl);
        this.wakeup = wakeup;
    }

    void connect() {
        if (closed.get() || capability == null || !capability.usable() || !connecting.compareAndSet(false, true)) return;
        String separator = websocketUrl.contains("?") ? "&" : "?";
        URI uri = URI.create(websocketUrl + separator + "apikey=" + capability.apiKey() + "&vsn=1.0.0");
        HTTP.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT).buildAsync(uri, this)
            .exceptionally(error -> {
                connecting.set(false);
                if (!closed.get()) MapKlussCompanionClient.LOGGER.debug("Lens Realtime unavailable; polling remains active.", error);
                scheduleReconnect();
                return null;
            });
    }

    boolean matches(LensDtos.Realtime other, String backendUrl) {
        return other != null
            && websocketUrl.equals(routeWebsocketUrl(other.websocketUrl(), backendUrl))
            && capability.apiKey().equals(other.apiKey())
            && capability.topic().equals(other.topic());
    }

    boolean isHealthy() {
        return healthy && !closed.get() && socket != null;
    }

    static String routeWebsocketUrl(String sourceUrl, String backendUrl) {
        if (sourceUrl == null || sourceUrl.isBlank() || backendUrl == null || backendUrl.isBlank()) return sourceUrl;
        try {
            URI source = URI.create(sourceUrl);
            URI backend = URI.create(backendUrl);
            String scheme = "https".equalsIgnoreCase(backend.getScheme()) ? "wss" : "ws";
            String path = source.getRawPath() == null || source.getRawPath().isBlank()
                ? "/realtime/v1/websocket"
                : source.getRawPath();
            String query = source.getRawQuery() == null ? "" : "?" + source.getRawQuery();
            return scheme + "://" + backend.getRawAuthority() + path + query;
        } catch (Exception ignored) {
            return sourceUrl;
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        connecting.set(false);
        if (closed.get()) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Lens closed before connect completed");
            return;
        }
        reconnectScheduled.set(false);
        healthy = false;
        messages.reset();
        WebSocket previousSocket = socket;
        socket = webSocket;
        if (previousSocket != null && previousSocket != webSocket) {
            previousSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Lens connection replaced");
        }
        joinReference = Long.toString(REFERENCES.incrementAndGet());
        JsonObject payload = new JsonObject();
        JsonObject config = new JsonObject();
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("self", false);
        config.add("broadcast", broadcast);
        config.add("presence", new JsonObject());
        JsonArray postgres = new JsonArray();
        config.add("postgres_changes", postgres);
        payload.add("config", config);
        webSocket.sendText(message("phx_join", payload), true);
        ScheduledFuture<?> previous = heartbeat;
        if (previous != null) previous.cancel(false);
        heartbeat = SCHEDULER.scheduleAtFixedRate(() -> {
            WebSocket current = socket;
            if (!closed.get() && current != null) current.sendText(heartbeatMessage(), true);
        }, 25, 25, TimeUnit.SECONDS);
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (closed.get() || socket != webSocket) return null;
        BoundedTextMessageAccumulator.Result result = messages.append(data, last);
        if (result.overflow()) {
            healthy = false;
            webSocket.sendClose(1009, "Lens Realtime message is too large");
            return null;
        }
        if (result.complete()) {
            String message = result.message();
            if (message.contains("phx_reply") && message.contains("\"status\":\"ok\"")) {
                healthy = true;
                reconnectAttempt.set(0);
            }
            if (message.contains("broadcast") || message.contains("revision") || message.contains("changedAt")) wakeup.run();
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (socket != null && socket != webSocket) return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        if (socket == webSocket) socket = null;
        healthy = false;
        messages.reset();
        cancelHeartbeat();
        scheduleReconnect();
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (socket != null && socket != webSocket) return;
        if (socket == webSocket) socket = null;
        connecting.set(false);
        healthy = false;
        messages.reset();
        cancelHeartbeat();
        if (!closed.get()) MapKlussCompanionClient.LOGGER.debug("Lens Realtime disconnected; polling remains active.", error);
        scheduleReconnect();
    }

    @Override
    public void close() {
        closed.set(true);
        WebSocket current = socket;
        socket = null;
        connecting.set(false);
        healthy = false;
        messages.reset();
        cancelHeartbeat();
        ScheduledFuture<?> pendingReconnect = reconnect;
        reconnect = null;
        if (pendingReconnect != null) pendingReconnect.cancel(false);
        if (current != null) current.sendClose(WebSocket.NORMAL_CLOSURE, "world changed");
    }

    private String message(String event, JsonObject payload) {
        JsonObject message = new JsonObject();
        message.addProperty("join_ref", joinReference);
        message.addProperty("topic", "realtime:" + capability.topic());
        message.addProperty("event", event);
        message.add("payload", payload);
        message.addProperty("ref", joinReference);
        return GSON.toJson(message);
    }

    private String heartbeatMessage() {
        JsonObject message = new JsonObject();
        message.add("join_ref", JsonNull.INSTANCE);
        message.addProperty("topic", "phoenix");
        message.addProperty("event", "heartbeat");
        message.add("payload", new JsonObject());
        message.addProperty("ref", Long.toString(REFERENCES.incrementAndGet()));
        return GSON.toJson(message);
    }

    private void scheduleReconnect() {
        if (closed.get() || !reconnectScheduled.compareAndSet(false, true)) return;
        int attempt = reconnectAttempt.getAndIncrement();
        long delayNanos = LensSyncPolicy.reconnectDelayNanos(attempt);
        reconnect = SCHEDULER.schedule(() -> {
            reconnect = null;
            reconnectScheduled.set(false);
            connect();
        }, delayNanos, TimeUnit.NANOSECONDS);
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> current = heartbeat;
        heartbeat = null;
        if (current != null) current.cancel(false);
    }
}
