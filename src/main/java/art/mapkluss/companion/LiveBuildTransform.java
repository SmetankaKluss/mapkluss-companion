package art.mapkluss.companion;

/** Mirror local X first, then rotate clockwise about the selected map's origin. */
public record LiveBuildTransform(int quarterTurns, boolean mirrorX) {
    public static final LiveBuildTransform NONE = new LiveBuildTransform(0, false);

    public LiveBuildTransform {
        if (quarterTurns < 0 || quarterTurns > 3) throw new IllegalArgumentException("Invalid rotation");
    }

    public LiveBuildProgress.Position apply(LiveBuildProgress.Position position) {
        int x = mirrorX ? Math.negateExact(position.x()) : position.x();
        int z = position.z();
        return switch (quarterTurns) {
            case 0 -> new LiveBuildProgress.Position(x, position.y(), z);
            case 1 -> new LiveBuildProgress.Position(Math.negateExact(z), position.y(), x);
            case 2 -> new LiveBuildProgress.Position(Math.negateExact(x), position.y(), Math.negateExact(z));
            case 3 -> new LiveBuildProgress.Position(z, position.y(), Math.negateExact(x));
            default -> throw new AssertionError();
        };
    }
}
