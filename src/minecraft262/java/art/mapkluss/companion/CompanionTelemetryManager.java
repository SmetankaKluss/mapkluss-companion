package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

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
        String modVersion = version(MapKlussCompanionClient.MOD_ID);
        String minecraftVersion = version("minecraft");
        telemetry = new CompanionTelemetryClient(config, settings, modVersion, minecraftVersion);
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

    private static void tick(Minecraft client) {
        if (promptShown || settings == null || settings.consent() != CompanionTelemetrySettings.Consent.UNKNOWN) return;
        if (!(client.gui.screen() instanceof TitleScreen current)) return;
        promptShown = true;
        client.gui.setScreen(new CompanionTelemetryConsentScreen(current));
    }

    private static String version(String id) {
        return FabricLoader.getInstance().getModContainer(id)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }
}
