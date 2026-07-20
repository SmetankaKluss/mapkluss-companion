package art.mapkluss.companion;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class MapScanService {
    private static final int MAP_SIZE = MapScanAssembler.MAP_SIZE;
    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private MapScanService() {
    }

    public static MapScanDraft scanHeldMap(Minecraft client) throws IOException {
        if (client.player == null || client.level == null) {
            throw new IOException("Open a world before scanning.");
        }
        ItemStack stack = client.player.getMainHandItem();
        if (!stack.is(Items.FILLED_MAP)) {
            stack = client.player.getOffhandItem();
        }
        if (!stack.is(Items.FILLED_MAP)) {
            throw new IOException("Hold a filled map in either hand.");
        }
        return scanStack(client, stack, "hand");
    }

    public static MapScanDraft scanTargetFrame(Minecraft client) throws IOException {
        ItemFrame frame = targetMapFrame(client);
        return scanStack(client, frame.getItem(), "frame");
    }

    public static MapScanDraft scanTargetWall(Minecraft client) throws IOException {
        if (client.level == null) throw new IOException("Open a world before scanning.");
        ItemFrame target = targetMapFrame(client);
        Direction facing = target.getDirection();
        FrameWallGeometry.ScanCoord origin = FrameWallGeometry.fromScanBlockPos(target.getPos(), facing);
        Map<FrameWallGeometry.ScanCoord, ItemFrame> frames = mapFramesOnPlane(client, target.getBoundingBox().inflate(64.0), facing, origin.plane());
        frames.putIfAbsent(origin, target);

        Set<FrameWallGeometry.ScanCoord> component = connectedComponent(frames, origin);
        if (component.isEmpty()) throw new IOException("No connected map frames found.");

        int minX = component.stream().mapToInt(FrameWallGeometry.ScanCoord::x).min().orElse(origin.x());
        int maxX = component.stream().mapToInt(FrameWallGeometry.ScanCoord::x).max().orElse(origin.x());
        int minY = component.stream().mapToInt(FrameWallGeometry.ScanCoord::y).min().orElse(origin.y());
        int maxY = component.stream().mapToInt(FrameWallGeometry.ScanCoord::y).max().orElse(origin.y());
        return scanRectangle(client, frames, origin.plane(), minX, maxX, minY, maxY, "wall");
    }

    public static MapFrameCorner captureTargetCorner(Minecraft client) throws IOException {
        ItemFrame target = targetMapFrame(client);
        Direction facing = target.getDirection();
        return FrameWallGeometry.scanCorner(target.getPos(), facing);
    }

    public static MapScanDraft scanWallBetweenCorners(Minecraft client, MapFrameCorner first, MapFrameCorner second) throws IOException {
        if (client.level == null || client.player == null) throw new IOException("Open a world before scanning.");
        if (first == null || second == null) throw new IOException("Select two corner frames first.");
        if (!first.samePlane(second)) throw new IOException("Both corners must be on the same wall face.");

        int minX = Math.min(first.x(), second.x());
        int maxX = Math.max(first.x(), second.x());
        int minY = Math.min(first.y(), second.y());
        int maxY = Math.max(first.y(), second.y());
        AABB searchBox = new AABB(client.player.blockPosition()).inflate(Math.max(16.0, Math.max(maxX - minX + 8.0, maxY - minY + 8.0)));
        Map<FrameWallGeometry.ScanCoord, ItemFrame> frames = mapFramesOnPlane(client, searchBox, first.facing(), first.plane());
        return scanRectangle(client, frames, first.plane(), minX, maxX, minY, maxY, "manual_wall");
    }

    private static MapScanDraft scanRectangle(
        Minecraft client,
        Map<FrameWallGeometry.ScanCoord, ItemFrame> frames,
        int plane,
        int minX,
        int maxX,
        int minY,
        int maxY,
        String source
    ) throws IOException {
        int wide = maxX - minX + 1;
        int tall = maxY - minY + 1;
        int missing = 0;
        List<MapScanAssembler.Tile> tiles = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                FrameWallGeometry.ScanCoord coord = new FrameWallGeometry.ScanCoord(plane, x, y);
                ItemFrame frame = frames.get(coord);
                if (frame == null) {
                    missing++;
                    continue;
                }
                MapItemSavedData state = MapItem.getSavedData(frame.getItem(), client.level);
                if (state == null) {
                    missing++;
                    continue;
                }
                tiles.add(new MapScanAssembler.Tile(x - minX, maxY - y, argbFromMapState(state)));
            }
        }
        String title = "scan-" + source + "-" + LocalDateTime.now().format(TITLE_TIME);
        return new MapScanDraft(title, source, wide, tall, missing, MapScanAssembler.assemblePng(tiles, wide, tall));
    }

    public static int[] argbFromMapState(MapItemSavedData state) {
        int[] argb = new int[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < argb.length; i++) {
            int colorByte = state.colors[i] & 255;
            argb[i] = colorByte == 0 ? 0x00000000 : MapColor.getColorFromPackedId(colorByte);
        }
        return argb;
    }

    public static byte[] pngFromMapState(MapItemSavedData state) throws IOException {
        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int colorByte = state.colors[x + y * MAP_SIZE] & 255;
                image.setRGB(x, y, colorByte == 0 ? 0x00000000 : MapColor.getColorFromPackedId(colorByte));
            }
        }
        return MapScanAssembler.writePng(image);
    }

    private static MapScanDraft scanStack(Minecraft client, ItemStack stack, String source) throws IOException {
        MapItemSavedData state = MapItem.getSavedData(stack, client.level);
        if (state == null) {
            throw new IOException("Map data is not loaded on the client yet.");
        }
        String title = "scan-" + source + "-" + LocalDateTime.now().format(TITLE_TIME);
        return new MapScanDraft(title, source, 1, 1, 0, pngFromMapState(state));
    }

    private static ItemFrame targetMapFrame(Minecraft client) throws IOException {
        if (client.level == null || client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) {
            throw new IOException("Look at an item frame with a filled map.");
        }
        Entity entity = ((EntityHitResult) client.hitResult).getEntity();
        if (!(entity instanceof ItemFrame frame) || !frame.hasFramedMap()) {
            throw new IOException("Look at an item frame with a filled map.");
        }
        return frame;
    }

    private static Map<FrameWallGeometry.ScanCoord, ItemFrame> mapFramesOnPlane(Minecraft client, AABB searchBox, Direction facing, int plane) {
        Map<FrameWallGeometry.ScanCoord, ItemFrame> frames = new HashMap<>();
        if (client.level == null) return frames;
        for (ItemFrame frame : client.level.getEntitiesOfClass(ItemFrame.class, searchBox, frame -> frame.hasFramedMap() && frame.getDirection() == facing)) {
            FrameWallGeometry.ScanCoord coord = FrameWallGeometry.fromScanBlockPos(frame.getPos(), facing);
            if (coord.plane() == plane) frames.putIfAbsent(coord, frame);
        }
        return frames;
    }

    private static Set<FrameWallGeometry.ScanCoord> connectedComponent(Map<FrameWallGeometry.ScanCoord, ItemFrame> frames, FrameWallGeometry.ScanCoord origin) {
        Set<FrameWallGeometry.ScanCoord> visited = new HashSet<>();
        ArrayDeque<FrameWallGeometry.ScanCoord> queue = new ArrayDeque<>();
        if (!frames.containsKey(origin)) return visited;
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            FrameWallGeometry.ScanCoord coord = queue.removeFirst();
            for (FrameWallGeometry.ScanCoord next : coord.neighbors()) {
                if (frames.containsKey(next) && visited.add(next)) queue.add(next);
            }
        }
        return visited;
    }

}
