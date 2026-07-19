package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CompanionI18nTest {
    @Test
    void translatesDynamicTwoLayerStatusWithoutAClientInstance() {
        assertEquals(
            "Map mismatch: 30 pixels. J: retry",
            CompanionI18n.translate(
                "Карта не совпадает: 30 пикс. J: повторить",
                true
            )
        );
        assertEquals(
            "Could not update the stage schematic. Use the MapKluss highlight.",
            CompanionI18n.translate(
                "Не удалось обновить схему этапа. Используйте подсветку MapKluss.",
                true
            )
        );
        assertEquals(
            "The mod shows each step and highlight. You change the blocks.",
            CompanionI18n.translate(
                "Мод показывает этапы и подсветку. Блоки меняет игрок.",
                true
            )
        );
        assertEquals(
            "Mismatch: 2 pixels. J: confirm",
            CompanionI18n.translate(
                "Расхождение: 2 пикс. J: подтвердить",
                true
            )
        );
        assertEquals("данные карты ✓", CompanionI18n.translate("данные карты ✓", false));
        assertEquals("map data …", CompanionI18n.translate("данные карты …", true));
    }

    @Test
    void translatesNewPersistentLabelsAndActionTooltips() {
        assertEquals("Build UUID", CompanionI18n.translate("UUID сборки", true));
        assertEquals("Scan the map in your hand", CompanionI18n.translate("Сканировать карту в руке", true));
        assertEquals("Remove the installed schematic", CompanionI18n.translate("Удалить установленную схему", true));
        assertEquals("Close personal sessions in the editor", CompanionI18n.translate("Личную сессию закрывают в редакторе", true));
    }
}
