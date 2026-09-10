package art.mapkluss.companion;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LiveBuildSchematicTest {
    private static byte[] withMetadata(byte[] bytes, Map<String, SuppressionNbt.Tag> metadata) throws Exception {
        var document=SuppressionNbt.readCompressed(bytes);
        SuppressionNbt.compound(document.root(),"root").put("Metadata",SuppressionNbt.compoundTag(metadata));
        return SuppressionNbt.writeCompressed(document);
    }
    @Test void renamedTwoLayerExportStillRequiresPlan(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        byte[] source=withMetadata(SuppressionTestFixtures.litematicV3Bytes(),Map.of(
            "Author",SuppressionNbt.stringTag("MapKluss"),"Name",SuppressionNbt.stringTag("art_two_layer")));
        var path=directory.resolve("ordinary-name.litematic");
        java.nio.file.Files.write(path,source);
        assertThrows(LiveBuildSchematic.PhasePlanRequired.class,()->LiveBuildSchematic.read(path));
        var target=LiveBuildPhaseTarget.read(SuppressionTestFixtures.planV3Bytes(source),source,0);
        assertEquals(256,target.cells().stream().filter(LiveBuildProgress.Cell::requiresAir).count());
    }
    @Test void renamedGeneratedReferenceCannotBecomeOrdinaryTarget() throws Exception {
        byte[] source=withMetadata(SuppressionTestFixtures.litematicV3Bytes(),Map.of(
            "Name",SuppressionNbt.stringTag("MapKluss reference_after_01_of_64"),
            "Description",SuppressionNbt.stringTag("REFERENCE ONLY - ACTIVE REMOVALS ARE AIR")));
        assertThrows(LiveBuildSchematic.PhasePlanRequired.class,()->LiveBuildSchematic.read(source));
    }
    @Test void filenameAndGeometryAloneDoNotInventTwoLayerSemantics(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var path=directory.resolve("my_two_layer_build.litematic");
        java.nio.file.Files.write(path,SuppressionTestFixtures.litematicV3Bytes());
        assertEquals(16385,LiveBuildSchematic.read(path).cells().size());
    }
    @Test void otherProducerTitleDoesNotClaimMapKlussPhasePlan() throws Exception {
        byte[] source=withMetadata(SuppressionTestFixtures.litematicV3Bytes(),Map.of(
            "Author",SuppressionNbt.stringTag("Other"),"Name",SuppressionNbt.stringTag("art_two_layer")));
        assertEquals(16385,LiveBuildSchematic.read(source).cells().size());
    }
    @Test void recognizesLegacyMapKlussNorthRowWithoutGuessingOtherProducers() throws Exception {
        for(String author:List.of("MapKluss","other")){
            var region=SuppressionNbt.compoundTag(Map.of("Position",vector(0,0,0),"Size",vector(128,1,129),
                "BlockStatePalette",SuppressionNbt.listTag(10,List.of(
                    SuppressionNbt.compoundTag(Map.of("Name",SuppressionNbt.stringTag("minecraft:stone"))))),
                "BlockStates",SuppressionNbt.longArrayTag(new long[516])));
            var bytes=SuppressionNbt.writeCompressed(new SuppressionNbt.Document("",SuppressionNbt.compoundTag(Map.of(
                "Version",SuppressionNbt.intTag(6),"Regions",SuppressionNbt.compoundTag(Map.of("art",region)),
                "Metadata",SuppressionNbt.compoundTag(Map.of("Author",SuppressionNbt.stringTag(author)))))));
            var source=LiveBuildSchematic.read(bytes);
            assertEquals(129,source.bounds().depth());
            assertEquals(author.equals("MapKluss")?128:129,source.artBounds().depth());
            assertEquals(128*129,source.cells().size());
        }
    }
    private static SuppressionNbt.Tag vector(int x, int y, int z) {
        return SuppressionNbt.compoundTag(Map.of("x", SuppressionNbt.intTag(x), "y", SuppressionNbt.intTag(y), "z", SuppressionNbt.intTag(z)));
    }
    private static SuppressionNbt.Tag region(int x, int size, long packed) {
        return SuppressionNbt.compoundTag(Map.of("Position", vector(x, 0, 0), "Size", vector(size, 1, 1),
            "BlockStatePalette", SuppressionNbt.listTag(10, List.of(
                SuppressionNbt.compoundTag(Map.of("Name", SuppressionNbt.stringTag("minecraft:air"))),
                SuppressionNbt.compoundTag(Map.of("Name", SuppressionNbt.stringTag("minecraft:oak_log"),
                    "Properties", SuppressionNbt.compoundTag(Map.of("axis", SuppressionNbt.stringTag("x"))))))),
            "BlockStates", SuppressionNbt.longArrayTag(new long[]{packed})));
    }
    private static byte[] file(Map<String, SuppressionNbt.Tag> regions) throws IOException {
        return SuppressionNbt.writeCompressed(new SuppressionNbt.Document("", SuppressionNbt.compoundTag(Map.of(
            "Version", SuppressionNbt.intTag(6), "Regions", SuppressionNbt.compoundTag(regions)))));
    }
    @Test void importsNegativeDimensionsAndIgnoresAir() throws Exception {
        var result = LiveBuildSchematic.read(file(Map.of("r", region(5, -3, 17))));
        assertEquals(2, result.cells().size());
        assertEquals(3, result.cells().get(0).relativePosition().x());
        assertEquals(5, result.cells().get(1).relativePosition().x());
        assertEquals("x", result.cells().get(0).expected().properties().get("axis"));
        assertEquals(64, result.sha256().length());
        assertEquals(new LiveBuildSchematic.Bounds(3,0,0,5,0,0),result.bounds());
    }
    @Test void importedAirRegionsPreserveMapBoundaries() throws Exception {
        var source=LiveBuildSchematic.read(file(Map.of("empty",region(-10,1,0),"filled",region(121,1,1),"edge",region(245,1,0))));
        assertEquals(256,source.bounds().width());
        var parts=new LiveBuildParts(source);
        assertTrue(parts.part(0).empty());assertEquals(1,parts.part(1).cells().size());
        assertEquals(3,parts.part(1).cells().getFirst().relativePosition().x());
    }
    @Test void importsMultipleNonoverlappingRegionsAndRejectsAmbiguity() throws Exception {
        assertEquals(2, LiveBuildSchematic.read(file(Map.of("a", region(0, 1, 1), "b", region(10, 1, 1)))).cells().size());
        assertThrows(IOException.class, () -> LiveBuildSchematic.read(file(Map.of("a", region(0, 1, 1), "b", region(0, 1, 1)))));
    }
    @Test void rejectsInvalidPaletteIndexVolumeAndEmptyTarget() {
        assertThrows(IOException.class, () -> LiveBuildSchematic.read(file(Map.of("a", region(0, 1, 3)))));
        assertThrows(IOException.class, () -> LiveBuildSchematic.read(file(Map.of("a", region(0, 0, 0)))));
        assertThrows(IOException.class, () -> LiveBuildSchematic.read(file(Map.of("a", region(0, Integer.MIN_VALUE, 0)))));
        assertThrows(IOException.class, () -> LiveBuildSchematic.read(file(Map.of("a", region(0, 1, 0)))));
        assertThrows(IOException.class, () -> LiveBuildSchematic.read(new byte[LiveBuildSchematic.MAX_FILE_BYTES + 1]));
    }
}
