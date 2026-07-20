package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class MapStackManager {
    private static final MapStackManager INSTANCE = new MapStackManager();
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int PRELOAD_TIMEOUT_TICKS = 80;
    private static final int MAX_MEMORY_PREVIEWS = 512;

    private final LinkedHashMap<PreviewKey, MapPreviewStore.Entry> previews = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<Integer> updatedMapIds = new HashSet<>();
    private final Set<Integer> validatedMapIds = new HashSet<>();
    private final Set<Integer> failedMapIds = new HashSet<>();
    private Map<Integer, Decoration> decorations = Map.of();
    private Path runDirectory;
    private MapPreviewStore previewStore;
    private String connectionKey = "";
    private Object activeWorld;
    private AbstractContainerMenu handler;
    private Preload preload;
    private int scanTicks;
    private boolean dirty = true;

    private MapStackManager() {
    }

    public static MapStackManager instance() {
        return INSTANCE;
    }

    public synchronized void tick(Minecraft client) {
        initialize(client);
        if (client.level != activeWorld) {
            cancelPreload(client, true);
            activeWorld = client.level;
            validatedMapIds.clear();
            failedMapIds.clear();
            updatedMapIds.clear();
            dirty = true;
        }
        String nextConnection = client.level == null ? "" : AutoFrameManager.instance().connectionKey(client);
        if (!nextConnection.equals(connectionKey)) {
            cancelPreload(client, true);
            connectionKey = nextConnection;
            validatedMapIds.clear();
            failedMapIds.clear();
            updatedMapIds.clear();
            handler = null;
            decorations = Map.of();
            dirty = true;
        }
        if (client.player == null || client.level == null || client.gameMode == null) {
            cancelPreload(client, true);
            handler = null;
            decorations = Map.of();
            return;
        }

        tickPreload(client);
        AbstractContainerMenu nextHandler = client.player.containerMenu;
        if (handler != nextHandler) {
            cancelPreload(client, true);
            handler = nextHandler;
            dirty = true;
        }
        if (scanTicks > 0) scanTicks--;
        if (dirty || scanTicks == 0) {
            scan(client, nextHandler);
            scanTicks = SCAN_INTERVAL_TICKS;
            dirty = false;
        }
        if (preload == null && client.gui.screen() instanceof AbstractContainerScreen<?>) beginPreload(client, nextHandler);
    }

    public synchronized void onMapUpdate(int mapId) {
        if (mapId >= 0) {
            updatedMapIds.add(mapId);
            dirty = true;
        }
    }

    public synchronized void onScreenRemoved(Minecraft client, AbstractContainerMenu closingHandler) {
        if (handler == closingHandler) {
            cancelPreload(client, true);
            handler = null;
            decorations = Map.of();
            dirty = true;
        }
    }

    synchronized Optional<MapPreviewStore.Entry> preview(Minecraft client, ItemStack stack) {
        initialize(client);
        Integer mapId = MapArtTiles.mapId(stack);
        if (mapId == null || client.level == null) return Optional.empty();
        String connection = AutoFrameManager.instance().connectionKey(client);
        return cachedPreview(connection, mapId);
    }

    public synchronized Optional<Decoration> decoration(ItemStack stack) {
        Integer mapId = MapArtTiles.mapId(stack);
        return mapId == null ? Optional.empty() : Optional.ofNullable(decorations.get(mapId));
    }

    private void scan(Minecraft client, AbstractContainerMenu currentHandler) {
        if (currentHandler == null || connectionKey.isBlank()) {
            decorations = Map.of();
            return;
        }
        List<MapStackRecognition.Observation> observations = new ArrayList<>();
        for (Slot slot : currentHandler.slots) {
            ItemStack stack = slot.getItem();
            Integer mapId = MapArtTiles.mapId(stack);
            if (mapId == null) continue;
            Optional<MapPreviewStore.Entry> cached = cachedPreview(connectionKey, mapId);
            Optional<MapPreviewStore.Entry> preview = cached;
            if (cached.isEmpty() || updatedMapIds.contains(mapId)) {
                preview = livePreview(client, stack, mapId, connectionKey).or(() -> cached);
            }
            observations.add(new MapStackRecognition.Observation(
                mapId, preview.map(MapPreviewStore.Entry::hash).orElse(null)
            ));
        }
        Map<Integer, AutoFrameManager.MapIdentity> identities = AutoFrameManager.instance().identifyMaps(client, observations);
        Map<String, Integer> colors = MapGroupColors.assign(identities.values().stream()
            .map(AutoFrameManager.MapIdentity::groupKey).toList());
        Map<Integer, Decoration> next = new HashMap<>();
        for (Map.Entry<Integer, AutoFrameManager.MapIdentity> entry : identities.entrySet()) {
            AutoFrameManager.MapIdentity identity = entry.getValue();
            int color = colors.getOrDefault(identity.groupKey(), 0xFF57FF6E);
            next.put(entry.getKey(), new Decoration(
                identity.groupKey(), identity.title(), identity.tileNumber(), color
            ));
        }
        decorations = Map.copyOf(next);
    }

    private Optional<MapPreviewStore.Entry> livePreview(
        Minecraft client,
        ItemStack stack,
        int mapId,
        String connection
    ) {
        if (client.level == null || stack == null || !stack.is(Items.FILLED_MAP)) return Optional.empty();
        MapItemSavedData state = MapItem.getSavedData(stack, client.level);
        if (state == null || state.colors == null || state.colors.length != MapPreviewStore.MAP_PIXELS) {
            return Optional.empty();
        }
        byte[] colors = state.colors.clone();
        MapPreviewStore.Entry entry = new MapPreviewStore.Entry(mapId, MapColorFingerprint.sha256(colors), colors);
        rememberPreview(connection, entry);
        updatedMapIds.remove(mapId);
        validatedMapIds.add(mapId);
        failedMapIds.remove(mapId);
        return Optional.of(entry);
    }

    private Optional<MapPreviewStore.Entry> cachedPreview(String connection, int mapId) {
        if (previewStore == null || connection == null || connection.isBlank()) return Optional.empty();
        PreviewKey key = new PreviewKey(connection, mapId);
        MapPreviewStore.Entry memory = previews.get(key);
        if (memory != null) return Optional.of(memory);
        try {
            Optional<MapPreviewStore.Entry> loaded = previewStore.read(connection, mapId);
            loaded.ifPresent(entry -> putMemory(key, entry));
            return loaded;
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not read cached map preview {}.", mapId, error);
            return Optional.empty();
        }
    }

    private void rememberPreview(String connection, MapPreviewStore.Entry entry) {
        if (previewStore == null || connection == null || connection.isBlank()) return;
        PreviewKey key = new PreviewKey(connection, entry.mapId());
        MapPreviewStore.Entry existing = previews.get(key);
        if (existing != null && existing.hash().equals(entry.hash())) return;
        putMemory(key, entry);
        try {
            previewStore.write(connection, entry);
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not persist map preview {}.", entry.mapId(), error);
        }
        dirty = true;
    }

    private void putMemory(PreviewKey key, MapPreviewStore.Entry entry) {
        previews.put(key, entry);
        while (previews.size() > MAX_MEMORY_PREVIEWS) previews.remove(previews.firstEntry().getKey());
    }

    private void beginPreload(Minecraft client, AbstractContainerMenu currentHandler) {
        if (currentHandler == null || !currentHandler.getCarried().isEmpty() || client.player == null) return;
        int selectedHotbar = client.player.getInventory().getSelectedSlot();
        Slot hotbar = hotbarSlot(client, currentHandler, selectedHotbar);
        if (hotbar == null) return;
        for (Slot source : currentHandler.slots) {
            ItemStack sourceStack = source.getItem();
            Integer mapId = MapArtTiles.mapId(sourceStack);
            if (mapId == null || source == hotbar || isHotbarSlot(client, source)) continue;
            if (validatedMapIds.contains(mapId) || failedMapIds.contains(mapId)
                || livePreview(client, sourceStack, mapId, connectionKey).isPresent()) continue;
            ItemStack hotbarStack = hotbar.getItem();
            if (!source.mayPickup(client.player) || !hotbar.mayPickup(client.player)
                || !hotbar.mayPlace(sourceStack) || (!hotbarStack.isEmpty() && !source.mayPlace(hotbarStack))) continue;
            preload = new Preload(
                currentHandler,
                source.index,
                hotbar.index,
                selectedHotbar,
                mapId,
                sourceStack.copy(),
                hotbarStack.copy(),
                PRELOAD_TIMEOUT_TICKS
            );
            client.gameMode.handleContainerInput(
                currentHandler.containerId, source.index, selectedHotbar, ContainerInput.SWAP, client.player
            );
            return;
        }
    }

    private void tickPreload(Minecraft client) {
        if (preload == null) return;
        if (client.player == null || client.gameMode == null
            || client.player.containerMenu != preload.handler()) {
            cancelPreload(client, true);
            return;
        }
        Slot source = slot(preload.handler(), preload.sourceSlotId());
        Slot hotbar = slot(preload.handler(), preload.hotbarSlotId());
        if (source == null || hotbar == null) {
            preload = null;
            return;
        }
        if (preload.restoring()) {
            if (ItemStack.matches(source.getItem(), preload.sourceBefore())
                && ItemStack.matches(hotbar.getItem(), preload.hotbarBefore())) {
                preload = null;
                dirty = true;
                return;
            }
        } else if (preload.mapId().equals(MapArtTiles.mapId(hotbar.getItem()))
            && ItemStack.matches(source.getItem(), preload.hotbarBefore())) {
            if (livePreview(client, hotbar.getItem(), preload.mapId(), connectionKey).isPresent()) {
                restorePreload(client, source, hotbar);
                return;
            }
        } else if (ItemStack.matches(source.getItem(), preload.sourceBefore())
            && ItemStack.matches(hotbar.getItem(), preload.hotbarBefore())) {
            // Wait for the server-confirmed swap.
        } else {
            AutoFrameManager.instance().showStatus("Прогрузка карт остановлена: содержимое инвентаря изменилось");
            preload = null;
            dirty = true;
            return;
        }
        preload = preload.tick();
        if (preload.ticksLeft() <= 0) {
            if (preload.restoring()) {
                AutoFrameManager.instance().showStatus("Не удалось прогрузить изображение одной из карт");
                failedMapIds.add(preload.mapId());
                preload = null;
                dirty = true;
            } else {
                AutoFrameManager.instance().showStatus("Не удалось прогрузить изображение одной из карт");
                failedMapIds.add(preload.mapId());
                restorePreload(client, source, hotbar);
            }
        }
    }

    private void restorePreload(Minecraft client, Slot source, Slot hotbar) {
        if (preload == null || preload.restoring()) return;
        if (ItemStack.matches(source.getItem(), preload.hotbarBefore())
            && preload.mapId().equals(MapArtTiles.mapId(hotbar.getItem()))) {
            client.gameMode.handleContainerInput(
                preload.handler().containerId,
                preload.sourceSlotId(),
                preload.hotbarIndex(),
                ContainerInput.SWAP,
                client.player
            );
            preload = preload.beginRestore();
        } else {
            preload = null;
            dirty = true;
        }
    }

    private void cancelPreload(Minecraft client, boolean restore) {
        if (preload == null) return;
        if (restore && client.player != null && client.gameMode != null
            && client.player.containerMenu == preload.handler()) {
            Slot source = slot(preload.handler(), preload.sourceSlotId());
            Slot hotbar = slot(preload.handler(), preload.hotbarSlotId());
            if (source != null && hotbar != null) restorePreload(client, source, hotbar);
        }
        preload = null;
        dirty = true;
    }

    private Slot hotbarSlot(Minecraft client, AbstractContainerMenu currentHandler, int hotbarIndex) {
        return currentHandler.slots.stream().filter(slot ->
            slot.container == client.player.getInventory() && slot.getContainerSlot() == hotbarIndex
        ).findFirst().orElse(null);
    }

    private boolean isHotbarSlot(Minecraft client, Slot slot) {
        return slot.container == client.player.getInventory() && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9;
    }

    private static Slot slot(AbstractContainerMenu handler, int id) {
        return handler.slots.stream().filter(candidate -> candidate.index == id).findFirst().orElse(null);
    }

    private void initialize(Minecraft client) {
        Path next = client.gameDirectory.toPath().toAbsolutePath().normalize();
        if (next.equals(runDirectory)) return;
        runDirectory = next;
        previewStore = new MapPreviewStore(LitematicaPaths.mapPreviewCacheDir(next));
        previews.clear();
        updatedMapIds.clear();
        validatedMapIds.clear();
        failedMapIds.clear();
        decorations = Map.of();
        dirty = true;
    }

    public record Decoration(String groupKey, String title, int tileNumber, int color) {
    }

    private record PreviewKey(String connectionKey, int mapId) {
    }

    private record Preload(
        AbstractContainerMenu handler,
        int sourceSlotId,
        int hotbarSlotId,
        int hotbarIndex,
        Integer mapId,
        ItemStack sourceBefore,
        ItemStack hotbarBefore,
        int ticksLeft,
        boolean restoring
    ) {
        private Preload(
            AbstractContainerMenu handler,
            int sourceSlotId,
            int hotbarSlotId,
            int hotbarIndex,
            Integer mapId,
            ItemStack sourceBefore,
            ItemStack hotbarBefore,
            int ticksLeft
        ) {
            this(handler, sourceSlotId, hotbarSlotId, hotbarIndex, mapId, sourceBefore, hotbarBefore, ticksLeft, false);
        }

        private Preload {
            sourceBefore = sourceBefore.copy();
            hotbarBefore = hotbarBefore.copy();
        }

        private Preload tick() {
            return new Preload(
                handler, sourceSlotId, hotbarSlotId, hotbarIndex, mapId,
                sourceBefore, hotbarBefore, ticksLeft - 1, restoring
            );
        }

        private Preload beginRestore() {
            return new Preload(
                handler, sourceSlotId, hotbarSlotId, hotbarIndex, mapId,
                sourceBefore, hotbarBefore, 20, true
            );
        }
    }
}
