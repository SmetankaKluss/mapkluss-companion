package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SuppressionPointAssignmentTest {
    @Test
    void assignsEveryPhasePixelToTheFirstCoveringPointAndChecksOnlyThatPoint() {
        SuppressionPlan.Phase phase = new SuppressionPlan.Phase(
            "phase-1", 0, List.of(0, 1), "", "", List.of(),
            List.of(new SuppressionPlan.PixelRun(1, 0, 1), new SuppressionPlan.PixelRun(0, 127, 1)),
            List.of(
                new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(1, 0, -2), 32),
                new SuppressionPlan.StandPoint(new SuppressionPlan.LocalPos(0, 0, 129), 32)
            ),
            2
        );
        byte[] target = new byte[128 * 128];
        target[1] = 4;
        target[127 * 128] = 8;
        byte[] actual = target.clone();

        assertEquals(0, SuppressionStateLogic.assignedStandPoint(phase.standPoints(), 1, 0));
        assertEquals(1, SuppressionStateLogic.assignedStandPoint(phase.standPoints(), 0, 127));
        assertTrue(SuppressionStateLogic.pointPixelsMatchTarget(actual, target, phase, 0));
        assertTrue(SuppressionStateLogic.pointPixelsMatchTarget(actual, target, phase, 1));

        actual[127 * 128] = 0;
        assertTrue(SuppressionStateLogic.pointPixelsMatchTarget(actual, target, phase, 0));
        assertFalse(SuppressionStateLogic.pointPixelsMatchTarget(actual, target, phase, 1));
    }
}
