package art.mapkluss.companion;

public final class SuppressionCapturePolicy {
    public static final int REQUIRED_STABLE_TICKS = 2;
    public static final double MAX_HORIZONTAL_SPEED_SQUARED = 0.0004D;
    public static final double CHECKPOINT_CENTER_TOLERANCE = 0.24D;

    private SuppressionCapturePolicy() { }

    public static boolean stableCapturePosture(
        boolean onPoint,
        boolean onGround,
        boolean hasVehicle,
        boolean swimming,
        boolean flying,
        boolean movementRequested,
        boolean screenOpen,
        double horizontalSpeedSquared
    ) {
        return onPoint && onGround && !hasVehicle && !swimming && !flying
            && !movementRequested && !screenOpen
            && horizontalSpeedSquared <= MAX_HORIZONTAL_SPEED_SQUARED;
    }

    public static int nextStableTick(int current, boolean stable) {
        if (!stable) return 0;
        return Math.min(REQUIRED_STABLE_TICKS, Math.max(0, current) + 1);
    }

    public static boolean readyToCapture(int stableTicks) {
        return stableTicks >= REQUIRED_STABLE_TICKS;
    }

    public static boolean centeredOnCheckpoint(
        double playerX,
        double playerY,
        double playerZ,
        int blockX,
        int blockY,
        int blockZ
    ) {
        return (int) Math.floor(playerY) - 1 == blockY
            && Math.abs(playerX - (blockX + 0.5D)) <= CHECKPOINT_CENTER_TOLERANCE
            && Math.abs(playerZ - (blockZ + 0.5D)) <= CHECKPOINT_CENTER_TOLERANCE;
    }
}
