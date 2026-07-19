package art.mapkluss.companion;

public record AutoFrameInventoryPlan(
    int sourceInventoryIndex,
    int sourceScreenSlot,
    int targetHotbarIndex,
    int originalSelectedHotbarIndex,
    boolean swapRequired,
    boolean selectionChangeRequired
) {
    public static AutoFrameInventoryPlan forInventoryIndex(int inventoryIndex, int selectedHotbarIndex) {
        if (inventoryIndex < 0 || inventoryIndex > 35) {
            throw new IllegalArgumentException("AutoFrame only supports the player main inventory");
        }
        if (selectedHotbarIndex < 0 || selectedHotbarIndex > 8) {
            throw new IllegalArgumentException("Selected hotbar slot is invalid");
        }
        if (inventoryIndex <= 8) {
            return new AutoFrameInventoryPlan(
                inventoryIndex,
                inventoryIndex + 36,
                inventoryIndex,
                selectedHotbarIndex,
                false,
                inventoryIndex != selectedHotbarIndex
            );
        }
        return new AutoFrameInventoryPlan(
            inventoryIndex,
            inventoryIndex,
            selectedHotbarIndex,
            selectedHotbarIndex,
            true,
            false
        );
    }
}
