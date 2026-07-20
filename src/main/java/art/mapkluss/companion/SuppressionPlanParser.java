package art.mapkluss.companion;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SuppressionPlanParser {
    public static final int MAX_PLAN_BYTES = 4 * 1024 * 1024;
    public static final int MAX_LITEMATIC_BYTES = 16 * 1024 * 1024;
    public static final int MAX_BUNDLE_BYTES = 128 * 1024 * 1024;
    public static final int MAX_BUNDLE_EXPANDED_BYTES = 192 * 1024 * 1024;
    public static final int MAP_BYTES = 128 * 128;
    public static final int PHASES = 64;
    public static final Set<String> SUPPORTED_MINECRAFT_VERSIONS = Set.of("1.21.4", "1.21.8", "1.21.11", "26.2");
    private static final Gson GSON = new Gson();
    private static final Set<String> UNSTABLE_TARGET_STATES = Set.of(
        "minecraft:dirt", "minecraft:grass_block", "minecraft:mycelium", "minecraft:dirt_path", "minecraft:farmland",
        "minecraft:crimson_nylium", "minecraft:warped_nylium",
        "minecraft:copper_block", "minecraft:cut_copper", "minecraft:exposed_copper",
        "minecraft:exposed_cut_copper", "minecraft:weathered_copper", "minecraft:weathered_cut_copper"
    );

    private SuppressionPlanParser() { }

    public static Parsed parse(byte[] jsonBytes) throws IOException {
        if (jsonBytes == null || jsonBytes.length == 0 || jsonBytes.length > MAX_PLAN_BYTES) {
            throw new IOException("Two-layer plan size is outside the safe limit");
        }
        final SuppressionPlan plan;
        try {
            plan = GSON.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), SuppressionPlan.class);
        } catch (RuntimeException error) {
            throw new IOException("Two-layer plan is not valid JSON", error);
        }
        validate(plan);
        return new Parsed(plan, decodeMapBytes(plan.initialMapBytesB64(), "initial"), decodeMapBytes(plan.targetMapBytesB64(), "target"));
    }

    private static void validate(SuppressionPlan plan) throws IOException {
        if (plan == null || !"mapkluss.suppression-plan".equals(plan.schema())
            || (plan.version() != 1 && plan.version() != 2 && plan.version() != 3)) {
            throw new IOException("Unsupported Two-layer plan version");
        }
        if (!"two_layer".equals(plan.method()) || !"west_to_east".equals(plan.direction())) {
            throw new IOException("Unsupported Two-layer method or direction");
        }
        if (plan.target() == null || plan.target().scale() != 0 || plan.target().width() != 128 || plan.target().height() != 128
            || !"minecraft:overworld".equals(plan.target().dimension())
            || !SUPPORTED_MINECRAFT_VERSIONS.contains(plan.target().minecraftVersion())) {
            throw new IOException("Unsupported Two-layer target");
        }
        if (plan.axes() == null || !"northwest_baseline".equals(plan.axes().anchor())
            || !"+x".equals(plan.axes().east()) || !"+z".equals(plan.axes().south()) || !"+y".equals(plan.axes().up())) {
            throw new IOException("Unsupported Two-layer coordinate system");
        }
        validateBounds(plan);
        if (plan.palette().isEmpty() || plan.palette().size() > 4096) throw new IOException("Two-layer palette is invalid");
        if (!"minecraft:air".equals(plan.palette().getFirst().state())
            || !plan.palette().getFirst().properties().isEmpty()) {
            throw new IOException("Two-layer palette index zero must be air");
        }
        Set<String> allowedRoles = plan.version() == 1
            ? Set.of("dominant_target", "recessive_target", "shade_filler", "marker")
            : Set.of("dominant_target", "recessive_target", "shade_filler");
        for (SuppressionPlan.PaletteEntry entry : plan.palette()) {
            if (entry == null || entry.state() == null || entry.state().length() > 256 || !entry.state().matches("minecraft:[a-z0-9_./-]+")) {
                throw new IOException("Two-layer palette contains an invalid block state");
            }
            if (entry.roles().size() > allowedRoles.size()
                || entry.roles().stream().anyMatch(role -> role == null || !allowedRoles.contains(role))) {
                throw new IOException("Two-layer palette contains an invalid block role");
            }
            if (entry.properties().size() > 1
                || entry.properties().entrySet().stream().anyMatch(property ->
                    !"axis".equals(property.getKey())
                        || !("x".equals(property.getValue()) || "y".equals(property.getValue()) || "z".equals(property.getValue())))) {
                throw new IOException("Two-layer palette contains invalid block properties");
            }
            if (UNSTABLE_TARGET_STATES.contains(entry.state())
                && (entry.roles().contains("dominant_target") || entry.roles().contains("recessive_target"))) {
                throw new IOException("Two-layer palette contains a block that can change while covered");
            }
        }
        if (plan.verification() == null || !"recessive_even".equals(plan.verification().initialParity())
            || !"dominant_odd".equals(plan.verification().phaseParity())
            || plan.verification().selectiveRing() == null
            || plan.verification().selectiveRing().innerExclusive() != 126
            || plan.verification().selectiveRing().outerExclusive() != 128
            || plan.verification().minDwellTicks() < 32 || plan.verification().minDwellTicks() > 12000) {
            throw new IOException("Two-layer verification policy is invalid");
        }
        if (plan.initialCapture() == null || plan.initialCapture().standPoints().size() != 1) {
            throw new IOException("Two-layer initial capture is invalid");
        }
        validateStandPoints(plan.initialCapture().standPoints());
        if (plan.phases().size() != PHASES) throw new IOException("Two-layer plan must contain exactly 64 phases");

        Set<String> phaseIds = new HashSet<>();
        long removedBlocks = 0;
        long updatedPixels = 0;
        for (int expectedIndex = 0; expectedIndex < plan.phases().size(); expectedIndex++) {
            SuppressionPlan.Phase phase = plan.phases().get(expectedIndex);
            if (phase == null || phase.index() != expectedIndex || phase.id() == null || phase.id().length() > 80 || !phaseIds.add(phase.id())) {
                throw new IOException("Two-layer phases are not ordered and unique");
            }
            if (phase.columns().size() != 2 || phase.columns().get(0) == null || phase.columns().get(1) == null
                || phase.columns().get(0) != expectedIndex * 2 || phase.columns().get(1) != expectedIndex * 2 + 1) {
                throw new IOException("Two-layer phase columns are invalid");
            }
            if (phase.labelRu() == null || phase.labelEn() == null || phase.labelRu().length() > 160 || phase.labelEn().length() > 160
                || phase.removeRuns().size() > 1024 || phase.updatePixelRuns().size() != 128
                || (phase.standPoints().size() != 4 && phase.standPoints().size() != 5)
                || phase.verifiedTargetPixels() != 8192 + (expectedIndex + 1) * 128) {
                throw new IOException("Two-layer phase exceeds safety limits");
            }
            for (SuppressionPlan.RemovalRun run : phase.removeRuns()) {
                if (run == null || run.length() < 1 || run.length() > 254 || run.paletteIndex() <= 0 || run.paletteIndex() >= plan.palette().size()) {
                    throw new IOException("Two-layer removal run is invalid");
                }
                validateLocalRun(plan, run.xStart(), run.y(), run.z(), run.length());
                int phaseX0 = expectedIndex * 2;
                int phaseX1 = phaseX0 + 1;
                if (run.xStart() < phaseX0 || run.xStart() + run.length() - 1 > phaseX1) {
                    throw new IOException("Two-layer removal run leaves its phase columns");
                }
                removedBlocks += run.length();
            }
            Set<Integer> phasePixels = new HashSet<>();
            for (SuppressionPlan.PixelRun run : phase.updatePixelRuns()) {
                if (run == null || run.xStart() < 0 || run.z() < 0 || run.z() >= 128 || run.length() < 1 || run.xStart() + run.length() > 128) {
                    throw new IOException("Two-layer pixel run is invalid");
                }
                for (int dx = 0; dx < run.length(); dx++) {
                    int x = run.xStart() + dx;
                    if ((x != expectedIndex * 2 && x != expectedIndex * 2 + 1) || ((x + run.z()) & 1) != 1
                        || !phasePixels.add(run.z() * 128 + x)) {
                        throw new IOException("Two-layer pixel run does not match dominant phase parity");
                    }
                    updatedPixels++;
                }
            }
            validateStandPoints(phase.standPoints());
            validateSelectiveCoverage(phase, expectedIndex);
        }
        if (removedBlocks != 16_384 || updatedPixels != MAP_BYTES / 2) {
            throw new IOException("Two-layer plan workload is incomplete");
        }
        if (plan.materials() == null || plan.materials().totalBlocks() < 1 || plan.materials().totalBlocks() > 80_000
            || plan.materials().recoverableBlocks() < 0 || plan.materials().recoverableBlocks() > plan.materials().totalBlocks()) {
            throw new IOException("Two-layer material totals are invalid");
        }
        if (plan.materials().initial().size() > 4096 || plan.materials().recoverable().size() > 4096) {
            throw new IOException("Two-layer material list is invalid");
        }
        validateMaterialCounts(plan.materials().initial(), plan.palette().size());
        validateMaterialCounts(plan.materials().recoverable(), plan.palette().size());
        if (plan.litematic() == null || !safeFilename(plan.litematic().filename(), ".litematic")
            || plan.litematic().sha256() == null || !plan.litematic().sha256().matches("[a-f0-9]{64}")) {
            throw new IOException("Two-layer Litematic metadata is invalid");
        }
    }

    private static void validateSelectiveCoverage(SuppressionPlan.Phase phase, int phaseIndex) throws IOException {
        int x0 = phaseIndex * 2;
        int x1 = x0 + 1;
        for (int z = 0; z < 128; z++) {
            int dominant = ((x0 + z) & 1) == 1 ? x0 : x1;
            int recessive = dominant == x0 ? x1 : x0;
            boolean covered = false;
            for (SuppressionPlan.StandPoint point : phase.standPoints()) {
                int playerX = point.standOn().x();
                int playerZ = point.standOn().z();
                if (SuppressionStateLogic.mapCellUpdatesAt(playerX, playerZ, dominant, z)) covered = true;
                if (SuppressionStateLogic.mapCellUpdatesAt(playerX, playerZ, recessive, z)) {
                    throw new IOException("Two-layer stand points update a recessive phase pixel");
                }
                if (phaseIndex > 0 && SuppressionStateLogic.mapCellUpdatesAt(playerX, playerZ, x0 - 1, z)) {
                    throw new IOException("Two-layer stand points reach an already completed column");
                }
            }
            if (!covered) throw new IOException("Two-layer stand points do not cover every dominant phase pixel");
        }
    }

    private static void validateStandPoints(java.util.List<SuppressionPlan.StandPoint> points) throws IOException {
        for (SuppressionPlan.StandPoint point : points) {
            if (point == null || point.standOn() == null || point.minTicks() < 32 || point.minTicks() > 12000) {
                throw new IOException("Two-layer stand point is invalid");
            }
            validatePos(point.standOn());
        }
    }

    private static void validateMaterialCounts(List<SuppressionPlan.MaterialCount> counts, int paletteSize) throws IOException {
        Set<Integer> seen = new HashSet<>();
        for (SuppressionPlan.MaterialCount count : counts) {
            if (count == null || count.paletteIndex() <= 0 || count.paletteIndex() >= paletteSize
                || count.count() < 1 || count.count() > 80_000 || !seen.add(count.paletteIndex())) {
                throw new IOException("Two-layer material count is invalid");
            }
        }
    }

    private static void validateLocalRun(SuppressionPlan plan, int x, int y, int z, int length) throws IOException {
        SuppressionPlan.LocalPos min = plan.effectiveStructureBounds().min();
        SuppressionPlan.LocalPos max = plan.effectiveStructureBounds().max();
        if (x < min.x() || x + length - 1 > max.x() || y < min.y() || y > max.y() || z < min.z() || z > max.z()) {
            throw new IOException("Two-layer removal run leaves declared bounds");
        }
    }

    private static void validateBounds(SuppressionPlan plan) throws IOException {
        SuppressionPlan.Bounds structure = plan.effectiveStructureBounds();
        SuppressionPlan.Bounds workflow = plan.effectiveWorkflowBounds();
        if (structure == null || structure.min() == null || structure.max() == null
            || workflow == null || workflow.min() == null || workflow.max() == null) {
            throw new IOException("Two-layer bounds are missing");
        }
        validatePos(structure.min());
        validatePos(structure.max());
        validatePos(workflow.min());
        validatePos(workflow.max());
        if (!samePos(structure.min(), 0, -1, -1)
            || !samePos(structure.max(), plan.version() == 1 ? 253 : 127, 3, 127)
            || !samePos(workflow.min(), 0, -1, -1)
            || !samePos(workflow.max(), 253, 3, 127)) {
            throw new IOException("Unsupported Two-layer bounds");
        }
        if (plan.version() == 1) return;
        if (plan.version() == 3) {
            if (plan.canvas() != null) throw new IOException("Two-layer v3 must not contain a generated canvas");
            return;
        }
        SuppressionPlan.Canvas canvas = plan.canvas();
        if (canvas == null || !"mapkluss.two-layer-canvas".equals(canvas.schema()) || canvas.version() != 1
            || !"minecraft:cobblestone".equals(canvas.material()) || canvas.standSurfaceY() != 4
            || canvas.blockCount() < 16_384 || canvas.blockCount() > 20_000
            || canvas.bounds() == null || canvas.bounds().min() == null || canvas.bounds().max() == null
            || !samePos(canvas.bounds().min(), 0, 0, 0) || !samePos(canvas.bounds().max(), 253, 4, 127)) {
            throw new IOException("Unsupported Two-layer reusable canvas");
        }
    }

    private static void validatePos(SuppressionPlan.LocalPos pos) throws IOException {
        if (Math.abs(pos.x()) > 4096 || Math.abs(pos.y()) > 4096 || Math.abs(pos.z()) > 4096) {
            throw new IOException("Two-layer coordinate exceeds safety limits");
        }
    }

    private static boolean samePos(SuppressionPlan.LocalPos pos, int x, int y, int z) {
        return pos.x() == x && pos.y() == y && pos.z() == z;
    }

    static boolean safeFilename(String filename, String suffix) {
        return filename != null && filename.length() <= 180 && filename.equals(java.nio.file.Path.of(filename).getFileName().toString())
            && filename.toLowerCase(java.util.Locale.ROOT).endsWith(suffix);
    }

    private static byte[] decodeMapBytes(String value, String label) throws IOException {
        try {
            byte[] decoded = Base64.getDecoder().decode(value == null ? "" : value);
            if (decoded.length != MAP_BYTES) throw new IOException("Two-layer " + label + " map has the wrong size");
            return decoded;
        } catch (IllegalArgumentException error) {
            throw new IOException("Two-layer " + label + " map is not valid base64", error);
        }
    }

    public record Parsed(SuppressionPlan plan, byte[] initialMapBytes, byte[] targetMapBytes) {
        public Parsed {
            initialMapBytes = initialMapBytes.clone();
            targetMapBytes = targetMapBytes.clone();
        }

        @Override public byte[] initialMapBytes() { return initialMapBytes.clone(); }
        @Override public byte[] targetMapBytes() { return targetMapBytes.clone(); }
    }
}
