package art.mapkluss.companion;

import java.util.Base64;

/** Companion build v1 packed evidence. All client times are monotonic milliseconds. */
public final class LiveBuildEvidenceCodec {
    public static final int PAGE_CELLS = 4096;
    public static final long FRESH_MS = 120_000;

    private LiveBuildEvidenceCodec() { }

    public static String upload(byte[] states, long[] observedAt, long leaseReceivedAt, long now) {
        if (states == null || observedAt == null || states.length != observedAt.length
            || states.length < 1 || states.length > PAGE_CELLS || now < leaseReceivedAt
            || Math.subtractExact(now, leaseReceivedAt) > 30_000) throw new IllegalArgumentException("Invalid evidence page or lease");
        byte[] bytes = new byte[states.length * 2];
        for (int i = 0; i < states.length; i++) {
            int state = states[i];
            if (state < 0 || state > 4) throw new IllegalArgumentException("Invalid evidence state");
            int units = 1200;
            if (state == 4) state = 0;
            if (state != 0) {
                if (observedAt[i] > now) throw new IllegalArgumentException("Future evidence");
                long delta = Math.subtractExact(observedAt[i], leaseReceivedAt);
                if (Math.subtractExact(now, observedAt[i]) > FRESH_MS || delta < -FRESH_MS) state = 0;
                else units = Math.toIntExact(Math.floorDiv(delta, 100) + 1200);
            }
            int word = (units << 3) | state;
            bytes[i * 2] = (byte) (word >>> 8);
            bytes[i * 2 + 1] = (byte) word;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static Page download(String packed, int expectedCells, long requestStartedAt) {
        if (packed == null || packed.length() > 10924 || expectedCells < 1 || expectedCells > PAGE_CELLS)
            throw new IllegalArgumentException("Invalid evidence size");
        byte[] bytes = Base64.getDecoder().decode(packed);
        if (bytes.length != expectedCells * 2 || !Base64.getEncoder().encodeToString(bytes).equals(packed))
            throw new IllegalArgumentException("Invalid evidence encoding");
        byte[] states = new byte[expectedCells];
        long[] times = new long[expectedCells];
        for (int i = 0; i < expectedCells; i++) {
            int word = (Byte.toUnsignedInt(bytes[i * 2]) << 8) | Byte.toUnsignedInt(bytes[i * 2 + 1]);
            int state = word & 7;
            int age = word >>> 3;
            if (state > 4 || age > 1201 || (state == 0 && age != 0)) throw new IllegalArgumentException("Invalid evidence value");
            states[i] = (byte) state;
            // Transit is included: receipt time must never become the freshness origin.
            times[i] = Math.subtractExact(requestStartedAt, age * 100L);
        }
        return new Page(states, times, requestStartedAt);
    }

    public static final class Page {
        private final byte[] states;
        private final long[] observedAt;
        private final long requestStartedAt;
        private Page(byte[] states, long[] observedAt, long requestStartedAt) {
            this.states = states;
            this.observedAt = observedAt;
            this.requestStartedAt = requestStartedAt;
        }
        public int size() { return states.length; }
        public long observedAt(int cell) { return observedAt[cell]; }
        public int state(int cell, long now) {
            if (now < requestStartedAt) return 0;
            int state = states[cell];
            if (state == 0 || state == 4) return state;
            return Math.subtractExact(now, observedAt[cell]) > FRESH_MS ? 4 : state;
        }
    }
}
