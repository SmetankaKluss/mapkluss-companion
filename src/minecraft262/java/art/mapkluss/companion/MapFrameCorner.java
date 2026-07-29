package art.mapkluss.companion;

import net.minecraft.core.Direction;

public record MapFrameCorner(Direction facing, Direction planeUp, int plane, int x, int y) {
    public MapFrameCorner(Direction facing, int plane, int x, int y) {
        this(facing, FrameWallGeometry.defaultPlaneUp(facing), plane, x, y);
    }

    public boolean samePlane(MapFrameCorner other) {
        return other != null && facing == other.facing() && planeUp == other.planeUp() && plane == other.plane();
    }

    public String label() {
        return facing.getSerializedName().charAt(0) + " " + x + "," + y;
    }
}
