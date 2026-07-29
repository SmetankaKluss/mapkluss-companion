package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LensRecoveryGateTest {
    @Test
    void invalidationLetsANewWorldStartWithoutRevivingOldWork() {
        LensRecoveryGate gate = new LensRecoveryGate();
        LensRecoveryGate.Token old = gate.tryBegin();
        assertNotNull(old);
        assertNull(gate.tryBegin());

        gate.invalidate();
        LensRecoveryGate.Token current = gate.tryBegin();
        assertNotNull(current);
        assertFalse(gate.isCurrent(old));
        assertFalse(gate.isLatest(old));
        assertTrue(gate.isCurrent(current));
        assertTrue(gate.isLatest(current));
    }

    @Test
    void staleCompletionCannotReleaseOrFinalizeNewWork() {
        LensRecoveryGate gate = new LensRecoveryGate();
        LensRecoveryGate.Token old = gate.tryBegin();
        gate.invalidate();
        LensRecoveryGate.Token current = gate.tryBegin();
        AtomicInteger finalized = new AtomicInteger();

        assertFalse(gate.finish(old, finalized::incrementAndGet));
        assertTrue(gate.isCurrent(current));
        assertTrue(gate.finish(current, finalized::incrementAndGet));
        assertFalse(gate.isCurrent(current));
        assertTrue(finalized.get() == 1);
    }

    @Test
    void invalidationCannotInterleaveWithCurrentFinalizer() throws Exception {
        LensRecoveryGate gate = new LensRecoveryGate();
        LensRecoveryGate.Token current = gate.tryBegin();
        CountDownLatch finalizerStarted = new CountDownLatch(1);
        CountDownLatch releaseFinalizer = new CountDownLatch(1);
        CountDownLatch invalidated = new CountDownLatch(1);
        Thread finishing = new Thread(() -> gate.finish(current, () -> {
            finalizerStarted.countDown();
            try {
                releaseFinalizer.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }));
        Thread invalidating = new Thread(() -> {
            gate.invalidate();
            invalidated.countDown();
        });

        finishing.start();
        assertTrue(finalizerStarted.await(2, TimeUnit.SECONDS));
        invalidating.start();
        assertFalse(invalidated.await(100, TimeUnit.MILLISECONDS));
        releaseFinalizer.countDown();
        finishing.join();
        invalidating.join();
        assertTrue(invalidated.await(2, TimeUnit.SECONDS));
        assertNotNull(gate.tryBegin());
    }
}
