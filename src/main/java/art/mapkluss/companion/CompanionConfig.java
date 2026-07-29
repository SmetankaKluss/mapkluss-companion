package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompanionConfig {
    private static final Gson GSON = new Gson();
    public static final String DEFAULT_SUPABASE_URL = "https://opxgnyadxybceldaokdi.supabase.co";
    public static final String DEFAULT_GATEWAY_URL = "https://api.mapkluss.art";
    public static final String DEFAULT_SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9weGdueWFkeHliY2VsZGFva2RpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQwNDU5MjEsImV4cCI6MjA4OTYyMTkyMX0.80IIx_1WGuUtxJlfu7qhOAQKdEb0FwEV8gD5ybe8DcQ";
    public static final String DEFAULT_SITE_URL = "https://mapkluss.art";
    public static final String DEFAULT_LANGUAGE = "ru";

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final String siteUrl;
    private final String language;
    private final String gatewayUrl;
    private String accessToken;

    public CompanionConfig() {
        this(DEFAULT_SUPABASE_URL, DEFAULT_SUPABASE_ANON_KEY, DEFAULT_SITE_URL, DEFAULT_LANGUAGE, DEFAULT_GATEWAY_URL);
    }

    public CompanionConfig(String supabaseUrl, String supabaseAnonKey) {
        this(supabaseUrl, supabaseAnonKey, DEFAULT_SITE_URL, DEFAULT_LANGUAGE, DEFAULT_GATEWAY_URL);
    }

    public CompanionConfig(String supabaseUrl, String supabaseAnonKey, String siteUrl) {
        this(supabaseUrl, supabaseAnonKey, siteUrl, DEFAULT_LANGUAGE, DEFAULT_GATEWAY_URL);
    }

    public CompanionConfig(String supabaseUrl, String supabaseAnonKey, String siteUrl, String language) {
        this(supabaseUrl, supabaseAnonKey, siteUrl, language, DEFAULT_GATEWAY_URL);
    }

    public CompanionConfig(String supabaseUrl, String supabaseAnonKey, String siteUrl, String language, String gatewayUrl) {
        this.supabaseUrl = stripTrailingSlash(blankToDefault(supabaseUrl, DEFAULT_SUPABASE_URL));
        this.supabaseAnonKey = blankToDefault(supabaseAnonKey, DEFAULT_SUPABASE_ANON_KEY);
        this.siteUrl = stripTrailingSlash(blankToDefault(siteUrl, DEFAULT_SITE_URL));
        this.language = normalizeLanguage(language);
        this.gatewayUrl = stripTrailingSlash(blankToDefault(gatewayUrl, DEFAULT_GATEWAY_URL));
    }

    public static CompanionConfig load(Path minecraftRunDir) throws IOException {
        Path path = LitematicaPaths.companionConfigPath(minecraftRunDir);
        if (!Files.exists(path)) {
            CompanionConfig config = new CompanionConfig();
            config.save(path);
            return config;
        }

        JsonObject json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
        if (json == null) return new CompanionConfig();
        CompanionConfig config = new CompanionConfig(
            stringField(json, "supabaseUrl", DEFAULT_SUPABASE_URL),
            stringField(json, "supabaseAnonKey", DEFAULT_SUPABASE_ANON_KEY),
            stringField(json, "siteUrl", DEFAULT_SITE_URL),
            stringField(json, "language", DEFAULT_LANGUAGE),
            stringField(json, "gatewayUrl", DEFAULT_GATEWAY_URL)
        );
        if (isLocalhostUrl(config.siteUrl())) {
            config = new CompanionConfig(
                config.supabaseUrl(), config.supabaseAnonKey(), DEFAULT_SITE_URL, config.language(), config.gatewayUrl()
            );
            config.save(path);
        }
        return config;
    }

    public String supabaseUrl() {
        return supabaseUrl;
    }

    public String supabaseAnonKey() {
        return supabaseAnonKey;
    }

    public String siteUrl() {
        return siteUrl;
    }

    public String language() {
        return language;
    }

    public String gatewayUrl() {
        return gatewayUrl;
    }

    public URI siteUri(String path) {
        String normalizedPath = path == null || path.isBlank()
            ? "/"
            : path.startsWith("/") ? path : "/" + path;
        return URI.create(siteUrl + normalizedPath);
    }

    public String accessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public void saveForRunDir(Path minecraftRunDir) throws IOException {
        save(LitematicaPaths.companionConfigPath(minecraftRunDir));
    }

    private void save(Path path) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("supabaseUrl", supabaseUrl);
        json.addProperty("supabaseAnonKey", supabaseAnonKey);
        json.addProperty("siteUrl", siteUrl);
        json.addProperty("language", language);
        json.addProperty("gatewayUrl", gatewayUrl);
        AtomicFiles.writePrivateUtf8(path, GSON.toJson(json));
    }

    private static String stringField(JsonObject json, String field, String fallback) {
        if (!json.has(field) || json.get(field).isJsonNull()) return fallback;
        return json.get(field).getAsString();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalizeLanguage(String value) {
        return "en".equalsIgnoreCase(value == null ? "" : value.trim()) ? "en" : DEFAULT_LANGUAGE;
    }

    private static boolean isLocalhostUrl(String value) {
        try {
            String host = URI.create(value).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }
}
