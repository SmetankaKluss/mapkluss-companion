package art.mapkluss.companion;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SuppressionManager {
    private static final SuppressionManager INSTANCE = new SuppressionManager();
    private static final int REMOVAL_SCAN_INTERVAL = 5;
    private static final int MAX_HIGHLIGHTS = 2048;

    private SuppressionBundle bundle;
    private SuppressionBundleInstaller.Installed installed;
    private SuppressionSessionStore sessionStore;
    private SuppressionStage stage;
    private BlockPos anchor;
    private String worldHash;
    private String dimension;
    private int mapId = -1;
    private int phaseIndex;
    private int standPointIndex;
    private int dwellTicks;
    private boolean mapUpdateObserved;
    private int stablePointTicks;
    private boolean manualOverrideArmed;
    private int activeGuidePhase = -1;
    private int scanTicker;
    private int remainingRemovalBlocks;
    private boolean initialized;
    private boolean buildPlacementSyncPending;
    private String status = "";

    private SuppressionManager() { }

    public static SuppressionManager instance() {
        return INSTANCE;
    }

    public synchronized void start(Minecraft client, SuppressionBundle bundle, SuppressionBundleInstaller.Installed installed) throws IOException {
        if (active()) {
            throw new IOException("Сначала остановите текущую стройку Two-layer — её прогресс не был заменён");
        }
        requireClientVersion(bundle.parsed().plan());
        int selectedMapId = findStartMapId(client);
        this.bundle = bundle;
        this.installed = installed;
        this.sessionStore = SuppressionSessionStore.forRunDir(client.gameDirectory.toPath());
        this.stage = SuppressionStage.WAITING_ANCHOR;
        this.anchor = null;
        this.worldHash = null;
        this.dimension = null;
        this.mapId = selectedMapId;
        this.phaseIndex = 0;
        this.standPointIndex = 0;
        this.dwellTicks = 0;
        this.mapUpdateObserved = false;
        this.stablePointTicks = 0;
        this.manualOverrideArmed = false;
        this.activeGuidePhase = -1;
        this.remainingRemovalBlocks = 0;
        this.buildPlacementSyncPending = false;
        this.initialized = true;
        this.status = "Выберите северо-западную опору и нажмите J.";
        persist();
        notifyPlayer(client, status);
    }

    public synchronized void tick(Minecraft client) {
        if (!initialized) initialize(client);
        if (!active() || client.level == null || client.player == null) return;
        if (anchor == null) return;
        String currentWorldHash = LensWorldIdentity.serverHash(client);
        String currentDimension = LensWorldIdentity.dimensionId(client);
        if (worldHash == null || !worldHash.equals(currentWorldHash) || !dimension.equals(currentDimension)) {
            status = "Вернитесь в исходный мир.";
            return;
        }
        if (buildPlacementSyncPending) syncRestoredBuildPlacement(client);

        switch (stage) {
            case REMOVE -> tickRemoval(client);
            case INITIAL_MOVE, MOVE -> tickMove(client);
            case INITIAL_EQUIP, EQUIP -> tickEquip(client);
            case INITIAL_DWELL, DWELL -> tickDwell(client);
            case INITIAL_STOW, STOW -> advanceCapturePoint(client, stage == SuppressionStage.INITIAL_STOW);
            case INITIAL_VERIFY, VERIFY -> tickVerify(client);
            default -> { }
        }
    }

    public synchronized void handleWorldAction(Minecraft client) {
        if (!initialized) initialize(client);
        if (!active()) {
            notifyPlayer(client, "Нет активной сессии Two-layer.");
            return;
        }
        if (anchor != null && !sameBoundWorld(client)) {
            status = "Вернитесь в исходный мир.";
            notifyPlayer(client, status);
            return;
        }
        try {
            switch (stage) {
                case WAITING_ANCHOR -> captureAnchor(client);
                case ANCHOR_CONFIRM -> confirmAnchor(client);
                case BUILDING -> {
                    stage = SuppressionStage.INITIAL_MOVE;
                    standPointIndex = 0;
                    dwellTicks = 0;
                    mapUpdateObserved = false;
                    stablePointTicks = 0;
                    manualOverrideArmed = false;
                    status = "Перейдите к первой точке.";
                    persist();
                }
                case INITIAL_VERIFY, VERIFY -> handleManualOverride(client);
                case READY_NEXT -> advancePhase(client);
                case COMPLETE -> notifyPlayer(client, "Готово. Проверьте и заблокируйте карту.");
                default -> notifyPlayer(client, actionHint());
            }
        } catch (Exception error) {
            status = CompanionUiErrors.message("two-layer", error);
            notifyPlayer(client, status);
        }
    }

    public synchronized boolean stop(Minecraft client) throws IOException {
        if (!initialized) initialize(client);
        if (!active()) return false;

        if (sessionStore == null) {
            sessionStore = SuppressionSessionStore.forRunDir(client.gameDirectory.toPath());
        }
        sessionStore.clear();

        BlockPos stoppedAnchor = anchor;
        SuppressionPlan stoppedPlan = bundle.parsed().plan();
        String stoppedPlanSha = bundle.planSha256();
        if (stoppedAnchor != null) {
            OptionalLitematicaAdapter.removePlanPlacements(stoppedAnchor, stoppedPlanSha);
            OptionalLitematicaAdapter.removePlanPlacements(
                SuppressionPlacementGeometry.legacyPlanOrigin(stoppedAnchor), stoppedPlanSha);
            OptionalLitematicaAdapter.removePlanPlacements(
                SuppressionPlacementGeometry.planOrigin(stoppedAnchor, stoppedPlan), stoppedPlanSha);
        }

        bundle = null;
        installed = null;
        stage = null;
        anchor = null;
        worldHash = null;
        dimension = null;
        mapId = -1;
        stablePointTicks = 0;
        phaseIndex = 0;
        standPointIndex = 0;
        dwellTicks = 0;
        mapUpdateObserved = false;
        manualOverrideArmed = false;
        activeGuidePhase = -1;
        scanTicker = 0;
        remainingRemovalBlocks = 0;
        buildPlacementSyncPending = false;
        status = "Постройка остановлена.";
        notifyPlayer(client, status);
        return true;
    }

    public synchronized boolean active() {
        return bundle != null && installed != null && stage != null;
    }

    public synchronized SuppressionStage stage() {
        return stage;
    }

    public synchronized List<String> hudLines() {
        if (!active()) return List.of();
        List<String> lines = new ArrayList<>();
        lines.add("Two-layer · " + shortTitle());
        if (stage == SuppressionStage.BUILDING) {
            lines.add("Постройте схему · J: готово");
        } else if (stage == SuppressionStage.WAITING_ANCHOR || stage == SuppressionStage.ANCHOR_CONFIRM) {
            lines.add(actionHint());
        } else if (stage == SuppressionStage.COMPLETE) {
            lines.add("Готово · заблокируйте карту");
        } else {
            lines.add("Этап " + Math.min(phaseIndex + 1, 64) + "/64 · " + stageLabel());
            if (stage == SuppressionStage.REMOVE) lines.add("Осталось: " + remainingRemovalBlocks);
            SuppressionPlan.StandPoint point = currentStandPoint();
            if (point != null && showsStandPoint(stage)) {
                String progress = stage == SuppressionStage.INITIAL_DWELL || stage == SuppressionStage.DWELL
                    ? " · " + dwellTicks + "/" + point.minTicks() + " · "
                        + SuppressionStateLogic.captureDataLabel(mapUpdateObserved)
                    : "";
                lines.add("Точка " + worldPos(point.standOn()).toShortString() + progress);
            }
            if ((stage == SuppressionStage.READY_NEXT || stage == SuppressionStage.VERIFY || stage == SuppressionStage.INITIAL_VERIFY)
                && (status == null || status.isBlank())) {
                lines.add(actionHint());
            }
        }
        if (status != null && !status.isBlank() && lines.stream().noneMatch(status::equals)) {
            lines.add(status);
        }
        while (lines.size() > 4) lines.remove(1);
        return lines.stream().map(CompanionI18n::translate).toList();
    }

    public synchronized List<SuppressionHighlight> highlights(Minecraft client) {
        if (!active() || anchor == null || client.level == null || !sameBoundWorld(client)) return List.of();
        List<SuppressionHighlight> result = new ArrayList<>();
        result.add(new SuppressionHighlight(anchor, SuppressionHighlight.Kind.ANCHOR));
        if (stage == SuppressionStage.ANCHOR_CONFIRM) {
            for (int offset = 1; offset < 128; offset++) {
                result.add(new SuppressionHighlight(anchor.offset(offset, 0, 0), SuppressionHighlight.Kind.FOOTPRINT));
            }
            for (int offset = 0; offset < 128; offset++) {
                result.add(new SuppressionHighlight(anchor.offset(offset, 0, 127), SuppressionHighlight.Kind.FOOTPRINT));
            }
            for (int offset = 1; offset < 127; offset++) {
                result.add(new SuppressionHighlight(anchor.offset(0, 0, offset), SuppressionHighlight.Kind.FOOTPRINT));
                result.add(new SuppressionHighlight(anchor.offset(127, 0, offset), SuppressionHighlight.Kind.FOOTPRINT));
            }
        }
        if (stage == SuppressionStage.REMOVE || stage == SuppressionStage.VERIFY || stage == SuppressionStage.READY_NEXT) {
            for (SuppressionPlan.LocalPos local : currentRemovalBlocks()) {
                BlockPos world = worldPos(local);
                if (!client.level.getBlockState(world).isAir()) {
                    result.add(new SuppressionHighlight(world, SuppressionHighlight.Kind.REMOVE));
                    if (result.size() >= MAX_HIGHLIGHTS) break;
                }
            }
        }
        SuppressionPlan.StandPoint point = currentStandPoint();
        if (point != null && showsStandPoint(stage)) {
            result.add(new SuppressionHighlight(worldPos(point.standOn()), SuppressionHighlight.Kind.STAND));
        }
        return List.copyOf(result);
    }

    private void initialize(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return;
        initialized = true;
        sessionStore = SuppressionSessionStore.forRunDir(client.gameDirectory.toPath());
        try {
            SuppressionSessionStore.StoredSession stored = sessionStore.load();
            if (stored == null) return;
            Path runDir = client.gameDirectory.toPath().toAbsolutePath().normalize();
            Path planPath = Path.of(stored.planPath()).toAbsolutePath().normalize();
            Path schematicPath = Path.of(stored.schematicPath()).toAbsolutePath().normalize();
            Path expectedPlans = runDir.resolve("config/mapkluss-companion/suppression/plans").normalize();
            Path expectedSchematics = runDir.resolve("schematics").normalize();
            if (!planPath.startsWith(expectedPlans) || !schematicPath.startsWith(expectedSchematics)) throw new IOException("Stored Two-layer paths leave managed folders");
            byte[] planBytes = readManagedFile(planPath, SuppressionPlanParser.MAX_PLAN_BYTES, "Two-layer plan");
            byte[] litematicBytes = readManagedFile(schematicPath, SuppressionPlanParser.MAX_LITEMATIC_BYTES, "Two-layer Litematic");
            SuppressionBundleReader.validateLitematic(litematicBytes);
            if (!SuppressionHashes.sha256(planBytes).equals(stored.planSha256())
                || !SuppressionHashes.sha256(litematicBytes).equals(stored.litematicSha256())) {
                throw new IOException("Stored Two-layer files no longer match their checksums");
            }
            SuppressionPlanParser.Parsed parsed = SuppressionPlanParser.parse(planBytes);
            requireClientVersion(parsed.plan());
            bundle = new SuppressionBundle(parsed, planBytes, litematicBytes, stored.planSha256(), stored.litematicSha256(),
                stored.artId(), stored.versionId(), stored.title(), "restored");
            installed = new SuppressionBundleInstaller.Installed(planPath, schematicPath);
            stage = SuppressionStateLogic.restoredStage(stored.formatVersion(), stored.stage());
            anchor = stage == SuppressionStage.WAITING_ANCHOR ? null : new BlockPos(stored.anchorX(), stored.anchorY(), stored.anchorZ());
            worldHash = stage == SuppressionStage.WAITING_ANCHOR ? null : stored.worldHash();
            dimension = stage == SuppressionStage.WAITING_ANCHOR ? null : stored.dimension();
            mapId = stored.mapId();
            phaseIndex = stored.phaseIndex();
            standPointIndex = stored.standPointIndex();
            dwellTicks = 0;
            mapUpdateObserved = false;
            stablePointTicks = 0;
            manualOverrideArmed = stored.manualOverrideArmed();
            activeGuidePhase = -1;
            buildPlacementSyncPending = stage != SuppressionStage.WAITING_ANCHOR
                && stage != SuppressionStage.ANCHOR_CONFIRM
                && stage != SuppressionStage.PAUSED;
            status = "Сессия Two-layer восстановлена.";
        } catch (Exception error) {
            status = "Сохранённая сессия Two-layer повреждена и не загружена.";
            MapKlussCompanionClient.LOGGER.warn("Could not restore Two-layer session.", error);
            bundle = null;
            installed = null;
            stage = null;
            anchor = null;
            worldHash = null;
            dimension = null;
            mapId = -1;
        }
    }

    private void captureAnchor(Minecraft client) throws IOException {
        MapCheck map = boundMapCheck(client);
        if (!map.valid()) throw new IOException(map.message());
        if (client.level == null || !Level.OVERWORLD.equals(client.level.dimension())) {
            throw new IOException("Запоминать позицию Two-layer можно только находясь в Overworld");
        }
        if (!(client.hitResult instanceof BlockHitResult hit)) throw new IOException("Наведитесь на точный северо-западный блок");
        BlockPos selected = hit.getBlockPos();
        if (!SuppressionStateLogic.isScaleZeroNorthWest(selected.getX(), selected.getZ())) {
            throw new IOException("Выбранный блок не лежит на северо-западной границе карты масштаба 0. Координаты X и Z должны давать остаток 64 при делении на 128");
        }
        String capturedWorldHash = LensWorldIdentity.serverHash(client);
        if (capturedWorldHash == null) throw new IOException("Не удалось определить текущий сервер или одиночный мир");
        anchor = selected.immutable();
        worldHash = capturedWorldHash;
        dimension = LensWorldIdentity.dimensionId(client);
        stage = SuppressionStage.ANCHOR_CONFIRM;
        status = "Опора " + anchor.toShortString() + " · J: подтвердить";
        persist();
        notifyPlayer(client, status);
    }

    private void confirmAnchor(Minecraft client) throws IOException {
        if (!(client.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(anchor)) {
            stage = SuppressionStage.WAITING_ANCHOR;
            status = "Опора изменилась. Выберите её снова.";
            persist();
            notifyPlayer(client, status);
            return;
        }
        BlockPos planOrigin = planOrigin();
        // Remove placements created by pre-fix builds that incorrectly treated
        // either the support itself or support + 1 as the schematic origin.
        OptionalLitematicaAdapter.removePlanPlacements(anchor, bundle.planSha256());
        OptionalLitematicaAdapter.removePlanPlacements(SuppressionPlacementGeometry.legacyPlanOrigin(anchor), bundle.planSha256());
        OptionalLitematicaAdapter.Result litematica = OptionalLitematicaAdapter.createPlacement(
            installed.schematicPath(), planOrigin, bundle.planSha256());
        activeGuidePhase = -1;
        stage = SuppressionStage.BUILDING;
        notifyPlayer(client, litematica.message());
        status = "Постройте схему · J: готово";
        persist();
        notifyPlayer(client, status);
    }

    private void tickRemoval(Minecraft client) {
        if (activeGuidePhase != phaseIndex) activateRemovalGuide(client);
        if (++scanTicker % REMOVAL_SCAN_INTERVAL != 0) return;
        int remaining = 0;
        for (SuppressionPlan.LocalPos local : currentRemovalBlocks()) {
            BlockPos pos = worldPos(local);
            if (!client.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                status = "Часть этапа вне загруженных чанков.";
                remaining++;
                continue;
            }
            if (!client.level.getBlockState(pos).isAir()) remaining++;
        }
        remainingRemovalBlocks = remaining;
        if (remaining == 0) {
            stage = SuppressionStage.MOVE;
            standPointIndex = 0;
            dwellTicks = 0;
            stablePointTicks = 0;
            status = "Блоки сняты. Перейдите к точке.";
            persistQuietly();
        }
    }

    private void tickMove(Minecraft client) {
        SuppressionPlan.StandPoint point = currentStandPoint();
        if (point == null) return;
        if (currentPointExpectedMapMatches(client)) {
            stablePointTicks = 0;
            advanceCapturePoint(client, stage == SuppressionStage.INITIAL_MOVE);
            return;
        }
        if (!playerStandsOn(client, worldPos(point.standOn()))) {
            stablePointTicks = 0;
            status = "Встаньте на подсвеченную точку.";
            return;
        }
        MapCheck held = validHeldMap(client, false);
        if (!held.valid()) {
            stablePointTicks = 0;
            status = "Возьмите выбранную карту.";
            return;
        }
        stablePointTicks = SuppressionCapturePolicy.nextStableTick(stablePointTicks, stableCapturePosture(client));
        if (!SuppressionCapturePolicy.readyToCapture(stablePointTicks)) {
            status = "Остановитесь на точке.";
            return;
        }
        stage = stage == SuppressionStage.INITIAL_MOVE ? SuppressionStage.INITIAL_DWELL : SuppressionStage.DWELL;
        dwellTicks = 0;
        mapUpdateObserved = false;
        stablePointTicks = 0;
        status = "Запись карты…";
        persistQuietly();
    }

    private void tickEquip(Minecraft client) {
        boolean initial = stage == SuppressionStage.INITIAL_EQUIP;
        stage = initial ? SuppressionStage.INITIAL_MOVE : SuppressionStage.MOVE;
        status = "Встаньте на подсвеченную точку.";
        persistQuietly();
    }

    private void tickDwell(Minecraft client) {
        SuppressionPlan.StandPoint point = currentStandPoint();
        if (point == null) return;
        if (!stableCapturePosture(client)) {
            boolean initial = stage == SuppressionStage.INITIAL_DWELL;
            stage = initial ? SuppressionStage.INITIAL_MOVE : SuppressionStage.MOVE;
            dwellTicks = 0;
            mapUpdateObserved = false;
            status = "Вернитесь на подсвеченную точку.";
            persistQuietly();
            return;
        }
        if (!validHeldMap(client, false).valid()) {
            boolean initial = stage == SuppressionStage.INITIAL_DWELL;
            stage = initial ? SuppressionStage.INITIAL_MOVE : SuppressionStage.MOVE;
            dwellTicks = 0;
            mapUpdateObserved = false;
            status = "Возьмите выбранную карту.";
            persistQuietly();
            return;
        }
        dwellTicks = SuppressionStateLogic.nextDwellTick(dwellTicks, point.minTicks());
        boolean expectedPointMatches = currentPointExpectedMapMatches(client);
        if (dwellTicks < point.minTicks() || !expectedPointMatches) {
            if (dwellTicks < point.minTicks()) return;
            status = "Ожидание обновления карты…";
            return;
        }
        boolean initial = stage == SuppressionStage.INITIAL_DWELL;
        dwellTicks = 0;
        mapUpdateObserved = false;
        advanceCapturePoint(client, initial);
    }

    private void advanceCapturePoint(Minecraft client, boolean initial) {
        standPointIndex++;
        List<SuppressionPlan.StandPoint> points = currentStandPointsFor(initial);
        if (standPointIndex < points.size()) {
            stage = initial ? SuppressionStage.INITIAL_MOVE : SuppressionStage.MOVE;
            status = "Точка записана. Перейдите к следующей.";
        } else {
            stage = initial ? SuppressionStage.INITIAL_VERIFY : SuppressionStage.VERIFY;
            status = "Проверка карты…";
        }
        persistQuietly();
    }

    private void tickVerify(Minecraft client) {
        MapCheck check = validMapAnywhere(client, false);
        if (!check.valid()) return;
        boolean initial = stage == SuppressionStage.INITIAL_VERIFY;
        int completedPhases = initial ? 0 : phaseIndex + 1;
        SuppressionStateLogic.VerificationMismatch mismatch = SuppressionStateLogic.compareVerifiedPixels(
            check.colors(), bundle.parsed().targetMapBytes(), completedPhases);
        if (mismatch.total() > 0) {
            if (initial) {
                manualOverrideArmed = false;
                status = "Карта не совпадает: " + mismatch.total() + " пикс. J: повторить";
            } else if (mismatch.recessive() > 0) {
                manualOverrideArmed = false;
                status = "Повреждены пиксели прошлых этапов: " + mismatch.recessive() + ". Начните заново.";
            } else {
                status = manualOverrideArmed
                    ? "Расхождение: " + mismatch.completedDominant() + " пикс. J: подтвердить"
                    : "Пиксели этапа не совпадают: " + mismatch.completedDominant() + ". Проверьте блоки.";
            }
            return;
        }
        manualOverrideArmed = false;
        if (initial) {
            phaseIndex = 0;
            standPointIndex = 0;
            stage = SuppressionStage.REMOVE;
            remainingRemovalBlocks = currentRemovalBlocks().size();
            status = "Снимите подсвеченные блоки.";
            activateRemovalGuide(client);
        } else {
            stage = SuppressionStage.READY_NEXT;
            status = phaseIndex == 63 ? "Карта готова · J: завершить" : "Этап готов · J: дальше";
        }
        persistQuietly();
    }

    private void handleManualOverride(Minecraft client) throws IOException {
        MapCheck check = validMapAnywhere(client, false);
        if (!check.valid()) throw new IOException(check.message());
        boolean initial = stage == SuppressionStage.INITIAL_VERIFY;
        int completedPhases = initial ? 0 : phaseIndex + 1;
        SuppressionStateLogic.VerificationMismatch mismatch = SuppressionStateLogic.compareVerifiedPixels(
            check.colors(), bundle.parsed().targetMapBytes(), completedPhases);
        if (mismatch.total() == 0) {
            manualOverrideArmed = false;
            tickVerify(client);
            return;
        }
        if (initial) {
            manualOverrideArmed = false;
            standPointIndex = 0;
            dwellTicks = 0;
            mapUpdateObserved = false;
            stablePointTicks = 0;
            stage = SuppressionStage.INITIAL_MOVE;
            status = "Повторите запись из центра.";
            persist();
            notifyPlayer(client, status);
            return;
        }
        if (mismatch.recessive() > 0) {
            manualOverrideArmed = false;
            status = "Нельзя продолжить: повреждены пиксели прошлых этапов.";
            persist();
            notifyPlayer(client, status);
            return;
        }
        if (!manualOverrideArmed) {
            manualOverrideArmed = true;
            status = "Расхождение: " + mismatch.completedDominant() + " пикс. Нажмите J ещё раз.";
            persist();
            notifyPlayer(client, status);
            return;
        }
        manualOverrideArmed = false;
        stage = SuppressionStage.READY_NEXT;
        status = "Переход выполнен с расхождением.";
        persist();
    }

    private void advancePhase(Minecraft client) throws IOException {
        if (phaseIndex >= bundle.parsed().plan().phases().size() - 1) {
            stage = SuppressionStage.COMPLETE;
            status = "Готово. Проверьте и заблокируйте карту.";
        } else {
            phaseIndex++;
            stage = SuppressionStage.REMOVE;
            standPointIndex = 0;
            dwellTicks = 0;
            mapUpdateObserved = false;
            stablePointTicks = 0;
            remainingRemovalBlocks = currentRemovalBlocks().size();
            status = "Снимите подсвеченные блоки.";
            activateRemovalGuide(client);
        }
        persist();
        notifyPlayer(client, status);
    }

    private MapCheck validHeldMap(Minecraft client, boolean allowAnyId) {
        if (client.player == null || client.level == null) return invalidMap("Мир не загружен");
        ItemStack mainStack = client.player.getMainHandItem();
        ItemStack offStack = client.player.getOffhandItem();
        MapCheck main = inspectMap(client, mainStack, allowAnyId);
        if (main.valid()) return main;
        MapCheck off = inspectMap(client, offStack, allowAnyId);
        if (off.valid()) return off;
        if (MapArtTiles.mapId(mainStack) != null) return main;
        if (MapArtTiles.mapId(offStack) != null) return off;
        return invalidMap("Возьмите выбранную карту");
    }

    private MapCheck validMapAnywhere(Minecraft client, boolean allowAnyId) {
        if (client.player == null || client.level == null) return invalidMap("Мир не загружен");
        return allowAnyId ? validHeldMap(client, true) : boundMapCheck(client);
    }

    public synchronized void onMapUpdate(int updatedMapId, boolean hasPixelUpdate) {
        if (!active() || !hasPixelUpdate || updatedMapId != mapId) return;
        if (SuppressionStateLogic.acceptsCaptureUpdate(stage)) {
            mapUpdateObserved = true;
        }
    }

    private boolean currentPointExpectedMapMatches(Minecraft client) {
        MapCheck check = boundMapCheck(client);
        if (!check.valid()) return false;
        if (isInitialCaptureStage(stage)) {
            return SuppressionStateLogic.verifiedPixelsMatchTarget(check.colors(), bundle.parsed().targetMapBytes(), 0);
        }
        if (phaseIndex < 0 || phaseIndex >= bundle.parsed().plan().phases().size()) return false;
        return SuppressionStateLogic.pointPixelsMatchTarget(
            check.colors(), bundle.parsed().targetMapBytes(), bundle.parsed().plan().phases().get(phaseIndex), standPointIndex);
    }

    private MapCheck inspectMap(Minecraft client, ItemStack stack, boolean allowAnyId) {
        Integer id = MapArtTiles.mapId(stack);
        if (id == null) return invalidMap("Возьмите заполненную карту");
        MapItemSavedData state = MapItem.getSavedData(stack, client.level);
        if (state == null) return invalidMap(id, "Данные карты ещё не загружены");
        if (!allowAnyId && id != mapId) return invalidMap(id, "Это другая карта");
        if (state.scale != 0) return invalidMap(id, "Нужна карта масштаба 0");
        if (state.locked) return invalidMap(id, "Карта уже заблокирована");
        if (!Level.OVERWORLD.equals(state.dimension)) return invalidMap(id, "Two-layer работает только в Overworld");
        if (state.colors == null || state.colors.length != 128 * 128) return invalidMap(id, "Неверный размер данных карты");
        return new MapCheck(true, id, state.colors.clone(), "");
    }

    private List<SuppressionPlan.LocalPos> currentRemovalBlocks() {
        if (!active() || phaseIndex < 0 || phaseIndex >= bundle.parsed().plan().phases().size()) return List.of();
        return SuppressionStateLogic.removalBlocks(bundle.parsed().plan().phases().get(phaseIndex));
    }

    private List<SuppressionPlan.StandPoint> currentStandPoints() {
        if (!active()) return List.of();
        if (isInitialCaptureStage(stage)) {
            return bundle.parsed().plan().initialCapture().standPoints();
        }
        if (phaseIndex < 0 || phaseIndex >= bundle.parsed().plan().phases().size()) return List.of();
        return bundle.parsed().plan().phases().get(phaseIndex).standPoints();
    }

    private SuppressionPlan.StandPoint currentStandPoint() {
        List<SuppressionPlan.StandPoint> points = currentStandPoints();
        return standPointIndex >= 0 && standPointIndex < points.size() ? points.get(standPointIndex) : null;
    }

    private List<SuppressionPlan.StandPoint> currentStandPointsFor(boolean initial) {
        if (initial) return bundle.parsed().plan().initialCapture().standPoints();
        if (phaseIndex < 0 || phaseIndex >= bundle.parsed().plan().phases().size()) return List.of();
        return bundle.parsed().plan().phases().get(phaseIndex).standPoints();
    }

    private BlockPos worldPos(SuppressionPlan.LocalPos local) {
        return SuppressionPlacementGeometry.worldPos(anchor, bundle.parsed().plan(), local);
    }

    private BlockPos planOrigin() {
        return SuppressionPlacementGeometry.planOrigin(anchor, bundle.parsed().plan());
    }

    private void activateRemovalGuide(Minecraft client) {
        if (!active() || anchor == null || phaseIndex < 0 || phaseIndex >= bundle.parsed().plan().phases().size()) return;
        activeGuidePhase = phaseIndex;
        try {
            Path reference = SuppressionReferenceLitematic.install(
                client.gameDirectory.toPath(),
                bundle.planSha256(),
                bundle.parsed().plan(),
                bundle.litematicBytes(),
                phaseIndex
            );
            OptionalLitematicaAdapter.Result result = OptionalLitematicaAdapter.createReferencePlacement(
                reference, planOrigin(), bundle.planSha256(), phaseIndex);
            if (!result.placed()) {
                // The original build placement would keep ghosting already
                // removed top blocks. Live MapKluss highlights are safer.
                OptionalLitematicaAdapter.removePlanPlacements(planOrigin(), bundle.planSha256());
            }
            notifyPlayer(client, result.message());
        } catch (Exception error) {
            MapKlussCompanionClient.LOGGER.warn("Could not create the safe Two-layer phase reference.", error);
            OptionalLitematicaAdapter.removePlanPlacements(planOrigin(), bundle.planSha256());
            notifyPlayer(client, "Не удалось обновить схему этапа. Используйте подсветку MapKluss.");
        }
    }

    private void syncRestoredBuildPlacement(Minecraft client) {
        buildPlacementSyncPending = false;
        try {
            if (usesPhaseReference(stage)) {
                activateRemovalGuide(client);
                status = "Схема этапа восстановлена.";
                return;
            }
            OptionalLitematicaAdapter.removePlanPlacements(anchor, bundle.planSha256());
            OptionalLitematicaAdapter.removePlanPlacements(
                SuppressionPlacementGeometry.legacyPlanOrigin(anchor), bundle.planSha256());
            OptionalLitematicaAdapter.Result result = OptionalLitematicaAdapter.createPlacement(
                installed.schematicPath(), planOrigin(), bundle.planSha256());
            status = result.placed() ? "Схема восстановлена." : result.message();
            notifyPlayer(client, status);
        } catch (Exception error) {
            MapKlussCompanionClient.LOGGER.warn("Could not synchronize the restored Two-layer placement.", error);
            status = "Не удалось обновить размещение схемы; запустите Two-layer заново.";
            notifyPlayer(client, status);
        }
    }

    private static boolean usesPhaseReference(SuppressionStage stage) {
        return stage == SuppressionStage.REMOVE || stage == SuppressionStage.MOVE
            || stage == SuppressionStage.EQUIP || stage == SuppressionStage.DWELL
            || stage == SuppressionStage.STOW || stage == SuppressionStage.VERIFY
            || stage == SuppressionStage.READY_NEXT || stage == SuppressionStage.COMPLETE;
    }

    private int findStartMapId(Minecraft client) throws IOException {
        if (client == null || client.player == null || client.level == null) {
            throw new IOException("Запустите Two-layer внутри загруженного мира");
        }
        MapCheck held = validHeldMap(client, true);
        if (held.valid()) return held.mapId();

        int candidate = -1;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            MapCheck check = inspectMap(client, client.player.getInventory().getItem(slot), true);
            if (!check.valid()) continue;
            if (candidate >= 0 && candidate != check.mapId()) {
                throw new IOException("Возьмите нужную карту в руку");
            }
            candidate = check.mapId();
        }
        if (candidate < 0) {
            throw new IOException("Подготовьте незаблокированную карту масштаба 0");
        }
        return candidate;
    }

    private MapCheck boundMapCheck(Minecraft client) {
        if (client == null || client.player == null || client.level == null || mapId < 0) {
            return invalidMap("Выбранная карта не найдена");
        }
        MapCheck held = validHeldMap(client, false);
        if (held.valid()) return held;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (!isBoundMapStack(stack)) continue;
            MapCheck check = inspectMap(client, stack, false);
            if (check.valid()) return check;
        }
        return invalidMap("Выбранная карта не найдена");
    }

    private boolean isBoundMapStack(ItemStack stack) {
        if (stack == null || mapId < 0) return false;
        Integer id = MapArtTiles.mapId(stack);
        return id != null && id == mapId;
    }

    private boolean stableCapturePosture(Minecraft client) {
        SuppressionPlan.StandPoint point = currentStandPoint();
        if (point == null || client == null || client.player == null) return false;
        double vx = client.player.getDeltaMovement().x;
        double vz = client.player.getDeltaMovement().z;
        return sameBoundWorld(client) && SuppressionCapturePolicy.stableCapturePosture(
            playerStandsOn(client, worldPos(point.standOn())),
            client.player.onGround(),
            client.player.isPassenger(),
            client.player.isSwimming(),
            client.player.getAbilities().flying,
            movementRequested(client),
            client.gui.screen() != null,
            vx * vx + vz * vz
        );
    }

    private boolean sameBoundWorld(Minecraft client) {
        if (client == null || client.level == null || worldHash == null || dimension == null) return false;
        return worldHash.equals(LensWorldIdentity.serverHash(client))
            && dimension.equals(LensWorldIdentity.dimensionId(client));
    }

    private static boolean movementRequested(Minecraft client) {
        return client.options.keyUp.isDown() || client.options.keyDown.isDown()
            || client.options.keyLeft.isDown() || client.options.keyRight.isDown()
            || client.options.keyJump.isDown() || client.options.keyShift.isDown();
    }

    private static boolean playerStandsOn(Minecraft client, BlockPos block) {
        return client.player != null && SuppressionCapturePolicy.centeredOnCheckpoint(
            client.player.getX(), client.player.getY(), client.player.getZ(),
            block.getX(), block.getY(), block.getZ());
    }

    private static byte[] readManagedFile(Path path, int maxBytes, String label) throws IOException {
        long size = Files.size(path);
        if (size < 1 || size > maxBytes) throw new IOException(label + " size is outside the safe limit");
        return Files.readAllBytes(path);
    }

    private void persist() throws IOException {
        if (sessionStore == null || bundle == null || installed == null || stage == null) return;
        BlockPos savedAnchor = anchor == null ? BlockPos.ZERO : anchor;
        sessionStore.save(new SuppressionSessionStore.StoredSession(
            4,
            installed.planPath().toString(),
            installed.schematicPath().toString(),
            bundle.planSha256(),
            bundle.litematicSha256(),
            bundle.artId(),
            bundle.versionId(),
            bundle.title(),
            stage,
            savedAnchor.getX(), savedAnchor.getY(), savedAnchor.getZ(),
            worldHash,
            dimension,
            mapId,
            phaseIndex,
            standPointIndex,
            dwellTicks,
            manualOverrideArmed,
            System.currentTimeMillis()
        ));
    }

    private void persistQuietly() {
        try {
            persist();
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.warn("Could not save Two-layer progress.", error);
            status = "Не удалось сохранить прогресс Two-layer.";
        }
    }

    private static void requireClientVersion(SuppressionPlan plan) throws IOException {
        if (plan.version() < 3) {
            throw new IOException("Этот старый Two-layer план содержит прежнее полотно или разметку. Экспортируйте новый ZIP версии 3 на сайте");
        }
        String current = FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("");
        if (!current.equals(plan.target().minecraftVersion())) {
            throw new IOException("План создан для Minecraft " + plan.target().minecraftVersion() + ", запущена " + current);
        }
    }

    private String actionHint() {
        return switch (stage) {
            case WAITING_ANCHOR -> "Выберите северо-западную опору · J";
            case ANCHOR_CONFIRM -> "J: подтвердить опору";
            case BUILDING -> "Постройте схему · J: готово";
            case INITIAL_MOVE, MOVE -> "Перейдите к точке";
            case INITIAL_EQUIP, EQUIP -> "Возьмите выбранную карту";
            case INITIAL_DWELL, DWELL -> "Не двигайтесь";
            case INITIAL_STOW, STOW -> "Перейдите к следующей точке";
            case READY_NEXT -> phaseIndex == 63 ? "J: завершить" : "J: дальше";
            case INITIAL_VERIFY -> "Проверка карты";
            case VERIFY -> manualOverrideArmed ? "J: продолжить с расхождением" : "Проверка карты";
            case PAUSED -> status == null || status.isBlank() ? "Пауза" : status;
            default -> "Следуйте подсветке";
        };
    }

    private String stageLabel() {
        return switch (stage) {
            case REMOVE -> "снимите блоки";
            case INITIAL_MOVE, MOVE -> "перейдите к точке";
            case INITIAL_EQUIP, EQUIP -> "возьмите карту";
            case INITIAL_DWELL, DWELL -> "запись карты";
            case INITIAL_STOW, STOW -> "следующая точка";
            case INITIAL_VERIFY, VERIFY -> "проверка карты";
            case READY_NEXT -> "этап готов";
            case PAUSED -> "пауза";
            default -> stage.name().toLowerCase(Locale.ROOT);
        };
    }

    private String shortTitle() {
        String title = bundle.title() == null || bundle.title().isBlank() ? "локальный план" : bundle.title();
        return title.length() <= 26 ? title : title.substring(0, 23) + "...";
    }

    private static void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(CompanionI18n.translate(message)));
    }

    private static boolean showsStandPoint(SuppressionStage stage) {
        return stage == SuppressionStage.INITIAL_MOVE || stage == SuppressionStage.INITIAL_EQUIP
            || stage == SuppressionStage.INITIAL_DWELL || stage == SuppressionStage.INITIAL_STOW
            || stage == SuppressionStage.MOVE || stage == SuppressionStage.EQUIP
            || stage == SuppressionStage.DWELL || stage == SuppressionStage.STOW;
    }

    private static boolean isInitialCaptureStage(SuppressionStage stage) {
        return stage == SuppressionStage.INITIAL_MOVE || stage == SuppressionStage.INITIAL_EQUIP
            || stage == SuppressionStage.INITIAL_DWELL || stage == SuppressionStage.INITIAL_STOW
            || stage == SuppressionStage.INITIAL_VERIFY;
    }

    private static MapCheck invalidMap(String message) {
        return invalidMap(-1, message);
    }

    private static MapCheck invalidMap(int id, String message) {
        return new MapCheck(false, id, null, message);
    }

    private record MapCheck(boolean valid, int mapId, byte[] colors, String message) { }

}
