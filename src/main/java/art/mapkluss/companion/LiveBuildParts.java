package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.List;

/** Immutable map partition. Build off-thread; empty margins remain part of the artwork. */
public final class LiveBuildParts {
    public record Part(int index, int column, int row, int width, int depth,
                       List<LiveBuildProgress.Cell> cells, String targetSha256) {
        public Part { cells=List.copyOf(cells); }
        public boolean empty() { return cells.isEmpty(); }
    }
    private final LiveBuildSchematic source;
    private final List<Part> parts;
    private final int columns;
    private final int rows;
    private final int[] partByCell;
    private final int[] localByCell;
    private final int requiredCells;

    public LiveBuildParts(LiveBuildSchematic source) {
        this.source=source;
        var b=source.artBounds();
        columns=(b.width()+127)/128;rows=(b.depth()+127)/128;
        var buckets=new ArrayList<List<LiveBuildProgress.Cell>>(columns*rows);
        for(int i=0;i<columns*rows;i++)buckets.add(new ArrayList<>());
        partByCell=new int[source.cells().size()];localByCell=new int[partByCell.length];
        for(int i=0;i<source.cells().size();i++){
            var cell=source.cells().get(i);var p=cell.relativePosition();
            int dx=p.x()-b.minX(),dz=p.z()-b.minZ();
            int column=Math.max(0,Math.min(columns-1,Math.floorDiv(dx,128)));
            int row=Math.max(0,Math.min(rows-1,Math.floorDiv(dz,128)));
            int part=row*columns+column;
            partByCell[i]=part;localByCell[i]=buckets.get(part).size();
            buckets.get(part).add(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(dx-column*128,
                Math.subtractExact(p.y(),b.minY()),dz-row*128),cell.expected(),cell.requiresAir()));
        }
        // A detached southern map needs its own copy of the preceding structure row.
        // Append references after artwork cells so source-to-preview indices stay stable.
        if (source.bounds().minZ() == (long)b.minZ()-1 && rows > 1) {
            boolean[] occupiedParts = new boolean[buckets.size()];
            for (int i=0;i<buckets.size();i++) occupiedParts[i]=!buckets.get(i).isEmpty();
            for (var cell:source.cells()) {
                var p=cell.relativePosition();
                int dx=p.x()-b.minX(),dz=p.z()-b.minZ();
                if (dx<0 || dx>=b.width() || dz<0 || (dz+1)%128!=0) continue;
                int nextRow=(dz+1)/128;
                if (nextRow>=rows) continue;
                int column=dx/128,part=nextRow*columns+column;
                if (!occupiedParts[part]) continue;
                buckets.get(part).add(new LiveBuildProgress.Cell(new LiveBuildProgress.Position(
                    dx-column*128,Math.subtractExact(p.y(),b.minY()),-1),cell.expected(),cell.requiresAir()));
            }
        }
        requiredCells=buckets.stream().mapToInt(List::size).sum();
        if(requiredCells>LiveBuildProgress.MAX_CELLS)throw new IllegalArgumentException("Build references exceed cell budget");
        var result=new ArrayList<Part>(buckets.size());
        for(int i=0;i<buckets.size();i++){
            int column=i%columns,row=i/columns;
            String identity=source.sha256()+":map-part-v2:"+b+":"+column+":"+row;
            result.add(new Part(i,column,row,Math.min(128,b.width()-column*128),Math.min(128,b.depth()-row*128),
                buckets.get(i),hash(identity)));
        }
        parts=List.copyOf(result);
    }
    public LiveBuildSchematic source(){return source;}
    public int columns(){return columns;}
    public int rows(){return rows;}
    public List<Part> parts(){return parts;}
    public int requiredCells(){return requiredCells;}
    public int partOfCell(int index){return partByCell[index];}
    public int localCellIndex(int index){return localByCell[index];}
    public Part part(int index){return parts.get(index);}
    public LiveBuildProgress.Position anchorOffset(int index, LiveBuildTransform transform) {
        var b=source.artBounds();var p=part(index);
        return transform.apply(new LiveBuildProgress.Position(Math.addExact(b.minX(),p.column()*128),
            b.minY(),Math.addExact(b.minZ(),p.row()*128)));
    }
    /** UI coordinates refer to the Litematica source, while progress is map-local. */
    public LiveBuildProgress.Position fromSchematicOrigin(int index, LiveBuildProgress.Position origin, LiveBuildTransform transform) {
        var d=anchorOffset(index,transform);
        return new LiveBuildProgress.Position(Math.addExact(origin.x(),d.x()),Math.addExact(origin.y(),d.y()),Math.addExact(origin.z(),d.z()));
    }
    public LiveBuildProgress.Position toSchematicOrigin(int index, LiveBuildProgress.Position origin, LiveBuildTransform transform) {
        var d=anchorOffset(index,transform);
        return new LiveBuildProgress.Position(Math.subtractExact(origin.x(),d.x()),Math.subtractExact(origin.y(),d.y()),Math.subtractExact(origin.z(),d.z()));
    }
    private static String hash(String value){
        try{return SuppressionHashes.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        catch(java.io.IOException failure){throw new IllegalStateException("SHA-256 unavailable",failure);}
    }
}
