package art.mapkluss.companion;

import java.util.*;
import java.nio.file.Path;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TrackerCloudAssetsTest {
    @TempDir Path directory;
    static class Api extends CompanionApiClient {
        CompanionManifest value; int downloads; String requestedVersion; byte[] bytes;
        Api(){super("http://127.0.0.1:1","fixture");}
        @Override public CompanionManifest manifest(String id,String version){requestedVersion=version;return value;}
        @Override public byte[] downloadArtifactBounded(CompanionArtifact artifact,int max){downloads++;assertTrue(artifact.sizeBytes()<=max);return bytes.clone();}
    }
    CompanionManifest manifest(String version,List<CompanionArtifact> artifacts){
        return new CompanionManifest("art",version,"owner","fixture","private",new CompanionManifest.Grid(1,1),"2d",
            "1.21.11","ordinary","https://storage.yandexcloud.net/fixture/fresh.png",false,List.of(),artifacts,null);
    }
    @Test void expiredPersistedUrlIsNotUsedAndVersionIsPinned()throws Exception{
        var api=new Api();api.value=manifest("old-version",List.of());
        var session=new BuildSessionState("session",null,"https://storage.yandexcloud.net/expired.png",List.of(),Map.of(),Map.of(),"gathering",null,"art","old-version");
        assertEquals(api.value.previewUrl(),TrackerCloudAssets.preview(api,session));
        assertEquals("old-version",api.requestedVersion);
        api.value=manifest("different-version",List.of());
        assertThrows(IOException.class,()->TrackerCloudAssets.preview(api,session));
    }
    @Test void sourceDownloadsVerifiesAndReusesPrivateCache()throws Exception{
        var api=new Api();api.bytes=SuppressionTestFixtures.litematicV3Bytes();
        var artifact=new CompanionArtifact("source","litematic","fixture.litematic",null,"https://storage.yandexcloud.net/fixture","application/octet-stream",api.bytes.length,SuppressionHashes.sha256(api.bytes),null);
        api.value=manifest("version",List.of(artifact));
        var cache=LiveBuildSourceCache.forRunDir(directory);
        try(var loaded=TrackerCloudAssets.source(api,cache,"art","version")){assertNotNull(loaded.schematic());}
        try(var loaded=TrackerCloudAssets.source(api,cache,"art","version")){assertEquals(artifact.sha256(),loaded.reference().sha256());}
        assertEquals(1,api.downloads);
    }
    @Test void corruptCloudBytesCannotBecomeBuildSource()throws Exception{
        var api=new Api();api.bytes=new byte[]{1,2,3};
        api.value=manifest("version",List.of(new CompanionArtifact("source","litematic","fixture",null,"https://storage.yandexcloud.net/fixture","application/octet-stream",3,"0".repeat(64),null)));
        assertThrows(IOException.class,()->TrackerCloudAssets.source(api,LiveBuildSourceCache.forRunDir(directory),"art","version"));
    }
    private Api legacyPair(int wide)throws Exception{
        byte[] schematic=SuppressionTestFixtures.litematicV3Bytes();
        byte[] plan=SuppressionTestFixtures.planV3Bytes(schematic);
        String filename=SuppressionPlanParser.parse(plan).plan().litematic().filename();
        var api=new Api(){
            @Override public byte[] downloadArtifactBounded(CompanionArtifact artifact,int max){
                downloads++;return (artifact.isSuppressionPlan()?plan:schematic).clone();
            }
        };
        var artifacts=List.of(
            new CompanionArtifact("plan","suppression_plan","plan.json",null,"https://storage.yandexcloud.net/fixture","application/vnd.mapkluss.suppression-plan+json;version=3",plan.length,SuppressionHashes.sha256(plan),null),
            new CompanionArtifact("source","suppression_litematic",filename,null,"https://storage.yandexcloud.net/fixture","application/octet-stream",schematic.length,SuppressionHashes.sha256(schematic),null));
        api.value=new CompanionManifest("art","version","owner","fixture","private",new CompanionManifest.Grid(wide,1),"3d","1.21.11","suppression_two_layer",null,false,List.of(),artifacts,null);
        return api;
    }
    @Test void legacySingleMapPairBecomesCompleteRecoverableSource()throws Exception{
        var api=legacyPair(1);var cache=LiveBuildSourceCache.forRunDir(directory);
        try(var loaded=TrackerCloudAssets.source(api,cache,"art","version")){
            assertNotNull(loaded.bundle());assertEquals(1,loaded.bundle().tileCount());
            try(var recovered=cache.load(loaded.reference())){assertEquals(1,recovered.bundle().tileCount());}
        }
        assertEquals(2,api.downloads);
    }
    @Test void ordinaryInstallSourceWinsOverOptionalTwoLayerExports()throws Exception{
        var api=legacyPair(1);
        var old=api.value;
        byte[] schematic=SuppressionTestFixtures.litematicV3Bytes();
        var regular=new CompanionArtifact("ordinary","litematic","ordinary.litematic",null,
            "https://storage.yandexcloud.net/fixture","application/octet-stream",schematic.length,SuppressionHashes.sha256(schematic),null);
        var artifacts=new ArrayList<>(old.artifacts());artifacts.add(regular);
        api.value=new CompanionManifest(old.artId(),old.versionId(),old.ownerId(),old.title(),old.privacy(),old.grid(),
            "3d",old.minecraftVersion(),"ordinary",null,false,List.of(),artifacts,null);
        try(var loaded=TrackerCloudAssets.source(api,LiveBuildSourceCache.forRunDir(directory),"art","version")){
            assertNotNull(loaded.schematic());assertNull(loaded.bundle());
            assertEquals(regular.sha256(),loaded.reference().sha256());
            assertEquals(LiveBuildSessionStore.SourceKind.LITEMATIC,loaded.reference().kind());
        }
        assertEquals(1,api.downloads);
        api.value=new CompanionManifest(old.artId(),old.versionId(),old.ownerId(),old.title(),old.privacy(),old.grid(),
            "3d",old.minecraftVersion(),"suppression_two_layer",null,false,List.of(),artifacts,null);
        try(var loaded=TrackerCloudAssets.source(api,LiveBuildSourceCache.forRunDir(directory),"art","version")){
            assertNotNull(loaded.bundle());assertNull(loaded.schematic());
            assertEquals(LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP,loaded.reference().kind());
        }
    }
    @Test void legacySinglePartCannotPretendToBeACompleteMultiMapArt()throws Exception{
        var api=legacyPair(2);
        assertThrows(IOException.class,()->TrackerCloudAssets.source(api,LiveBuildSourceCache.forRunDir(directory),"art","version"));
        assertEquals(0,api.downloads);
    }
}
