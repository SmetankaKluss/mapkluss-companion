package art.mapkluss.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Whole-art document; tile geometry is prepared lazily and never flattened into adjacent structures. */
public final class LiveBuildBundleWorkspace implements AutoCloseable {
    public record Request(int tile,int phase,long revision) { }
    public record Prepared(Request request,LiveBuildSchematic target,LiveBuildTopView projection,short[] pixelDependencies) { }
    private final SuppressionBundleCatalog catalog;
    private final byte[] mapColours;
    private final long[] revisions;
    private final Request[] requests;
    private final Request[] staged;
    private final Map<Integer,LiveBuildAssembly> assemblies=new HashMap<>();
    private boolean closed;
    private final int activeCellBudget;

    private LiveBuildBundleWorkspace(SuppressionBundleCatalog catalog){
        this(catalog,LiveBuildProgress.MAX_CELLS);
    }
    private LiveBuildBundleWorkspace(SuppressionBundleCatalog catalog,int activeCellBudget){
        if(activeCellBudget<1||activeCellBudget>LiveBuildProgress.MAX_CELLS)throw new IllegalArgumentException("Invalid active cell budget");
        this.activeCellBudget=activeCellBudget;
        this.catalog=catalog;
        revisions=new long[catalog.tiles().size()];
        requests=new Request[revisions.length];
        staged=new Request[revisions.length];
        mapColours=new byte[width()*height()];
        for(var tile:catalog.tiles()){
            byte[] colours=tile.bundle().parsed().targetMapBytes();
            for(int row=0;row<128;row++)System.arraycopy(colours,row*128,mapColours,
                (tile.row()*128+row)*width()+tile.column()*128,128);
        }
    }
    public static LiveBuildBundleWorkspace read(Path path)throws IOException{
        return new LiveBuildBundleWorkspace(SuppressionBundleReader.readCatalog(path));
    }
    static LiveBuildBundleWorkspace read(byte[] bytes)throws IOException{
        return new LiveBuildBundleWorkspace(SuppressionBundleReader.readCatalog(bytes,"Two-layer"));
    }
    static LiveBuildBundleWorkspace read(byte[] bytes,int activeCellBudget)throws IOException{
        return new LiveBuildBundleWorkspace(SuppressionBundleReader.readCatalog(bytes,"Two-layer"),activeCellBudget);
    }
    public int width(){return catalog.gridWide()*128;}
    public int height(){return catalog.gridTall()*128;}
    public int tileCount(){return catalog.tiles().size();}
    public SuppressionBundleCatalog catalog(){return catalog;}
    public byte[] mapColours(){return mapColours.clone();}
    public synchronized LiveBuildAssembly assembly(int tile){return assemblies.get(tile);}
    int activeCellBudget(){return activeCellBudget;}
    synchronized int activeCells(){return assemblies.values().stream().mapToInt(a->a.parts().requiredCells()).sum();}
    public synchronized int phase(int tile){return requests[tile]==null?-1:requests[tile].phase();}
    public synchronized void invalidate(int tile){
        requireOpen();
        if(tile<0||tile>=revisions.length)throw new IndexOutOfBoundsException(tile);
        revisions[tile]++;requests[tile]=null;staged[tile]=null;
        var old=assemblies.remove(tile);if(old!=null)old.close();
    }

