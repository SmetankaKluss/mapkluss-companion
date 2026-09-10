package art.mapkluss.companion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A local-only controller for the ordinary dev Library screen. It never exposes Cloud or
 * player data: commands only select anonymous fixtures and request a screenshot of the dev window.
 */
public final class MapKlussControlDesk {
    private static final Set<String> FIXTURES = Set.of("empty", "loading", "populated", "error", "long-name");
    private static final Set<String> LANGUAGES = Set.of("ru", "en");
    private static final Set<String> GUI_SCALES = Set.of("auto", "2", "3", "4");
    private static final Set<String> VIEWPORTS = Set.of("320x240", "480x270", "640x360", "960x540", "1280x720");
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicLong COMMAND_VERSION = new AtomicLong();
    private static final AtomicLong CAPTURE_VERSION = new AtomicLong();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String fixture = normalize(System.getProperty("mapkluss.dev.libraryFixture", "populated"), FIXTURES, "populated");
    private static volatile String language = normalize(System.getProperty("mapkluss.dev.language", "ru"), LANGUAGES, "ru");
    private static volatile String guiScale = "auto";
    private static volatile String viewport = "960x540";
    private static volatile byte[] latestCapture;
    private static volatile String captureStatus = "idle";
    private static volatile String origin;
    private static volatile byte[] token;

    private MapKlussControlDesk() {
    }

