package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildSourceCacheTest {
    @Test void ownedSourceKeepsValidationAndRecovery() throws Exception {
        var store = LiveBuildSourceCache.forRunDir(run);
        var kind = LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP;
        byte[] bytes = SuppressionTestFixtures.multiZipBytes();
        String expectedHash = SuppressionHashes.sha256(bytes);
        try (var imported = store.importOwnedBytes(bytes, kind)) {
            assertEquals(expectedHash, imported.reference().sha256());
            assertEquals(2, imported.bundle().tileCount());
            try (var recovered = store.load(imported.reference())) {
                assertEquals(imported.reference(), recovered.reference());
                assertEquals(2, recovered.bundle().tileCount());
            }
        }
        assertThrows(IOException.class, () -> store.importOwnedBytes(new byte[0], kind));
        assertThrows(IOException.class, () -> store.importOwnedBytes(new byte[]{1, 2, 3}, kind));
    }

    @Test void catalogRecoveryKeepsAllTilesAndIsDeterministic() throws Exception {
        var catalog=SuppressionBundleReader.readCatalog(SuppressionTestFixtures.multiZipBytes(),"private title");
        var store=LiveBuildSourceCache.forRunDir(run);
        LiveBuildSourceCache.Reference ref;
        try(var loaded=store.importCatalog(catalog)) {
            ref=loaded.reference();assertEquals(2,loaded.bundle().tileCount());
            for(int i=0;i<2;i++) {
                var expected=catalog.tiles().get(i).bundle();
                var actual=loaded.bundle().catalog().tiles().get(i).bundle();
                assertArrayEquals(expected.planBytes(),actual.planBytes());
                assertArrayEquals(expected.litematicBytes(),actual.litematicBytes());
                assertArrayEquals(expected.parsed().targetMapBytes(),actual.parsed().targetMapBytes());
            }
        }
        try(var second=store.importCatalog(catalog)){assertEquals(ref,second.reference());}
        try(var reopened=store.load(ref)){assertEquals(256,reopened.bundle().width());}
    }
    @Test void singleCatalogAndUntrustedMetadataAreRevalidated() throws Exception {
        var catalog=SuppressionBundleReader.readCatalog(SuppressionTestFixtures.multiZipBytes(),"fixture");
        var bundle=catalog.tiles().getFirst().bundle();
        var store=LiveBuildSourceCache.forRunDir(run);
        try(var single=store.importCatalog(SuppressionBundleCatalog.single(bundle))) {
            assertEquals(1,single.bundle().tileCount());
            assertEquals(bundle.planSha256(),single.bundle().catalog().tiles().getFirst().bundle().planSha256());
        }
        var invalid=new SuppressionBundleCatalog(null,null,"bad",null,null,1,2,catalog.tiles());
        assertThrows(IOException.class,()->store.importCatalog(invalid));
        var changed=new SuppressionBundle(bundle.parsed(),new byte[]{1},bundle.litematicBytes(),
            bundle.planSha256(),bundle.litematicSha256(),null,null,null,null);
        assertThrows(IOException.class,()->store.importCatalog(SuppressionBundleCatalog.single(changed)));
    }
    @TempDir Path run;
    private Path cache() { return run.resolve("config/mapkluss-companion/live-build-sources"); }
    @Test void litematicCanReopenAfterOriginalMovesAndCacheIsDeduplicated() throws Exception {
        var store = LiveBuildSourceCache.forRunDir(run);
        Path original = run.resolve("original.litematic"); Files.write(original,SuppressionTestFixtures.litematicV3Bytes());
        LiveBuildSourceCache.Reference ref;
        try (var loaded = store.importFile(original,LiveBuildSessionStore.SourceKind.LITEMATIC)) {
            ref=loaded.reference();assertEquals(loaded.schematic().sha256(),ref.sha256());
            assertNotNull(loaded.projection());assertNull(loaded.bundle());
        }
        try (var duplicate = store.importFile(original,LiveBuildSessionStore.SourceKind.LITEMATIC)) { assertEquals(ref,duplicate.reference()); }
        Files.move(original,run.resolve("renamed.litematic"));
        try (var reopened = store.load(ref)) { assertEquals(ref.sha256(),reopened.schematic().sha256()); }
        try (var files = Files.list(cache())) { assertEquals(1,files.count()); }
    }
    @Test void bundleReopensWithAllMapsAndValidatedPhaseSource() throws Exception {
        var store = LiveBuildSourceCache.forRunDir(run);
        LiveBuildSourceCache.Reference ref;
        try (var imported=store.importBytes(SuppressionTestFixtures.multiZipBytes(),LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP)) {
            ref=imported.reference();assertEquals(2,imported.bundle().tileCount());
        }
        try (var reopened=store.load(ref)) {
            assertEquals(256,reopened.bundle().width());assertEquals(128,reopened.bundle().height());
            var prepared=reopened.bundle().prepare(reopened.bundle().begin(1,0));
            assertEquals(256,prepared.target().cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
        }
    }
    @Test void corruptCacheIsNotOverwrittenAndMalformedImportCreatesNoSource() throws Exception {
        var store = LiveBuildSourceCache.forRunDir(run);
        assertThrows(IOException.class,()->store.importBytes(new byte[]{1,2,3},LiveBuildSessionStore.SourceKind.LITEMATIC));
        assertFalse(Files.exists(cache()));
        byte[] bytes=SuppressionTestFixtures.litematicV3Bytes();
        LiveBuildSourceCache.Reference ref;
        try(var imported=store.importBytes(bytes,LiveBuildSessionStore.SourceKind.LITEMATIC)){ref=imported.reference();}
        Path path=cache().resolve(ref.sha256()+".litematic");Files.write(path,new byte[]{4});
        assertThrows(IOException.class,()->store.load(ref));
        assertThrows(IOException.class,()->store.importBytes(bytes,LiveBuildSessionStore.SourceKind.LITEMATIC));
        assertArrayEquals(new byte[]{4},Files.readAllBytes(path));
        assertThrows(IllegalArgumentException.class,()->new LiveBuildSourceCache.Reference(ref.kind(),"../outside"));
    }
    @Test void sessionStopDoesNotRemoveItsRecoverySource() throws Exception {
        var sourceStore=LiveBuildSourceCache.forRunDir(run);
        try(var imported=sourceStore.importBytes(SuppressionTestFixtures.litematicV3Bytes(),LiveBuildSessionStore.SourceKind.LITEMATIC)){
            var ref=imported.reference();var session=LiveBuildSessionStore.forRunDir(run);
            session.save(new LiveBuildSessionStore.Snapshot(ref.kind(),ref.sha256(),"a".repeat(64),"minecraft:overworld",1,1,0,0,java.util.List.of()));
            session.clear();assertNull(session.load());
            try(var reopened=sourceStore.load(ref)){assertEquals(ref,reopened.reference());}
        }
    }
}
