package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Bounded Litematic import; entities and block-entity inventories are never executed or loaded. */
public record LiveBuildSchematic(String sha256, List<LiveBuildProgress.Cell> cells, Bounds bounds, Bounds artBounds) {
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ
                || (long)maxX-minX+1 > 8192 || (long)maxZ-minZ+1 > 8192) {
                throw new IllegalArgumentException("Invalid schematic bounds");
            }
        }
        public int width() { return maxX-minX+1; }
        public int depth() { return maxZ-minZ+1; }
        public boolean contains(LiveBuildProgress.Position p) {
            return p.x()>=minX&&p.x()<=maxX&&p.y()>=minY&&p.y()<=maxY&&p.z()>=minZ&&p.z()<=maxZ;
        }
        static Bounds occupied(List<LiveBuildProgress.Cell> cells) {
            if(cells.isEmpty())throw new IllegalArgumentException("Empty schematic");
            int x=Integer.MAX_VALUE,y=x,z=x,xx=Integer.MIN_VALUE,yy=xx,zz=xx;
            for(var cell:cells){var p=cell.relativePosition();x=Math.min(x,p.x());y=Math.min(y,p.y());z=Math.min(z,p.z());xx=Math.max(xx,p.x());yy=Math.max(yy,p.y());zz=Math.max(zz,p.z());}
            return new Bounds(x,y,z,xx,yy,zz);
        }
    }
    public LiveBuildSchematic(String sha256,List<LiveBuildProgress.Cell> cells) { this(sha256,cells,Bounds.occupied(cells)); }
    public LiveBuildSchematic(String sha256,List<LiveBuildProgress.Cell> cells,Bounds bounds) { this(sha256,cells,bounds,bounds); }
    public LiveBuildSchematic {
        cells = List.copyOf(cells);
        java.util.Objects.requireNonNull(bounds);
        java.util.Objects.requireNonNull(artBounds);
        if(!bounds.contains(new LiveBuildProgress.Position(artBounds.minX(),artBounds.minY(),artBounds.minZ()))
            ||!bounds.contains(new LiveBuildProgress.Position(artBounds.maxX(),artBounds.maxY(),artBounds.maxZ())))throw new IllegalArgumentException("Art outside structure");
        for(var cell:cells)if(!bounds.contains(cell.relativePosition()))throw new IllegalArgumentException("Cell outside bounds");
    }

    public static LiveBuildSchematic read(Path path) throws IOException {
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(MAX_FILE_BYTES + 1); }
        return read(bytes);
    }

    static LiveBuildSchematic read(byte[] bytes) throws IOException {
        return read(bytes, false);
    }

    // Only the validated plan/source path may interpret a Two-layer source as a phase target.
    static LiveBuildSchematic readPhaseSource(byte[] bytes) throws IOException {
        return read(bytes, true);
    }

    public static final class PhasePlanRequired extends IOException {
        public PhasePlanRequired() { super("Import the Two-layer ZIP with its phase plan"); }
    }

    private static LiveBuildSchematic read(byte[] bytes, boolean phaseSource) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw new IOException("Schematic exceeds 16 MiB");
        try {
            var root = SuppressionNbt.compound(SuppressionNbt.readCompressed(bytes).root(), "root");
            if (!phaseSource && root.containsKey("Metadata")) {
                var metadata = SuppressionNbt.compound(root.get("Metadata"), "Metadata");
                String author = metadataString(metadata, "Author");
                String name = metadataString(metadata, "Name");
                String description = metadataString(metadata, "Description");
                if (("MapKluss".equals(author) && name.endsWith("_two_layer"))
                    || (name.startsWith("MapKluss reference_after_")
                        && "REFERENCE ONLY - ACTIVE REMOVALS ARE AIR".equals(description))) {
                    throw new PhasePlanRequired();
                }
            }
            int version = SuppressionNbt.intValue(root.get("Version"), "Version");
            if (version < 4 || version > 7) throw new IOException("Unsupported Litematic version");
            var regions = SuppressionNbt.compound(root.get("Regions"), "Regions");
            if (regions.isEmpty() || regions.size() > 256) throw new IOException("Invalid region count");
            var cells = new ArrayList<LiveBuildProgress.Cell>();
            var occupied = new HashMap<LiveBuildProgress.Position, LiveBuildProgress.State>();
            long totalVolume = 0;
            int minX=Integer.MAX_VALUE,minY=minX,minZ=minX,maxX=Integer.MIN_VALUE,maxY=maxX,maxZ=maxX;
            for (var tag : regions.values()) {
                var region = SuppressionNbt.compound(tag, "region");
                var origin = vector(region.get("Position"));
                var signed = vector(region.get("Size"));
                int sx = dimension(signed.x()), sy = dimension(signed.y()), sz = dimension(signed.z());
                long volume = (long) sx * sy * sz;
                totalVolume += volume;
                if (totalVolume > LiveBuildProgress.MAX_CELLS) throw new IOException("Schematic volume exceeds two million cells");
                int ox = Math.addExact(origin.x(), signed.x() < 0 ? signed.x() + 1 : 0);
                int oy = Math.addExact(origin.y(), signed.y() < 0 ? signed.y() + 1 : 0);
                int oz = Math.addExact(origin.z(), signed.z() < 0 ? signed.z() + 1 : 0);
                minX=Math.min(minX,ox);minY=Math.min(minY,oy);minZ=Math.min(minZ,oz);
                maxX=Math.max(maxX,Math.addExact(ox,sx-1));maxY=Math.max(maxY,Math.addExact(oy,sy-1));maxZ=Math.max(maxZ,Math.addExact(oz,sz-1));
                var paletteTags = SuppressionNbt.list(region.get("BlockStatePalette"), 10, "palette").values();
                if (paletteTags.isEmpty() || paletteTags.size() > 65536) throw new IOException("Invalid palette size");
                var palette = new ArrayList<LiveBuildProgress.State>();
                for (var entry : paletteTags) {
                    var state = SuppressionNbt.compound(entry, "state");
                    String name = SuppressionNbt.stringValue(state.get("Name"), "Name");
                    Map<String, String> props = new HashMap<>();
                    if (state.containsKey("Properties")) {
                        for (var property : SuppressionNbt.compound(state.get("Properties"), "Properties").entrySet()) {
                            props.put(property.getKey(), SuppressionNbt.stringValue(property.getValue(), "property"));
                        }
                    }
                    palette.add(new LiveBuildProgress.State(name, props));
                }
                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
                int[] indices = SuppressionReferenceLitematic.unpack(SuppressionNbt.longArray(region.get("BlockStates"), "BlockStates"),
                    (int) volume, bits, palette.size());
                for (int i = 0; i < indices.length; i++) {
                    var state = palette.get(indices[i]);
                    if (state.isAir()) continue;
                    var pos = new LiveBuildProgress.Position(Math.addExact(ox, i % sx),
                        Math.addExact(oy, i / (sx * sz)), Math.addExact(oz, (i / sx) % sz));
                    if (occupied.putIfAbsent(pos, state) != null) throw new IOException("Overlapping non-air regions are ambiguous");
                    cells.add(new LiveBuildProgress.Cell(pos, state));
                }
            }
            if (cells.isEmpty()) throw new IOException("Schematic has no required blocks");
            var bounds=new Bounds(minX,minY,minZ,maxX,maxY,maxZ);
            var artBounds=bounds;
            // Legacy MapKluss exports identify the producer but predate explicit artwork bounds.
            if(regions.size()==1 && bounds.width()%128==0 && bounds.depth()>1 && (bounds.depth()-1)%128==0 && root.containsKey("Metadata")){
                var metadata=SuppressionNbt.compound(root.get("Metadata"),"Metadata");
                if(metadata.containsKey("Author") && "MapKluss".equals(SuppressionNbt.stringValue(metadata.get("Author"),"Author")))
                    artBounds=new Bounds(minX,minY,minZ+1,maxX,maxY,maxZ);
            }
            return new LiveBuildSchematic(SuppressionHashes.sha256(bytes),cells,bounds,artBounds);
        } catch (IllegalArgumentException | ArithmeticException error) {
            throw new IOException("Invalid schematic data", error);
        }
    }

    private static int dimension(int signed) throws IOException {
        if (signed == 0 || signed == Integer.MIN_VALUE || Math.abs(signed) > 8192) throw new IOException("Invalid region size");
        return Math.abs(signed);
    }

    private static String metadataString(Map<String, SuppressionNbt.Tag> metadata, String key) throws IOException {
        return metadata.containsKey(key) ? SuppressionNbt.stringValue(metadata.get(key), key) : "";
    }

    private static LiveBuildProgress.Position vector(SuppressionNbt.Tag tag) throws IOException {
        var data = SuppressionNbt.compound(tag, "vector");
        return new LiveBuildProgress.Position(SuppressionNbt.intValue(data.get("x"), "x"),
            SuppressionNbt.intValue(data.get("y"), "y"), SuppressionNbt.intValue(data.get("z"), "z"));
    }
}
