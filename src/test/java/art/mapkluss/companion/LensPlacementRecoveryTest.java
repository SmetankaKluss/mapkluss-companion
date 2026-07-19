package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LensPlacementRecoveryTest {
    @Test
    void keepsOwnedPlacementIdentityAcrossTemporaryWorldDisconnect() throws Exception {
        Constructor<LensManager> constructor = LensManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        LensManager manager = constructor.newInstance();

        Method remember = LensManager.class.getDeclaredMethod(
            "rememberOwnedPlacement", String.class, String.class, String.class
        );
        remember.setAccessible(true);
        remember.invoke(manager, "placement-a", "session-a", "server|dimension");

        Method clearWorld = LensManager.class.getDeclaredMethod("clearWorldState");
        clearWorld.setAccessible(true);
        clearWorld.invoke(manager);

        Method ownedIds = LensManager.class.getDeclaredMethod("ownedPlacementIds", String.class);
        ownedIds.setAccessible(true);
        assertEquals(Set.of("placement-a"), ownedIds.invoke(manager, "server|dimension"));

        Method removeSession = LensManager.class.getDeclaredMethod("removeSessionState", String.class);
        removeSession.setAccessible(true);
        removeSession.invoke(manager, "session-a");
        assertEquals(Set.of(), ownedIds.invoke(manager, "server|dimension"));
    }
}
