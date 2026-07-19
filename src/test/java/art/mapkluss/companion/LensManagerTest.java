package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class LensManagerTest {
    @Test
    void placementIdentityIsStableForRetriesButChangesWithAnchor() {
        LensDtos.Anchor anchor = new LensDtos.Anchor(10, 64, -5);
        String first = LensManager.placementId("session", "server", "minecraft:overworld", anchor, "north");
        String retry = LensManager.placementId("session", "server", "minecraft:overworld", anchor, "north");
        String moved = LensManager.placementId(
            "session", "server", "minecraft:overworld", new LensDtos.Anchor(11, 64, -5), "north"
        );
        assertEquals(first, retry);
        assertNotEquals(first, moved);
    }
}
