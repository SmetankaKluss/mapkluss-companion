package art.mapkluss.companion;

import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class MapScanService {
    private static final int MAP_SIZE = MapScanAssembler.MAP_SIZE;
    private static final DateTimeFormatter TITLE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private MapScanService() {
    }

    public static MapScanDraft scanHeldMap(MinecraftClient client) throws IOException {
        if (client.player == null || client.world == null) {
            throw new IOException("Open a world before scanning.");
        }
        ItemStack stack = client.player.getMainHandStack();
        if (!stack.isOf(Items.FILLED_MAP)) {
            stack = client.player.getOffHandStack();
        }
        if (!stack.isOf(Items.FILLED_MAP)) {
            throw new IOException("Hold a filled map in either hand.");
        }
        return scanStack(client, stack, 0, "hand");
    }

    public static MapScanDraft scanTargetFrame(MinecraftClient client) throws IOException {
        ItemFrameEntity frame = targetMapFrame(client);
        return scanStack(client, frame.getHeldItemStack(), frame.getRotation(), "frame");
    }

    public static MapScanDraft scanTargetWall(MinecraftClient client) throws IOException {
        if (client.world == null || client.player == null) throw new IOException("Open a world before scanning.");
        ItemFrameEntity target = targetMapFrame(client);
        Direction facing = target.getHorizontalFacing();
        Direction planeUp = AutoFramePlacement.planeUpFromPlayerView(facing, client.player.getHorizontalFacing());
        FrameWallGeometry.ScanCoord origin = FrameWallGeometry.fromScanBlockPos(target.getAttachedBlockPos(), facing, planeUp);
        Map<FrameWallGeometry.ScanCoord, ItemFrameEntity> frames = mapFramesOnPlane(
            client, target.getBoundingBox().expand(64.0), facing, planeUp, origin.plane());
        frames.putIfAbsent(origin, target);

        Set<FrameWallGeometry.ScanCoord> component = connectedComponent(frames.keySet(), origin);
        if (component.isEmpty()) throw new IOException("No connected map frames found.");
        Set<FrameWallGeometry.ScanCoord> filled = new HashSet<>();
        for (FrameWallGeometry.ScanCoord coord : component) {
            ItemFrameEntity frame = frames.get(coord);
            if (frame != null && frame.containsMap()) filled.add(coord);
        }
        if (filled.isEmpty()) throw new IOException("No filled map frames found.");

        int minX = filled.stream().mapToInt(FrameWallGeometry.ScanCoord::x).min().orElse(origin.x());
        int maxX = filled.stream().mapToInt(FrameWallGeometry.ScanCoord::x).max().orElse(origin.x());
        int minY = filled.stream().mapToInt(FrameWallGeometry.ScanCoord::y).min().orElse(origin.y());
        int maxY = filled.stream().mapToInt(FrameWallGeometry.ScanCoord::y).max().orElse(origin.y());
        return scanRectangle(client, frames, origin.plane(), minX, maxX, minY, maxY, "wall");
    }

    public static MapFrameCorner captureTargetCorner(MinecraftClient client) throws IOException {
        return captureTargetCorner(client, null);
    }

    public static MapFrameCorner captureTargetCorner(MinecraftClient client, MapFrameCorner basis) throws IOException {
        if (client.player == null) throw new IOException("Open a world before scanning.");
        ItemFrameEntity target = targetMapFrame(client);
        Direction facing = target.getHorizontalFacing();
        Direction planeUp = basis != null && basis.facing() == facing
            ? basis.planeUp()
            : AutoFramePlacement.planeUpFromPlayerView(facing, client.player.getHorizontalFacing());
        return FrameWallGeometry.scanCorner(target.getAttachedBlockPos(), facing, planeUp);
    }

    public static MapScanDraft scanWallBetweenCorners(MinecraftClient client, MapFrameCorner first, MapFrameCorner second) throws IOException {
        if (client.world == null || client.player == null) throw new IOException("Open a world before scanning.");
        if (first == null || second == null) throw new IOException("Select two corner frames first.");
        if (!first.samePlane(second)) throw new IOException("Both corners must be on the same wall face.");

        int minX = Math.min(first.x(), second.x());
        int maxX = Math.max(first.x(), second.x());
        int minY = Math.min(first.y(), second.y());
        int maxY = Math.max(first.y(), second.y());
        Box searchBox = new Box(client.player.getBlockPos()).expand(Math.max(16.0, Math.max(maxX - minX + 8.0, maxY - minY + 8.0)));
        Map<FrameWallGeometry.ScanCoord, ItemFrameEntity> frames = mapFramesOnPlane(
            client, searchBox, first.facing(), first.planeUp(), first.plane());
        return scanRectangle(client, frames, first.plane(), minX, maxX, minY, maxY, "manual_wall");
    }

    private static MapScanDraft scanRectangle(
        MinecraftClient client,
        Map<FrameWallGeometry.ScanCoord, ItemFrameEntity> frames,
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
                ItemFrameEntity frame = frames.get(coord);
                if (frame == null || !frame.containsMap()) {
                    missing++;
                    continue;
                }
                MapState state = FilledMapItem.getMapState(frame.getHeldItemStack(), client.world);
                if (state == null) {
                    missing++;
                    continue;
                }
                tiles.add(new MapScanAssembler.Tile(
                    x - minX,
                    maxY - y,
                    MapScanAssembler.rotateTileClockwise(argbFromMapState(state), frame.getRotation())
                ));
            }
        }
        String title = "scan-" + source + "-" + LocalDateTime.now().format(TITLE_TIME);
        return new MapScanDraft(title, source, wide, tall, missing, MapScanAssembler.assemblePng(tiles, wide, tall));
    }

    public static int[] argbFromMapState(MapState state) {
        int[] argb = new int[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < argb.length; i++) {
            int colorByte = state.colors[i] & 255;
            argb[i] = colorByte == 0 ? 0x00000000 : MapColor.getRenderColor(colorByte);
        }
        return argb;
    }

    public static byte[] pngFromMapState(MapState state) throws IOException {
        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                int colorByte = state.colors[x + y * MAP_SIZE] & 255;
                image.setRGB(x, y, colorByte == 0 ? 0x00000000 : MapColor.getRenderColor(colorByte));
            }
        }
        return MapScanAssembler.writePng(image);
    }

    private static MapScanDraft scanStack(MinecraftClient client, ItemStack stack, int rotation, String source) throws IOException {
        MapState state = FilledMapItem.getMapState(stack, client.world);
        if (state == null) {
            throw new IOException("Map data is not loaded on the client yet.");
        }
        String title = "scan-" + source + "-" + LocalDateTime.now().format(TITLE_TIME);
        int[] rotated = MapScanAssembler.rotateTileClockwise(argbFromMapState(state), rotation);
        return new MapScanDraft(title, source, 1, 1, 0,
            MapScanAssembler.assemblePng(List.of(new MapScanAssembler.Tile(0, 0, rotated)), 1, 1));
    }

    private static ItemFrameEntity targetMapFrame(MinecraftClient client) throws IOException {
        if (client.world == null || client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
            throw new IOException("Look at an item frame with a filled map.");
        }
        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (!(entity instanceof ItemFrameEntity frame) || !frame.containsMap()) {
            throw new IOException("Look at an item frame with a filled map.");
        }
        return frame;
    }

    private static Map<FrameWallGeometry.ScanCoord, ItemFrameEntity> mapFramesOnPlane(
        MinecraftClient client, Box searchBox, Direction facing, Direction planeUp, int plane
    ) {
        Map<FrameWallGeometry.ScanCoord, ItemFrameEntity> frames = new HashMap<>();
        if (client.world == null) return frames;
        for (ItemFrameEntity frame : client.world.getEntitiesByClass(
            ItemFrameEntity.class, searchBox, frame -> frame.getHorizontalFacing() == facing)) {
            FrameWallGeometry.ScanCoord coord = FrameWallGeometry.fromScanBlockPos(frame.getAttachedBlockPos(), facing, planeUp);
            if (coord.plane() == plane) frames.putIfAbsent(coord, frame);
        }
        return frames;
    }

    static Set<FrameWallGeometry.ScanCoord> connectedComponent(
        Set<FrameWallGeometry.ScanCoord> framePositions, FrameWallGeometry.ScanCoord origin
    ) {
        Set<FrameWallGeometry.ScanCoord> visited = new HashSet<>();
        ArrayDeque<FrameWallGeometry.ScanCoord> queue = new ArrayDeque<>();
        if (!framePositions.contains(origin)) return visited;
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            FrameWallGeometry.ScanCoord coord = queue.removeFirst();
            for (FrameWallGeometry.ScanCoord next : coord.neighbors()) {
                if (framePositions.contains(next) && visited.add(next)) queue.add(next);
            }
        }
        return visited;
    }

}
