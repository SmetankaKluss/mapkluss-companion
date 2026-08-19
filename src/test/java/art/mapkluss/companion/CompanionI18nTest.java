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
        assertEquals("Clear search", CompanionI18n.translate("Очистить поиск", true));
        assertEquals("Check for updates", CompanionI18n.translate("Проверить обновление", true));
        assertEquals("Place on frame", CompanionI18n.translate("Закрепить по рамке", true));
        assertEquals("Mode: Group", CompanionI18n.translate("Режим: ", true) + CompanionI18n.translate("Группа", true));
        assertEquals("Details", CompanionI18n.translate("Детали", true));
        assertEquals("Cloud connected", CompanionI18n.translate("Cloud подключён", true));
        assertEquals(
            "Library, Lens and progress are synchronized",
            CompanionI18n.translate("Библиотека, Lens и прогресс синхронизируются", true)
        );
        assertEquals("Saved: ", CompanionI18n.translate("Сохранено: ", true));
        assertEquals("Lens is not connected", CompanionI18n.translate("Lens не подключён", true));
        assertEquals("No scans yet", CompanionI18n.translate("Сканов пока нет", true));
        assertEquals("Recent sessions", CompanionI18n.translate("Недавние сессии", true));
        assertEquals(
            "Open the tracker from a saved art or paste a UUID",
            CompanionI18n.translate("Открой трекер из сохранённого арта или вставь UUID", true)
        );
        assertEquals("Build plan", CompanionI18n.translate("План строительства", true));
        assertEquals(
            "Cloud is preparing the art preview",
            CompanionI18n.translate("Облако готовит изображение арта", true)
        );
        assertEquals("Refreshing...", CompanionI18n.translate("Обновление...", true));
        assertEquals("Files and schematics", CompanionI18n.translate("Файлы и схемы", true));
        assertEquals("Cloud art", CompanionI18n.translate("Облачный арт", true));
        assertEquals("Build tools", CompanionI18n.translate("Инструменты постройки", true));
        assertEquals("Exports and archives", CompanionI18n.translate("Экспорт и архивы", true));
    }
}
