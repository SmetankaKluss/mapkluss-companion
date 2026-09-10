package art.mapkluss.companion;

/** Captured under the Two-layer manager lock; contains no mutable world access. */
public record LiveBuildPhaseSession(SuppressionBundle bundle,int phase,String worldKey,String dimension,
                                    LiveBuildProgress.Position origin) {
    public static int targetPhase(SuppressionStage stage,int phase,int count) {
        if(stage==null||phase<0||phase>=count)return -2;
        return switch(stage){
            case BUILDING,INITIAL_MOVE,INITIAL_EQUIP,INITIAL_DWELL,INITIAL_STOW,INITIAL_VERIFY -> -1;
            case REMOVE,MOVE,EQUIP,DWELL,STOW,VERIFY,READY_NEXT -> phase;
            case COMPLETE -> count-1;
            default -> -2;
        };
    }
    public LiveBuildSchematic prepare() throws java.io.IOException {
        return LiveBuildPhaseTarget.read(bundle.planBytes(),bundle.litematicBytes(),phase);
    }
}
