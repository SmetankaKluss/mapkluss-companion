package art.mapkluss.companion;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryPlacementSourceTest {
    @Test void artworkCornerMatchesTrackerAndKeepsNorthReferenceOutside() {
        var stone=new LiveBuildProgress.State("minecraft:stone",Map.of());
        var cells=List.of(
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,0),stone),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,0,1),stone),
            new LiveBuildProgress.Cell(new LiveBuildProgress.Position(0,3,2),stone));
        var source=new LiveBuildSchematic("a".repeat(64),cells,
            new LiveBuildSchematic.Bounds(0,0,0,127,3,128),
            new LiveBuildSchematic.Bounds(0,0,1,127,3,128));
        var prepared=new LibraryPlacementSource.Prepared(Path.of("fixture"),source.sha256(),null,null,
            new LiveBuildProgress.Position(0,0,1));
        var origin=prepared.schematicOrigin(-192,-60,-64);
        assertEquals(new LiveBuildProgress.Position(-192,-60,-65),origin);
        var part=new LiveBuildParts(source).part(0);
        var progress=new LiveBuildProgress(new LiveBuildProgress.Identity(part.targetSha256(),"test","minecraft:overworld",
            new LiveBuildProgress.Position(-192,-60,-64),1),part.cells());
        var world=new HashMap<LiveBuildProgress.Position,LiveBuildProgress.State>();
        for(var cell:cells) {
            var p=cell.relativePosition();
            world.put(new LiveBuildProgress.Position(origin.x()+p.x(),origin.y()+p.y(),origin.z()+p.z()),stone);
        }
        progress.scan(progress.identity(),world::get,3,10);
        assertEquals(3,progress.summary(10,1000).correct());
        var pixel=new LiveBuildProgress.Position(-192,-60,-64);
        world.put(pixel,new LiveBuildProgress.State("minecraft:dirt",Map.of()));
        progress.scan(progress.identity(),world::get,3,20);
        assertEquals(1,progress.summary(20,1000).wrong());
        world.put(pixel,stone);
        progress.scan(progress.identity(),world::get,3,30);
        assertEquals(3,progress.summary(30,1000).correct());
    }
    @Test void artworkOriginUsesDeclaredOffsetsNotAnUnconditionalNorthShift() {
        var unpadded=new LibraryPlacementSource.Prepared(Path.of("fixture"),"",null,null,new LiveBuildProgress.Position(0,0,0));
        assertEquals(new LiveBuildProgress.Position(16,64,16),unpadded.schematicOrigin(16,64,16));
        var shifted=new LibraryPlacementSource.Prepared(Path.of("fixture"),"",null,null,new LiveBuildProgress.Position(-7,3,9));
        assertEquals(new LiveBuildProgress.Position(23,61,7),shifted.schematicOrigin(16,64,16));
    }
    @TempDir Path run;
    static byte[] zip(Map<String,byte[]> files)throws IOException {
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out)){for(var e:files.entrySet()){
            zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();
        }}
        return out.toByteArray();
    }
    @Test void selectionUsesCoordinatesNotZipOrderAndRejectsAmbiguity()throws Exception {
        var files=new LinkedHashMap<String,byte[]>();
        files.put("mapart_4_2x2.litematic",new byte[]{4});files.put("mapart_2_2x1.litematic",new byte[]{2});
        assertArrayEquals(new byte[]{4},LibraryPlacementSource.selectTile(zip(files),new CompanionManifest.Grid(2,2),3));
        assertThrows(IOException.class,()->LibraryPlacementSource.selectTile(zip(files),new CompanionManifest.Grid(2,2),0));
        files.put("mapart_4.litematic",new byte[]{9});
        assertThrows(IOException.class,()->LibraryPlacementSource.selectTile(zip(files),new CompanionManifest.Grid(2,2),3));
    }
    static class Api extends CompanionApiClient {
        CompanionManifest manifest;Map<String,byte[]> payloads=new HashMap<>();List<String> downloaded=new ArrayList<>();
        Api(){super("http://127.0.0.1:1","fixture");}
        @Override public CompanionManifest manifest(String art,String version){return manifest;}
        @Override public byte[] downloadArtifactBounded(CompanionArtifact artifact,int max){downloaded.add(artifact.kind());return payloads.get(artifact.kind()).clone();}
        CompanionArtifact artifact(String kind,String name,byte[] bytes,String mime)throws IOException{
            payloads.put(kind,bytes);
            return new CompanionArtifact(kind,kind,name,null,"https://storage.yandexcloud.net/fixture",mime,bytes.length,SuppressionHashes.sha256(bytes),null);
        }
    }
    @Test void explicitTwoLayerPlacesItsSchemeEvenWhenOrdinaryExportExists()throws Exception {
        var api=new Api();byte[] schematic=SuppressionTestFixtures.litematicV3Bytes(),plan=SuppressionTestFixtures.planV3Bytes(schematic);
        var artifacts=List.of(
            api.artifact("litematic","ordinary.litematic",schematic,"application/octet-stream"),
            api.artifact("suppression_litematic",SuppressionPlanParser.parse(plan).plan().litematic().filename(),schematic,"application/octet-stream"),
            api.artifact("suppression_plan","plan.json",plan,"application/vnd.mapkluss.suppression-plan+json;version=3"));
        api.manifest=new CompanionManifest("art","v","owner","fixture","private",new CompanionManifest.Grid(1,1),"3d",
            "1.21.11","suppression_two_layer",null,false,List.of(),artifacts,null);
        var result=LibraryPlacementSource.prepare(api,run,"art","v",0);
        assertTrue(result.twoLayer());assertTrue(Files.exists(result.path()));
        assertEquals(SuppressionHashes.sha256(plan),result.planSha256());
        assertFalse(api.downloaded.contains("litematic"));
        assertThrows(IOException.class,()->LibraryPlacementSource.prepare(api,run,"art","old",0));
    }
    @Test void ordinaryPartIsStoredWithoutReplacingExistingSchematics()throws Exception {
        var api=new Api();byte[] tile=SuppressionTestFixtures.litematicV3Bytes();
        var artifact=api.artifact("litematic_tiles_zip","parts.zip",zip(Map.of("mapart_2_2x1.litematic",tile)),"application/zip");
        api.manifest=new CompanionManifest("art","v","owner","fixture","private",new CompanionManifest.Grid(2,1),"3d",
            "1.21.11","standard",null,false,List.of(),List.of(artifact),null);
        var result=LibraryPlacementSource.prepare(api,run,"art","v",1);
        assertFalse(result.twoLayer());assertArrayEquals(tile,Files.readAllBytes(result.path()));
        assertEquals(result.path(),LibraryPlacementSource.prepare(api,run,"art","v",1).path());
        assertThrows(IOException.class,()->LibraryPlacementSource.prepare(api,run,"art","v",2));
    }
}
