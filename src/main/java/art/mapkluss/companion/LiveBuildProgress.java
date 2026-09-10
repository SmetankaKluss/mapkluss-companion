package art.mapkluss.companion;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only verification state. Call from one owner thread; no world writes or networking. */
public final class LiveBuildProgress {
    public static final int MAX_CELLS = 2_000_000;
    public static final int MAX_SCAN_BUDGET = 4096;
    public static final long LIVE_FRESHNESS_MS = 120_000;
    private static final long SUMMARY_BUCKET_MS = 50;

    public enum Status { UNKNOWN, CORRECT, MISSING, WRONG, STALE }
    private static final Status[] STATUS_VALUES = Status.values();

    public record Position(int x, int y, int z) {
        Position plus(Position other) {
            return new Position(Math.addExact(x, other.x), Math.addExact(y, other.y), Math.addExact(z, other.z));
        }
    }

    public record State(String block, Map<String, String> properties) {
        public State {
            Objects.requireNonNull(block);
            if (!block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid block identifier");
            properties = Map.copyOf(properties);
        }

        public boolean isAir() {
            return block.equals("minecraft:air") || block.equals("minecraft:cave_air") || block.equals("minecraft:void_air");
        }
    }

    public record Cell(Position relativePosition, State expected, boolean requiresAir) {
        public Cell(Position relativePosition, State expected) { this(relativePosition, expected, false); }
        public Cell {
            Objects.requireNonNull(relativePosition);
            Objects.requireNonNull(expected);
            if (expected.isAir()) throw new IllegalArgumentException("Only required structural blocks belong in the build target");
        }
    }

    /** A new identity is required after schematic, origin, orientation, world or phase changes. */
    public record Identity(String schematicSha256, String worldKey, String dimension, Position origin, long placementRevision,
        LiveBuildTransform transform) {
        public Identity(String schematicSha256, String worldKey, String dimension, Position origin, long placementRevision) {
            this(schematicSha256, worldKey, dimension, origin, placementRevision, LiveBuildTransform.NONE);
        }
        public Identity {
            Objects.requireNonNull(schematicSha256);
            Objects.requireNonNull(worldKey);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(origin);
            Objects.requireNonNull(transform);
            if (!schematicSha256.matches("[a-f0-9]{64}") || worldKey.isBlank() || dimension.isBlank() || placementRevision < 0) {
                throw new IllegalArgumentException("Invalid build identity");
            }
        }
    }

    public record Observation(Status status, Status lastKnown, long observedAt) { }
    public record Summary(int total, int correct, int missing, int wrong, int unknown, int stale) {
        public double completion() { return total == 0 ? 0 : (double) correct / total; }
        public int checked() { return correct + missing + wrong; }
    }

    @FunctionalInterface
    public interface WorldReader {
        /** Null means unavailable/unloaded, never air. The adapter must not load chunks. */
        State readLoaded(Position position);
    }

    private final Identity identity;
    private List<Cell> cells;
    private final short[] materialIds;
    private final String[] materialNames;
    private final byte[] statuses;
    private final boolean[] available;
    private final long[] observedAt;
    private int cursor;
    private long lastScanAt = -1;
    private boolean closed;
    private final java.util.NavigableMap<Long, int[]> liveBuckets = new java.util.TreeMap<>();
    private final int[] liveCounts = new int[STATUS_VALUES.length];
    private int known;

    public LiveBuildProgress(Identity identity, List<Cell> cells) {
        this(identity, cells, () -> false);
    }

    LiveBuildProgress(Identity identity, List<Cell> cells, java.util.function.BooleanSupplier cancelled) {
        this.identity = Objects.requireNonNull(identity);
        if (cells.isEmpty() || cells.size() > MAX_CELLS) throw new IllegalArgumentException("Invalid build size");
        this.cells = List.copyOf(cells);
        var dictionary = new java.util.LinkedHashMap<String, Short>();
        materialIds = new short[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            if (cell.requiresAir()) { materialIds[i] = -1; continue; }
            if (dictionary.size() >= 512 && !dictionary.containsKey(cell.expected().block()))
                throw new IllegalArgumentException("Too many build materials");
            materialIds[i] = dictionary.computeIfAbsent(cell.expected().block(), key -> (short) dictionary.size());
        }
        materialNames = dictionary.keySet().toArray(String[]::new);
        HashSet<Position> unique = new HashSet<>();
        for (Cell cell : this.cells) {
            if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("Build preparation cancelled");
            if (!unique.add(cell.relativePosition())) throw new IllegalArgumentException("Overlapping build cells");
            cell.relativePosition().plus(identity.origin());
        }
        statuses = new byte[cells.size()];
        available = new boolean[cells.size()];
        observedAt = new long[cells.size()];
    }

