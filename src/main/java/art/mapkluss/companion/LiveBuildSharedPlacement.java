package art.mapkluss.companion;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/** Validated wire metadata only; decoding never attaches a remote world or starts scanning. */
public record LiveBuildSharedPlacement(int tile, long revision, String targetSha256, int phase,
    int cellCount, String worldBinding, String dimension, LiveBuildProgress.Position origin,
    LiveBuildTransform transform) {
    public LiveBuildSharedPlacement {
        if(tile<0||tile>=100||revision<0||targetSha256==null||!targetSha256.matches("[a-f0-9]{64}")
            ||phase< -1||phase>4095||cellCount<1||cellCount>LiveBuildProgress.MAX_CELLS
            ||worldBinding==null||!UUID.fromString(worldBinding).toString().equals(worldBinding)
            ||dimension==null||dimension.length()>128||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
            ||origin==null||origin.x()< -30_000_000||origin.x()>30_000_000||origin.z()< -30_000_000
            ||origin.z()>30_000_000||origin.y()< -2048||origin.y()>2048||transform==null)
            throw new IllegalArgumentException("Invalid shared placement");
    }
    public JsonObject input() {
        var value=new JsonObject();value.addProperty("tile",tile);value.addProperty("target_sha256",targetSha256);
        value.addProperty("phase",phase);value.addProperty("cell_count",cellCount);value.addProperty("world_binding",worldBinding);
        value.addProperty("dimension",dimension);value.addProperty("origin_x",origin.x());value.addProperty("origin_y",origin.y());
        value.addProperty("origin_z",origin.z());value.addProperty("rotation",transform.quarterTurns());value.addProperty("mirrored",transform.mirrorX());
        return value;
    }
    public boolean matchesLocal(LiveBuildProgress.Identity identity,int localPhase,int localCells) {
        return identity!=null&&targetSha256.equals(identity.schematicSha256())&&phase==localPhase&&cellCount==localCells
            &&dimension.equals(identity.dimension())&&origin.equals(identity.origin())&&transform.equals(identity.transform());
    }
    static long integer(JsonObject value,String name) {
        var field=value.get(name);
        if(field==null||!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer");
        return field.getAsBigDecimal().longValueExact();
    }
    private static int number(JsonObject value,String name) {return Math.toIntExact(integer(value,name));}
    public static List<LiveBuildSharedPlacement> decode(JsonObject build,LiveBuildGroupController.Group group) {
        if(!build.has("placements")||!build.get("placements").isJsonArray())throw new IllegalArgumentException("Missing placement set");
        var values=build.getAsJsonArray("placements");
        if(values.size()>group.source().wide()*group.source().tall())throw new IllegalArgumentException("Too many maps");
        var result=new ArrayList<LiveBuildSharedPlacement>();var tiles=new HashSet<Integer>();long cells=0;
        for(var entry:values){
            var p=entry.getAsJsonObject();
            if(!group.id().equals(p.get("build_id").getAsString()))throw new IllegalArgumentException("Different build");
            var mirrored=p.get("mirrored");
            if(!mirrored.isJsonPrimitive()||!mirrored.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean");
            var placement=new LiveBuildSharedPlacement(number(p,"tile"),integer(p,"revision"),p.get("target_sha256").getAsString(),
                number(p,"phase"),number(p,"cell_count"),p.get("world_binding").getAsString(),p.get("dimension").getAsString(),
                new LiveBuildProgress.Position(number(p,"origin_x"),number(p,"origin_y"),number(p,"origin_z")),
                new LiveBuildTransform(number(p,"rotation"),mirrored.getAsBoolean()));
            cells+=placement.cellCount();
            if(placement.tile()>=group.source().wide()*group.source().tall()||!tiles.add(placement.tile())
                ||placement.revision()<1||placement.revision()>group.revision()||cells>8_000_000)
                throw new IllegalArgumentException("Invalid placement set");
            result.add(placement);
        }
        return List.copyOf(result);
    }
}
