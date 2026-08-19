package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class CompanionTelemetrySettings {
    public enum Consent { UNKNOWN, ENABLED, DISABLED }

    private static final Gson GSON = new Gson();
    private final Path path;
    private Consent consent;
    private String installationId;

    private CompanionTelemetrySettings(Path path, Consent consent, String installationId) {
        this.path = path;
        boolean validEnabledState = consent != Consent.ENABLED || validUuid(installationId);
        this.consent = validEnabledState ? consent : Consent.UNKNOWN;
        this.installationId = consent == Consent.ENABLED && validEnabledState ? installationId : null;
    }

    public static CompanionTelemetrySettings load(Path runDir) {
        Path path = LitematicaPaths.telemetrySettingsPath(runDir);
        if (!Files.exists(path)) return new CompanionTelemetrySettings(path, Consent.UNKNOWN, null);
        try {
            JsonObject json = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
            String rawConsent = json != null && json.has("consent") ? json.get("consent").getAsString() : "unknown";
            Consent consent = switch (rawConsent) {
                case "enabled" -> Consent.ENABLED;
                case "disabled" -> Consent.DISABLED;
                default -> Consent.UNKNOWN;
            };
            String id = json != null && json.has("installationId") ? json.get("installationId").getAsString() : null;
            return new CompanionTelemetrySettings(path, consent, id);
        } catch (Exception ignored) {
            return new CompanionTelemetrySettings(path, Consent.UNKNOWN, null);
        }
    }

    public synchronized Consent consent() {
        return consent;
    }

    public synchronized boolean enabled() {
        return consent == Consent.ENABLED;
    }

    public synchronized String enable() throws IOException {
        consent = Consent.ENABLED;
        if (!validUuid(installationId)) installationId = UUID.randomUUID().toString();
        save();
        return installationId;
    }

    public synchronized void disable() throws IOException {
        consent = Consent.DISABLED;
        installationId = null;
        save();
    }

    public synchronized String installationId() {
        return enabled() ? installationId : null;
    }

    private void save() throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("consent", consent == Consent.ENABLED ? "enabled" : "disabled");
        if (installationId != null) json.addProperty("installationId", installationId);
        AtomicFiles.writePrivateUtf8(path, GSON.toJson(json));
    }

    private static boolean validUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).version() == 4;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
