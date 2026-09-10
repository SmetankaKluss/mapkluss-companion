package art.mapkluss.companion;

import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/** Incremental display counts, isolated from observations that may be uploaded or saved. */
final class LiveBuildDisplayProgress implements LiveBuildProgress.DisplayEvidence {
    private final LiveBuildProgress local;
    private final LiveBuildRemoteEvidence remote;
    private final LiveBuildGroupController.Adoption adoption;
    private final BooleanSupplier valid;
    private final byte[] states;
    private final long[] times;
    private final TreeMap<Long,int[]> buckets=new TreeMap<>();
    private final int[] counts=new int[5];
    private int known;
    LiveBuildDisplayProgress(LiveBuildProgress local,LiveBuildRemoteEvidence remote,
                            LiveBuildGroupController.Adoption adoption,BooleanSupplier valid){
        this.local=local;this.remote=remote;this.adoption=adoption;this.valid=valid;
        states=new byte[local.size()];times=new long[local.size()];
    }
    @Override public LiveBuildProgress.Observation merge(int cell,LiveBuildProgress.Observation observed,long now){
        return valid.getAsBoolean()?remote.merge(adoption,cell,observed,now):observed;
    }
    @Override public void changed(int cell,long now){
        var observed=merge(cell,local.observation(cell,now,120000),now);
        int previous=states[cell];
        if(previous!=0)known--;
        if(previous>0&&previous<4){
            var bucket=buckets.get(times[cell]);
            if(bucket!=null){bucket[previous]--;counts[previous]--;if(bucket[1]+bucket[2]+bucket[3]==0)buckets.remove(times[cell]);}
        }
        int state=observed.status().ordinal();
        long time=Math.floorDiv(observed.observedAt(),50)*50;
        if(state>0&&state<4&&now-time>120000)state=4;
        states[cell]=(byte)state;
        if(state!=0)known++;
        if(state>0&&state<4){
            times[cell]=time;
            buckets.computeIfAbsent(time,ignored->new int[4])[state]++;counts[state]++;
        }
    }
    void page(int page,long now){for(int cell=page*4096;cell<Math.min(local.size(),(page+1)*4096);cell++)changed(cell,now);}
    void reset(){java.util.Arrays.fill(states,(byte)0);known=0;java.util.Arrays.fill(counts,0);buckets.clear();}
    @Override public LiveBuildProgress.Summary summary(long now){
        if(!valid.getAsBoolean())return null;
        while(!buckets.isEmpty()&&now-buckets.firstKey()>120000){
            var expired=buckets.pollFirstEntry().getValue();for(int state=1;state<4;state++)counts[state]-=expired[state];
        }
        return new LiveBuildProgress.Summary(states.length,counts[1],counts[2],counts[3],states.length-known,known-counts[1]-counts[2]-counts[3]);
    }
}
