package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigOnFirstLoad() throws Exception {
        CompanionConfig config = CompanionConfig.load(tempDir);
        Path configPath = LitematicaPaths.companionConfigPath(tempDir);

        assertTrue(Files.exists(configPath));
        assertEquals(CompanionConfig.DEFAULT_SUPABASE_URL, config.supabaseUrl());
        assertEquals(CompanionConfig.DEFAULT_GATEWAY_URL, config.gatewayUrl());
        assertEquals(CompanionConfig.DEFAULT_SITE_URL, config.siteUrl());
        assertEquals(CompanionConfig.DEFAULT_LANGUAGE, config.language());
    }

    @Test
    void loadsCustomConfigAndBuildsSiteUris() throws Exception {
        Path configPath = LitematicaPaths.companionConfigPath(tempDir);
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
            {
              "supabaseUrl": "https://example.supabase.co/",
              "supabaseAnonKey": "anon-test",
              "gatewayUrl": "https://gateway.example/",
              "siteUrl": "https://staging.mapkluss.test/",
              "language": "en"
            }
            """, StandardCharsets.UTF_8);

        CompanionConfig config = CompanionConfig.load(tempDir);

        assertEquals("https://example.supabase.co", config.supabaseUrl());
        assertEquals("anon-test", config.supabaseAnonKey());
        assertEquals("https://gateway.example", config.gatewayUrl());
        assertEquals("https://staging.mapkluss.test", config.siteUrl());
        assertEquals("en", config.language());
        assertEquals("https://staging.mapkluss.test/art/art-1", config.siteUri("art/art-1").toString());
        assertEquals("https://staging.mapkluss.test/cloud", config.siteUri("/cloud").toString());
    }

    @Test
    void migratesLocalhostSiteUrlToProduction() throws Exception {
        Path configPath = LitematicaPaths.companionConfigPath(tempDir);
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
            {
              "supabaseUrl": "https://example.supabase.co/",
              "supabaseAnonKey": "anon-test",
              "siteUrl": "http://localhost:5173/",
              "language": "en"
            }
            """, StandardCharsets.UTF_8);

        CompanionConfig config = CompanionConfig.load(tempDir);
        CompanionConfig reloaded = CompanionConfig.load(tempDir);

        assertEquals(CompanionConfig.DEFAULT_SITE_URL, config.siteUrl());
        assertEquals(CompanionConfig.DEFAULT_SITE_URL, reloaded.siteUrl());
        assertEquals("en", reloaded.language());
    }

    @Test
    void savesLanguageWithoutChangingEndpointSettings() throws Exception {
        CompanionConfig config = new CompanionConfig(
            "https://example.supabase.co/",
            "anon-test",
            "https://staging.mapkluss.test/",
            "en",
            "https://gateway.example/"
        );

        config.saveForRunDir(tempDir);
        CompanionConfig reloaded = CompanionConfig.load(tempDir);

        assertEquals("https://example.supabase.co", reloaded.supabaseUrl());
        assertEquals("https://gateway.example", reloaded.gatewayUrl());
        assertEquals("anon-test", reloaded.supabaseAnonKey());
        assertEquals("https://staging.mapkluss.test", reloaded.siteUrl());
        assertEquals("en", reloaded.language());
    }
}
