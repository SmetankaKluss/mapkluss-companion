package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class LensManager {
    private static final LensManager INSTANCE = new LensManager();
    private static final double RENDER_DISTANCE_SQUARED = 256.0 * 256.0;
    private static final int RENDER_BATCH_SIZE = 16;
    private static final int MAX_RENDER_PLACEMENTS = 8;
    private static final int MAX_RENDER_CELLS = 40_000;

    private final Map<String, LensDtos.Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, LensDtos.Placement> placements = new ConcurrentHashMap<>();
    private final Map<String, LensTextureAtlas> atlases = new ConcurrentHashMap<>();
    private final Map<String, LensRealtimeWakeup> realtime = new ConcurrentHashMap<>();
    private final Set<String> realtimeDirtySessions = ConcurrentHashMap.newKeySet();
    private final Map<String, OwnedPlacementIdentity> locallyOwnedPlacements = new ConcurrentHashMap<>();
    private final Map<String, SnapshotGeometry> snapshotGeometry = new ConcurrentHashMap<>();
    private final LensRecoveryGate recoveryGate = new LensRecoveryGate();
    private final AtomicBoolean heartbeatWork = new AtomicBoolean();
    private final AtomicBoolean sessionRefresh = new AtomicBoolean();
    private final AtomicBoolean placementMutation = new AtomicBoolean();
    private final AtomicLong realtimeWakeups = new AtomicLong();
    private final AtomicLong fallbackPolls = new AtomicLong();
    private final AtomicLong worldGeneration = new AtomicLong();
    private volatile List<LensRenderSnapshot> renderSnapshots = List.of();

    private volatile LensPreferences preferences;
    private volatile String worldKey;
    private volatile String status = CompanionI18n.translate("Lens готов");
    private volatile boolean enabled = true;
    private volatile long nextPollAt;
    private volatile long nextHeartbeatAt;
    private volatile long nextSessionListAt;
    private volatile long nextCapabilitiesAt;
    private volatile int renderRefreshTicks;
    private volatile int degradedPollAttempt;
    private volatile boolean capabilitiesKnown;
    private volatile boolean screenOpen;
    private volatile String lastRecoverySource = "poll";
    private volatile String realtimeBackendUrl = CompanionConfig.DEFAULT_SUPABASE_URL;

    private LensManager() {
    }

    public static LensManager instance() {
        return INSTANCE;
    }

    public List<LensDtos.Session> sessions() {
        return sessions.values().stream().sorted((a, b) -> a.title().compareToIgnoreCase(b.title())).toList();
    }

    public List<LensDtos.Placement> placements() {
        LensPreferences prefs = preferences;
        if (prefs == null) return List.copyOf(placements.values());
        return LensStateLogic.visiblePlacements(
            List.copyOf(placements.values()),
            prefs.hiddenPlacementIds(),
            prefs.blockedOwnerKeys()
        );
    }

    public List<LensRenderSnapshot> renderSnapshots() {
        return renderSnapshots;
    }

    public String status() {
        return status;
    }

    void setLocalStatus(String nextStatus) {
        status = nextStatus == null ? "" : nextStatus;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean locallyOwned(String placementId) {
        return locallyOwnedPlacements.containsKey(placementId);
    }

    public long realtimeWakeupCount() {
        return realtimeWakeups.get();
    }

    public long fallbackPollCount() {
        return fallbackPolls.get();
    }

    public String lastRecoverySource() {
        return lastRecoverySource;
    }

    public void tick(MinecraftClient client) {
        ensurePreferences(client);
        if (client.world == null || client.player == null) {
            if (worldKey != null) {
                clearWorldState();
                worldKey = null;
                worldGeneration.incrementAndGet();
            }
            return;
        }

        String currentWorld = String.valueOf(LensWorldIdentity.serverHash(client)) + "|" + LensWorldIdentity.dimensionId(client);
        if (!currentWorld.equals(worldKey)) {
            clearWorldState();
            worldKey = currentWorld;
            worldGeneration.incrementAndGet();
            nextPollAt = 0;
            nextHeartbeatAt = 0;
            nextSessionListAt = 0;
        }

        if (placements.isEmpty()) {
            if (!renderSnapshots.isEmpty()) renderSnapshots = List.of();
            snapshotGeometry.clear();
            renderRefreshTicks = 0;
        } else {
            long totalCells = placements.values().stream()
                .filter(placement -> placement.grid() != null)
                .mapToLong(placement -> (long) placement.grid().wide() * placement.grid().tall())
                .sum();
            renderRefreshTicks++;
            if (renderRefreshTicks >= LensSyncPolicy.renderRefreshTicks(totalCells)) {
                renderRefreshTicks = 0;
                rebuildRenderSnapshots(client);
            }
        }

        if (!LensSyncPolicy.networkActive(
            screenOpen, sessions.size(), placements.size(), ownedPlacementIds(worldKey).size()
        )) {
            return;
        }
        long now = System.nanoTime();
        if (now >= nextSessionListAt) refreshSessions(client);
        if (now >= nextHeartbeatAt) scheduleHeartbeat(client);
        boolean realtimePending = !realtimeDirtySessions.isEmpty();
        if (realtimePending) nextPollAt = 0;
        if (now >= nextPollAt) scheduleRecovery(client, realtimePending);
    }

    public void screenOpened() {
        screenOpen = true;
        nextSessionListAt = 0;
    }

    public void screenClosed() {
        screenOpen = false;
    }

    public void disconnected() {
        clearWorldState();
        worldKey = null;
        worldGeneration.incrementAndGet();
    }

    public void refreshSessions(MinecraftClient client) {
        if (!sessionRefresh.compareAndSet(false, true)) return;
        long generation = worldGeneration.get();
        nextSessionListAt = System.nanoTime() + LensSyncPolicy.SESSION_LIST_NANOS;
        status = CompanionI18n.translate("Загрузка сессий Lens...");
        CompletableFuture.runAsync(() -> {
            try {
                LensApiClient api = api(client);
                if (!capabilitiesKnown || System.nanoTime() >= nextCapabilitiesAt) {
                    LensDtos.Capabilities capabilities = api.capabilities();
                    capabilitiesKnown = true;
                    nextCapabilitiesAt = System.nanoTime() + LensSyncPolicy.CAPABILITIES_NANOS;
                    if (!capabilities.enabled()) {
                        client.execute(() -> {
                            if (generation == worldGeneration.get()) clearDisabledState();
                        });
                        return;
                    }
                }
                LensDtos.SessionList result = api.sessionList();
                client.execute(() -> {
                    if (generation != worldGeneration.get()) return;
                    enabled = true;
                    Set<String> received = new LinkedHashSet<>();
                    for (LensDtos.Session session : result.safeSessions().stream()
                        .limit(LensSyncPolicy.MAX_SYNC_SESSIONS).toList()) {
                        if (session == null || session.sessionId() == null) continue;
                        received.add(session.sessionId());
                        acceptSession(session);
                    }
                    sessions.keySet().stream().filter(id -> !received.contains(id)).toList().forEach(id -> {
                        sessions.remove(id);
                        removeSessionState(id);
                    });
                    nextHeartbeatAt = 0;
                    nextPollAt = 0;
                    status = CompanionI18n.translate(enabled ? "Сессии Lens обновлены" : "Lens отключён сервером MapKluss");
                });
            } catch (Exception e) {
                nextSessionListAt = System.nanoTime() + LensSyncPolicy.HEARTBEAT_NANOS;
                setError(client, "Не удалось загрузить сессии Lens", e, generation, null);
            } finally {
                sessionRefresh.set(false);
            }
        });
    }

    public void join(MinecraftClient client, String sessionCode) {
        String code = sessionCode == null ? "" : sessionCode.trim().toUpperCase();
        if (code.isBlank()) {
            status = CompanionI18n.translate("Введите код группы Lens");
            return;
        }
        status = CompanionI18n.translate("Вход в группу Lens...");
        long generation = worldGeneration.get();
        CompletableFuture.runAsync(() -> {
            try {
                LensDtos.Session session = api(client).join(code);
                client.execute(() -> {
                    if (generation != worldGeneration.get()) return;
                    acceptSession(session);
                    status = CompanionI18n.translate("Группа подключена") + ": " + session.title();
                    nextPollAt = 0;
                });
            } catch (Exception e) {
                setError(client, "Не удалось войти в группу Lens", e, generation, null);
            }
        });
    }

    public void leave(MinecraftClient client, String sessionId) {
        if (sessionId == null) return;
        long generation = worldGeneration.get();
        CompletableFuture.runAsync(() -> {
            try {
                api(client).leave(sessionId);
                client.execute(() -> {
                    if (generation != worldGeneration.get()) return;
                    sessions.remove(sessionId);
                    removeSessionState(sessionId);
                    status = CompanionI18n.translate("Вы вышли из группы Lens");
                });
            } catch (Exception e) {
                setError(client, "Не удалось выйти из группы Lens", e, generation, null);
            }
        });
    }

    public void anchorTarget(MinecraftClient client, String sessionId, String visibility) {
        if (!placementMutation.compareAndSet(false, true)) {
            status = CompanionI18n.translate("Размещение Lens уже сохраняется");
            return;
        }
        LensDtos.Session session = sessions.get(sessionId);
        if (session == null) {
            status = CompanionI18n.translate("Сначала выберите сессию Lens");
            placementMutation.set(false);
            return;
        }
        final LensFrameTarget.Target target;
        try {
            target = LensFrameTarget.capture(client);
        } catch (IOException e) {
            status = CompanionUiErrors.message("lens", e);
            placementMutation.set(false);
            return;
        }
        String serverHash = LensWorldIdentity.serverHash(client);
        String dimensionId = LensWorldIdentity.dimensionId(client);
        if (!"personal".equals(visibility) && serverHash == null) {
            status = CompanionI18n.translate("В одиночной игре размещение Lens может быть только личным");
            placementMutation.set(false);
            return;
        }
        LensDtos.Anchor anchor = new LensDtos.Anchor(target.anchor().getX(), target.anchor().getY(), target.anchor().getZ());
        String placementId = placementId(sessionId, serverHash, dimensionId, anchor, target.facing().asString());
        status = CompanionI18n.translate("Создание размещения Lens...");
        long generation = worldGeneration.get();
        CompletableFuture.runAsync(() -> {
            try {
                LensDtos.Placement placement = api(client).upsertPlacement(
                    placementId, sessionId, visibility, serverHash, dimensionId, anchor, target.facing().asString()
                );
                client.execute(() -> {
                    if (generation != worldGeneration.get()) return;
                    allowOwnPlacement(placement);
                    placements.put(placement.placementId(), placement);
                    rememberOwnedPlacement(placement.placementId(), placement.sessionId(), worldKey);
                    acceptSession(session);
                    status = CompanionI18n.translate("Lens закреплён") + ": " + placement.title();
                    nextPollAt = 0;
                });
            } catch (Exception e) {
                setError(client, "Не удалось создать размещение Lens", e, generation, null);
            } finally {
                placementMutation.set(false);
            }
        });
    }

    public void deletePlacement(MinecraftClient client, String placementId) {
        if (placementId == null) return;
        long generation = worldGeneration.get();
        CompletableFuture.runAsync(() -> {
            try {
                api(client).deletePlacement(placementId);
                client.execute(() -> {
                    if (generation != worldGeneration.get()) return;
                    placements.remove(placementId);
                    locallyOwnedPlacements.remove(placementId);
                    snapshotGeometry.remove(placementId);
                    status = CompanionI18n.translate("Размещение Lens удалено");
                });
            } catch (Exception e) {
                setError(client, "Не удалось удалить размещение Lens", e, generation, null);
            }
        });
    }

    public void hidePlacement(String placementId) {
        try {
            preferences.hide(placementId);
            status = CompanionI18n.translate("Размещение Lens скрыто локально");
        } catch (Exception e) {
            status = CompanionI18n.translate("Не удалось сохранить настройки Lens");
        }
    }

    public void blockOwner(String ownerKey) {
        try {
            preferences.block(ownerKey);
            status = CompanionI18n.translate("Автор Lens заблокирован локально");
        } catch (Exception e) {
            status = CompanionI18n.translate("Не удалось сохранить настройки Lens");
        }
    }

    public void reportPlacement(MinecraftClient client, LensDtos.Placement placement, String reason) {
        if (placement == null) return;
        hidePlacement(placement.placementId());
        long generation = worldGeneration.get();
        CompletableFuture.runAsync(() -> {
            try {
                api(client).reportPlacement(placement.placementId(), reason);
                client.execute(() -> {
                    if (generation == worldGeneration.get()) {
                        status = CompanionI18n.translate("Жалоба отправлена, размещение скрыто");
                    }
                });
            } catch (Exception e) {
                setError(client, "Размещение скрыто, но жалобу отправить не удалось", e, generation, null);
            }
        });
    }

    private void scheduleHeartbeat(MinecraftClient client) {
        if (!heartbeatWork.compareAndSet(false, true)) return;
        nextHeartbeatAt = System.nanoTime() + LensSyncPolicy.jitteredDelayNanos(LensSyncPolicy.HEARTBEAT_NANOS);
        Set<String> sessionIds = syncSessionIds(false);
        Set<String> ownedPlacementIds = ownedPlacementIds(worldKey).stream()
            .limit(LensSyncPolicy.MAX_SYNC_SESSIONS).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (sessionIds.isEmpty() && ownedPlacementIds.isEmpty()) {
            heartbeatWork.set(false);
            return;
        }
        long generation = worldGeneration.get();
        CompletableFuture.runAsync(() -> {
            try {
                api(client).heartbeat(sessionIds, ownedPlacementIds);
            } catch (Exception error) {
                setError(client, "Не удалось подтвердить активность Lens", error, generation, null);
            } finally {
                heartbeatWork.set(false);
            }
        });
    }

    private boolean scheduleRecovery(MinecraftClient client, boolean realtimeTriggered) {
        LensRecoveryGate.Token recovery = recoveryGate.tryBegin();
        if (recovery == null) return false;
        Set<String> sessionIds = syncSessionIds(realtimeTriggered);
        if (sessionIds.isEmpty()) {
            recoveryGate.finish(recovery, () ->
                nextPollAt = System.nanoTime() + LensSyncPolicy.HEALTHY_POLL_NANOS
            );
            return false;
        }
        if (realtimeTriggered) realtimeDirtySessions.removeAll(sessionIds);
        nextPollAt = System.nanoTime() + 12_000_000_000L;
        lastRecoverySource = realtimeTriggered ? "ws" : "poll";
        if (!realtimeTriggered) fallbackPolls.incrementAndGet();
        long generation = worldGeneration.get();
        String serverHash = LensWorldIdentity.serverHash(client);
        String dimensionId = LensWorldIdentity.dimensionId(client);

        CompletableFuture.runAsync(() -> {
            boolean failed = false;
            try {
                if (!recoveryGate.isCurrent(recovery)) return;
                LensApiClient api = api(client);
                for (String sessionId : sessionIds) {
                    if (!recoveryGate.isCurrent(recovery)) return;
                    try {
                        pollSession(api, client, recovery, generation, sessionId, serverHash, dimensionId);
                    } catch (LensApiException error) {
                        if (!recoveryGate.isCurrent(recovery)) return;
                        if ("session_gone".equals(error.errorCode()) || "not_joined".equals(error.errorCode()) || "not_found".equals(error.errorCode())) {
                            client.execute(() -> {
                                if (generation != worldGeneration.get() || !recoveryGate.isLatest(recovery)) return;
                                sessions.remove(sessionId);
                                removeSessionState(sessionId);
                            });
                        } else {
                            if (!recoveryGate.runIfCurrent(recovery, () -> realtimeDirtySessions.add(sessionId))) return;
                            failed = true;
                            MapKlussCompanionClient.LOGGER.debug("Lens session {} poll failed.", sessionId, error);
                        }
                    } catch (Exception error) {
                        if (!recoveryGate.runIfCurrent(recovery, () -> realtimeDirtySessions.add(sessionId))) return;
                        failed = true;
                        MapKlussCompanionClient.LOGGER.debug("Lens session {} poll failed.", sessionId, error);
                    }
                }
            } catch (Exception e) {
                if (!recoveryGate.runIfCurrent(recovery, () -> realtimeDirtySessions.addAll(sessionIds))) return;
                failed = true;
                setError(client, "Не удалось обновить Lens", e, generation, recovery);
            } finally {
                boolean healthy = !failed && realtimeHealthy(sessionIds);
                int currentAttempt = degradedPollAttempt;
                recoveryGate.finish(recovery, () -> {
                    degradedPollAttempt = LensSyncPolicy.nextDegradedAttempt(healthy, currentAttempt);
                    nextPollAt = System.nanoTime() + LensSyncPolicy.jitteredDelayNanos(
                        LensSyncPolicy.recoveryDelayNanos(healthy, currentAttempt)
                    );
                });
            }
        });
        return true;
    }

    private void pollSession(
        LensApiClient api,
        MinecraftClient client,
        LensRecoveryGate.Token recovery,
        long generation,
        String sessionId,
        String serverHash,
        String dimensionId
    )
        throws IOException, InterruptedException {
        LensTextureAtlas atlas = atlases.get(sessionId);
        long knownRevision = atlas == null ? 0 : Math.max(0, atlas.revision());
        LensDtos.PollResult result = api.poll(sessionId, knownRevision, serverHash, dimensionId);
        client.execute(() -> {
            if (generation != worldGeneration.get() || !recoveryGate.isLatest(recovery) || result.session() == null) return;
            LensDtos.Session current = sessions.get(sessionId);
            if (current != null && result.session().revision() < current.revision()) return;
            acceptSession(result.session());
            Set<String> returnedPlacementIds = new LinkedHashSet<>();
            for (LensDtos.Placement placement : result.safePlacements()) {
                if (placement != null && placement.placementId() != null) {
                    if (result.session().ownedByUser()) allowOwnPlacement(placement);
                    returnedPlacementIds.add(placement.placementId());
                    placements.put(placement.placementId(), placement);
                    if (placement.ownedByDevice()) rememberOwnedPlacement(placement.placementId(), placement.sessionId(), worldKey);
                }
            }
            placements.values().stream()
                .filter(placement -> sessionId.equals(placement.sessionId()) && !returnedPlacementIds.contains(placement.placementId()))
                .map(LensDtos.Placement::placementId).toList().forEach(id -> {
                    placements.remove(id);
                    snapshotGeometry.remove(id);
                    locallyOwnedPlacements.remove(id);
                });
            if (returnedPlacementIds.isEmpty()) {
                LensTextureAtlas unused = atlases.remove(sessionId);
                if (unused != null) unused.close();
            } else if (result.changed()) {
                LensTextureAtlas targetAtlas = atlases.computeIfAbsent(sessionId, LensTextureAtlas::new);
                if (!LensStateLogic.acceptsRevision(targetAtlas.revision(), result.session().revision())) return;
                targetAtlas.request(
                    result.signedPreviewUrl(), result.session().revision(),
                    result.session().previewWidth(), result.session().previewHeight()
                );
            }
            status = CompanionI18n.translate("Ревизия Lens") + " " + result.session().revision();
        });
    }

    private void acceptSession(LensDtos.Session session) {
        if (session == null || session.sessionId() == null) return;
        sessions.compute(session.sessionId(), (id, current) ->
            current == null || session.revision() >= current.revision() ? session : current
        );
        LensDtos.Realtime capability = session.realtime();
        if (capability != null && capability.usable()) {
            realtime.compute(session.sessionId(), (id, current) -> {
                if (current != null && current.matches(capability, realtimeBackendUrl)) return current;
                if (current != null) current.close();
                LensRealtimeWakeup created = new LensRealtimeWakeup(
                    capability, realtimeBackendUrl, () -> {
                        realtimeWakeups.incrementAndGet();
                        realtimeDirtySessions.add(session.sessionId());
                    }
                );
                created.connect();
                return created;
            });
        } else {
            LensRealtimeWakeup current = realtime.remove(session.sessionId());
            if (current != null) current.close();
        }
    }

    private void rebuildRenderSnapshots(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            renderSnapshots = List.of();
            return;
        }
        List<LensRenderSnapshot> next = new ArrayList<>();
        List<LensDtos.Placement> visiblePlacements = placements();
        Set<String> retainedPlacementIds = new LinkedHashSet<>();
        String currentDimension = LensWorldIdentity.dimensionId(client);
        String currentServer = LensWorldIdentity.serverHash(client);
        for (LensDtos.Placement placement : visiblePlacements) {
            if (next.size() >= MAX_RENDER_PLACEMENTS) break;
            LensDtos.Session session = sessions.get(placement.sessionId());
            if (session == null || placement.anchor() == null || placement.grid() == null) continue;
            long cellCount = (long) placement.grid().wide() * placement.grid().tall();
            if (cellCount <= 0 || cellCount > MAX_RENDER_CELLS) continue;
            if (placement.dimensionId() != null && !placement.dimensionId().equals(currentDimension)) continue;
            if (placement.serverHash() != null && !placement.serverHash().isBlank()
                && !placement.serverHash().equals(currentServer)) continue;
            Direction facing = parseFacing(placement.facing());
            if (facing == null) continue;
            BlockPos anchor = new BlockPos(placement.anchor().x(), placement.anchor().y(), placement.anchor().z());
            FrameWallGeometry.Coord origin = FrameWallGeometry.fromBlockPos(anchor, facing);
            BlockPos topRight = FrameWallGeometry.toBlockPos(
                facing, FrameWallGeometry.cell(origin, placement.grid().wide() - 1, placement.grid().tall() - 1)
            );
            Box bounds = bounds(anchor, topRight, 1.25);
            if (LensStateLogic.squaredDistanceToBounds(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ
            ) > RENDER_DISTANCE_SQUARED) continue;
            retainedPlacementIds.add(placement.placementId());

            Set<BlockPos> realFrames = new LinkedHashSet<>();
            for (ItemFrameEntity frame : client.world.getEntitiesByClass(
                ItemFrameEntity.class, bounds, frame -> frame.getHorizontalFacing() == facing
            )) {
                realFrames.add(frame.getAttachedBlockPos());
            }

            LayoutKey layout = new LayoutKey(anchor, facing, placement.grid().wide(), placement.grid().tall());
            SnapshotGeometry geometry = snapshotGeometry.get(placement.placementId());
            if (geometry == null || !geometry.layout().equals(layout) || !geometry.framePositions().equals(realFrames)) {
                geometry = buildGeometry(placement.sessionId(), layout, bounds, Set.copyOf(realFrames));
                snapshotGeometry.put(placement.placementId(), geometry);
            }
            LensTextureAtlas atlas = atlases.get(placement.sessionId());
            next.add(new LensRenderSnapshot(
                placement.placementId(), placement.title(), facing, geometry.bounds(),
                atlas != null && atlas.readyFor(session.revision()) ? atlas.identifier() : null,
                session.revision(), geometry.batches(), geometry.frameCount(), geometry.cellCount()
            ));
        }
        snapshotGeometry.keySet().removeIf(id -> !retainedPlacementIds.contains(id));
        renderSnapshots = List.copyOf(next);
    }

    private static SnapshotGeometry buildGeometry(String sessionId, LayoutKey layout, Box bounds, Set<BlockPos> realFrames) {
        FrameWallGeometry.Coord origin = FrameWallGeometry.fromBlockPos(layout.anchor(), layout.facing());
        List<LensRenderSnapshot.CellBatch> batches = new ArrayList<>();
        int frameCount = 0;
        for (int firstRow = 0; firstRow < layout.tall(); firstRow += RENDER_BATCH_SIZE) {
            int lastRow = Math.min(layout.tall(), firstRow + RENDER_BATCH_SIZE);
            for (int firstColumn = 0; firstColumn < layout.wide(); firstColumn += RENDER_BATCH_SIZE) {
                int lastColumn = Math.min(layout.wide(), firstColumn + RENDER_BATCH_SIZE);
                List<LensRenderSnapshot.Cell> cells = new ArrayList<>((lastRow - firstRow) * (lastColumn - firstColumn));
                for (int row = firstRow; row < lastRow; row++) {
                    for (int column = firstColumn; column < lastColumn; column++) {
                        BlockPos position = FrameWallGeometry.toBlockPos(
                            layout.facing(), FrameWallGeometry.cell(origin, column, row)
                        );
                        boolean hasFrame = realFrames.contains(position);
                        if (hasFrame) frameCount++;
                        float minU = column / (float) layout.wide();
                        float maxU = (column + 1) / (float) layout.wide();
                        int atlasRow = layout.tall() - 1 - row;
                        float minV = atlasRow / (float) layout.tall();
                        float maxV = (atlasRow + 1) / (float) layout.tall();
                        cells.add(new LensRenderSnapshot.Cell(position, hasFrame, minU, minV, maxU, maxV));
                    }
                }
                BlockPos batchBottomLeft = FrameWallGeometry.toBlockPos(
                    layout.facing(), FrameWallGeometry.cell(origin, firstColumn, firstRow)
                );
                BlockPos batchTopRight = FrameWallGeometry.toBlockPos(
                    layout.facing(), FrameWallGeometry.cell(origin, lastColumn - 1, lastRow - 1)
                );
                batches.add(new LensRenderSnapshot.CellBatch(
                    bounds(batchBottomLeft, batchTopRight, 0.75), List.copyOf(cells)
                ));
            }
        }
        return new SnapshotGeometry(
            sessionId, layout, realFrames, bounds, List.copyOf(batches), frameCount, layout.wide() * layout.tall()
        );
    }

    private static Box bounds(BlockPos first, BlockPos second, double expansion) {
        return new Box(
            Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
            Math.max(first.getX(), second.getX()) + 1, Math.max(first.getY(), second.getY()) + 1, Math.max(first.getZ(), second.getZ()) + 1
        ).expand(expansion);
    }

    private LensApiClient api(MinecraftClient client) throws IOException {
        CompanionRuntime runtime = CompanionRuntime.create(client);
        if (!runtime.sessionStore().hasAccessToken()) {
            throw new LensApiException(401, "unauthorized", "Sign in to MapKluss first", null);
        }
        realtimeBackendUrl = runtime.backendUrl();
        return new LensApiClient(runtime.config(), runtime.sessionStore().accessToken());
    }

    private void ensurePreferences(MinecraftClient client) {
        if (preferences != null) return;
        try {
            preferences = LensPreferences.load(client.runDirectory.toPath());
        } catch (IOException e) {
            status = CompanionI18n.translate("Не удалось загрузить настройки Lens");
        }
    }

    private void clearWorldState() {
        recoveryGate.invalidate();
        placements.clear();
        snapshotGeometry.clear();
        renderSnapshots = List.of();
        realtime.values().forEach(LensRealtimeWakeup::close);
        realtime.clear();
        realtimeDirtySessions.clear();
        atlases.values().forEach(LensTextureAtlas::close);
        atlases.clear();
        degradedPollAttempt = 0;
    }

    private void clearAccountState() {
        worldGeneration.incrementAndGet();
        clearWorldState();
        sessions.clear();
        locallyOwnedPlacements.clear();
        capabilitiesKnown = false;
        nextCapabilitiesAt = 0;
        enabled = true;
        status = CompanionI18n.translate("Войдите в MapKluss, чтобы использовать Lens");
    }

    public void clearForLogout() {
        clearAccountState();
    }

    private void removeSessionState(String sessionId) {
        placements.values().removeIf(placement -> sessionId.equals(placement.sessionId()));
        locallyOwnedPlacements.values().removeIf(identity -> sessionId.equals(identity.sessionId()));
        snapshotGeometry.values().removeIf(geometry -> sessionId.equals(geometry.sessionId()));
        LensRealtimeWakeup socket = realtime.remove(sessionId);
        if (socket != null) socket.close();
        realtimeDirtySessions.remove(sessionId);
        LensTextureAtlas atlas = atlases.remove(sessionId);
        if (atlas != null) atlas.close();
    }

    private void clearDisabledState() {
        worldGeneration.incrementAndGet();
        clearWorldState();
        sessions.clear();
        enabled = false;
        status = CompanionI18n.translate("Lens отключён сервером MapKluss");
    }

    private void setError(MinecraftClient client, String prefix, Exception error) {
        setError(client, prefix, error, -1, null);
    }

    private void setError(
        MinecraftClient client,
        String prefix,
        Exception error,
        long expectedGeneration,
        LensRecoveryGate.Token recovery
    ) {
        client.execute(() -> {
            if (expectedGeneration >= 0 && expectedGeneration != worldGeneration.get()) return;
            if (recovery != null && !recoveryGate.isLatest(recovery)) return;
            if (error instanceof LensApiException apiError) {
                if (apiError.statusCode() == 401 || "unauthorized".equals(apiError.errorCode())) {
                    clearAccountState();
                    return;
                }
                if ("lens_disabled".equals(apiError.errorCode())) {
                    clearDisabledState();
                    return;
                }
            }
            status = CompanionI18n.translate(prefix) + ": " + CompanionUiErrors.message("lens", error);
        });
        MapKlussCompanionClient.LOGGER.debug(prefix, error);
    }

    private void rememberOwnedPlacement(String placementId, String sessionId, String placementWorldKey) {
        if (placementId == null || sessionId == null || placementWorldKey == null) return;
        locallyOwnedPlacements.put(placementId, new OwnedPlacementIdentity(sessionId, placementWorldKey));
    }

    private void allowOwnPlacement(LensDtos.Placement placement) {
        LensPreferences prefs = preferences;
        if (prefs == null || placement == null) return;
        try {
            prefs.allowOwnPlacement(placement.placementId(), placement.ownerKey());
        } catch (IOException error) {
            MapKlussCompanionClient.LOGGER.warn(
                "Could not clear local Lens moderation for own placement {}.", placement.placementId(), error
            );
        }
    }

    private Set<String> ownedPlacementIds(String placementWorldKey) {
        if (placementWorldKey == null) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        locallyOwnedPlacements.forEach((id, identity) -> {
            if (placementWorldKey.equals(identity.worldKey())) ids.add(id);
        });
        return Set.copyOf(ids);
    }

    private Set<String> syncSessionIds(boolean realtimeOnly) {
        Set<String> ids = new LinkedHashSet<>();
        if (realtimeOnly) ids.addAll(realtimeDirtySessions);
        if (!realtimeOnly || ids.isEmpty()) {
            ids.addAll(sessions.keySet());
            placements.values().forEach(placement -> ids.add(placement.sessionId()));
            locallyOwnedPlacements.values().stream()
                .filter(identity -> identity.worldKey().equals(worldKey))
                .forEach(identity -> ids.add(identity.sessionId()));
        }
        return ids.stream().filter(id -> id != null && !id.isBlank()).limit(LensSyncPolicy.MAX_SYNC_SESSIONS)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean realtimeHealthy(Set<String> sessionIds) {
        return !sessionIds.isEmpty() && sessionIds.stream().allMatch(id -> {
            LensRealtimeWakeup socket = realtime.get(id);
            return socket != null && socket.isHealthy();
        });
    }

    static String placementId(
        String sessionId,
        String serverHash,
        String dimensionId,
        LensDtos.Anchor anchor,
        String facing
    ) {
        String identity = String.join("|",
            sessionId == null ? "" : sessionId,
            serverHash == null ? "singleplayer" : serverHash,
            dimensionId == null ? "" : dimensionId,
            Integer.toString(anchor.x()), Integer.toString(anchor.y()), Integer.toString(anchor.z()),
            facing == null ? "" : facing
        );
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Direction parseFacing(String value) {
        if (value == null) return null;
        return switch (value) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            default -> null;
        };
    }

    private record OwnedPlacementIdentity(String sessionId, String worldKey) {
    }

    private record LayoutKey(BlockPos anchor, Direction facing, int wide, int tall) {
    }

    private record SnapshotGeometry(
        String sessionId,
        LayoutKey layout,
        Set<BlockPos> framePositions,
        Box bounds,
        List<LensRenderSnapshot.CellBatch> batches,
        int frameCount,
        int cellCount
    ) {
    }
}
