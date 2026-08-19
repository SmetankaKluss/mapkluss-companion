package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

import java.nio.file.Path;

final class CompanionTelemetryManager {
    private static CompanionTelemetrySettings settings;
    private static CompanionTelemetryClient telemetry;
    private static boolean registered;
    private static boolean launchRecorded;
    private static boolean promptShown;

    private CompanionTelemetryManager() { }

    static synchronized void register() {
        if (registered) return;
        registered = true;
        Path runDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        settings = CompanionTelemetrySettings.load(runDir);
        CompanionConfig config;
        try {
            config = CompanionConfig.load(runDir);
        } catch (Exception error) {
            config = new CompanionConfig();
        }
        telemetry = new CompanionTelemetryClient(
            config, settings, version(MapKlussCompanionClient.MOD_ID), version("minecraft")
        );
        if (settings.enabled()) recordLaunch();
        ClientTickEvents.END_CLIENT_TICK.register(CompanionTelemetryManager::tick);
    }

    static CompanionTelemetrySettings.Consent consent() {
        return settings == null ? CompanionTelemetrySettings.Consent.UNKNOWN : settings.consent();
    }

    static void enable() throws Exception {
        settings.enable();
        recordLaunch();
    }

    static void disable() throws Exception {
        settings.disable();
    }

    static void record(CompanionTelemetryEvent event) {
        if (telemetry != null && settings != null && settings.enabled()) telemetry.record(event);
    }

    private static void recordLaunch() {
        if (launchRecorded) return;
        launchRecorded = true;
        record(CompanionTelemetryEvent.LAUNCH);
    }

    private static void tick(MinecraftClient client) {
        if (promptShown || settings == null || settings.consent() != CompanionTelemetrySettings.Consent.UNKNOWN) return;
        if (!(client.currentScreen instanceof TitleScreen current)) return;
        promptShown = true;
        client.setScreen(new CompanionTelemetryConsentScreen(current));
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }
}
