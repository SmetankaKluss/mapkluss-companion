package art.mapkluss.companion;

final class GroupPlacementVersion {
    static int current() { return net.minecraft.SharedConstants.getGameVersion().getSaveVersion().getId(); }
}
