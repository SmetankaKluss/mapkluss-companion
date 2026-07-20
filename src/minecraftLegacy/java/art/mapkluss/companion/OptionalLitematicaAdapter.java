package art.mapkluss.companion;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OptionalLitematicaAdapter {
    private OptionalLitematicaAdapter() { }

    public static Result createPlacement(Path schematicPath, BlockPos origin, String planSha256) {
        return createPlacement(schematicPath, origin, planSha256, "build", PlacementKind.BUILD);
    }

    public static Result createReferencePlacement(Path schematicPath, BlockPos origin, String planSha256, int phaseIndex) {
        return createPlacement(schematicPath, origin, planSha256, String.format("reference-%02d", phaseIndex + 1), PlacementKind.REFERENCE);
    }

    private static Result createPlacement(Path schematicPath, BlockPos origin, String planSha256, String role, PlacementKind kind) {
        if (!FabricLoader.getInstance().isModLoaded("litematica") || !FabricLoader.getInstance().isModLoaded("malilib")) {
            return new Result(false, "Litematica недоступна. Импортируйте схему вручную.");
        }
        String placementName = "MapKluss Two-layer " + shortSha(planSha256) + " " + role + " @ "
            + origin.getX() + "," + origin.getY() + "," + origin.getZ();
        UUID placementId = placementUuid(planSha256, role, origin);
        try {
            Class<?> holderClass = Class.forName("fi.dy.masa.litematica.data.SchematicHolder");
            Object holder = holderClass.getMethod("getInstance").invoke(null);
            Object schematic = holderClass.getMethod("getOrLoad", Path.class).invoke(holder, schematicPath);
            if (schematic == null) return new Result(false, "Не удалось открыть схему в Litematica. Импортируйте её вручную.");

            Class<?> dataManagerClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object manager = dataManagerClass.getMethod("getSchematicPlacementManager").invoke(null);
            Method allMethod = manager.getClass().getMethod("getAllSchematicsPlacements");
            @SuppressWarnings("unchecked")
            List<Object> existing = (List<Object>) allMethod.invoke(manager);
            List<Object> superseded = new ArrayList<>();
            Object matching = null;
            for (Object placement : existing) {
                Object existingOrigin = placement.getClass().getMethod("getOrigin").invoke(placement);
                UUID existingId = (UUID) placement.getClass().getMethod("getHashId").invoke(placement);
                if (placementId.equals(existingId)) {
                    if (!origin.equals(existingOrigin)) {
                        return new Result(false, "Размещение перемещено. Импортируйте схему вручную.");
                    }
                    if (matching == null) matching = placement;
                    else superseded.add(placement);
                    continue;
                }
                if (origin.equals(existingOrigin) && ownsPlacementUuid(existingId, planSha256, origin)) superseded.add(placement);
            }

            if (matching != null) {
                if (!removePlacements(manager, superseded)
                    || !hasExactlyOnePlanPlacement(manager, origin, planSha256, placementId)) {
                    return unsafeReplacement();
                }
                manager.getClass().getMethod("setSelectedSchematicPlacement", matching.getClass()).invoke(manager, matching);
                return new Result(true, selectedMessage(kind, true));
            }

            // Fail closed: never add an air-reference beside the old full
            // build placement. Remove and verify the superseded placement
            // first; on failure the manager falls back to its live overlay.
            if (!removePlacements(manager, superseded)
                || !hasNoPlanPlacement(manager, origin, planSha256)) {
                return unsafeReplacement();
            }

            Class<?> placementClass = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
            Method create = java.util.Arrays.stream(placementClass.getMethods())
                .filter(method -> method.getName().equals("createFor") && method.getParameterCount() == 6
                    && method.getParameterTypes()[5] == UUID.class)
                .findFirst().orElseThrow();
            Object placement = create.invoke(null, schematic, origin, placementName, true, true, placementId);
            Method add = java.util.Arrays.stream(manager.getClass().getMethods())
                .filter(method -> method.getName().equals("addSchematicPlacement") && method.getParameterCount() == 2)
                .findFirst().orElseThrow();
            add.invoke(manager, placement, true);
            if (!hasExactlyOnePlanPlacement(manager, origin, planSha256, placementId)) {
                removePlacements(manager, planPlacementsAtOrigin(manager, origin, planSha256));
                return unsafeReplacement();
            }
            Method select = java.util.Arrays.stream(manager.getClass().getMethods())
                .filter(method -> method.getName().equals("setSelectedSchematicPlacement") && method.getParameterCount() == 1)
                .findFirst().orElseThrow();
            select.invoke(manager, placement);
            return new Result(true, selectedMessage(kind, false));
        } catch (Throwable error) {
            MapKlussCompanionClient.LOGGER.warn("Optional Litematica placement failed.", error);
            return new Result(false, "Не удалось разместить схему. Импортируйте её вручную.");
        }
    }

    public static boolean removePlanPlacements(BlockPos origin, String planSha256) {
        if (!FabricLoader.getInstance().isModLoaded("litematica") || !FabricLoader.getInstance().isModLoaded("malilib")) return false;
        try {
            Class<?> dataManagerClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object manager = dataManagerClass.getMethod("getSchematicPlacementManager").invoke(null);
            List<Object> owned = planPlacementsAtOrigin(manager, origin, planSha256);
            return owned.isEmpty() || (removePlacements(manager, owned) && planPlacementsAtOrigin(manager, origin, planSha256).isEmpty());
        } catch (Throwable error) {
            MapKlussCompanionClient.LOGGER.warn("Could not remove the exact MapKluss Litematica placement.", error);
            return false;
        }
    }

    private static boolean removePlacements(Object manager, List<Object> placements) {
        boolean removed = true;
        for (Object previous : placements) {
            try {
                manager.getClass().getMethod("removeSchematicPlacement", previous.getClass()).invoke(manager, previous);
            } catch (ReflectiveOperationException error) {
                MapKlussCompanionClient.LOGGER.warn("Could not remove a superseded MapKluss Litematica placement.", error);
                removed = false;
            }
        }
        return removed;
    }

    private static List<Object> planPlacementsAtOrigin(Object manager, BlockPos origin, String planSha256)
        throws ReflectiveOperationException {
        List<Object> result = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<Object> existing = (List<Object>) manager.getClass().getMethod("getAllSchematicsPlacements").invoke(manager);
        for (Object placement : existing) {
            Object existingOrigin = placement.getClass().getMethod("getOrigin").invoke(placement);
            UUID existingId = (UUID) placement.getClass().getMethod("getHashId").invoke(placement);
            if (origin.equals(existingOrigin) && ownsPlacementUuid(existingId, planSha256, origin)) result.add(placement);
        }
        return result;
    }

    private static boolean hasNoPlanPlacement(Object manager, BlockPos origin, String planSha256)
        throws ReflectiveOperationException {
        return planPlacementsAtOrigin(manager, origin, planSha256).isEmpty();
    }

    private static boolean hasExactlyOnePlanPlacement(
        Object manager,
        BlockPos origin,
        String planSha256,
        UUID expectedId
    ) throws ReflectiveOperationException {
        List<Object> placements = planPlacementsAtOrigin(manager, origin, planSha256);
        if (placements.size() != 1) return false;
        return expectedId.equals(placements.getFirst().getClass().getMethod("getHashId").invoke(placements.getFirst()));
    }

    private static Result unsafeReplacement() {
        return new Result(false, "Не удалось обновить схему этапа. Используйте подсветку MapKluss.");
    }

    private static String shortSha(String sha) {
        return sha == null || sha.length() < 12 ? "bundle" : sha.substring(0, 12);
    }

    private static String selectedMessage(PlacementKind kind, boolean existing) {
        return switch (kind) {
            case BUILD -> existing
                ? "Размещение Litematica выбрано."
                : "Схема загружена в Litematica.";
            case REFERENCE -> existing
                ? "Схема этапа выбрана в Litematica."
                : "Схема этапа обновлена.";
        };
    }

    static UUID placementUuid(String planSha256, String role, BlockPos origin) {
        String identity = "mapkluss:two-layer:" + planSha256 + ':' + role + ':'
            + origin.getX() + ':' + origin.getY() + ':' + origin.getZ();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    static boolean ownsPlacementUuid(UUID id, String planSha256, BlockPos origin) {
        if (id == null || planSha256 == null || origin == null) return false;
        if (id.equals(placementUuid(planSha256, "build", origin))) return true;
        for (int phase = 1; phase <= SuppressionPlanParser.PHASES; phase++) {
            if (id.equals(placementUuid(planSha256, String.format("remove-%02d", phase), origin))
                || id.equals(placementUuid(planSha256, String.format("reference-%02d", phase), origin))) return true;
        }
        return false;
    }

    public record Result(boolean placed, String message) { }

    private enum PlacementKind { BUILD, REFERENCE }
}
