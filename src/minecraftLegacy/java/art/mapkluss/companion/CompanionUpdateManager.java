package art.mapkluss.companion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;

import java.nio.file.Path;

final class CompanionUpdateManager {
    private static volatile CompanionReleaseChecker.Release pending;
    private static CompanionUpdateStore store;
    private static boolean registered;

    private CompanionUpdateManager() {
    }

    static synchronized void register() {
        if (registered) return;
        registered = true;

        Path gameDirectory = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        store = CompanionUpdateStore.load(LitematicaPaths.updateNoticePath(gameDirectory));
        String currentVersion = FabricLoader.getInstance().getModContainer(MapKlussCompanionClient.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("");

        new CompanionReleaseChecker().findUpdate(currentVersion)
            .thenAccept(release -> release.ifPresent(value -> pending = value));
        ClientTickEvents.END_CLIENT_TICK.register(CompanionUpdateManager::tick);
    }

    private static void tick(MinecraftClient client) {
        CompanionReleaseChecker.Release release = pending;
        Screen current = client.currentScreen;
        if (release == null || !(current instanceof TitleScreen)) return;
        pending = null;
        if (!store.shouldShow(release.version())) return;
        try {
            store.markShown(release.version());
        } catch (Exception error) {
            MapKlussCompanionClient.LOGGER.debug("Could not persist the update notice state.", error);
        }
        client.setScreen(new CompanionUpdateScreen(current, release.version()));
    }
}