    public Identity identity() { return identity; }
    public int size() { return statuses.length; }
    public String material(int index) { return materialIds[index] < 0 ? null : materialNames[materialIds[index]]; }
    public Cell cell(int index) {
        if (cells == null) throw new IllegalStateException("Geometry is dormant");
        return cells.get(index);
    }

    /** Owner-thread eviction preserves observations and their actual freshness, not geometry. */
    void detachGeometry() { cells = null; }
    boolean geometryResident() { return cells != null; }
    void resumeGeometry(LiveBuildProgress prepared) {
        if (cells != null || !identity.equals(prepared.identity) || size() != prepared.size()
            || prepared.cells == null || prepared.closed || prepared.lastScanAt != -1 || prepared.known != 0)
            throw new IllegalArgumentException("Geometry does not match dormant observations");
        cells = prepared.cells;
        prepared.cells = null;
        prepared.close();
    }

    /** Owner-thread snapshot of last known states only; no process-relative timestamps survive restart. */
    public byte[] savedStates() { return statuses.clone(); }

    /** Apply only to a newly prepared, identical target. Every restored observation starts stale. */
    public void restoreStates(Identity savedIdentity, byte[] saved) {
        if (!identity.equals(savedIdentity) || closed || lastScanAt != -1 || known != 0 || saved.length != size())
            throw new IllegalArgumentException("Stored build does not match a fresh target");
        for (byte status : saved) if (status < 0 || status > Status.WRONG.ordinal())
            throw new IllegalArgumentException("Invalid stored observation");
        System.arraycopy(saved, 0, statuses, 0, saved.length);
        java.util.Arrays.fill(observedAt, -1);
        for (byte status : statuses) if (status != Status.UNKNOWN.ordinal()) known++;
    }

    /** Round-robin scan with a hard read limit; times use a nonnegative monotonic clock. */
    public int scan(Identity activeIdentity, WorldReader reader, int budget, long now) {
        if (budget < 0 || budget > MAX_SCAN_BUDGET || now < 0 || now < lastScanAt) {
            throw new IllegalArgumentException("Invalid scan budget or clock");
        }
        if (closed || cells == null || !identity.equals(activeIdentity)) return 0;
        Objects.requireNonNull(reader);
        lastScanAt = now;
        expireLive(now);
        int count = Math.min(budget, cells.size());
        for (int read = 0; read < count; read++) {
            int index = cursor;
            Cell cell = cells.get(index);
            State actual = reader.readLoaded(cell.relativePosition().plus(identity.origin()));
            removeLive(index);
            if (actual == null) {
                available[index] = false;
            } else {
                if (statuses[index] == Status.UNKNOWN.ordinal()) known++;
                Status status = cell.requiresAir() ? (actual.isAir() ? Status.CORRECT : Status.WRONG)
                    : cell.expected().equals(actual) ? Status.CORRECT : actual.isAir() ? Status.MISSING : Status.WRONG;
                statuses[index] = (byte) status.ordinal();
                available[index] = true;
                observedAt[index] = now;
                liveBuckets.computeIfAbsent(bucket(now), ignored -> new int[STATUS_VALUES.length])[status.ordinal()]++;
                liveCounts[status.ordinal()]++;
            }
            if(displayEvidence!=null)displayEvidence.changed(index,now);
            cursor = (cursor + 1) % cells.size();
        }
        return count;
    }

