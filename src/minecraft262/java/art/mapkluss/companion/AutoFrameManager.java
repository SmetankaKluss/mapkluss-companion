package art.mapkluss.companion;

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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoFrameManager {
    private static final AutoFrameManager INSTANCE = new AutoFrameManager();
    private static final int ACTION_TIMEOUT_TICKS = 100;

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
        Minecraft client = Minecraft.getInstance();
        if (action != null) restoreInventory(client, action);
        placement = null;
        action = null;
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

    public synchronized List<AutoFrameTemplate> templates(Minecraft client) {
        try {
            return store(client).templates();
        } catch (IOException error) {
            setStatus("Не удалось прочитать шаблоны AutoFrame");
            return List.of();
        }
    }

    public synchronized Optional<AutoFrameTemplate> activeTemplate(Minecraft client) {
        try {
            return store(client).activeTemplate();
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    public synchronized void activate(Minecraft client, AutoFrameTemplate template) throws IOException {
        if (action != null) restoreInventory(client, action);
        AutoFrameTemplateStore templates = store(client);
        templates.setActive(template);
        templates.save();
        placement = null;
        action = null;
        setStatus("Выбран арт: " + template.title());
    }

    public synchronized void stopPlacement(Minecraft client) {
        if (action != null) restoreInventory(client, action);
        placement = null;
        action = null;
        setStatus("Размещение AutoFrame остановлено");
    }

    synchronized void showStatus(String value) {
        setStatus(value);
    }

    public synchronized void activateTargetWall(Minecraft client) {
        if (client.gui.screen() != null) return;
        if (client.player == null || client.level == null) {
            setStatus("Откройте мир и наведитесь на начальную рамку сетки");
            return;
        }
        ItemStack hand = client.player.getMainHandItem();
        if (!hand.is(Items.FILLED_MAP)) {
            if (placement != null || action != null) stopPlacement(client);
            else setStatus("Возьмите в основную руку часть нужного арта");
            return;
        }
        if (action != null) {
            setStatus("Дождитесь завершения установки карты");
            return;
        }
        Optional<MapArtLayoutSolver.Tile> handTile = MapArtTiles.fromStack(client, hand);
        if (handTile.isEmpty()) {
            setStatus("Подождите, пока изображение карты в руке загрузится");
            return;
        }
        Optional<ItemFrame> target = targetFrame(client);
        if (target.isEmpty()) {
            if (placement != null) stopPlacement(client);
            else setStatus("Наведитесь на рамку снизу слева; на полу — на ближнюю слева");
            return;
        }

        ItemFrame frame = target.get();
        List<MapArtLayoutSolver.Tile> inventoryTiles = inventoryTiles(client);
        int tileCount = MapArtTiles.uniqueByMapId(inventoryTiles).size();
        FrameGridResolver.DimensionResolution dimensions = inferFrameDimensions(client, frame, tileCount);
        KnownSelection known = knownTemplateForHeldMap(
            client,
            inventoryTiles,
            handTile.get(),
            dimensions.found() ? dimensions.dimensions() : null
        );
        if (known.ambiguous()) return;
        Optional<AutoFrameTemplate> selected = known.template();
        if (selected.isEmpty()) {
            if (!dimensions.found()) {
                setStatus(dimensions.status() == FrameGridResolver.Status.AMBIGUOUS
                    ? "Размер сетки неоднозначен. Наведитесь на её ближнюю левую рамку"
                    : "Не найдена полная сетка из " + tileCount + " рамок впереди и справа от курсора");
                return;
            }
            selected = inferLocalTemplate(client, inventoryTiles, null, dimensions.dimensions());
        }
        if (selected.isEmpty()) return;

        rememberMapMappings(client, selected.get(), inventoryTiles);

        AutoFramePlacement.PlacementResolution resolved = placement(selected.get(), frame, client);
        if (!resolved.found()) {
            placement = null;
            action = null;
            setStatus(
                "Не найдена полная сетка " + selected.get().wide() + "x" + selected.get().tall()
                    + " впереди и справа от курсора"
            );
            return;
        }
        placement = resolved.placement();
        action = null;
        setStatus("AutoFrame закреплён. Нажимайте ПКМ по рамкам в любом порядке");
    }

    public synchronized boolean interceptItemUse(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return false;
        if (client.player.isShiftKeyDown() || client.gui.screen() != null) return false;
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return false;
        Entity target = ((EntityHitResult) client.hitResult).getEntity();
        if (!(target instanceof ItemFrame frame)) return false;
        if (action != null) {
            setStatus("Дождитесь завершения установки карты");
            return true;
        }

        Optional<AutoFramePlacement.Cell> cell = placement == null
            ? Optional.empty()
            : placement.cellAt(frame.getPos(), frame.getDirection());
        return cell.isPresent() && interceptPlacementCell(client, frame, cell.get());
    }

    private boolean interceptPlacementCell(Minecraft client, ItemFrame frame, AutoFramePlacement.Cell cell) {
        ItemStack held = frame.getItem();
        if (!held.isEmpty()) {
            if (!matchesCell(client, held, placement.template(), cell)) {
                setStatus("Рамка занята другим предметом");
                return true;
            }
            if (matchesRotation(frame, cell)) {
                setStatus("Эта рамка уже заполнена правильной картой");
                return true;
            }
            action = PlacementAction.rotationOnly(
                frame.getId(),
                frame.getPos(),
                frame.getDirection(),
                placement.template(),
                cell
            );
            setStatus("Поворачиваю часть " + (cell.tileIndex() + 1));
            return true;
        }

        int inventoryIndex = findInventoryMap(client, placement.template(), cell);
        if (inventoryIndex < 0) {
            setStatus("Нужной части арта нет в инвентаре");
            return true;
        }
        int selected = client.player.getInventory().getSelectedSlot();
        AutoFrameInventoryPlan plan = AutoFrameInventoryPlan.forInventoryIndex(inventoryIndex, selected);
        ItemStack originalHotbar = client.player.getInventory().getItem(selected).copy();
        action = new PlacementAction(
            frame.getId(), frame.getPos(), frame.getDirection(), placement.template(), cell, plan, originalHotbar
        );
        setStatus("Ставлю часть " + (cell.tileIndex() + 1) + "/" + placement.template().tileHashes().size());
        return true;
    }

    public synchronized void tick(Minecraft client) {
        if (client.level != activeWorld) {
            if (action != null) restoreInventory(client, action);
            activeWorld = client.level;
            placement = null;
            action = null;
        }
        if (statusTicks > 0) statusTicks--;
        if (client.player == null || client.level == null || client.gameMode == null) return;
        tickPlacementAction(client);
    }

    private void tickPlacementAction(Minecraft client) {
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

        ItemFrame frame = findFrame(client, action.entityId, action.attachedPos, action.facing);
        if (frame == null) {
            restoreInventory(client, action);
            action = null;
            setStatus("Рамка больше недоступна");
            return;
        }

        switch (action.phase) {
            case PREPARE -> {
                if (action.plan.swapRequired()) {
                    int sourceScreenSlot = playerScreenSlot(client, action.plan.sourceInventoryIndex());
                    if (sourceScreenSlot < 0) {
                        action = null;
                        setStatus("Не удалось найти слот карты в открытом интерфейсе");
                        return;
                    }
                    client.gameMode.handleContainerInput(
                        client.player.containerMenu.containerId,
                        sourceScreenSlot,
                        action.plan.targetHotbarIndex(),
                        ContainerInput.SWAP,
                        client.player
                    );
                    action.swapSent = true;
                } else if (action.plan.selectionChangeRequired()) {
                    client.player.getInventory().setSelectedSlot(action.plan.targetHotbarIndex());
                }
                action.advance(Phase.WAIT_FOR_MAP);
                action.waitTicks = 0;
            }
            case WAIT_FOR_MAP -> {
                if (!matchesCell(client, client.player.getMainHandItem(), placement.template(), action.cell)) return;
                if (!frame.getItem().isEmpty()) {
                    restoreInventory(client, action);
                    action = null;
                    setStatus("Рамка была заполнена, установка отменена");
                    return;
                }
                client.gameMode.interact(client.player, frame, new EntityHitResult(frame, frame.position()), InteractionHand.MAIN_HAND);
                action.advance(Phase.WAIT_FOR_FRAME);
                action.waitTicks = 0;
            }
            case WAIT_FOR_FRAME -> {
                if (!matchesCell(client, frame.getItem(), placement.template(), action.cell)) return;
                if (!matchesRotation(frame, action.cell)) {
                    action.advance(Phase.ROTATE_FRAME);
                    action.rotationClickPending = false;
                    action.lastObservedRotation = normalizedRotation(frame);
                    return;
                }
                finishPlacement(client);
            }
            case ROTATE_FRAME -> {
                if (!matchesCell(client, frame.getItem(), placement.template(), action.cell)) {
                    restoreInventory(client, action);
                    action = null;
                    setStatus("Карта в рамке изменилась, поворот отменён");
                    return;
                }
                int currentRotation = normalizedRotation(frame);
                if (currentRotation == action.cell.mapRotation()) {
                    finishPlacement(client);
                    return;
                }
                if (currentRotation != action.lastObservedRotation) {
                    action.lastObservedRotation = currentRotation;
                    action.rotationClickPending = false;
                }
                if (action.rotationClickPending) return;
                client.gameMode.interact(client.player, frame, new EntityHitResult(frame, frame.position()), InteractionHand.MAIN_HAND);
                action.rotationClickPending = true;
            }
        }
    }

    private void finishPlacement(Minecraft client) {
        if (action == null) return;
        restoreInventory(client, action);
        int tileNumber = action.cell.tileIndex() + 1;
        action = null;
        setStatus("Установлена часть " + tileNumber);
    }

    private static int normalizedRotation(ItemFrame frame) {
        return Math.floorMod(frame.getRotation(), 4);
    }

    private static boolean matchesRotation(ItemFrame frame, AutoFramePlacement.Cell cell) {
        return normalizedRotation(frame) == cell.mapRotation();
    }

    private void restoreInventory(Minecraft client, PlacementAction current) {
        if (client.player == null || client.gameMode == null || current.restored) return;
        if (current.plan == null) return;
        if (current.swapSent && safeToReverseSwap(client, current)) {
            int sourceScreenSlot = playerScreenSlot(client, current.plan.sourceInventoryIndex());
            if (sourceScreenSlot < 0) return;
            client.gameMode.handleContainerInput(
                client.player.containerMenu.containerId,
                sourceScreenSlot,
                current.plan.targetHotbarIndex(),
                ContainerInput.SWAP,
                client.player
            );
        }
        client.player.getInventory().setSelectedSlot(current.plan.originalSelectedHotbarIndex());
        current.restored = true;
    }

    private boolean safeToReverseSwap(Minecraft client, PlacementAction current) {
        ItemStack source = client.player.getInventory().getItem(current.plan.sourceInventoryIndex());
        ItemStack hotbar = client.player.getInventory().getItem(current.plan.targetHotbarIndex());
        boolean sourceStillOriginalHotbar = ItemStack.matches(source, current.originalHotbarStack);
        boolean hotbarStillExpectedMap = hotbar.isEmpty()
            || matchesCell(client, hotbar, current.template, current.cell);
        return sourceStillOriginalHotbar && hotbarStillExpectedMap;
    }

    private static int playerScreenSlot(Minecraft client, int inventoryIndex) {
        if (client.player == null || client.player.containerMenu == null) return -1;
        return client.player.containerMenu.slots.stream()
            .filter(slot -> slot.container == client.player.getInventory() && slot.getContainerSlot() == inventoryIndex)
            .mapToInt(slot -> slot.index)
            .findFirst()
            .orElse(-1);
    }

    public synchronized Snapshot snapshot(Minecraft client) {
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

    public synchronized int tileNumber(Minecraft client, ItemStack stack) {
        Integer mapId = MapArtTiles.mapId(stack);
        if (mapId == null) return -1;
        String hash = mapHash(stack, client);
        return identifyMaps(client, List.of(new MapStackRecognition.Observation(mapId, hash)))
            .getOrDefault(mapId, MapIdentity.NONE).tileNumber();
    }

    synchronized Map<Integer, MapIdentity> identifyMaps(
        Minecraft client,
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
        Minecraft client,
        List<MapArtLayoutSolver.Tile> available,
        MapArtLayoutSolver.Tile held,
        FrameGridResolver.Dimensions dimensions
    ) {
        try {
            AutoFrameTemplateStore templates = store(client);
            List<AutoFrameTemplate> candidates = templates.templates().stream()
                .filter(template -> template.tileHashes().contains(held.hash()))
                .filter(template -> dimensions == null
                    || template.tileHashes().size() != dimensions.cellCount()
                    || (template.wide() == dimensions.wide() && template.tall() == dimensions.tall()))
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
        Minecraft client,
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
        Minecraft client,
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
            MapArtLayoutSolver.Layout layout = MapArtLayoutSolver.solveWithDimensions(
                MapArtTiles.uniqueByMapId(tiles),
                template.wide(),
                template.tall()
            );
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
        Minecraft client,
        List<MapArtLayoutSolver.Tile> input,
        Integer bottomLeftMapId
    ) {
        return inferLocalTemplate(client, input, bottomLeftMapId, null);
    }

    private Optional<AutoFrameTemplate> inferLocalTemplate(
        Minecraft client,
        List<MapArtLayoutSolver.Tile> input,
        Integer bottomLeftMapId,
        FrameGridResolver.Dimensions dimensions
    ) {
        List<MapArtLayoutSolver.Tile> tiles = MapArtTiles.uniqueByMapId(input);
        if (tiles.isEmpty()) {
            setStatus("В инвентаре или сундуке нет загруженных карт");
            return Optional.empty();
        }

        final MapArtLayoutSolver.Layout layout;
        try {
            layout = dimensions == null
                ? MapArtLayoutSolver.solveByMapId(tiles, bottomLeftMapId)
                : MapArtLayoutSolver.solveByMapIdWithDimensions(
                    tiles,
                    bottomLeftMapId,
                    dimensions.wide(),
                    dimensions.tall()
                );
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

    synchronized Optional<AutoFrameTemplate> inferAndRememberVisibleMaps(
        Minecraft client,
        List<MapArtLayoutSolver.Tile> tiles
    ) {
        Optional<AutoFrameTemplate> inferred = inferLocalTemplate(client, tiles, null);
        inferred.ifPresent(template -> rememberMapMappings(client, template, tiles));
        return inferred;
    }

    private List<MapArtLayoutSolver.Tile> inventoryTiles(Minecraft client) {
        List<MapArtLayoutSolver.Tile> tiles = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            MapArtTiles.fromStack(client, client.player.getInventory().getItem(index)).ifPresent(tiles::add);
        }
        return tiles;
    }

    private Optional<ItemFrame> targetFrame(Minecraft client) {
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) return Optional.empty();
        Entity entity = ((EntityHitResult) client.hitResult).getEntity();
        if (!(entity instanceof ItemFrame frame)) return Optional.empty();
        return Optional.of(frame);
    }

    private int findInventoryMap(
        Minecraft client,
        AutoFrameTemplate template,
        AutoFramePlacement.Cell cell
    ) {
        String connection = connectionKey(client);
        try {
            AutoFrameMapRegistry registry = registry(client);
            for (int index = 0; index < 36; index++) {
                ItemStack stack = client.player.getInventory().getItem(index);
                Integer mapId = MapArtTiles.mapId(stack);
                if (mapId == null) continue;
                Optional<AutoFrameMapRegistry.Binding> binding = registry.find(connection, mapId);
                if (binding.isPresent()
                    && binding.get().artId().equals(template.artId())
                    && binding.get().versionId().equals(template.versionId())
                    && binding.get().tileIndex() == cell.tileIndex()
                    && (binding.get().tileHash() == null || binding.get().tileHash().equals(cell.expectedHash()))) {
                    String liveHash = mapHash(stack, client);
                    if (cell.expectedHash().equals(liveHash)) return index;
                }
            }
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.debug("Could not read exact AutoFrame map bindings.", error);
        }
        for (int index = 0; index < 36; index++) {
            if (cell.expectedHash().equals(mapHash(client.player.getInventory().getItem(index), client))) return index;
        }
        return -1;
    }

    private boolean matchesCell(
        Minecraft client,
        ItemStack stack,
        AutoFrameTemplate template,
        AutoFramePlacement.Cell cell
    ) {
        String liveHash = mapHash(stack, client);
        if (liveHash != null) return cell.expectedHash().equals(liveHash);
        return false;
    }

    private int matchingInventoryCount(Minecraft client, AutoFrameTemplate template) {
        if (client.player == null || client.level == null) return 0;
        List<MapStackRecognition.Observation> observations = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            ItemStack stack = client.player.getInventory().getItem(index);
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

    private int correctFrameCount(Minecraft client, AutoFramePlacement active) {
        if (client.level == null) return 0;
        BlockPos opposite = active.blockAt(active.template().wide() - 1, active.template().tall() - 1);
        AABB bounds = new AABB(
            Math.min(active.leftBottom().getX(), opposite.getX()),
            Math.min(active.leftBottom().getY(), opposite.getY()),
            Math.min(active.leftBottom().getZ(), opposite.getZ()),
            Math.max(active.leftBottom().getX(), opposite.getX()) + 1.0,
            Math.max(active.leftBottom().getY(), opposite.getY()) + 1.0,
            Math.max(active.leftBottom().getZ(), opposite.getZ()) + 1.0
        ).inflate(2.0);
        int correct = 0;
        for (ItemFrame frame : client.level.getEntitiesOfClass(ItemFrame.class, bounds, value -> value.getDirection() == active.facing())) {
            Optional<AutoFramePlacement.Cell> cell = active.cellAt(frame.getPos(), frame.getDirection());
            if (cell.isPresent()
                && matchesCell(client, frame.getItem(), active.template(), cell.get())
                && matchesRotation(frame, cell.get())) {
                correct++;
            }
        }
        return correct;
    }

    private AutoFramePlacement.PlacementResolution placement(
        AutoFrameTemplate template,
        ItemFrame frame,
        Minecraft client
    ) {
        String worldKey = client.level == null ? "" : client.level.dimension().identifier().toString();
        Direction facing = frame.getDirection();
        Direction planeUp = AutoFramePlacement.planeUpFromPlayerView(
            facing,
            client.player.getDirection()
        );
        int radius = Math.max(template.wide(), template.tall()) + 1;
        return AutoFramePlacement.resolveFromFrames(
            template,
            frame.getPos(),
            facing,
            planeUp,
            worldKey,
            nearbyFramePositions(client, frame, facing, radius)
        );
    }

    private FrameGridResolver.DimensionResolution inferFrameDimensions(
        Minecraft client,
        ItemFrame frame,
        int cellCount
    ) {
        Direction facing = frame.getDirection();
        Direction planeUp = AutoFramePlacement.planeUpFromPlayerView(
            facing,
            client.player.getDirection()
        );
        return AutoFramePlacement.inferDimensionsFromFrames(
            frame.getPos(),
            facing,
            planeUp,
            nearbyFramePositions(client, frame, facing, cellCount + 1),
            cellCount
        );
    }

    private List<BlockPos> nearbyFramePositions(
        Minecraft client,
        ItemFrame target,
        Direction facing,
        int radius
    ) {
        if (client.level == null) return List.of();
        AABB bounds = new AABB(target.getPos()).inflate(Math.max(2, radius));
        return client.level.getEntitiesOfClass(
            ItemFrame.class,
            bounds,
            value -> value.getDirection() == facing
        ).stream().map(ItemFrame::getPos).distinct().toList();
    }

    private ItemFrame findFrame(Minecraft client, int entityId, BlockPos attachedPos, Direction facing) {
        if (client.level == null) return null;
        Entity direct = client.level.getEntity(entityId);
        if (direct instanceof ItemFrame frame
            && frame.getDirection() == facing
            && frame.getPos().equals(attachedPos)) return frame;
        AABB bounds = new AABB(attachedPos).inflate(2.0);
        return client.level.getEntitiesOfClass(
            ItemFrame.class,
            bounds,
            value -> value.getDirection() == facing && value.getPos().equals(attachedPos)
        ).stream().findFirst().orElse(null);
    }

    static String mapHash(ItemStack stack, Minecraft client) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.FILLED_MAP) || client.level == null) return null;
        MapItemSavedData state = MapItem.getSavedData(stack, client.level);
        return state == null ? null : MapColorFingerprint.sha256(state.colors);
    }

    private AutoFrameTemplateStore store(Minecraft client) throws IOException {
        if (store == null) store = AutoFrameTemplateStore.load(LitematicaPaths.autoFrameTemplatesPath(client.gameDirectory.toPath()));
        return store;
    }

    private AutoFrameMapRegistry registry(Minecraft client) throws IOException {
        if (mapRegistry == null) {
            mapRegistry = AutoFrameMapRegistry.load(LitematicaPaths.autoFrameMapRegistryPath(client.gameDirectory.toPath()));
        }
        return mapRegistry;
    }

    String connectionKey(Minecraft client) {
        String connection = client.getCurrentServer() == null
            ? "singleplayer"
            : client.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
        if (client.getSingleplayerServer() != null) {
            connection = "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
        }
        String dimension = client.level == null ? "unknown" : client.level.dimension().identifier().toString();
        return connection + "|" + dimension;
    }

    private void setStatus(String value) {
        status = value;
        statusTicks = 100;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) client.player.sendOverlayMessage(Component.literal(CompanionI18n.translate(value)));
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
        WAIT_FOR_FRAME,
        ROTATE_FRAME
    }

    private static final class PlacementAction {
        private final int entityId;
        private final BlockPos attachedPos;
        private final Direction facing;
        private final AutoFrameTemplate template;
        private final AutoFramePlacement.Cell cell;
        private final AutoFrameInventoryPlan plan;
        private final ItemStack originalHotbarStack;
        private Phase phase = Phase.PREPARE;
        private int ticks;
        private int waitTicks;
        private boolean swapSent;
        private boolean restored;
        private boolean rotationClickPending;
        private int lastObservedRotation = -1;

        private PlacementAction(
            int entityId,
            BlockPos attachedPos,
            Direction facing,
            AutoFrameTemplate template,
            AutoFramePlacement.Cell cell,
            AutoFrameInventoryPlan plan,
            ItemStack originalHotbarStack
        ) {
            this.entityId = entityId;
            this.attachedPos = attachedPos;
            this.facing = facing;
            this.template = template;
            this.cell = cell;
            this.plan = plan;
            this.originalHotbarStack = originalHotbarStack;
        }

        private static PlacementAction rotationOnly(
            int entityId,
            BlockPos attachedPos,
            Direction facing,
            AutoFrameTemplate template,
            AutoFramePlacement.Cell cell
        ) {
            PlacementAction action = new PlacementAction(
                entityId,
                attachedPos,
                facing,
                template,
                cell,
                null,
                ItemStack.EMPTY
            );
            action.phase = Phase.ROTATE_FRAME;
            return action;
        }

        private void advance(Phase next) {
            phase = next;
            ticks = 0;
        }
    }
}
