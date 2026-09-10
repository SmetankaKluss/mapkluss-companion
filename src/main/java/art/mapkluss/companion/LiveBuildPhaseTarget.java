package art.mapkluss.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/** Pure, bounded preparation from a validated plan and its pinned full structure. Run off-thread. */
public final class LiveBuildPhaseTarget {
    private LiveBuildPhaseTarget() { }

    /** -1 is the initial full build; 0 is the state after the first removal phase. */
    public static LiveBuildSchematic read(byte[] planBytes,byte[] sourceBytes,int phase) throws IOException {
        var plan=SuppressionPlanParser.parse(planBytes).plan();
        if(phase < -1 || phase >= plan.phases().size())throw new IOException("Invalid build phase");
        if(sourceBytes==null || sourceBytes.length>LiveBuildSchematic.MAX_FILE_BYTES)throw new IOException("Invalid source size");
        SuppressionReferenceLitematic.validateSource(plan,sourceBytes);
        var source=LiveBuildSchematic.readPhaseSource(sourceBytes);
        var removed=new HashSet<LiveBuildProgress.Position>();
        for(int index=0;index<=phase;index++){
            for(var run:plan.phases().get(index).removeRuns()){
                for(int dx=0;dx<run.length();dx++){
                    removed.add(new LiveBuildProgress.Position(run.xStart()+dx,run.y(),run.z()));
                }
            }
        }
        var cells=new ArrayList<LiveBuildProgress.Cell>(source.cells().size());
        for(var cell:source.cells()){
            cells.add(new LiveBuildProgress.Cell(cell.relativePosition(),cell.expected(),removed.remove(cell.relativePosition())));
        }
        if(!removed.isEmpty())throw new IOException("Removal absent from source structure");
        String identity=SuppressionHashes.sha256(planBytes)+":"+source.sha256()+":build-phase-v1:"+phase;
        var bounds=source.bounds();
        // Plan coordinates explicitly define one map; workflow margins are not additional maps.
        var art=new LiveBuildSchematic.Bounds(0,bounds.minY(),0,127,bounds.maxY(),127);
        return new LiveBuildSchematic(SuppressionHashes.sha256(identity.getBytes(StandardCharsets.UTF_8)),cells,bounds,art);
    }
}