    @FunctionalInterface public interface DisplayEvidence {
        Observation merge(int cell,Observation local,long now);
        default void changed(int cell,long now) { }
        default Summary summary(long now){return null;}
    }
    private DisplayEvidence displayEvidence;
    public void displayEvidence(DisplayEvidence evidence){displayEvidence=evidence;}
    public Observation displayObservation(int index,long now,long freshness){
        var local=observation(index,now,freshness);
        return displayEvidence==null||closed?local:displayEvidence.merge(index,local,now);
    }
    public Observation observation(int index, long now, long freshness) {
        if (now < 0 || freshness < 0) throw new IllegalArgumentException("Invalid observation clock");
        Status last = STATUS_VALUES[statuses[index]];
        if (last == Status.UNKNOWN) return new Observation(Status.UNKNOWN, last, -1);
        return new Observation(currentStatus(index, now, freshness), last, observedAt[index]);
    }
    public LiveBuildLocalEvidence exportPage(int page,long now,long consentAt) {
        if(page<0||page>=(size()+4095)/4096||now<0||consentAt<0)throw new IllegalArgumentException("Invalid evidence page");
        int start=page*4096,count=Math.min(4096,size()-start);var values=new byte[count];var times=new long[count];boolean observed=false;
        for(int i=0;i<count;i++){
            int cell=start+i;var state=currentStatus(cell,now,LIVE_FRESHNESS_MS);
            if(observedAt[cell]>=consentAt&&(state==Status.CORRECT||state==Status.MISSING||state==Status.WRONG)){
                values[i]=(byte)state.ordinal();times[i]=observedAt[cell];observed=true;
            }
        }
        return new LiveBuildLocalEvidence(values,times,observed);
    }

    private Status currentStatus(int index, long now, long freshness) {
        Status last = STATUS_VALUES[statuses[index]];
        if (last == Status.UNKNOWN) return last;
        boolean fresh = !closed && available[index] && now >= observedAt[index] && now - observedAt[index] <= freshness;
        return fresh ? last : Status.STALE;
    }

    /** Aggregate on demand, not on each render frame. */
    public Summary summary(long now, long freshness) {
        if (now < 0 || freshness < 0) throw new IllegalArgumentException("Invalid observation clock");
        int[] counts = new int[STATUS_VALUES.length];
        for (int index = 0; index < size(); index++) counts[currentStatus(index, now, freshness).ordinal()]++;
        return new Summary(size(), counts[Status.CORRECT.ordinal()], counts[Status.MISSING.ordinal()],
            counts[Status.WRONG.ordinal()], counts[Status.UNKNOWN.ordinal()], counts[Status.STALE.ordinal()]);
    }

    private static long bucket(long time) { return time / SUMMARY_BUCKET_MS * SUMMARY_BUCKET_MS; }

    private void removeLive(int index) {
        if (!available[index]) return;
        long key = bucket(observedAt[index]);
        int[] counts = liveBuckets.get(key);
        if (counts == null) return;
        int status = statuses[index];
        counts[status]--;
        liveCounts[status]--;
        if (counts[Status.CORRECT.ordinal()] + counts[Status.MISSING.ordinal()] + counts[Status.WRONG.ordinal()] == 0) liveBuckets.remove(key);
    }

    private void expireLive(long now) {
        while (!liveBuckets.isEmpty() && now - liveBuckets.firstKey() > LIVE_FRESHNESS_MS) {
            int[] counts = liveBuckets.pollFirstEntry().getValue();
            for (int i = 0; i < counts.length; i++) liveCounts[i] -= counts[i];
        }
    }

    /** At most 2401 time buckets, independent of the number of blocks. Expiry may be 49 ms early, never late. */
    public Summary liveSummary(long now) {
        if (now < 0 || now < lastScanAt) throw new IllegalArgumentException("Invalid summary clock");
        if(displayEvidence!=null&&!closed){var shared=displayEvidence.summary(now);if(shared!=null)return shared;}
        expireLive(now);
        int correct = closed ? 0 : liveCounts[Status.CORRECT.ordinal()];
        int missing = closed ? 0 : liveCounts[Status.MISSING.ordinal()];
        int wrong = closed ? 0 : liveCounts[Status.WRONG.ordinal()];
        return new Summary(size(), correct, missing, wrong, size() - known, known - correct - missing - wrong);
    }

    /** World exit/logout: old observations must never be treated as live after reconnect. */
    public void close() { closed = true; }
}
