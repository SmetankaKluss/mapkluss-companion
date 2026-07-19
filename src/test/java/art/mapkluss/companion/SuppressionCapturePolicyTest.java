package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionCapturePolicyTest {
    @Test
    void requiresTwoCompletelyStableTicksBeforeCapture() {
        int ticks = SuppressionCapturePolicy.nextStableTick(0, true);
        assertFalse(SuppressionCapturePolicy.readyToCapture(ticks));
        ticks = SuppressionCapturePolicy.nextStableTick(ticks, true);
        assertTrue(SuppressionCapturePolicy.readyToCapture(ticks));
        assertEquals(0, SuppressionCapturePolicy.nextStableTick(ticks, false));
    }

    @Test
    void rejectsMovementJumpFlightVehicleAndOpenScreens() {
        assertTrue(SuppressionCapturePolicy.stableCapturePosture(true, true, false, false, false, false, false, 0.0));
        assertFalse(SuppressionCapturePolicy.stableCapturePosture(true, false, false, false, false, false, false, 0.0));
        assertFalse(SuppressionCapturePolicy.stableCapturePosture(true, true, true, false, false, false, false, 0.0));
        assertFalse(SuppressionCapturePolicy.stableCapturePosture(true, true, false, false, false, true, false, 0.0));
        assertFalse(SuppressionCapturePolicy.stableCapturePosture(true, true, false, false, false, false, true, 0.0));
        assertFalse(SuppressionCapturePolicy.stableCapturePosture(true, true, false, false, false, false, false, 0.01));
    }

    @Test
    void requiresThePlayerToStandNearTheCheckpointCenter() {
        assertTrue(SuppressionCapturePolicy.centeredOnCheckpoint(10.5, 65.0, -3.5, 10, 64, -4));
        assertTrue(SuppressionCapturePolicy.centeredOnCheckpoint(10.73, 65.0, -3.27, 10, 64, -4));
        assertFalse(SuppressionCapturePolicy.centeredOnCheckpoint(10.75, 65.0, -3.5, 10, 64, -4));
        assertFalse(SuppressionCapturePolicy.centeredOnCheckpoint(10.5, 65.0, -3.75, 10, 64, -4));
        assertFalse(SuppressionCapturePolicy.centeredOnCheckpoint(10.5, 66.0, -3.5, 10, 64, -4));
    }
}