    public static void register() {
        if (!Boolean.getBoolean("mapkluss.dev.controlDesk.enabled") || !STARTED.compareAndSet(false, true)) return;
        try {
            token = sessionToken();
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 8);
            origin = "http://127.0.0.1:" + server.getAddress().getPort();
            ExecutorService executor = Executors.newSingleThreadExecutor(daemonFactory("mapkluss-control-desk"));
            server.setExecutor(executor);
            server.createContext("/", MapKlussControlDesk::handle);
            server.start();
            openBrowser(origin + "/?token=" + Base64.getUrlEncoder().withoutPadding().encodeToString(token));
            MapKlussCompanionClient.LOGGER.info("MapKluss Control Desk is listening on loopback only; its browser session was opened locally.");
        } catch (IOException error) {
            STARTED.set(false);
            MapKlussCompanionClient.LOGGER.warn("Could not start the local MapKluss Control Desk.", error);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isLocal(exchange)) {
                reply(exchange, 403, "text/plain; charset=utf-8", "Local requests only.".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                reply(exchange, 200, "text/html; charset=utf-8", HTML.getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/desk.js".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                reply(exchange, 200, "text/javascript; charset=utf-8", SCRIPT.getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/desk.css".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                reply(exchange, 200, "text/css; charset=utf-8", STYLE.getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!hasValidToken(exchange)) {
                reply(exchange, 403, "text/plain; charset=utf-8", "Forbidden.".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if ("/api/state".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                if (!sameOrigin(exchange)) {
                    reply(exchange, 403, "text/plain; charset=utf-8", "Forbidden.".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                replyJson(exchange, state());
                return;
            }
            if ("/api/capture".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                byte[] capture = latestCapture;
                if (capture == null) {
                    reply(exchange, 404, "text/plain; charset=utf-8", "No capture yet.".getBytes(StandardCharsets.UTF_8));
                } else {
                    reply(exchange, 200, "image/png", capture);
                }
                return;
            }
            if ("/api/command".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                if (!sameOrigin(exchange)) {
                    reply(exchange, 403, "text/plain; charset=utf-8", "Forbidden.".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                applyCommand(exchange);
                return;
            }
            reply(exchange, 404, "text/plain; charset=utf-8", "Not found.".getBytes(StandardCharsets.UTF_8));
        } finally {
            exchange.close();
        }
    }

    private static void applyCommand(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(2_049);
        if (body.length > 2_048) {
            reply(exchange, 413, "text/plain; charset=utf-8", "Request too large.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
        } catch (RuntimeException error) {
            reply(exchange, 400, "text/plain; charset=utf-8", "Invalid command.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!parsed.isJsonObject()) {
            reply(exchange, 400, "text/plain; charset=utf-8", "Invalid command.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        JsonObject command = parsed.getAsJsonObject();
        String nextFixture = choice(command, "fixture", FIXTURES, fixture);
        String nextLanguage = choice(command, "language", LANGUAGES, language);
        String nextScale = choice(command, "guiScale", GUI_SCALES, guiScale);
        String nextViewport = choice(command, "viewport", VIEWPORTS, viewport);
        boolean capture = !command.has("capture") || (command.get("capture").isJsonPrimitive() && command.get("capture").getAsBoolean());
        if (nextFixture == null || nextLanguage == null || nextScale == null || nextViewport == null) {
            reply(exchange, 400, "text/plain; charset=utf-8", "Unsupported command.".getBytes(StandardCharsets.UTF_8));
            return;
        }
        fixture = nextFixture;
        language = nextLanguage;
        guiScale = nextScale;
        viewport = nextViewport;
        long commandVersion = COMMAND_VERSION.incrementAndGet();
        MapKlussControlDeskBridge.apply(new Selection(nextFixture, nextLanguage, nextScale, nextViewport));
        if (capture) scheduleCapture(commandVersion);
        replyJson(exchange, state());
    }

    private static void scheduleCapture(long commandVersion) {
        captureStatus = "capturing";
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(Duration.ofMillis(450));
                if (COMMAND_VERSION.get() != commandVersion) return;
                byte[] capture = MapKlussControlDeskCapture.captureWindow();
                if (COMMAND_VERSION.get() == commandVersion) {
                    latestCapture = capture;
                    CAPTURE_VERSION.incrementAndGet();
                    captureStatus = "ready";
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                captureStatus = "failed";
                MapKlussCompanionClient.LOGGER.warn("Could not capture the local MapKluss Control Desk window.", error);
            }
        });
    }

    private static JsonObject state() {
        JsonObject state = new JsonObject();
        state.addProperty("fixture", fixture);
        state.addProperty("language", language);
        state.addProperty("guiScale", guiScale);
        state.addProperty("viewport", viewport);
        state.addProperty("captureStatus", captureStatus);
        state.addProperty("captureVersion", CAPTURE_VERSION.get());
        return state;
    }

    private static String choice(JsonObject command, String key, Set<String> allowed, String current) {
        if (!command.has(key)) return current;
        JsonElement value = command.get(key);
        if (!value.isJsonPrimitive()) return null;
        String candidate = value.getAsString();
        return allowed.contains(candidate) ? candidate : null;
    }

    private static String normalize(String value, Set<String> allowed, String fallback) {
        return allowed.contains(value) ? value : fallback;
    }

    private static boolean isLocal(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().isLoopbackAddress()
            && exchange.getLocalAddress().getAddress().isLoopbackAddress();
    }

    private static boolean sameOrigin(HttpExchange exchange) {
        String requestOrigin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin.equals(requestOrigin)) return true;
        return requestOrigin == null && origin.equals(exchange.getRequestHeaders().getFirst("X-MapKluss-Control-Origin"));
    }

    private static boolean hasValidToken(HttpExchange exchange) {
        String supplied = query(exchange.getRequestURI().getRawQuery(), "token");
        if (supplied == null || token == null) return false;
        try {
            return MessageDigest.isEqual(token, Base64.getUrlDecoder().decode(supplied));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String query(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;
            String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (name.equals(key)) return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static byte[] randomToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return value;
    }

    private static byte[] sessionToken() {
        String testToken = System.getProperty("mapkluss.dev.controlDesk.testToken", "");
        if (Boolean.getBoolean("mapkluss.dev.controlDesk.testMode") && !testToken.isBlank()) {
            try {
                byte[] configured = Base64.getUrlDecoder().decode(testToken);
                if (configured.length == 32) return configured;
            } catch (IllegalArgumentException ignored) {
                // Development tests fall back to a fresh per-session token when input is malformed.
            }
        }
        return randomToken();
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception error) {
            MapKlussCompanionClient.LOGGER.debug("Could not open the local MapKluss Control Desk browser tab.", error);
        }
    }

    private static void replyJson(HttpExchange exchange, JsonObject payload) throws IOException {
        reply(exchange, 200, "application/json; charset=utf-8", payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void reply(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; connect-src 'self'; img-src 'self'; style-src 'self'; script-src 'self'; base-uri 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    public record Selection(String fixture, String language, String guiScale, String viewport) {
    }

    private static final String HTML = """
        <!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>MapKluss Control Desk</title><link rel=\"stylesheet\" href=\"/desk.css\"></head><body><main><header><strong>MapKluss Control Desk</strong><span id=\"status\">Connecting</span></header><section class=\"controls\"><label>Fixture<select id=\"fixture\"><option value=\"empty\">Empty</option><option value=\"loading\">Loading</option><option value=\"populated\">Populated</option><option value=\"error\">Error</option><option value=\"long-name\">Long name</option></select></label><label>Language<select id=\"language\"><option value=\"ru\">RU</option><option value=\"en\">EN</option></select></label><label>GUI scale<select id=\"guiScale\"><option value=\"auto\">Auto</option><option value=\"2\">2</option><option value=\"3\">3</option><option value=\"4\">4</option></select></label><label>Window<select id=\"viewport\"><option value=\"320x240\">320 x 240</option><option value=\"480x270\">480 x 270</option><option value=\"640x360\">640 x 360</option><option value=\"960x540\">960 x 540</option><option value=\"1280x720\">1280 x 720</option></select></label><button id=\"apply\">Apply & capture</button></section><section class=\"capture\"><img id=\"capture\" alt=\"Actual Minecraft dev-client capture\"></section></main><script src=\"/desk.js\"></script></body></html>
        """;
    private static final String STYLE = """
        :root{color-scheme:dark;font:14px/1.4 system-ui,sans-serif;background:#101114;color:#eef2f4}body{margin:0;padding:20px}main{max-width:1280px;margin:auto}header{display:flex;justify-content:space-between;align-items:center;padding-bottom:14px;border-bottom:1px solid #31363d}strong{font-size:16px}#status{color:#9fd2c8}.controls{display:grid;grid-template-columns:repeat(4,minmax(0,1fr)) auto;gap:10px;padding:14px 0}label{display:grid;gap:4px;color:#aeb8c1}select,button{min-height:32px;border:1px solid #3c4650;background:#181b20;color:#f4f7f8;padding:0 9px;border-radius:3px}button{background:#b5ef55;color:#152006;border-color:#c7ff6f;font-weight:700;cursor:pointer}.capture{min-height:220px;border:1px solid #31363d;background:#08090b;display:grid;place-items:center}.capture img{display:block;max-width:100%;height:auto;image-rendering:auto}@media(max-width:760px){.controls{grid-template-columns:repeat(2,minmax(0,1fr))}.controls button{grid-column:span 2}}
        """;
    private static final String SCRIPT = """
        const token=new URLSearchParams(location.search).get('token');const $=id=>document.getElementById(id);const ids=['fixture','language','guiScale','viewport'];let captureVersion=-1;
        const localHeaders={'x-mapkluss-control-origin':location.origin};
        async function state(){const r=await fetch('/api/state?token='+encodeURIComponent(token),{cache:'no-store',headers:localHeaders});if(!r.ok)throw new Error('unavailable');const s=await r.json();ids.forEach(id=>$(id).value=s[id]);$('status').textContent=s.captureStatus;if(s.captureVersion!==captureVersion&&s.captureVersion>0){captureVersion=s.captureVersion;$('capture').src='/api/capture?token='+encodeURIComponent(token)+'&v='+captureVersion}};
        async function apply(){const payload=Object.fromEntries(ids.map(id=>[id,$(id).value]));payload.capture=true;$('apply').disabled=true;try{await fetch('/api/command?token='+encodeURIComponent(token),{method:'POST',headers:{'content-type':'application/json','x-mapkluss-control-origin':location.origin},body:JSON.stringify(payload)});await state()}finally{$('apply').disabled=false}};
        $('apply').addEventListener('click',apply);setInterval(()=>state().catch(()=>{$('status').textContent='Disconnected'}),700);state().catch(()=>{$('status').textContent='Unavailable'});
        """;
}
