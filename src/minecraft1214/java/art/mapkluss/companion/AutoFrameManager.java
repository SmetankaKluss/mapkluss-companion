package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AutoFrameManager {
    private static final AutoFrameManager INSTANCE = new AutoFrameManager();
    private static final int ACTION_TIMEOUT_TICKS = 30;

    private AutoFrameTemplateStore store;
    private AutoFrameMapRegistry mapRegistry;
    private AutoFramePlacement placement;
    private Object activeWorld;
    private PlacementAction action;
    private String status = "AutoFrame готов";
    private int statusTicks;

    private AutoFrameManager() {
    }

    public static AutoFrameManager instance() {
        return INSTANCE;
    }

    public synchronized CompletableFuture<AutoFrameTemplate> prepare(CompanionManifest manifest) {
        MinecraftClient client = MinecraftClient.getInstance();
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (manifest == null || manifest.grid() == null) throw new IOException("У арта нет размера сетки.");
                CompanionArtifact artifact = manifest.mapDatArtifact()
                    .orElseThrow(() -> new IOException("Для арта нет архива MAP.DAT."));
                CompanionRuntime runtime = CompanionRuntime.create(client);
                byte[] bytes = runtime.apiClient().downloadArtifact(artifact);
                MapDatTileSet tiles = MapDatTileSet.read(bytes, manifest.grid().wide(), manifest.grid().tall());
                AutoFrameTemplate template = new AutoFrameTemplate(
                    manifest.artId(),
                    manifest.versionId(),
                    manifest.title(),
                    manifest.grid().wide(),
                    manifest.grid().tall(),
                    tiles.tileHashes(),
                    tiles.mapIds(),
                    manifest.updatedAt()
                );
                synchronized (AutoFrameManager.this) {
                    AutoFrameTemplateStore templates = store(client);
                    templates.upsert(template);
                    templates.setActive(template);
                    templates.save();
                    placement = null;
                    action = null;
                    setStatus("AutoFrame подготовлен: " + template.title());
                }
                return template;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Подготовка AutoFrame прервана.", interrupted);
            } catch (IOException error) {
                throw new IllegalStateException(error.getMessage(), error);
            }
        });
    }

    public synchronized List<AutoFrameTemplate> templates(MinecraftClient client) {
        try {
            return store(client).templates();
        } catch (IOException error) {
            setStatus("Не удалось прочитать шаблоны AutoFrame");
            return List.of();
        }
    }

    public synchronized Optional<AutoFrameTemplate> activeTemplate(MinecraftClient client) {
        try {
            return store(client).activeTemplate();
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    public synchronized void activate(MinecraftClient client, AutoFrameTemplate template) throws IOException {
        AutoFrameTemplateStore templates = store(client);
        templates.setActive(template);
        templates.save();
        placement = null;
        action = null;
        setStatus("Выбран арт: " + template.title());
    }

    public synchronized void stopPlacement() {
        placement = null;
        action = null;
        setStatus("Размещение AutoFrame остановлено");
    }

    synchronized void showStatus(String value) {
        setStatus(value);
    }

    public synchronized void activateTargetWall(MinecraftClient client) {
        if (client.currentScreen != null) return;
        if (client.player == null || client.world == null) {
            setStatus("Откройте мир и смотрите на левую нижнюю рамку");
            return;
        }
        if (action != null) {
            setStatus("Дождитесь завершения установки карты");
            return;
        }
        ItemStack hand = client.player.getMainHandStack();
        if (!hand.isOf(Items.FILLED_MAP)) {
            setStatus("Возьмите в основную руку часть нужного арта");
            return;
        }
        Optional<MapArtLayoutSolver.Tile> handTile = MapArtTiles.fromStack(client, hand);
        if (handTile.isEmpty()) {
            setStatus("Подождите, пока изображение карты в руке загрузится");
            return;
        }
        Optional<ItemFrameEntity> target = targetFrame(client);
        if (target.isEmpty()) {
            if (placement != null) stopPlacement();
            else setStatus("Смотрите на левую нижнюю рамку и нажмите клавишу AutoFrame");
            return;
        }

        ItemFrameEntity frame = target.get();
        List<MapArtLayoutSolver.Tile> inventoryTiles = inventoryTiles(client);
        KnownSelection known = knownTemplateForHeldMap(client, inventoryTiles, handTile.get());
        if (known.ambiguous()) return;
        Optional<AutoFrameTemplate> selected = known.template();
        if (selected.isEmpty()) selected = inferLocalTemplate(client, inventoryTiles, null);
        if (selected.isEmpty()) return;

        rememberMapMappings(client, selected.get(), inventoryTiles);

        placement = placement(selected.get(), frame, client);
        action = null;
        setStatus("AutoFrame закреплён. Нажимайте ПКМ по рамкам в любом порядке");
    }

    public synchronized boolean interceptItemUse(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return false;
        if (client.player.isSneaking() || client.currentScreen != null) return false;
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) return false;
        Entity target = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (!(target instanceof ItemFrameEntity frame) || frame.getHorizontalFacing().getAxis().isVertical()) return false;
        if (action != null) {
            setStatus("Дождитесь завершения установки карты");
            return true;
        }

        Optional<AutoFramePlacement.Cell> cell = placement == null
            ? Optional.empty()
            : placement.cellAt(frame.getAttachedBlockPos(), frame.getHorizontalFacing());
        return cell.isPresent() && interceptPlacementCell(client, frame, cell.get());
    }

    private boolean interceptPlacementCell(MinecraftClient client, ItemFrameEntity frame, AutoFramePlacement.Cell cell) {
        ItemStack held = frame.getHeldItemStack();
        if (!held.isEmpty()) {
            setStatus(matchesCell(client, held, placement.template(), cell)
                ? "Эта рамка уже заполнена правильной картой"
                : "Рамка занята другим предметом");
            return true;
        }

        int inventoryIndex = findInventoryMap(client, placement.template(), cell);
        if (inventoryIndex < 0) {
            setStatus("Нужной части арта нет в инвентаре");
            return true;
        }
        int selected = client.player.getInventory().selectedSlot;
        AutoFrameInventoryPlan plan = AutoFrameInventoryPlan.forInventoryIndex(inventoryIndex, selected);
        ItemStack originalHotbar = client.player.getInventory().getStack(selected).copy();
        action = new PlacementAction(frame.getId(), frame.getAttachedBlockPos(), frame.getHorizontalFacing(), cell, plan, originalHotbar);
        setStatus("Ставлю часть " + (cell.tileIndex() + 1) + "/" + placement.template().tileHashes().size());
        return true;
    }

    public synchronized void tick(MinecraftClient client) {
        if (client.world != activeWorld) {
            activeWorld = client.world;
            placement = null;
            action = null;
        }
        if (statusTicks > 0) statusTicks--;
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        tickPlacementAction(client);
    }

    private void tickPlacementAction(MinecraftClient client) {
        if (action == null) return;
        action.ticks++;
        if (action.ticks > ACTION_TIMEOUT_TICKS) {
            restoreInventory(client, action);
            action = null;
            setStatus("Сервер не подтвердил установку карты");
            return;
        }
        if (action.waitTicks > 0) {
            action.waitTicks--;
            return;
        }

        ItemFrameEntity frame = findFrame(client, action.entityId, action.attachedPos, action.facing);
        if (frame == null) {
            restoreInventory(client, action);
            action = null;
            setStatus("Рамка больше недоступна");
            return;
        }

        switch (action.phase) {
            case PREPARE -> {
                if (action.plan.swapRequired()) {
                    client.interactionManager.clickSlot(
                        client.player.currentScreenHandler.syncId,
                        action.plan.sourceScreenSlot(),
                        action.plan.targetHotbarIndex(),
                        SlotActionType.SWAP,
                        client.player
                    );
                    action.swapSent = true;
                } else if (action.plan.selectionChangeRequired()) {
                    client.player.getInventory().setSelectedSlot(action.plan.targetHotbarIndex());
                }
                action.phase = Phase.WAIT_FOR_MAP;
                action.waitTicks = 2;
            }
            case WAIT_FOR_MAP -> {
                if (!matchesCell(client, client.player.getMainHandStack(), placement.template(), action.cell)) return;
                if (!frame.getHeldItemStack().isEmpty()) {
                    restoreInventory(client, action);
                    action = null;
                    setStatus("Рамка была заполнена, установка отменена");
                    return;
                }
                client.interactionManager.interactEntity(client.player, frame, Hand.MAIN_HAND);
                action.phase = Phase.WAIT_FOR_FRAME;
                action.waitTicks = 2;
            }
            case WAIT_FOR_FRAME -> {
                if (!matchesCell(client, frame.getHeldItemStack(), placement.template(), action.cell)) return;
                restoreInventory(client, action);
                int tileNumber = action.cell.tileIndex() + 1;
                action = null;
                setStatus("Установлена часть " + tileNumber);
            }
        }
    }

    private void restoreInventory(MinecraftClient client, PlacementAction current) {
        if (client.player == null || client.interactionManager == null || current.restored) return;
        current.restored = true;
        if (current.swapSent && safeToReverseSwap(client, current)) {
            client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                current.plan.sourceScreenSlot(),
                current.plan.targetHotbarIndex(),
                SlotActionType.SWAP,
                client.player
            );
        }
        client.player.getInventory().setSelectedSlot(current.plan.originalSelectedHotbarIndex());
    }

    private boolean safeToReverseSwap(MinecraftClient client, PlacementAction current) {
        ItemStack source = client.player.getInventory().getStack(current.plan.sourceInventoryIndex());
        ItemStack hotbar = client.player.getInventory().getStack(current.plan.targetHotbarIndex());
        boolean sourceStillOriginalHotbar = ItemStack.areEqual(source, current.originalHotbarStack);
        boolean hotbarStillExpectedMap = hotbar.isEmpty()
            || (placement != null && matchesCell(client, hotbar, placement.template(), current.cell));
        return sourceStillOriginalHotbar && hotbarStillExpectedMap;
    }

    public synchronized Snapshot snapshot(MinecraftClient client) {
        Optional<AutoFrameTemplate> selected = activeTemplate(client);
        if (selected.isEmpty()) return new Snapshot(null, false, 0, 0, 0, statusText());
        AutoFrameTemplate template = selected.get();
        int inventory = matchingInventoryCount(client, template);
        int placed = placement == null ? 0 : correctFrameCount(client, placement);
        return new Snapshot(template, placement != null, placed, inventory, template.tileHashes().size(), statusText());
    }

    public synchronized String hudText() {
        if (placement != null) {
            String busy = action == null ? "ПКМ по пустой рамке" : "установка...";
            return "AutoFrame  " + placement.template().title() + "  " + placement.template().wide() + "x" + placement.template().tall() + "  " + busy;
        }
        return statusTicks > 0 ? statusText() : "";
    }

    public synchronized int tileNumber(MinecraftClient client, ItemStack stack) {
        Integer mapId = MapArtTiles.mapId(stack);
        if (mapId == null) return -1;
        String hash = mapHash(stack, client);
        return identifyMaps(client, List.of(new MapStackRecognition.Observation(mapId, hash)))
            .getOrDefault(mapId, MapIdentity.NONE).tileNumber();
    }

    synchronized Map<Integer, MapIdentity> identifyMaps(
        MinecraftClient client,
        List<MapStackRecognition.Observation> observations
    ) {
        if (observations.isEmpty()) return Map.of();
        try {
            AutoFrameTemplateStore templates = store(client);
            AutoFrameMapRegistry registry = registry(client);
            String connection = connectionKey(client);
            List<Integer> mapIds = observations.stream().map(MapStackRecognition.Observation::mapId).toList();
            List<AutoFrameMapRegistry.Binding> bindings = registry.findAll(connection, mapIds);
            List<MapStackRecognition.Known> known = bindings.stream().map(binding ->
                new MapStackRecognition.Known(
                    binding.mapId(), binding.groupKey(), binding.tileIndex(), binding.tileHash()
                )
            ).toList();
            Map<Integer, MapStackRecognition.Match> matches = MapStackRecognition.resolve(
                observations, templates.templates(), known
            );
            boolean changed = false;
            for (MapStackRecognition.Observation observation : observations) {
                AutoFrameMapRegistry.Binding previous = registry.find(connection, observation.mapId()).orElse(null);
                MapStackRecognition.Match match = matches.get(observation.mapId());
                if (match == null) {
                    if (previous != null && observation.hash() != null) changed |= registry.forget(connection, observation.mapId());
                    continue;
                }
                if (previous == null
                    || !previous.groupKey().equals(match.groupKey())
                    || previous.tileIndex() != match.tileIndex()
                    || !match.tileHash().equals(previous.tileHash())) {
                    registry.remember(
                        connection,
                        match.mapId(),
                        match.template(),
                        match.tileIndex(),
                        match.tileHash()
                    );
                    changed = true;
                }
            }
            if (changed) registry.save();
            Map<Integer, MapIdentity> result = new LinkedHashMap<>();
            for (MapStackRecognition.Match match : matches.values()) {
                result.put(match.mapId(), new MapIdentity(
                    match.groupKey(), match.template().title(), match.tileNumber(), match.tileHash()
                ));
            }
            return Map.copyOf(result);
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not resolve AutoFrame map identities.", error);
            return Map.of();
        }
    }

    private KnownSelection knownTemplateForHeldMap(
        MinecraftClient client,
        List<MapArtLayoutSolver.Tile> available,
        MapArtLayoutSolver.Tile held
    ) {
        try {
            AutoFrameTemplateStore templates = store(client);
            List<AutoFrameTemplate> candidates = templates.templates().stream()
                .filter(template -> template.tileHashes().contains(held.hash()))
                .sorted(Comparator.comparingInt((AutoFrameTemplate template) -> template.tileHashes().size()).reversed())
                .toList();
            if (candidates.isEmpty()) return new KnownSelection(Optional.empty(), false);

            Optional<AutoFrameMapRegistry.Binding> binding = registry(client)
                .find(connectionKey(client), held.mapId());
            if (binding.isPresent()) {
                Optional<AutoFrameTemplate> mapped = candidates.stream().filter(template ->
                    template.artId().equals(binding.get().artId())
                        && template.versionId().equals(binding.get().versionId())
                ).findFirst();
                if (mapped.isPresent()) return selectKnown(templates, mapped.get(), client, held);
            }

            if (candidates.size() == 1) return selectKnown(templates, candidates.getFirst(), client, held);

            Map<String, Integer> counts = tileCounts(available);
            List<AutoFrameTemplate> complete = candidates.stream()
                .filter(template -> containsTemplate(counts, template))
                .toList();
            if (complete.size() == 1) return selectKnown(templates, complete.getFirst(), client, held);

            Optional<AutoFrameTemplate> active = templates.activeTemplate().filter(candidates::contains);
            if (active.isPresent() && allAvailableTilesBelongTo(active.get(), available)) {
                return selectKnown(templates, active.get(), client, held);
            }
            setStatus("Эта карта подходит к нескольким артам. Оставьте в инвентаре карты только одного арта");
            return new KnownSelection(Optional.empty(), true);
        } catch (IOException error) {
            setStatus("Не удалось прочитать шаблоны AutoFrame");
            return new KnownSelection(Optional.empty(), true);
        }
    }

    private KnownSelection selectKnown(
        AutoFrameTemplateStore templates,
        AutoFrameTemplate template,
        MinecraftClient client,
        MapArtLayoutSolver.Tile held
    ) throws IOException {
        templates.setActive(template);
        templates.save();
        AutoFrameMapRegistry registry = registry(client);
        String connection = connectionKey(client);
        int tileIndex = exactTileIndex(template, held);
        if (tileIndex < 0) {
            tileIndex = registry.find(connection, held.mapId())
                .filter(binding -> binding.artId().equals(template.artId())
                    && binding.versionId().equals(template.versionId())
                    && binding.tileIndex() < template.tileHashes().size()
                    && template.tileHashes().get(binding.tileIndex()).equals(held.hash()))
                .map(AutoFrameMapRegistry.Binding::tileIndex)
                .orElse(-1);
        }
        if (tileIndex >= 0) {
            registry.remember(connection, held.mapId(), template, tileIndex, held.hash());
            registry.save();
        }
        return new KnownSelection(Optional.of(template), false);
    }

    synchronized void rememberMapMappings(
        MinecraftClient client,
        AutoFrameTemplate template,
        List<MapArtLayoutSolver.Tile> tiles
    ) {
        try {
            AutoFrameMapRegistry registry = registry(client);
            String connection = connectionKey(client);
            Map<Integer, Integer> solvedIndices = solvedTileIndices(template, tiles);
            for (MapArtLayoutSolver.Tile tile : tiles) {
                int index = solvedIndices.getOrDefault(tile.mapId(), exactTileIndex(template, tile));
                if (index >= 0) registry.remember(connection, tile.mapId(), template, index, tile.hash());
            }
            registry.save();
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not save AutoFrame map bindings.", error);
        }
    }

    private Map<Integer, Integer> solvedTileIndices(
        AutoFrameTemplate template,
        List<MapArtLayoutSolver.Tile> tiles
    ) {
        if (tiles.size() != template.tileHashes().size() || tiles.size() > 54) return Map.of();
        try {
            MapArtLayoutSolver.Layout layout = MapArtLayoutSolver.solveByMapId(MapArtTiles.uniqueByMapId(tiles), null);
            if (!layout.reliable() || !layout.tileHashes().equals(template.tileHashes())) return Map.of();
            Map<Integer, Integer> values = new HashMap<>();
            for (int index = 0; index < layout.tileMapIds().size(); index++) values.put(layout.tileMapIds().get(index), index);
            return Map.copyOf(values);
        } catch (IllegalArgumentException unresolved) {
            return Map.of();
        }
    }

    private int exactTileIndex(AutoFrameTemplate template, MapArtLayoutSolver.Tile tile) {
        int byId = template.tileIndexForMapId(tile.mapId());
        if (byId >= 0 && template.tileHashes().get(byId).equals(tile.hash())) return byId;
        int found = -1;
        for (int index = 0; index < template.tileHashes().size(); index++) {
            if (!template.tileHashes().get(index).equals(tile.hash())) continue;
            if (found >= 0) return -1;
            found = index;
        }
        return found;
    }

    private boolean allAvailableTilesBelongTo(AutoFrameTemplate template, List<MapArtLayoutSolver.Tile> available) {
        if (available.isEmpty()) return true;
        Set<String> expected = new HashSet<>(template.tileHashes());
        return available.stream().allMatch(tile -> expected.contains(tile.hash()));
    }

    private Map<String, Integer> tileCounts(List<MapArtLayoutSolver.Tile> tiles) {
        Map<String, Integer> counts = new HashMap<>();
        for (MapArtLayoutSolver.Tile tile : tiles) counts.merge(tile.hash(), 1, Integer::sum);
        return counts;
    }

    private boolean containsTemplate(Map<String, Integer> available, AutoFrameTemplate template) {
        Map<String, Integer> needed = new HashMap<>();
        for (String hash : template.tileHashes()) needed.merge(hash, 1, Integer::sum);
        return needed.entrySet().stream().allMatch(entry -> available.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private Optional<AutoFrameTemplate> inferLocalTemplate(
        MinecraftClient client,
        List<MapArtLayoutSolver.Tile> input,
        Integer bottomLeftMapId
    ) {
        List<MapArtLayoutSolver.Tile> tiles = MapArtTiles.uniqueByMapId(input);
        if (tiles.isEmpty()) {
            setStatus("В инвентаре или сундуке нет загруженных карт");
            return Optional.empty();
        }

        final MapArtLayoutSolver.Layout layout;
        try {
            layout = MapArtLayoutSolver.solveByMapId(tiles, bottomLeftMapId);
        } catch (IllegalArgumentException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not infer a local map-art layout.", error);
            setStatus("Не удалось восстановить сетку карт");
            return Optional.empty();
        }
        if (!layout.reliable()) {
            setStatus("Не удалось надёжно восстановить сетку. Оставьте вместе только карты одного арта");
            return Optional.empty();
        }

        String signature = layout.wide() + "x" + layout.tall() + ":" + String.join(",", layout.tileHashes());
        String localId = "local-" + UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8));
        AutoFrameTemplate template = new AutoFrameTemplate(
            localId,
            localId,
            "Локальный арт " + layout.wide() + "x" + layout.tall(),
            layout.wide(),
            layout.tall(),
            layout.tileHashes(),
            layout.tileMapIds(),
            Instant.now().toString()
        );
        try {
            AutoFrameTemplateStore templates = store(client);
            templates.upsert(template);
            templates.setActive(template);
            templates.save();
            setStatus("Сетка распознана: " + layout.wide() + "x" + layout.tall());
            return Optional.of(template);
        } catch (IOException error) {
            setStatus("Не удалось сохранить локальный шаблон карт");
            return Optional.empty();
        }
    }

    private List<MapArtLayoutSolver.Tile> inventoryTiles(MinecraftClient client) {
        List<MapArtLayoutSolver.Tile> tiles = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            MapArtTiles.fromStack(client, client.player.getInventory().getStack(index)).ifPresent(tiles::add);
        }
        return tiles;
    }

    private Optional<ItemFrameEntity> targetFrame(MinecraftClient client) {
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.ENTITY) return Optional.empty();
        Entity entity = ((EntityHitResult) client.crosshairTarget).getEntity();
        if (!(entity instanceof ItemFrameEntity frame) || frame.getHorizontalFacing().getAxis().isVertical()) return Optional.empty();
        return Optional.of(frame);
    }

    private int findInventoryMap(
        MinecraftClient client,
        AutoFrameTemplate template,
        AutoFramePlacement.Cell cell
    ) {
        String connection = connectionKey(client);
        try {
            AutoFrameMapRegistry registry = registry(client);
            for (int index = 0; index < 36; index++) {
                ItemStack stack = client.player.getInventory().getStack(index);
                Integer mapId = MapArtTiles.mapId(stack);
                if (mapId == null) continue;
                Optional<AutoFrameMapRegistry.Binding> binding = registry.find(connection, mapId);
                if (binding.isPresent()
                    && binding.get().artId().equals(template.artId())
                    && binding.get().versionId().equals(template.versionId())
                    && binding.get().tileIndex() == cell.tileIndex()
                    && (binding.get().tileHash() == null || binding.get().tileHash().equals(cell.expectedHash()))) {
                    String liveHash = mapHash(stack, client);
                    if (liveHash == null || liveHash.equals(cell.expectedHash())) return index;
                }
            }
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not read exact AutoFrame map bindings.", error);
        }
        for (int index = 0; index < 36; index++) {
            if (cell.expectedHash().equals(mapHash(client.player.getInventory().getStack(index), client))) return index;
        }
        return -1;
    }

    private boolean matchesCell(
        MinecraftClient client,
        ItemStack stack,
        AutoFrameTemplate template,
        AutoFramePlacement.Cell cell
    ) {
        String liveHash = mapHash(stack, client);
        if (liveHash != null) return cell.expectedHash().equals(liveHash);
        Integer mapId = MapArtTiles.mapId(stack);
        if (mapId == null) return false;
        try {
            Optional<AutoFrameMapRegistry.Binding> binding = registry(client).find(connectionKey(client), mapId);
            return binding.isPresent()
                && binding.get().artId().equals(template.artId())
                && binding.get().versionId().equals(template.versionId())
                && binding.get().tileIndex() == cell.tileIndex()
                && (binding.get().tileHash() == null || binding.get().tileHash().equals(cell.expectedHash()));
        } catch (IOException error) {
            return false;
        }
    }

    private int matchingInventoryCount(MinecraftClient client, AutoFrameTemplate template) {
        if (client.player == null || client.world == null) return 0;
        List<MapStackRecognition.Observation> observations = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            ItemStack stack = client.player.getInventory().getStack(index);
            Integer mapId = MapArtTiles.mapId(stack);
            if (mapId == null) continue;
            String hash = mapHash(stack, client);
            if (hash == null) {
                hash = MapStackManager.instance().preview(client, stack)
                    .map(MapPreviewStore.Entry::hash)
                    .orElse(null);
            }
            observations.add(new MapStackRecognition.Observation(mapId, hash));
        }
        Set<Integer> tileIndices = new HashSet<>();
        for (MapIdentity identity : identifyMaps(client, observations).values()) {
            if (identity.groupKey().equals(template.artId() + "|" + template.versionId())) {
                tileIndices.add(identity.tileNumber() - 1);
            }
        }
        return tileIndices.size();
    }

    private int correctFrameCount(MinecraftClient client, AutoFramePlacement active) {
        if (client.world == null) return 0;
        FrameWallGeometry.Coord origin = FrameWallGeometry.fromBlockPos(active.leftBottom(), active.facing());
        BlockPos opposite = FrameWallGeometry.toBlockPos(
            active.facing(),
            FrameWallGeometry.cell(origin, active.template().wide() - 1, active.template().tall() - 1)
        );
        Box bounds = new Box(
            Math.min(active.leftBottom().getX(), opposite.getX()),
            Math.min(active.leftBottom().getY(), opposite.getY()),
            Math.min(active.leftBottom().getZ(), opposite.getZ()),
            Math.max(active.leftBottom().getX(), opposite.getX()) + 1.0,
            Math.max(active.leftBottom().getY(), opposite.getY()) + 1.0,
            Math.max(active.leftBottom().getZ(), opposite.getZ()) + 1.0
        ).expand(2.0);
        int correct = 0;
        for (ItemFrameEntity frame : client.world.getEntitiesByClass(ItemFrameEntity.class, bounds, value -> value.getHorizontalFacing() == active.facing())) {
            Optional<AutoFramePlacement.Cell> cell = active.cellAt(frame.getAttachedBlockPos(), frame.getHorizontalFacing());
            if (cell.isPresent() && matchesCell(client, frame.getHeldItemStack(), active.template(), cell.get())) correct++;
        }
        return correct;
    }

    private AutoFramePlacement placement(AutoFrameTemplate template, ItemFrameEntity frame, MinecraftClient client) {
        String worldKey = client.world == null ? "" : client.world.getRegistryKey().getValue().toString();
        return new AutoFramePlacement(template, frame.getAttachedBlockPos(), frame.getHorizontalFacing(), worldKey);
    }

    private ItemFrameEntity findFrame(MinecraftClient client, int entityId, BlockPos attachedPos, Direction facing) {
        if (client.world == null) return null;
        Entity direct = client.world.getEntityById(entityId);
        if (direct instanceof ItemFrameEntity frame
            && frame.getHorizontalFacing() == facing
            && frame.getAttachedBlockPos().equals(attachedPos)) return frame;
        Box bounds = new Box(attachedPos).expand(2.0);
        return client.world.getEntitiesByClass(
            ItemFrameEntity.class,
            bounds,
            value -> value.getHorizontalFacing() == facing && value.getAttachedBlockPos().equals(attachedPos)
        ).stream().findFirst().orElse(null);
    }

    static String mapHash(ItemStack stack, MinecraftClient client) {
        if (stack == null || stack.isEmpty() || !stack.isOf(Items.FILLED_MAP) || client.world == null) return null;
        MapState state = FilledMapItem.getMapState(stack, client.world);
        return state == null ? null : MapColorFingerprint.sha256(state.colors);
    }

    private AutoFrameTemplateStore store(MinecraftClient client) throws IOException {
        if (store == null) store = AutoFrameTemplateStore.load(LitematicaPaths.autoFrameTemplatesPath(client.runDirectory.toPath()));
        return store;
    }

    private AutoFrameMapRegistry registry(MinecraftClient client) throws IOException {
        if (mapRegistry == null) {
            mapRegistry = AutoFrameMapRegistry.load(LitematicaPaths.autoFrameMapRegistryPath(client.runDirectory.toPath()));
        }
        return mapRegistry;
    }

    String connectionKey(MinecraftClient client) {
        String connection = client.getCurrentServerEntry() == null
            ? "singleplayer"
            : client.getCurrentServerEntry().address.toLowerCase(java.util.Locale.ROOT);
        if (client.getServer() != null) {
            connection = "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
        }
        String dimension = client.world == null ? "unknown" : client.world.getRegistryKey().getValue().toString();
        return connection + "|" + dimension;
    }

    private void setStatus(String value) {
        status = value;
        statusTicks = 100;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) client.player.sendMessage(Text.literal(CompanionI18n.translate(value)), true);
        });
    }

    private String statusText() {
        return CompanionI18n.translate(status);
    }

    public record Snapshot(
        AutoFrameTemplate template,
        boolean placementActive,
        int placed,
        int inInventory,
        int total,
        String status
    ) {
    }

    record MapIdentity(String groupKey, String title, int tileNumber, String tileHash) {
        private static final MapIdentity NONE = new MapIdentity("", "", -1, "");
    }

    private record KnownSelection(Optional<AutoFrameTemplate> template, boolean ambiguous) {
    }

    private enum Phase {
        PREPARE,
        WAIT_FOR_MAP,
        WAIT_FOR_FRAME
    }

    private static final class PlacementAction {
        private final int entityId;
        private final BlockPos attachedPos;
        private final Direction facing;
        private final AutoFramePlacement.Cell cell;
        private final AutoFrameInventoryPlan plan;
        private final ItemStack originalHotbarStack;
        private Phase phase = Phase.PREPARE;
        private int ticks;
        private int waitTicks;
        private boolean swapSent;
        private boolean restored;

        private PlacementAction(
            int entityId,
            BlockPos attachedPos,
            Direction facing,
            AutoFramePlacement.Cell cell,
            AutoFrameInventoryPlan plan,
            ItemStack originalHotbarStack
        ) {
            this.entityId = entityId;
            this.attachedPos = attachedPos;
            this.facing = facing;
            this.cell = cell;
            this.plan = plan;
            this.originalHotbarStack = originalHotbarStack;
        }
    }
}