    /** Main-owner thread: invalidate only the chosen tile before requesting a new phase. */
    public synchronized Request begin(int tile,int phase){
        requireOpen();
        var bundle=catalog.tiles().get(tile).bundle();
        if(phase < -1 || phase>=bundle.parsed().plan().phases().size())throw new IllegalArgumentException("Invalid tile phase");
        staged[tile]=null;
        var old=assemblies.remove(tile);if(old!=null)old.close();
        return requests[tile]=new Request(tile,phase,++revisions[tile]);
    }
    /** Keep the committed phase alive until its fully prepared replacement is published. */
    synchronized Request stage(int tile,int phase){
        requireOpen();
        var bundle=catalog.tiles().get(tile).bundle();
        if(phase < -1 || phase>=bundle.parsed().plan().phases().size())throw new IllegalArgumentException("Invalid tile phase");
        return staged[tile]=new Request(tile,phase,++revisions[tile]);
    }
    synchronized void cancelStage(int tile){staged[tile]=null;}
    /** Worker only. No registries/world access; caller subsequently uses normal bounded preparation. */
    public Prepared prepare(Request request)throws IOException{
        synchronized(this){requireCurrent(request);}
        var bundle=catalog.tiles().get(request.tile()).bundle();
        var raw=LiveBuildPhaseTarget.read(bundle.planBytes(),bundle.litematicBytes(),request.phase());
        var target=new LiveBuildSchematic(targetHash(request),raw.cells(),raw.bounds(),raw.artBounds());
        var projection=new LiveBuildTopView(target.cells(),target.artBounds());
        if(projection.width()!=128||projection.height()!=128)throw new IOException("Invalid tile projection");
        short[] dependencies=new short[target.cells().size()];
        var bounds=target.artBounds();
        for(int i=0;i<dependencies.length;i++){
            var p=target.cells().get(i).relativePosition();
            int x=Math.max(0,Math.min(127,p.x()-bounds.minX()));
            int z=Math.max(0,Math.min(127,p.z()-bounds.minZ()));
            dependencies[i]=(short)(z*128+x);
        }
        synchronized(this){requireCurrent(request);}
        return new Prepared(request,target,projection,dependencies);
    }
    /** Publish only the requested normalized tile; retain all other placements and their observations. */
    public synchronized void publish(Prepared prepared,LiveBuildAssembly assembly)throws IOException{
        requireCurrent(prepared.request());
        String expected=targetHash(prepared.request());
        if(!expected.equals(prepared.target().sha256())||!expected.equals(assembly.parts().source().sha256()))
            throw new IllegalArgumentException("Wrong tile target");
        int count=assembly.parts().requiredCells();
        for(var entry:assemblies.entrySet())if(entry.getKey()!=prepared.request().tile())count=Math.addExact(count,entry.getValue().parts().requiredCells());
        if(count>activeCellBudget)throw new IllegalArgumentException("Active build cell budget exceeded");
        requests[prepared.request().tile()]=prepared.request();staged[prepared.request().tile()]=null;
        var old=assemblies.put(prepared.request().tile(),assembly);if(old!=null&&old!=assembly)old.close();
    }
    /** Unprepared/unplaced tiles contribute zero; every map has equal weight in whole-art progress. */
    public synchronized double completion(long now){
        double total=0;
        for(var assembly:assemblies.values())total+=assembly.summary(now).completion();
        return total/tileCount();
    }
    private String targetHash(Request request)throws IOException{
        var tile=catalog.tiles().get(request.tile());
        String scope=catalog.bundleSha256()+":tracker-bundle-v1:"+tile.id()+":"+tile.index()+":"+
            tile.column()+":"+tile.row()+":"+tile.bundle().planSha256()+":"+tile.bundle().litematicSha256()+":"+request.phase();
        return SuppressionHashes.sha256(scope.getBytes(StandardCharsets.UTF_8));
    }
    public synchronized boolean supportsPhase(LiveBuildSharedPlacement remote){
        requireOpen();
        if(remote.tile()<0||remote.tile()>=catalog.tiles().size())return false;
        var tile=catalog.tiles().get(remote.tile());
        return remote.phase()>=-1&&remote.phase()<tile.bundle().parsed().plan().phases().size();
    }
    private void requireOpen(){if(closed)throw new java.util.concurrent.CancellationException("Bundle closed");}
    private void requireCurrent(Request request){
        requireOpen();
        if(request.tile()<0||request.tile()>=revisions.length||!request.equals(staged[request.tile()]!=null?staged[request.tile()]:requests[request.tile()]))
            throw new java.util.concurrent.CancellationException("Superseded tile request");
    }
    @Override public synchronized void close(){
        closed=true;assemblies.values().forEach(LiveBuildAssembly::close);assemblies.clear();
    }
}
