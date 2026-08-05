package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

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

public final class MapStackManager {
    private static final MapStackManager INSTANCE = new MapStackManager();
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final int PRELOAD_TIMEOUT_TICKS = 80;
    private static final int MAX_MEMORY_PREVIEWS = 512;

    private final LinkedHashMap<PreviewKey, MapPreviewStore.Entry> previews = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<Integer> updatedMapIds = new HashSet<>();
    private final Set<Integer> validatedMapIds = new HashSet<>();
    private final Set<Integer> failedMapIds = new HashSet<>();
    private final MapStackScanGate scanGate = new MapStackScanGate();
    private Map<Integer, Decoration> decorations = Map.of();
    private Path runDirectory;
    private MapPreviewStore previewStore;
    private String connectionKey = "";
    private Object activeWorld;
    private ScreenHandler handler;
    private Preload preload;
    private ScreenHandler recognitionHandler;
    private boolean recognitionRequested;
    private int scanTicks;
    private boolean dirty = true;

    private MapStackManager() {
    }

    public static MapStackManager instance() {
        return INSTANCE;
    }

    public synchronized void tick(MinecraftClient client) {
        initialize(client);
        if (client.world != activeWorld) {
            cancelPreload(client, true);
            activeWorld = client.world;
            validatedMapIds.clear();
            failedMapIds.clear();
            updatedMapIds.clear();
            recognitionHandler = null;
            recognitionRequested = false;
            scanGate.reset();
            dirty = true;
        }
        String nextConnection = client.world == null ? "" : AutoFrameManager.instance().connectionKey(client);
        if (!nextConnection.equals(connectionKey)) {
            cancelPreload(client, true);
            connectionKey = nextConnection;
            validatedMapIds.clear();
            failedMapIds.clear();
            updatedMapIds.clear();
            handler = null;
            recognitionHandler = null;
            recognitionRequested = false;
            decorations = Map.of();
            scanGate.reset();
            dirty = true;
        }
        if (client.player == null || client.world == null || client.interactionManager == null) {
            cancelPreload(client, true);
            handler = null;
            recognitionHandler = null;
            recognitionRequested = false;
            decorations = Map.of();
            return;
        }

        tickPreload(client);
        ScreenHandler nextHandler = client.player.currentScreenHandler;
        if (handler != nextHandler) {
            cancelPreload(client, true);
            handler = nextHandler;
            dirty = true;
        }
        if (scanTicks > 0) scanTicks--;
        if (dirty || scanTicks == 0) {
            int[] slotMapIds = slotMapIds(nextHandler);
            long mappingRevision = AutoFrameManager.instance().mapMappingRevision();
            if (scanGate.shouldScan(slotMapIds, mappingRevision, dirty)) {
                scan(client, nextHandler);
                scanGate.markScanned(slotMapIds, AutoFrameManager.instance().mapMappingRevision());
            }
            scanTicks = SCAN_INTERVAL_TICKS;
            dirty = false;
        }
        boolean handledScreenOpen = client.currentScreen instanceof HandledScreen<?>;
        if (recognitionRequested) {
            if (!handledScreenOpen || recognitionHandler != nextHandler) {
                recognitionHandler = null;
                recognitionRequested = false;
                AutoFrameManager.instance().showStatus("Распознавание карт отменено");
            } else if (preload == null) {
                beginPreload(client, nextHandler);
                if (preload == null) finishRecognition(client, nextHandler);
            }
        }
    }

    public synchronized void onMapUpdate(int mapId) {
        if (mapId >= 0) {
            updatedMapIds.add(mapId);
            dirty = true;
        }
    }

    public synchronized void requestRecognition(MinecraftClient client, ScreenHandler currentHandler) {
        initialize(client);
        if (client.player == null || client.world == null || currentHandler == null
            || client.player.currentScreenHandler != currentHandler) {
            AutoFrameManager.instance().showStatus("Откройте инвентарь или хранилище с картами");
            return;
        }
        recognitionHandler = currentHandler;
        recognitionRequested = true;
        failedMapIds.clear();
        dirty = true;
        AutoFrameManager.instance().showStatus("Загружаю и распознаю карты…");
    }

