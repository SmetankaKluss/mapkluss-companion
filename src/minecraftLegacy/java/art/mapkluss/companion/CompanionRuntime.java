package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.Path;

public final class CompanionRuntime {
    private final Path runDir;
    private final CompanionConfig config;
    private final CompanionSessionStore sessionStore;
    private final String backendUrl;

    private CompanionRuntime(Path runDir, CompanionConfig config, CompanionSessionStore sessionStore, String backendUrl) {
        this.runDir = runDir;
        this.config = config;
        this.sessionStore = sessionStore;
        this.backendUrl = backendUrl;
        if (sessionStore.hasAccessToken()) {
            this.config.setAccessToken(sessionStore.accessToken());
        }
    }

    public static CompanionRuntime create(MinecraftClient client) throws IOException {
        Path runDir = client.runDirectory.toPath();
        CompanionSessionStore sessionStore = CompanionSessionStore.load(runDir);
        CompanionConfig config = CompanionConfig.load(runDir);
        return new CompanionRuntime(runDir, config, sessionStore, CompanionBackendRouter.select(config));
    }

    public CompanionApiClient apiClient() {
        CompanionApiClient api = new CompanionApiClient(backendUrl, config.supabaseAnonKey());
        if (sessionStore.hasAccessToken()) api.setBearerToken(sessionStore.accessToken());
        return api;
    }

    public CompanionSyncService syncService() throws IOException {
        return CompanionSyncService.create(runDir, config, backendUrl);
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
        return backendUrl;
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
        sessionStore.clear();
        config.setAccessToken(null);
    }
}
