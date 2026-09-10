package art.mapkluss.companion;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

/** Incremental structural dependency reduction. Reads observations only, never the world. */
public final class LiveBuildPreviewCheck {
    private final LiveBuildAssembly assembly;
    private final LiveBuildTopView view;
    private final long epoch;
    private final LiveBuildProgress.Status[] statuses;
    private final boolean[] covered;
    private final LiveBuildProgress progress;
    private final short[] dependencies;
    private int partIndex,cellIndex;

    public LiveBuildPreviewCheck(LiveBuildAssembly assembly,LiveBuildTopView view){
        progress=null;dependencies=null;
        this.assembly=assembly;this.view=view;epoch=assembly.placementEpoch();
        statuses=new LiveBuildProgress.Status[view.width()*view.height()];
        covered=new boolean[statuses.length];
        Arrays.fill(statuses,LiveBuildProgress.Status.CORRECT);
    }
    /** Bundle dependencies are compiled off-thread and remain small when geometry is evicted. */
    LiveBuildPreviewCheck(LiveBuildProgress progress,short[] dependencies){
        if(dependencies.length!=progress.size())throw new IllegalArgumentException("Wrong preview dependencies");
        this.progress=progress;this.dependencies=dependencies;assembly=null;view=null;epoch=0;
        statuses=new LiveBuildProgress.Status[16384];covered=new boolean[16384];
        Arrays.fill(statuses,LiveBuildProgress.Status.CORRECT);
    }
    /** Returns a complete reduction only. A placement change invalidates the entire pending result. */
    public LiveBuildProgress.Status[] advance(int budget,long now){
        if(budget<1||budget>4096||now<0)throw new IllegalArgumentException("Invalid preview budget");
        if(progress!=null){
            int end=Math.min(dependencies.length,cellIndex+budget);
            for(;cellIndex<end;cellIndex++){
                int pixel=dependencies[cellIndex];
                var status=progress.displayObservation(cellIndex,now,LiveBuildProgress.LIVE_FRESHNESS_MS).status();
                covered[pixel]=true;
                if(rank(status)>rank(statuses[pixel]))statuses[pixel]=status;
            }
            if(cellIndex<dependencies.length)return null;
            var result=statuses.clone();
            for(int i=0;i<result.length;i++)if(!covered[i])result[i]=LiveBuildProgress.Status.UNKNOWN;
            return result;
        }
        if(epoch!=assembly.placementEpoch())throw new CancellationException("Placement changed");
        var parts=assembly.parts();var bounds=parts.source().artBounds();
        int read=0;
        while(partIndex<parts.parts().size()&&read<budget){
            var part=parts.part(partIndex);
            if(cellIndex>=part.cells().size()){partIndex++;cellIndex=0;read++;continue;}
            var cell=part.cells().get(cellIndex);var p=cell.relativePosition();
            // A map's copied north reference constrains its first row, not its source neighbour.
            int x=Math.max(0,Math.min(part.width()-1,p.x()));
            int z=Math.max(0,Math.min(part.depth()-1,p.z()));
            int pixel=view.pixelAt(bounds.minX()+part.column()*128+x,bounds.minZ()+part.row()*128+z);
            var placed=assembly.placement(partIndex);
            var status=placed==null?LiveBuildProgress.Status.UNKNOWN:
                placed.displayObservation(cellIndex,now,LiveBuildProgress.LIVE_FRESHNESS_MS).status();
            if(pixel>=0){covered[pixel]=true;if(rank(status)>rank(statuses[pixel]))statuses[pixel]=status;}
            cellIndex++;read++;
        }
        if(partIndex!=parts.parts().size())return null;
        var result=statuses.clone();
        for(int i=0;i<result.length;i++)if(!covered[i])result[i]=LiveBuildProgress.Status.UNKNOWN;
        return result;
    }
    private static int rank(LiveBuildProgress.Status status){
        return switch(status){case CORRECT->0;case UNKNOWN->1;case STALE->2;case MISSING->3;case WRONG->4;};
    }
}
