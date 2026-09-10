package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

public final class CompanionRuntime {
    private final Path runDir;
    private final CompanionConfig config;
    private final CompanionSessionStore sessionStore;

    private CompanionRuntime(Path runDir, CompanionConfig config, CompanionSessionStore sessionStore) {
        this.runDir = runDir;
        this.config = config;
        this.sessionStore = sessionStore;
        if (sessionStore.hasAccessToken()) {
            this.config.setAccessToken(sessionStore.accessToken());
        }
    }

    public static CompanionRuntime create(Minecraft client) throws IOException {
        Path runDir = client.gameDirectory.toPath();
        CompanionSessionStore sessionStore = CompanionSessionStore.load(runDir);
        CompanionConfig config = CompanionConfig.load(runDir);
        return new CompanionRuntime(runDir, config, sessionStore);
    }

    public CompanionApiClient apiClient() {
        CompanionApiClient api = new CompanionApiClient(config);
        if (sessionStore.hasAccessToken()) api.setBearerToken(sessionStore.accessToken());
        return api;
    }

    public CompanionSyncService syncService() throws IOException {
        return CompanionSyncService.create(runDir, config);
    }

    public LibraryCache libraryCache() throws IOException {
        return LibraryCache.load(LitematicaPaths.libraryCachePath(runDir));
    }

    public ManifestCache manifestCache() throws IOException {
        return ManifestCache.load(LitematicaPaths.manifestCachePath(runDir));
    }

    public CompanionConfig config() {
        return config;
    }

    public String backendUrl() {
        return CompanionBackendRouter.select(config);
    }

    public Path schematicDir() {
        return LitematicaPaths.defaultSchematicDir(runDir);
    }

    public LitematicaStatus litematicaStatus() {
        return LitematicaPaths.detectLitematica(runDir);
    }

    public CompanionSessionStore sessionStore() {
        return sessionStore;
    }

    public CompanionSessionInfo sessionInfo() {
        return sessionStore.sessionInfo();
    }

    public void saveSession(String accessToken, String userId) throws IOException {
        LiveBuildClient.instance().groups().clear();
        sessionStore.saveSession(accessToken, userId);
        config.setAccessToken(accessToken);
    }

    public String revokeAndClearSession() throws IOException {
        String warning = null;
        if (sessionStore.hasAccessToken()) {
            try {
                CompanionApiClient api = apiClient();
                api.logoutDeviceSession();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                MapKlussCompanionClient.LOGGER.warn("Remote session revoke was interrupted.", e);
                warning = "Не удалось завершить удалённую сессию.";
            } catch (Exception e) {
                MapKlussCompanionClient.LOGGER.warn("Remote session revoke failed.", e);
                warning = "Не удалось завершить удалённую сессию.";
            }
        }
        clearSession();
        return warning;
    }

    public void clearSession() throws IOException {
        LiveBuildClient.instance().groups().clear();
        sessionStore.clear();
        config.setAccessToken(null);
    }
}
