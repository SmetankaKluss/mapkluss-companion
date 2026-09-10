package art.mapkluss.companion;

import java.util.Arrays;
import java.util.List;

/** Bounded top-down sampling for the progress overview; it is not a map export renderer. */
public final class LiveBuildTopView {
    private final int width;
    private final int height;
    private final int[] indices;
    private final LiveBuildSchematic.Bounds bounds;
    private final long scale;
    private LiveBuildTopView(int width,int height){
        if(width<1||height<1||width>1280||height>1280)throw new IllegalArgumentException("Invalid overview size");
        this.width=width;this.height=height;scale=1;
        bounds=new LiveBuildSchematic.Bounds(0,0,0,width-1,0,height-1);
        indices=new int[0];
    }
    public static LiveBuildTopView overview(int width,int height){return new LiveBuildTopView(width,height);}

    public LiveBuildTopView(List<LiveBuildProgress.Cell> cells) {
        this(cells,LiveBuildSchematic.Bounds.occupied(cells));
    }
    public LiveBuildTopView(List<LiveBuildProgress.Cell> cells, LiveBuildSchematic.Bounds bounds) {
        this.bounds=bounds;
        int minX=bounds.minX(),minZ=bounds.minZ(),maxX=bounds.maxX(),maxZ=bounds.maxZ();
        long spanX = (long) maxX - minX + 1, spanZ = (long) maxZ - minZ + 1;
        if (cells.isEmpty() || spanX > 8192 || spanZ > 8192) throw new IllegalArgumentException("Schematic footprint exceeds 8192 blocks");
        scale = Math.max(1, (Math.max(spanX, spanZ) + 127) / 128);
        width = (int) ((spanX + scale - 1) / scale); height = (int) ((spanZ + scale - 1) / scale);
        indices = new int[width * height]; Arrays.fill(indices, -1);
        for (int i = 0; i < cells.size(); i++) {
            if(cells.get(i).requiresAir())continue;
            var p = cells.get(i).relativePosition();
            if(p.x()<minX||p.x()>maxX||p.z()<minZ||p.z()>maxZ)continue;
            int pixel = (int) (((long) p.z() - minZ) / scale) * width + (int) (((long) p.x() - minX) / scale);
            if (indices[pixel] < 0 || p.y() > cells.get(indices[pixel]).relativePosition().y()) indices[pixel] = i;
        }
    }
    public int width() { return width; }
    public int height() { return height; }
    public int index(int pixel) { return indices[pixel]; }
    public int pixelAt(int x,int z) {
        if(x<bounds.minX()||x>bounds.maxX()||z<bounds.minZ()||z>bounds.maxZ())return -1;
        return (int)(((long)z-bounds.minZ())/scale)*width+(int)(((long)x-bounds.minX())/scale);
    }
}
