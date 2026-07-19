package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class SuppressionStateLogic {
    private SuppressionStateLogic() { }

    public static byte[] expectedMapBytes(SuppressionPlanParser.Parsed parsed, int completedPhases) {
        return expectedMapBytes(parsed, parsed.initialMapBytes(), completedPhases);
    }

    public static byte[] expectedMapBytes(SuppressionPlanParser.Parsed parsed, byte[] capturedInitialMap, int completedPhases) {
        if (capturedInitialMap == null || capturedInitialMap.length != 128 * 128) {
            throw new IllegalArgumentException("Captured initial map must contain exactly 16384 bytes");
        }
        byte[] result = capturedInitialMap.clone();
        byte[] target = parsed.targetMapBytes();
        int phaseLimit = Math.max(0, Math.min(completedPhases, parsed.plan().phases().size()));
        for (int phaseIndex = 0; phaseIndex < phaseLimit; phaseIndex++) {
            for (SuppressionPlan.PixelRun run : parsed.plan().phases().get(phaseIndex).updatePixelRuns()) {
                for (int dx = 0; dx < run.length(); dx++) {
                    int index = run.z() * 128 + run.xStart() + dx;
                    result[index] = target[index];
                }
            }
        }
        return result;
    }

    public static boolean recessivePixelsMatchTarget(byte[] actual, byte[] target) {
        return compareRecessivePixels(actual, target).total() == 0;
    }

    public static MapMismatch compareRecessivePixels(byte[] actual, byte[] target) {
        if (actual == null || target == null || actual.length != 128 * 128 || target.length != 128 * 128) {
            return new MapMismatch(128 * 128 / 2, 128 * 128 / 2, 0);
        }
        int total = 0;
        int baseColor = 0;
        int shade = 0;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                if (((x + z) & 1) != 0) continue;
                int index = z * 128 + x;
                int actualByte = Byte.toUnsignedInt(actual[index]);
                int targetByte = Byte.toUnsignedInt(target[index]);
                if (actualByte == targetByte) continue;
                total++;
                if (actualByte / 4 == targetByte / 4) shade++;
                else baseColor++;
            }
        }
        return new MapMismatch(total, baseColor, shade);
    }

    public static boolean verifiedPixelsMatchTarget(byte[] actual, byte[] target, int completedPhases) {
        return compareVerifiedPixels(actual, target, completedPhases).total() == 0;
    }

    public static VerificationMismatch compareVerifiedPixels(byte[] actual, byte[] target, int completedPhases) {
        int completed = Math.max(0, Math.min(64, completedPhases));
        if (actual == null || target == null || actual.length != 128 * 128 || target.length != 128 * 128) {
            return new VerificationMismatch(128 * 128, 128 * 128 / 2, completed * 128, 128 * 128, 0);
        }
        int total = 0;
        int recessive = 0;
        int completedDominant = 0;
        int baseColor = 0;
        int shade = 0;
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                boolean isRecessive = ((x + z) & 1) == 0;
                boolean isCompletedDominant = !isRecessive && x / 2 < completed;
                if (!isRecessive && !isCompletedDominant) continue;
                int index = z * 128 + x;
                int actualByte = Byte.toUnsignedInt(actual[index]);
                int targetByte = Byte.toUnsignedInt(target[index]);
                if (actualByte == targetByte) continue;
                total++;
                if (isRecessive) recessive++;
                else completedDominant++;
                if (actualByte / 4 == targetByte / 4) shade++;
                else baseColor++;
            }
        }
        return new VerificationMismatch(total, recessive, completedDominant, baseColor, shade);
    }

    public static boolean mapMatches(byte[] actual, byte[] expected) {
        return actual != null && expected != null && actual.length == 128 * 128 && Arrays.equals(actual, expected);
    }

    public static MapMismatch compareMaps(byte[] actual, byte[] expected) {
        if (actual == null || expected == null || actual.length != 128 * 128 || expected.length != 128 * 128) {
            return new MapMismatch(128 * 128, 128 * 128, 0);
        }
        int total = 0;
        int baseColor = 0;
        int shade = 0;
        for (int index = 0; index < actual.length; index++) {
            int actualByte = Byte.toUnsignedInt(actual[index]);
            int expectedByte = Byte.toUnsignedInt(expected[index]);
            if (actualByte == expectedByte) continue;
            total++;
            if (actualByte / 4 == expectedByte / 4) shade++;
            else baseColor++;
        }
        return new MapMismatch(total, baseColor, shade);
    }

    public static int nextDwellTick(int dwellTicks, int minTicks) {
        return Math.min(Math.max(1, minTicks), Math.max(0, dwellTicks) + 1);
    }

    public static boolean dwellComplete(int dwellTicks, int minTicks, boolean mapUpdateObserved, boolean expectedMapAlreadyMatches) {
        return dwellTicks >= minTicks && (mapUpdateObserved || expectedMapAlreadyMatches);
    }

    public static boolean acceptsCaptureUpdate(SuppressionStage stage) {
        return stage == SuppressionStage.INITIAL_DWELL || stage == SuppressionStage.DWELL;
    }

    public static String captureDataLabel(boolean mapUpdateObserved) {
        return mapUpdateObserved ? "данные карты ✓" : "данные карты …";
    }

    public static boolean pointPixelsMatchTarget(
        byte[] actual,
        byte[] target,
        SuppressionPlan.Phase phase,
        int standPointIndex
    ) {
        if (actual == null || target == null || actual.length != 128 * 128 || target.length != 128 * 128
            || phase == null || standPointIndex < 0 || standPointIndex >= phase.standPoints().size()) return false;
        boolean assignedAny = false;
        for (SuppressionPlan.PixelRun run : phase.updatePixelRuns()) {
            for (int dx = 0; dx < run.length(); dx++) {
                int x = run.xStart() + dx;
                int assigned = assignedStandPoint(phase.standPoints(), x, run.z());
                if (assigned != standPointIndex) continue;
                assignedAny = true;
                int index = run.z() * 128 + x;
                if (actual[index] != target[index]) return false;
            }
        }
        return assignedAny;
    }

    public static int assignedStandPoint(List<SuppressionPlan.StandPoint> points, int mapX, int mapZ) {
        if (points == null) return -1;
        for (int index = 0; index < points.size(); index++) {
            SuppressionPlan.LocalPos stand = points.get(index).standOn();
            if (mapCellUpdatesAt(stand.x(), stand.z(), mapX, mapZ)) return index;
        }
        return -1;
    }

    public static SuppressionStage restoredStage(int formatVersion, SuppressionStage saved) {
        if (formatVersion == 1) {
            return switch (saved) {
                case INITIAL_MOVE, INITIAL_DWELL, INITIAL_VERIFY -> SuppressionStage.INITIAL_MOVE;
                case REMOVE, MOVE, DWELL, VERIFY, READY_NEXT, COMPLETE -> SuppressionStage.PAUSED;
                default -> saved;
            };
        }
        return switch (saved) {
            case INITIAL_EQUIP, INITIAL_DWELL, INITIAL_STOW -> SuppressionStage.INITIAL_MOVE;
            case EQUIP, DWELL, STOW, PAUSED -> SuppressionStage.MOVE;
            default -> saved;
        };
    }

    public static boolean isScaleZeroNorthWest(int blockX, int blockZ) {
        return Math.floorMod(blockX, 128) == 64 && Math.floorMod(blockZ, 128) == 64;
    }

    public static boolean mapCellUpdatesAt(int playerX, int playerZ, int mapX, int mapZ) {
        int dx = mapX - playerX;
        int dz = mapZ - playerZ;
        int distanceSquared = dx * dx + dz * dz;
        if (distanceSquared >= 128 * 128) return false;
        return distanceSquared <= 126 * 126 || ((mapX + mapZ) & 1) == 1;
    }

    public static List<SuppressionPlan.LocalPos> removalBlocks(SuppressionPlan.Phase phase) {
        List<SuppressionPlan.LocalPos> result = new ArrayList<>();
        for (SuppressionPlan.RemovalRun run : phase.removeRuns()) {
            for (int dx = 0; dx < run.length(); dx++) {
                result.add(new SuppressionPlan.LocalPos(run.xStart() + dx, run.y(), run.z()));
            }
        }
        return List.copyOf(result);
    }

    public static SuppressionStage actionTransition(SuppressionStage stage, boolean verificationMatches, boolean overrideConfirmed, boolean lastPhase) {
        return switch (stage) {
            case BUILDING -> SuppressionStage.INITIAL_MOVE;
            case INITIAL_VERIFY -> verificationMatches ? SuppressionStage.REMOVE : SuppressionStage.INITIAL_VERIFY;
            case READY_NEXT -> lastPhase ? SuppressionStage.COMPLETE : SuppressionStage.REMOVE;
            case VERIFY -> verificationMatches || overrideConfirmed ? SuppressionStage.READY_NEXT : SuppressionStage.VERIFY;
            default -> stage;
        };
    }

    public record MapMismatch(int total, int baseColor, int shade) { }

    public record VerificationMismatch(
        int total,
        int recessive,
        int completedDominant,
        int baseColor,
        int shade
    ) { }
}
