package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class CompanionUiErrorsTest {
    @Test
    void scanErrorsAreLocalizedInsteadOfShowingEnglishJavaMessages() {
        assertEquals("Возьмите заполненную карту в любую руку.",
            CompanionUiErrors.message("scan", "Hold a filled map in either hand.", false));
        assertEquals("Наведитесь на рамку с заполненной картой.",
            CompanionUiErrors.message("scan", "Look at an item frame with a filled map.", false));
    }

    @Test
    void tracker404NeverLeaksHttpOrJson() {
        String message = CompanionUiErrors.message("tracker", "MapKluss API failed with HTTP 404: {\"error\":\"not_found\"}", true);
        assertEquals("Session not found. Check the UUID and try again.", message);
        assertFalse(message.contains("404"));
        assertFalse(message.contains("{"));
    }

    @Test
    void genericFailuresRemainActionableAndBounded() {
        assertEquals("Не удалось сохранить изменения. Повторите попытку.",
            CompanionUiErrors.message("save", "java.lang.IllegalStateException", false));
        assertEquals("Could not complete the Two-layer action. Try again.",
            CompanionUiErrors.message("two-layer", "java.lang.IllegalStateException", true));
    }
}
