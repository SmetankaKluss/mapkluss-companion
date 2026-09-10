package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LiveBuildSessionStoreTest {
    @TempDir Path run;
    private static final String WORLD = "a".repeat(64), SOURCE = "b".repeat(64);
    private static final String DIMENSION = "minecraft:overworld";
    private LiveBuildProgress.Identity identity(int part) {
        return new LiveBuildProgress.Identity((part == 0 ? "c" : "d").repeat(64), WORLD, DIMENSION,
            new LiveBuildProgress.Position(part * 1000, 64, -32), 2, new LiveBuildTransform(part, true));
    }
    private LiveBuildSessionStore.Placement placement(int tile, byte[] states) {
        return new LiveBuildSessionStore.Placement(tile, -1, false, identity(tile), states);
    }
    private LiveBuildSessionStore.Snapshot snapshot(List<LiveBuildSessionStore.Placement> placements) {
        return new LiveBuildSessionStore.Snapshot(LiveBuildSessionStore.SourceKind.LITEMATIC,
            SOURCE, WORLD, DIMENSION, 2, 1, 1, 123456, placements);
    }
    private Path file() { return run.resolve("config/mapkluss-companion/live-build-session.bin"); }
    @Test void v3KeepsOnlyExactConsentedGroupAssociationsAndReadsV2() throws Exception {
        var local=placement(0,new byte[]{1,2});var id=local.identity();
        String groupId="11111111-1111-4111-8111-111111111111";
        var remote=new LiveBuildSharedPlacement(0,5,id.schematicSha256(),-1,2,groupId,DIMENSION,id.origin(),id.transform());
        var link=new LiveBuildSessionStore.GroupLink(groupId,7,List.of(remote));
        var input=snapshot(List.of(local)).withGroup(link);
        assertEquals(link,LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(input)).group());
        assertThrows(IllegalArgumentException.class,()->snapshot(List.of(placement(0,new byte[]{1}))).withGroup(link));
        byte[] current=LiveBuildSessionStore.encode(snapshot(List.of(local)));
        // V2 predates both the optional-group and optional-Cloud flags.
        byte[] old=Arrays.copyOf(current,current.length-2);old[7]=2;resign(old);
        var restored=LiveBuildSessionStore.decode(old);
        assertNull(restored.group());assertArrayEquals(local.states(),restored.placements().getFirst().states());
    }
    @Test void roundTripKeepsIndependentMapsAndDefensiveObservations() throws Exception {
        byte[] states = {0,1,2,3};
        var first = placement(0, states); states[1] = 3;
        first.states()[1] = 3;
        var input = snapshot(List.of(first, placement(1, new byte[]{3,1})));
        var output = LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(input));
        assertEquals(input.kind(), output.kind()); assertEquals(SOURCE, output.sourceSha256());
        assertEquals(WORLD, output.worldHash()); assertEquals(DIMENSION, output.dimension());
        assertEquals(2, output.gridWide()); assertEquals(1, output.gridTall()); assertEquals(1, output.selected());
        assertEquals(123456, output.savedAt()); assertEquals(2, output.placements().size());
        assertEquals(identity(0), output.placements().get(0).identity());
        assertEquals(identity(1), output.placements().get(1).identity());
        assertArrayEquals(new byte[]{0,1,2,3}, output.placements().get(0).states());
    }
    @Test void twoLayerPhaseAndBindingSurviveWithoutImplicitPhaseConversion() throws Exception {
        var part = new LiveBuildSessionStore.Placement(1, 63, true, identity(1), new byte[]{1,3});
        var input = new LiveBuildSessionStore.Snapshot(LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP,
            SOURCE, WORLD, DIMENSION, 2, 1, 1, 0, List.of(part));
        var output = LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(input));
        assertEquals(63, output.placements().get(0).phase()); assertTrue(output.placements().get(0).followsTwoLayer());
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(part)));
    }
    @Test void rejectsCorruptionTruncationOversizeAndFutureFormat() throws Exception {
        byte[] good = LiveBuildSessionStore.encode(snapshot(List.of(placement(0, new byte[]{1}))));
        for (int length : new int[]{0, 8, good.length-1})
            assertThrows(IOException.class, () -> LiveBuildSessionStore.decode(Arrays.copyOf(good, length)));
        byte[] corrupt = good.clone(); corrupt[20] ^= 1;
        assertThrows(IOException.class, () -> LiveBuildSessionStore.decode(corrupt));
        assertThrows(IOException.class, () -> LiveBuildSessionStore.decode(new byte[LiveBuildSessionStore.MAX_BYTES+1]));
        byte[] future = good.clone(); future[7] = (byte)(LiveBuildSessionStore.VERSION+1); resign(future);
        assertThrows(IOException.class, () -> LiveBuildSessionStore.decode(future));
        byte[] invalidState = good.clone(); invalidState[invalidState.length-33] = 4; resign(invalidState);
        assertThrows(IOException.class, () -> LiveBuildSessionStore.decode(invalidState));
    }
    @Test void rejectsDuplicateMapsWrongWorldAndAggregateBudget() {
        var first = placement(0, new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(first, first)));
        var other = new LiveBuildProgress.Identity("c".repeat(64), "e".repeat(64), DIMENSION,
            new LiveBuildProgress.Position(0,0,0), 1);
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(
            new LiveBuildSessionStore.Placement(0,-1,false,other,new byte[]{1}))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(
            placement(0,new byte[1_000_001]),placement(1,new byte[1_000_000]))));
        assertThrows(IllegalArgumentException.class, () -> placement(0, new byte[]{4}));
        assertThrows(IllegalArgumentException.class, () -> placement(0, new byte[]{-1}));
    }
    @Test void maximumObservationPayloadRoundTripsWithinFileBudget() throws Exception {
        byte[] states = new byte[LiveBuildProgress.MAX_CELLS]; Arrays.fill(states,(byte)3);
        byte[] encoded = LiveBuildSessionStore.encode(snapshot(List.of(placement(0,states))));
        assertTrue(encoded.length < LiveBuildSessionStore.MAX_BYTES);
        assertArrayEquals(states, LiveBuildSessionStore.decode(encoded).placements().get(0).states());
    }
    @Test void denseHundredMapBundleKeepsEveryObservationInCompactPayload() throws Exception {
        var parts = new ArrayList<LiveBuildSessionStore.Placement>();
        byte[] states = new byte[32769];
        for (int i = 0; i < states.length; i++) states[i] = (byte)(i % 4);
        for (int tile = 0; tile < 100; tile++)
            parts.add(new LiveBuildSessionStore.Placement(tile, 2, tile == 73, identity(0), states));
        var input = new LiveBuildSessionStore.Snapshot(LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP,
            SOURCE, WORLD, DIMENSION, 10, 10, 73, 42, parts);
        byte[] bytes = LiveBuildSessionStore.encode(input);
        assertTrue(bytes.length < 850_000);
        var output = LiveBuildSessionStore.decode(bytes);
        assertEquals(100, output.placements().size());
        assertEquals(73, output.selected());
        for (int tile = 0; tile < 100; tile++) {
            assertEquals(tile, output.placements().get(tile).tile());
            assertArrayEquals(states, output.placements().get(tile).states());
            assertEquals(tile == 73, output.placements().get(tile).followsTwoLayer());
        }
    }
    @Test void readsLegacyRawStatesAndUpgradesWithoutChangingIdentity() throws Exception {
        // Independent v1 wire fixture, not the current encoder with a changed version byte.
        var buffer = new java.io.ByteArrayOutputStream();
        var out = new java.io.DataOutputStream(buffer);
        out.writeInt(0x4d4b4254); out.writeInt(1); out.writeByte(0);
        out.writeUTF(SOURCE); out.writeUTF(WORLD); out.writeUTF(DIMENSION);
        out.writeInt(2); out.writeInt(1); out.writeInt(0); out.writeLong(7); out.writeInt(1);
        out.writeInt(0); out.writeInt(-1); out.writeBoolean(false); out.writeUTF("c".repeat(64));
        out.writeInt(0); out.writeInt(64); out.writeInt(-32); out.writeLong(2);
        out.writeByte(0); out.writeBoolean(true); out.writeInt(5); out.write(new byte[]{0,1,2,3,1});
        out.write(new byte[32]);
        byte[] legacy = buffer.toByteArray(); resign(legacy);
        var restored = LiveBuildSessionStore.decode(legacy);
        assertEquals(identity(0), restored.placements().get(0).identity());
        assertArrayEquals(new byte[]{0,1,2,3,1}, restored.placements().get(0).states());
        var upgraded = LiveBuildSessionStore.decode(LiveBuildSessionStore.encode(restored));
        assertArrayEquals(restored.placements().get(0).states(), upgraded.placements().get(0).states());
        legacy[legacy.length - 33] = 4; resign(legacy);
        assertThrows(IOException.class, () -> LiveBuildSessionStore.decode(legacy));
    }
    @Test void bundleObservationBudgetRemainsBounded() {
        var parts = new ArrayList<LiveBuildSessionStore.Placement>();
        for (int tile = 0; tile < 4; tile++)
            parts.add(new LiveBuildSessionStore.Placement(tile, 0, false, identity(0), new byte[2_000_000]));
        assertDoesNotThrow(() -> new LiveBuildSessionStore.Snapshot(LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP,
            SOURCE, WORLD, DIMENSION, 5, 1, 0, 0, parts));
        parts.add(new LiveBuildSessionStore.Placement(4, 0, false, identity(0), new byte[]{0}));
        assertThrows(IllegalArgumentException.class, () -> new LiveBuildSessionStore.Snapshot(
            LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP, SOURCE, WORLD, DIMENSION, 5, 1, 0, 0, parts));
    }
    @Test void privateAtomicStoreReplacesAndLeavesNoTemporaryFiles() throws Exception {
        var store = LiveBuildSessionStore.forRunDir(run); assertNull(store.load());
        store.save(snapshot(List.of(placement(0,new byte[]{1}))));
        store.save(snapshot(List.of(placement(1,new byte[]{2}))));
        assertEquals(1,store.load().placements().get(0).tile());
        try (var files = Files.list(file().getParent())) { assertEquals(1,files.count()); }
        byte[] damaged = Files.readAllBytes(file()); damaged[0] ^= 1; Files.write(file(), damaged);
        assertThrows(IOException.class,store::load);
        assertArrayEquals(damaged,Files.readAllBytes(file()));
    }
    @Test void failedAtomicReplacePreservesExistingDirectoryAndCleansOnlyOwnTemporary() throws Exception {
        Files.createDirectories(file());
        Path unrelated = file().resolve("keep.txt"); Files.writeString(unrelated,"keep");
        var store = LiveBuildSessionStore.forRunDir(run);
        assertThrows(IOException.class, () -> store.save(snapshot(List.of())));
        assertEquals("keep",Files.readString(unrelated));
        try (var files = Files.list(file().getParent())) { assertEquals(1,files.count()); }
    }
    @Test void restoredObservationsAreStaleUntilEachCellIsRescanned() {
        var stone = new LiveBuildProgress.State("minecraft:stone",Map.of());
        var air = new LiveBuildProgress.State("minecraft:air",Map.of());
        var dirt = new LiveBuildProgress.State("minecraft:dirt",Map.of());
        var cells = new ArrayList<LiveBuildProgress.Cell>();
        for(int i=0;i<4;i++)cells.add(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(i,0,0),stone));
        var original = new LiveBuildProgress(identity(0),cells);
        original.scan(identity(0),p->p.x()==0?stone:p.x()==1?air:p.x()==2?dirt:null,4,100_000);
        var restored = new LiveBuildProgress(identity(0),cells);
        restored.restoreStates(identity(0),original.savedStates());
        assertEquals(0,restored.liveSummary(0).correct());assertEquals(3,restored.liveSummary(0).stale());
        assertEquals(1,restored.liveSummary(0).unknown());
        assertEquals(LiveBuildProgress.Status.CORRECT,restored.observation(0,0,120000).lastKnown());
        assertEquals(LiveBuildProgress.Status.STALE,restored.observation(0,0,120000).status());
        assertEquals(-1,restored.observation(0,0,120000).observedAt());
        restored.scan(identity(0),p->stone,1,0);
        assertEquals(1,restored.liveSummary(0).correct());assertEquals(2,restored.liveSummary(0).stale());
        assertThrows(IllegalArgumentException.class, () -> restored.restoreStates(identity(0),original.savedStates()));
        var fresh = new LiveBuildProgress(identity(0),cells);
        assertThrows(IllegalArgumentException.class, () -> fresh.restoreStates(identity(1),original.savedStates()));
        assertThrows(IllegalArgumentException.class, () -> fresh.restoreStates(identity(0),new byte[]{1,2,3,4}));
        assertArrayEquals(new byte[4],fresh.savedStates());
    }
    private void resign(byte[] encoded) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(encoded,encoded.length-32));
        System.arraycopy(digest,0,encoded,encoded.length-32,32);
    }
}
