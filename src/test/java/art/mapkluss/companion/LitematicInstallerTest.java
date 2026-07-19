package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LitematicInstallerTest {
    private static final byte[] VALID_LITEMATIC_BYTES = Base64.getDecoder().decode(
        "H4sIAAAAAAAACu3dQW7TQBTG8c84ah0nVRWJBUdgDTt2kJYNCopo6LYaxa9hFGccPBOp7Y4jcAJuwPlYgh2FSBXqls3/t5v3ye+N5wKvlHI9n/lgy9bdpguX3LW10TdBOv+a6/TvSSelipklV7nkCg0+uo1psnHbdb2L8SaaVTev7l4XOnm7S1+aVsXMbT90WaHRhcVl67ep7zTQaOE3Nm3NJauk7MfLn99/DTTuqrOm8rf+WC51dhmWdRN9WF35B8uV3UmZcmX3krJc2YOUfVOu0Sdb+SZMm11IXTTUi4WvbdqE5Hyw9jIkn+77OO9ydUqd7j+L5T/+plQxb6Lv7r0frMNg7Qf3HQZPXmyoybu6Wa6vkks2d7WlZKWkZ4dHPNscnv+N860O5fNjOaYmmMYaHRtFaVx8BgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/92w27lQW78YwVvsthJoqOLReTK3UPmw6pcPLPxy/Th4X+98dQz++A3HAyFiV2IAAA=="
    );

    @TempDir
    Path tempDir;

    @Test
    void renamesExistingInstallWhenCanonicalFilenameChanges() throws Exception {
        byte[] bytes = VALID_LITEMATIC_BYTES;
        String sha = sha256(bytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed.json"));
        LitematicInstaller installer = new LitematicInstaller(tempDir.resolve("schematics"), index);
        CompanionManifest oldManifest = manifest("Castle 01", "castle_01_2x3.litematic", sha);
        CompanionArtifact oldArtifact = oldManifest.litematicArtifact().orElseThrow();

        InstalledArtifact first = installer.install(oldManifest, oldArtifact, bytes);
        assertTrue(Files.exists(Path.of(first.path())));

        CompanionManifest renamedManifest = manifest("Castle Final", "castle_final_2x3.litematic", sha);
        InstalledArtifact second = installer.install(renamedManifest, renamedManifest.litematicArtifact().orElseThrow(), bytes);

        assertEquals("castle_final_2x3.litematic", second.filename());
        assertTrue(Files.exists(Path.of(second.path())));
        assertFalse(Files.exists(Path.of(first.path())));
        assertEquals(1, index.entries().size());
    }

    @Test
    void usesConflictSuffixWhenCanonicalFilenameBelongsToDifferentFile() throws Exception {
        byte[] installedBytes = VALID_LITEMATIC_BYTES;
        String installedSha = sha256(installedBytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed-conflict.json"));
        Path schematics = tempDir.resolve("schematics");
        Files.createDirectories(schematics);
        Files.write(schematics.resolve("castle_final_2x3.litematic"), "someone-else".getBytes());

        LitematicInstaller installer = new LitematicInstaller(schematics, index);
        CompanionManifest manifest = manifest("Castle Final", "castle_final_2x3.litematic", installedSha);
        CompanionArtifact artifact = manifest.litematicArtifact().orElseThrow();

        InstalledArtifact installed = installer.install(manifest, artifact, installedBytes);

        assertEquals("castle_final_2x3_01.litematic", installed.filename());
        assertTrue(Files.exists(Path.of(installed.path())));
        assertTrue(Files.exists(schematics.resolve("castle_final_2x3.litematic")));
        assertEquals(1, index.entries().size());
    }

    @Test
    void incrementsNumberedConflictSuffixes() throws Exception {
        byte[] installedBytes = VALID_LITEMATIC_BYTES;
        String installedSha = sha256(installedBytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed-conflict-numbered.json"));
        Path schematics = tempDir.resolve("schematics");
        Files.createDirectories(schematics);
        Files.write(schematics.resolve("castle_final_2x3.litematic"), "someone-else".getBytes());
        Files.write(schematics.resolve("castle_final_2x3_01.litematic"), "another-file".getBytes());

        LitematicInstaller installer = new LitematicInstaller(schematics, index);
        CompanionManifest manifest = manifest("Castle Final", "castle_final_2x3.litematic", installedSha);

        InstalledArtifact installed = installer.install(manifest, manifest.litematicArtifact().orElseThrow(), installedBytes);

        assertEquals("castle_final_2x3_02.litematic", installed.filename());
        assertTrue(Files.exists(Path.of(installed.path())));
    }

    @Test
    void reusesExistingInstallWithoutCreatingDuplicateIndexEntry() throws Exception {
        byte[] bytes = VALID_LITEMATIC_BYTES;
        String sha = sha256(bytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed-repeat.json"));
        LitematicInstaller installer = new LitematicInstaller(tempDir.resolve("schematics"), index);
        CompanionManifest manifest = manifest("Castle 01", "castle_01_2x3.litematic", sha);
        CompanionArtifact artifact = manifest.litematicArtifact().orElseThrow();

        InstalledArtifact first = installer.install(manifest, artifact, bytes);
        InstalledArtifact second = installer.install(manifest, artifact, bytes);

        assertEquals(first.path(), second.path());
        assertEquals(first.filename(), second.filename());
        assertEquals(1, index.entries().size());
    }

    @Test
    void fallsBackToLitematicExtensionWhenArtifactFilenameIsWrong() throws Exception {
        byte[] bytes = VALID_LITEMATIC_BYTES;
        String sha = sha256(bytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed-extension.json"));
        LitematicInstaller installer = new LitematicInstaller(tempDir.resolve("schematics"), index);
        CompanionManifest manifest = manifest("Castle Final", "castle_final_2x3.png", sha);

        InstalledArtifact installed = installer.install(manifest, manifest.litematicArtifact().orElseThrow(), bytes);

        assertEquals("castle_final_2x3.litematic", installed.filename());
        assertTrue(Files.exists(Path.of(installed.path())));
    }

    @Test
    void ignoresCorruptIndexPathOutsideSchematicFolderWhenInstalling() throws Exception {
        byte[] bytes = VALID_LITEMATIC_BYTES;
        String sha = sha256(bytes);
        Path outside = tempDir.resolve("outside.litematic");
        Files.write(outside, bytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed-corrupt.json"));
        index.upsert(new InstalledArtifact("art", "artifact", sha, outside.toString(), outside.getFileName().toString(), 1L));
        LitematicInstaller installer = new LitematicInstaller(tempDir.resolve("schematics"), index);
        CompanionManifest manifest = manifest("Castle 01", "castle_01_2x3.litematic", sha);

        InstalledArtifact installed = installer.install(manifest, manifest.litematicArtifact().orElseThrow(), bytes);

        assertEquals("castle_01_2x3.litematic", installed.filename());
        assertTrue(Files.exists(outside));
        assertTrue(Path.of(installed.path()).toAbsolutePath().normalize().startsWith(tempDir.resolve("schematics").toAbsolutePath().normalize()));
        assertEquals(1, index.entries().size());
    }

    @Test
    void uninstallRemovesIndexButDoesNotDeleteOutsideSchematicFolder() throws Exception {
        byte[] bytes = VALID_LITEMATIC_BYTES;
        String sha = sha256(bytes);
        Path outside = tempDir.resolve("outside-delete-me-not.litematic");
        Files.write(outside, bytes);
        InstalledArtifactIndex index = InstalledArtifactIndex.load(tempDir.resolve("installed-outside-delete.json"));
        index.upsert(new InstalledArtifact("art", "artifact", sha, outside.toString(), outside.getFileName().toString(), 1L));
        LitematicInstaller installer = new LitematicInstaller(tempDir.resolve("schematics"), index);

        assertTrue(installer.uninstall("art", "artifact"));

        assertTrue(Files.exists(outside));
        assertEquals(0, index.entries().size());
    }

    private static CompanionManifest manifest(String title, String filename, String sha) {
        CompanionArtifact artifact = new CompanionArtifact(
            "artifact",
            "litematic",
            filename,
            "storage/" + filename,
            null,
            "application/octet-stream",
            VALID_LITEMATIC_BYTES.length,
            sha,
            "now"
        );
        return new CompanionManifest(
            "art",
            "version",
            "owner",
            title,
            "unlisted",
            new CompanionManifest.Grid(2, 3),
            "2d",
            "1.21.11",
            "standard",
            null,
            false,
            List.of(),
            List.of(artifact),
            "now"
        );
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
