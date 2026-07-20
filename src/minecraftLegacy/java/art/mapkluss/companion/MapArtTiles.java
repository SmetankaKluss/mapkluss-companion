package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MapArtTiles {
    private MapArtTiles() {
    }

    static Integer mapId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) return null;
        MapIdComponent component = stack.get(DataComponentTypes.MAP_ID);
        return component == null ? null : component.id();
    }

    static Optional<MapArtLayoutSolver.Tile> fromStack(MinecraftClient client, ItemStack stack) {
        if (client.world == null || stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP)) {
            return Optional.empty();
        }
        Integer mapId = mapId(stack);
        MapState state = FilledMapItem.getMapState(stack, client.world);
        if (mapId == null || state == null) return Optional.empty();
        return Optional.of(new MapArtLayoutSolver.Tile(
            mapId,
            MapColorFingerprint.sha256(state.colors),
            MapScanService.argbFromMapState(state)
        ));
    }

    static List<MapArtLayoutSolver.Tile> uniqueByMapId(List<MapArtLayoutSolver.Tile> tiles) {
        Map<Integer, MapArtLayoutSolver.Tile> unique = new LinkedHashMap<>();
        for (MapArtLayoutSolver.Tile tile : tiles) unique.putIfAbsent(tile.mapId(), tile);
        return List.copyOf(unique.values());
    }

}