    public synchronized void onScreenRemoved(MinecraftClient client, ScreenHandler closingHandler) {
        if (handler == closingHandler) {
            cancelPreload(client, true);
            handler = null;
            recognitionHandler = null;
            recognitionRequested = false;
            decorations = Map.of();
            scanGate.reset();
            dirty = true;
        }
    }

    synchronized Optional<MapPreviewStore.Entry> preview(MinecraftClient client, ItemStack stack) {
        initialize(client);
        Integer mapId = MapArtTiles.mapId(stack);
        if (mapId == null || client.world == null) return Optional.empty();
        String connection = AutoFrameManager.instance().connectionKey(client);
        return cachedPreview(connection, mapId);
    }

    public synchronized Optional<Decoration> decoration(ItemStack stack) {
        Integer mapId = MapArtTiles.mapId(stack);
        return mapId == null ? Optional.empty() : Optional.ofNullable(decorations.get(mapId));
    }

    private void scan(MinecraftClient client, ScreenHandler currentHandler) {
        if (currentHandler == null || connectionKey.isBlank()) {
            decorations = Map.of();
            return;
        }
        List<MapStackRecognition.Observation> observations = new ArrayList<>();
        for (Slot slot : currentHandler.slots) {
            ItemStack stack = slot.getStack();
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

    private static int[] slotMapIds(ScreenHandler currentHandler) {
        if (currentHandler == null) return new int[0];
        int[] result = new int[currentHandler.slots.size()];
        for (int index = 0; index < currentHandler.slots.size(); index++) {
            Integer mapId = MapArtTiles.mapId(currentHandler.slots.get(index).getStack());
            result[index] = mapId == null ? -1 : mapId;
        }
        return result;
    }

    private void finishRecognition(MinecraftClient client, ScreenHandler currentHandler) {
        recognitionHandler = null;
        recognitionRequested = false;
        scan(client, currentHandler);

        Set<Integer> mapIds = new HashSet<>();
        List<MapArtLayoutSolver.Tile> tiles = new ArrayList<>();
        for (Slot slot : currentHandler.slots) {
            ItemStack stack = slot.getStack();
            Integer mapId = MapArtTiles.mapId(stack);
            if (mapId == null || !mapIds.add(mapId)) continue;
            MapArtTiles.fromStack(client, stack).ifPresent(tiles::add);
        }
        if (mapIds.isEmpty()) {
            AutoFrameManager.instance().showStatus("В открытых слотах нет заполненных карт");
            return;
        }
        if (tiles.size() != mapIds.size()) {
            AutoFrameManager.instance().showStatus("Не все карты загрузились. Повторите распознавание");
            return;
        }

        List<MapArtLayoutSolver.Tile> unknown = tiles.stream()
            .filter(tile -> !decorations.containsKey(tile.mapId()))
            .toList();
        if (unknown.isEmpty()) {
            AutoFrameManager.instance().showStatus("Распознано карт: " + decorations.size());
            return;
        }
        if (AutoFrameManager.instance().inferAndRememberVisibleMaps(client, unknown).isPresent()) {
            scan(client, currentHandler);
            AutoFrameManager.instance().showStatus("Распознано карт: " + decorations.size());
        }
        dirty = true;
    }

    private Optional<MapPreviewStore.Entry> livePreview(
        MinecraftClient client,
        ItemStack stack,
        int mapId,
        String connection
    ) {
        if (client.world == null || stack == null || !stack.isOf(Items.FILLED_MAP)) return Optional.empty();
        MapState state = FilledMapItem.getMapState(stack, client.world);
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

    private void beginPreload(MinecraftClient client, ScreenHandler currentHandler) {
        if (currentHandler == null || !currentHandler.getCursorStack().isEmpty() || client.player == null) return;
        int selectedHotbar = client.player.getInventory().selectedSlot;
        Slot hotbar = hotbarSlot(client, currentHandler, selectedHotbar);
        if (hotbar == null) return;
        for (Slot source : currentHandler.slots) {
            ItemStack sourceStack = source.getStack();
            Integer mapId = MapArtTiles.mapId(sourceStack);
            if (mapId == null || source == hotbar || isHotbarSlot(client, source)) continue;
            if (validatedMapIds.contains(mapId) || failedMapIds.contains(mapId)
                || livePreview(client, sourceStack, mapId, connectionKey).isPresent()) continue;
            ItemStack hotbarStack = hotbar.getStack();
            if (!source.canTakeItems(client.player) || !source.canInsert(sourceStack)
                || !hotbar.canTakeItems(client.player) || !hotbar.canInsert(sourceStack)
                || (!hotbarStack.isEmpty() && !source.canInsert(hotbarStack))) continue;
            preload = new Preload(
                currentHandler,
                source.id,
                hotbar.id,
                selectedHotbar,
                mapId,
                sourceStack.copy(),
                hotbarStack.copy(),
                PRELOAD_TIMEOUT_TICKS
            );
            client.interactionManager.clickSlot(
                currentHandler.syncId, source.id, selectedHotbar, SlotActionType.SWAP, client.player
            );
            return;
        }
    }

    private void tickPreload(MinecraftClient client) {
        if (preload == null) return;
        if (client.player == null || client.interactionManager == null
            || client.player.currentScreenHandler != preload.handler()) {
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
            if (ItemStack.areEqual(source.getStack(), preload.sourceBefore())
                && ItemStack.areEqual(hotbar.getStack(), preload.hotbarBefore())) {
                preload = null;
                dirty = true;
                return;
            }
        } else if (ItemStack.areEqual(hotbar.getStack(), preload.sourceBefore())
            && ItemStack.areEqual(source.getStack(), preload.hotbarBefore())) {
            if (livePreview(client, hotbar.getStack(), preload.mapId(), connectionKey).isPresent()) {
                restorePreload(client, source, hotbar);
                return;
            }
        } else if (ItemStack.areEqual(source.getStack(), preload.sourceBefore())
            && ItemStack.areEqual(hotbar.getStack(), preload.hotbarBefore())) {
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

    private void restorePreload(MinecraftClient client, Slot source, Slot hotbar) {
        if (preload == null || preload.restoring()) return;
        if (ItemStack.areEqual(source.getStack(), preload.hotbarBefore())
            && ItemStack.areEqual(hotbar.getStack(), preload.sourceBefore())) {
            client.interactionManager.clickSlot(
                preload.handler().syncId,
                preload.sourceSlotId(),
                preload.hotbarIndex(),
                SlotActionType.SWAP,
                client.player
            );
            preload = preload.beginRestore();
        } else {
            preload = null;
            dirty = true;
        }
    }

    private void cancelPreload(MinecraftClient client, boolean restore) {
        if (preload == null) return;
        if (restore && client.player != null && client.interactionManager != null
            && client.player.currentScreenHandler == preload.handler()) {
            Slot source = slot(preload.handler(), preload.sourceSlotId());
            Slot hotbar = slot(preload.handler(), preload.hotbarSlotId());
            if (source != null && hotbar != null) restorePreload(client, source, hotbar);
        }
        preload = null;
        dirty = true;
    }

    private Slot hotbarSlot(MinecraftClient client, ScreenHandler currentHandler, int hotbarIndex) {
        return currentHandler.slots.stream().filter(slot ->
            slot.inventory == client.player.getInventory() && slot.getIndex() == hotbarIndex
        ).findFirst().orElse(null);
    }

    private boolean isHotbarSlot(MinecraftClient client, Slot slot) {
        return slot.inventory == client.player.getInventory() && slot.getIndex() >= 0 && slot.getIndex() < 9;
    }

    private static Slot slot(ScreenHandler handler, int id) {
        return handler.slots.stream().filter(candidate -> candidate.id == id).findFirst().orElse(null);
    }

    private void initialize(MinecraftClient client) {
        Path next = client.runDirectory.toPath().toAbsolutePath().normalize();
        if (next.equals(runDirectory)) return;
        runDirectory = next;
        previewStore = new MapPreviewStore(LitematicaPaths.mapPreviewCacheDir(next));
        previews.clear();
        updatedMapIds.clear();
        validatedMapIds.clear();
        failedMapIds.clear();
        decorations = Map.of();
        scanGate.reset();
        dirty = true;
    }

    public record Decoration(String groupKey, String title, int tileNumber, int color) {
    }

    private record PreviewKey(String connectionKey, int mapId) {
    }

    private record Preload(
        ScreenHandler handler,
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
            ScreenHandler handler,
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
