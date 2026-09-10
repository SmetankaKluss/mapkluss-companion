package art.mapkluss.companion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapKlussCompanionClient implements ClientModInitializer {
    public static final String MOD_ID = "mapkluss-companion";
    public static final String UI_BUILD = "companion-ui-0.13.1-2026-08-20";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        MapKlussUiResources.register();
        LensRenderBridge.register(LensManager.instance());
        AutoFrameBridge.register(AutoFrameManager.instance());
        CompanionTelemetryManager.register();
        CompanionUpdateManager.register();
        LiveBuildClient.register();
        registerDevelopmentUiLab();
        registerDevelopmentLibraryHarness();
        registerDevelopmentControlDesk();
        LOGGER.info("MapKluss Companion initialized for Fabric client. UI build: {}.", UI_BUILD);
    }

    private static void registerDevelopmentUiLab() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        try {
            Class.forName("art.mapkluss.companion.MapKlussUiLab")
                .getMethod("register")
                .invoke(null);
        } catch (ClassNotFoundException ignored) {
            LOGGER.debug("MapKluss UI Lab is not enabled for this development run.");
        } catch (ReflectiveOperationException error) {
            LOGGER.warn("Could not initialize MapKluss UI Lab.", error);
        }
    }

    private static void registerDevelopmentLibraryHarness() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        try {
            Class.forName("art.mapkluss.companion.MapKlussLibraryHarness")
                .getMethod("register")
                .invoke(null);
        } catch (ClassNotFoundException ignored) {
            LOGGER.debug("MapKluss Library harness is not enabled for this development run.");
        } catch (ReflectiveOperationException error) {
            LOGGER.warn("Could not initialize the MapKluss Library harness.", error);
        }
    }

    private static void registerDevelopmentControlDesk() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        try {
            Class.forName("art.mapkluss.companion.MapKlussControlDesk")
                .getMethod("register")
                .invoke(null);
        } catch (ClassNotFoundException ignored) {
            LOGGER.debug("MapKluss Control Desk is not enabled for this development run.");
        } catch (ReflectiveOperationException error) {
            LOGGER.warn("Could not initialize the MapKluss Control Desk.", error);
        }
    }
}
