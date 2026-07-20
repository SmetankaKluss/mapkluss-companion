package art.mapkluss.companion;

import net.minecraft.core.Direction;

public record MapFrameCorner(Direction facing, int plane, int x, int y) {
    public boolean samePlane(MapFrameCorner other) {
        return other != null && facing == other.facing() && plane == other.plane();
    }

    public String label() {
        return facing.getSerializedName().charAt(0) + " " + x + "," + y;
    }
}
