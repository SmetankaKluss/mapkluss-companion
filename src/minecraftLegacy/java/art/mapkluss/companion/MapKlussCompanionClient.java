package art.mapkluss.companion;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapKlussCompanionClient implements ClientModInitializer {
    public static final String MOD_ID = "mapkluss-companion";
    public static final String UI_BUILD = "two-layer-builder-2026-07-16";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LensRenderBridge.register(LensManager.instance());
        AutoFrameBridge.register(AutoFrameManager.instance());
        LOGGER.info("MapKluss Companion initialized for Fabric client. UI build: {}.", UI_BUILD);
    }
}
