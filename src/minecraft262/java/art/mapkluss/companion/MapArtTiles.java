package art.mapkluss.companion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class MapArtTiles {
    private MapArtTiles() {
    }

    static Integer mapId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.FILLED_MAP)) return null;
        MapId component = stack.get(DataComponents.MAP_ID);
        return component == null ? null : component.id();
    }

    static Optional<MapArtLayoutSolver.Tile> fromStack(Minecraft client, ItemStack stack) {
        if (client.level == null || stack == null || stack.isEmpty() || !stack.is(Items.FILLED_MAP)) {
            return Optional.empty();
        }
        Integer mapId = mapId(stack);
        MapItemSavedData state = MapItem.getSavedData(stack, client.level);
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
