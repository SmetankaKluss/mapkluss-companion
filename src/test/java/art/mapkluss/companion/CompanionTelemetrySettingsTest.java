package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CompanionTelemetrySettingsTest {
    @TempDir Path tempDir;

    @Test
    void createsNoIdentifierBeforeConsentAndDeletesItWhenDisabled() throws Exception {
        CompanionTelemetrySettings settings = CompanionTelemetrySettings.load(tempDir);
        assertEquals(CompanionTelemetrySettings.Consent.UNKNOWN, settings.consent());
        assertNull(settings.installationId());
        assertFalse(Files.exists(LitematicaPaths.telemetrySettingsPath(tempDir)));

        String id = settings.enable();
        assertNotNull(id);
        assertEquals(id, CompanionTelemetrySettings.load(tempDir).installationId());

        settings.disable();
        assertNull(settings.installationId());
        String file = Files.readString(LitematicaPaths.telemetrySettingsPath(tempDir));
        assertFalse(file.contains("installationId"));
        assertEquals(CompanionTelemetrySettings.Consent.DISABLED, CompanionTelemetrySettings.load(tempDir).consent());
    }

    @Test
    void mapsOnlyCoarseOsFamilies() {
        assertEquals("windows", CompanionTelemetryClient.osFamily("Windows 11"));
        assertEquals("macos", CompanionTelemetryClient.osFamily("Mac OS X"));
        assertEquals("linux", CompanionTelemetryClient.osFamily("Linux"));
        assertEquals("other", CompanionTelemetryClient.osFamily("Plan 9"));
    }

    @Test
    void asksAgainWhenEnabledStateHasNoValidIdentifier() throws Exception {
        Path path = LitematicaPaths.telemetrySettingsPath(tempDir);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "{\"consent\":\"enabled\",\"installationId\":\"broken\"}");

        CompanionTelemetrySettings settings = CompanionTelemetrySettings.load(tempDir);

        assertEquals(CompanionTelemetrySettings.Consent.UNKNOWN, settings.consent());
        assertNull(settings.installationId());
    }

    @Test
    void doesNotSelectABackendBeforeConsentOrAfterDecline() throws Exception {
        CompanionTelemetrySettings settings = CompanionTelemetrySettings.load(tempDir);
        CompanionConfig unreachable = new CompanionConfig(
            "http://127.0.0.1:1", "anon", "https://mapkluss.art", "ru", "http://127.0.0.1:1"
        );
        CompanionTelemetryClient client = new CompanionTelemetryClient(unreachable, settings, "0.13.0", "1.21.11");

        client.record(CompanionTelemetryEvent.LAUNCH).get(100, TimeUnit.MILLISECONDS);
        settings.disable();
        client.record(CompanionTelemetryEvent.LAUNCH).get(100, TimeUnit.MILLISECONDS);
    }
}
