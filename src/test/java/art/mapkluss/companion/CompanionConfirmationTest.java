package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CompanionConfirmationTest {
    @Test
    void destructiveActionRequiresTwoExplicitActivations() {
        CompanionConfirmation confirmation = new CompanionConfirmation();
        assertFalse(confirmation.confirmOrArm());
        assertTrue(confirmation.armed());
        assertTrue(confirmation.confirmOrArm());
        assertFalse(confirmation.armed());
    }

    @Test
    void resetCancelsAnArmedAction() {
        CompanionConfirmation confirmation = new CompanionConfirmation();
        confirmation.confirmOrArm();
        confirmation.reset();
        assertFalse(confirmation.armed());
        assertFalse(confirmation.confirmOrArm());
    }
}
