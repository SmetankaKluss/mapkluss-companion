package art.mapkluss.companion;

import java.util.LinkedHashMap;
import java.util.Map;

/** One complete artwork with independently placed maps. Single client-thread owner after preparation. */
public final class LiveBuildAssembly {
    private final LiveBuildParts parts;
    private final Map<Integer,LiveBuildProgress> placed=new LinkedHashMap<>();
    private LiveBuildProgress[] scanTargets=new LiveBuildProgress[0];
    private int scanPart;
    private long placementEpoch;
    public long placementEpoch(){return placementEpoch;}
    public LiveBuildAssembly(LiveBuildParts parts){this.parts=parts;}
    public LiveBuildParts parts(){return parts;}
    public int size(){return parts.source().cells().size();}
    public LiveBuildProgress.Cell cell(int index){return parts.source().cells().get(index);}
    public LiveBuildProgress placement(int part){return placed.get(part);}
    public void attach(int part,LiveBuildProgress progress){
        if(!parts.part(part).targetSha256().equals(progress.identity().schematicSha256()))throw new IllegalArgumentException("Wrong map part");
        var old=placed.put(part,progress);if(old!=null)old.close();
        scanTargets=placed.values().toArray(LiveBuildProgress[]::new);
        placementEpoch++;
    }
    public void remove(int part){var old=placed.remove(part);if(old!=null){old.close();scanTargets=placed.values().toArray(LiveBuildProgress[]::new);placementEpoch++;}}
    LiveBuildProgress detach(int part){
        var old=placed.remove(part);
        if(old!=null){scanTargets=placed.values().toArray(LiveBuildProgress[]::new);placementEpoch++;old.detachGeometry();}
        return old;
    }
    public int scan(LiveBuildProgress.WorldReader reader,int budget,long now){
        if(budget<0||budget>LiveBuildProgress.MAX_SCAN_BUDGET)throw new IllegalArgumentException("Invalid scan budget");
        if(placed.isEmpty()||budget==0)return 0;
        scanPart%=scanTargets.length;
        var target=scanTargets[scanPart];scanPart=(scanPart+1)%scanTargets.length;
        return target.scan(target.identity(),reader,budget,now);
    }
    public LiveBuildProgress.Summary summary(long now){
        int total=parts.requiredCells();
        int correct=0,missing=0,wrong=0,unknown=total,stale=0;
        for(var target:placed.values()){
            var s=target.liveSummary(now);unknown-=s.total();unknown+=s.unknown();
            correct+=s.correct();missing+=s.missing();wrong+=s.wrong();stale+=s.stale();
        }
        return new LiveBuildProgress.Summary(total,correct,missing,wrong,unknown,stale);
    }
    public LiveBuildProgress.Status status(int sourceCell,long now){
        var target=placed.get(parts.partOfCell(sourceCell));
        return target==null?LiveBuildProgress.Status.UNKNOWN:
            target.observation(parts.localCellIndex(sourceCell),now,LiveBuildProgress.LIVE_FRESHNESS_MS).status();
    }
    public void close(){placed.values().forEach(LiveBuildProgress::close);placed.clear();scanTargets=new LiveBuildProgress[0];placementEpoch++;}
}
