package art.mapkluss.companion;

/** Immutable owner-thread copy; never contains remote or restored-as-stale evidence. */
public final class LiveBuildLocalEvidence {
    private final byte[] states;
    private final long[] times;
    private final boolean observed;
    LiveBuildLocalEvidence(byte[] states,long[] times,boolean observed){this.states=states;this.times=times;this.observed=observed;}
    public int size(){return states.length;}
    public boolean hasObservations(){return observed;}
    public String encode(long leaseReceived,long now){return LiveBuildEvidenceCodec.upload(states,times,leaseReceived,now);}
}
