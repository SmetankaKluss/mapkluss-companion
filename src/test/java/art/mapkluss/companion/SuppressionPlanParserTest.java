package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionPlanParserTest {
    @Test
    void acceptsEveryReleasedMinecraftTarget() throws Exception {
        for (String minecraftVersion : SuppressionPlanParser.SUPPORTED_MINECRAFT_VERSIONS) {
            JsonObject root = JsonParser.parseString(new String(
                SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
                StandardCharsets.UTF_8
            )).getAsJsonObject();
            root.getAsJsonObject("target").addProperty("minecraftVersion", minecraftVersion);

            SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(
                root.toString().getBytes(StandardCharsets.UTF_8));
            assertEquals(minecraftVersion, parsed.plan().target().minecraftVersion());
        }
    }

    @Test
    void acceptsV2WithSeparateStructureWorkflowAndCanvasContracts() throws Exception {
        JsonObject root = v2PlanJson();

        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(
            root.toString().getBytes(StandardCharsets.UTF_8));

        assertEquals(2, parsed.plan().version());
        assertEquals(127, parsed.plan().effectiveStructureBounds().max().x());
        assertEquals(253, parsed.plan().effectiveWorkflowBounds().max().x());
        assertEquals(18_430, parsed.plan().canvas().blockCount());
    }

    @Test
    void acceptsV3WithoutAnyGeneratedCanvasContract() throws Exception {
        JsonObject root = v3PlanJson();

        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(
            root.toString().getBytes(StandardCharsets.UTF_8));

        assertEquals(3, parsed.plan().version());
        assertEquals(127, parsed.plan().effectiveStructureBounds().max().x());
        assertEquals(253, parsed.plan().effectiveWorkflowBounds().max().x());
        assertEquals(null, parsed.plan().canvas());
    }

    @Test
    void rejectsGeneratedCanvasMetadataInV3() throws Exception {
        JsonObject root = v2PlanJson();
        root.addProperty("version", 3);

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));

        assertTrue(error.getMessage().contains("must not contain"));
    }

    @Test
    void rejectsLegacyMarkerRolesInV2() throws Exception {
        JsonObject root = v2PlanJson();
        root.getAsJsonArray("palette").get(1).getAsJsonObject()
            .getAsJsonArray("roles").add("marker");

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("block role"));
    }

    @Test
    void parsesBoundedPlanAndReconstructsCompletedPhases() throws Exception {
        byte[] litematic = SuppressionTestFixtures.litematicBytes();
        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(SuppressionTestFixtures.planBytes(litematic));

        assertEquals(64, parsed.plan().phases().size());
        assertEquals(1, SuppressionStateLogic.expectedMapBytes(parsed, 0)[0]);
        assertEquals(1, SuppressionStateLogic.expectedMapBytes(parsed, 1)[0]);
        assertEquals(1, SuppressionStateLogic.expectedMapBytes(parsed, 64)[126]);
        assertEquals(256, SuppressionStateLogic.removalBlocks(parsed.plan().phases().get(0)).size());
    }

    @Test
    void rejectsRemovalRunOutsideDeclaredBounds() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.getAsJsonArray("phases").get(0).getAsJsonObject()
            .getAsJsonArray("removeRuns").get(0).getAsJsonObject().addProperty("length", 999);

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("removal run"));
    }

    @Test
    void rejectsMissingPhasesAsACheckedInputError() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.remove("phases");

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("64 phases"));
    }

    @Test
    void rejectsTheObsoleteFourBlockVerticalLayout() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.getAsJsonObject("bounds").getAsJsonObject("max").addProperty("y", 6);

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("bounds"));
    }

    @Test
    void acceptsBoundedAxisPropertiesAndRejectsUnknownProperties() throws Exception {
        JsonObject valid = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonObject properties = new JsonObject();
        properties.addProperty("axis", "y");
        valid.getAsJsonArray("palette").get(1).getAsJsonObject().add("properties", properties);

        SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(
            valid.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals("y", parsed.plan().palette().get(1).properties().get("axis"));

        properties.addProperty("powered", "true");
        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(valid.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("properties"));
    }

    @Test
    void rejectsNyliumThatCanDecayUnderTheUpperLayer() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.getAsJsonArray("palette").get(1).getAsJsonObject()
            .addProperty("state", "minecraft:crimson_nylium");

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("change while covered"));
    }

    @Test
    void rejectsDirtThatCanSpreadIntoAVisibleMapColour() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.getAsJsonArray("palette").get(1).getAsJsonObject()
            .addProperty("state", "minecraft:dirt");

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("can change"));
    }

    @Test
    void rejectsPixelRunsOutsideTheDominantParity() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.getAsJsonArray("phases").get(0).getAsJsonObject()
            .getAsJsonArray("updatePixelRuns").get(0).getAsJsonObject().addProperty("xStart", 2);

        java.io.IOException error = assertThrows(java.io.IOException.class,
            () -> SuppressionPlanParser.parse(root.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("dominant phase parity"));
    }

    @Test
    void requiresTwoCyclesAndEitherFreshPixelsOrAnAlreadyMatchingMap() {
        assertEquals(false, SuppressionStateLogic.dwellComplete(31, 32, true, false));
        assertEquals(false, SuppressionStateLogic.dwellComplete(32, 32, false, false));
        assertEquals(true, SuppressionStateLogic.dwellComplete(32, 32, true, false));
        assertEquals(true, SuppressionStateLogic.dwellComplete(32, 32, false, true));
    }

    @Test
    void capsVisibleDwellProgressAtTheRequiredTicks() {
        assertEquals(1, SuppressionStateLogic.nextDwellTick(0, 32));
        assertEquals(32, SuppressionStateLogic.nextDwellTick(31, 32));
        assertEquals(32, SuppressionStateLogic.nextDwellTick(32, 32));
        assertEquals(32, SuppressionStateLogic.nextDwellTick(999, 32));
    }

    @Test
    void acceptsMapUpdatesOnlyDuringManualCapture() {
        assertEquals(true, SuppressionStateLogic.acceptsCaptureUpdate(SuppressionStage.DWELL));
        assertEquals(false, SuppressionStateLogic.acceptsCaptureUpdate(SuppressionStage.MOVE));
    }

    @Test
    void reportsBaseColourAndShadeMismatchesSeparately() {
        byte[] expected = new byte[128 * 128];
        byte[] actual = expected.clone();
        expected[0] = 41;
        actual[0] = 42;
        expected[1] = 41;
        actual[1] = 45;

        SuppressionStateLogic.MapMismatch mismatch = SuppressionStateLogic.compareMaps(actual, expected);

        assertEquals(2, mismatch.total());
        assertEquals(1, mismatch.baseColor());
        assertEquals(1, mismatch.shade());
    }

    @Test
    void initialVerificationIgnoresDominantPixelsButProtectsRecessivePixels() {
        byte[] target = new byte[128 * 128];
        java.util.Arrays.fill(target, (byte) 41);
        byte[] actual = target.clone();
        actual[1] = 99; // (1,0) dominant: it will be rewritten by a later phase.

        assertEquals(true, SuppressionStateLogic.recessivePixelsMatchTarget(actual, target));

        actual[0] = 99; // (0,0) recessive: it must be correct before demolition.
        assertEquals(false, SuppressionStateLogic.recessivePixelsMatchTarget(actual, target));
        assertEquals(1, SuppressionStateLogic.compareRecessivePixels(actual, target).total());
    }

    @Test
    void verificationChecksOnlyFrozenAndAlreadyCompletedPixels() {
        byte[] target = new byte[128 * 128];
        java.util.Arrays.fill(target, (byte) 41);
        byte[] actual = target.clone();

        actual[5] = 99; // Future dominant pixel in x=5: phase 3, ignored after phase 1.
        assertEquals(true, SuppressionStateLogic.verifiedPixelsMatchTarget(actual, target, 1));

        actual[1] = 99; // Completed dominant pixel in x=1: phase 1.
        SuppressionStateLogic.VerificationMismatch completed =
            SuppressionStateLogic.compareVerifiedPixels(actual, target, 1);
        assertEquals(1, completed.total());
        assertEquals(0, completed.recessive());
        assertEquals(1, completed.completedDominant());

        actual[0] = 99; // Recessive pixel is frozen from the initial capture onward.
        SuppressionStateLogic.VerificationMismatch frozen =
            SuppressionStateLogic.compareVerifiedPixels(actual, target, 1);
        assertEquals(2, frozen.total());
        assertEquals(1, frozen.recessive());
        assertEquals(1, frozen.completedDominant());

        assertEquals(false, SuppressionStateLogic.verifiedPixelsMatchTarget(actual, target, 64));
    }

    @Test
    void recognizesScaleZeroNorthWestGridAtNegativeCoordinates() {
        assertEquals(true, SuppressionStateLogic.isScaleZeroNorthWest(-576, -1216));
        assertEquals(true, SuppressionStateLogic.isScaleZeroNorthWest(64, 64));
        assertEquals(false, SuppressionStateLogic.isScaleZeroNorthWest(-575, -1216));
        assertEquals(false, SuppressionStateLogic.isScaleZeroNorthWest(-576, -1215));
    }

    @Test
    void neverTrustsCompletedProgressFromTheUnsafeLegacySessionFormat() {
        assertEquals(SuppressionStage.PAUSED,
            SuppressionStateLogic.restoredStage(1, SuppressionStage.COMPLETE));
        assertEquals(SuppressionStage.PAUSED,
            SuppressionStateLogic.restoredStage(1, SuppressionStage.VERIFY));
        assertEquals(SuppressionStage.COMPLETE,
            SuppressionStateLogic.restoredStage(2, SuppressionStage.COMPLETE));
        assertEquals(SuppressionStage.MOVE,
            SuppressionStateLogic.restoredStage(3, SuppressionStage.DWELL));
        assertEquals(SuppressionStage.INITIAL_MOVE,
            SuppressionStateLogic.restoredStage(3, SuppressionStage.INITIAL_STOW));
        assertEquals(SuppressionStage.MOVE,
            SuppressionStateLogic.restoredStage(3, SuppressionStage.PAUSED));
        assertEquals(SuppressionStage.MOVE,
            SuppressionStateLogic.restoredStage(4, SuppressionStage.STOW));
    }

    private static JsonObject v2PlanJson() throws Exception {
        JsonObject root = JsonParser.parseString(new String(
            SuppressionTestFixtures.planBytes(SuppressionTestFixtures.litematicBytes()),
            StandardCharsets.UTF_8
        )).getAsJsonObject();
        root.addProperty("version", 2);
        JsonObject workflow = root.remove("bounds").getAsJsonObject();
        JsonObject structure = workflow.deepCopy();
        structure.getAsJsonObject("max").addProperty("x", 127);
        root.add("structureBounds", structure);
        root.add("workflowBounds", workflow);
        JsonObject canvas = new JsonObject();
        canvas.addProperty("schema", "mapkluss.two-layer-canvas");
        canvas.addProperty("version", 1);
        canvas.addProperty("material", "minecraft:cobblestone");
        JsonObject canvasBounds = new JsonObject();
        JsonObject min = new JsonObject();
        min.addProperty("x", 0); min.addProperty("y", 0); min.addProperty("z", 0);
        JsonObject max = new JsonObject();
        max.addProperty("x", 253); max.addProperty("y", 4); max.addProperty("z", 127);
        canvasBounds.add("min", min); canvasBounds.add("max", max);
        canvas.add("bounds", canvasBounds);
        canvas.addProperty("standSurfaceY", 4);
        canvas.addProperty("blockCount", 18_430);
        root.add("canvas", canvas);
        return root;
    }

    private static JsonObject v3PlanJson() throws Exception {
        JsonObject root = v2PlanJson();
        root.addProperty("version", 3);
        root.remove("canvas");
        return root;
    }
}
