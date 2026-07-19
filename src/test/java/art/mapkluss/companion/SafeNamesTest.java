package art.mapkluss.companion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SafeNamesTest {
    @Test
    void buildsLitematicFilenameFromTitleAndGrid() {
        assertEquals("castle_01_2x3.litematic", SafeNames.litematicFilename("Castle 01", 2, 3));
    }

    @Test
    void keepsCyrillicAndFallsBack() {
        assertEquals("мой_арт_1_1x1.litematic", SafeNames.litematicFilename(" Мой арт №1 ", 1, 1));
        assertEquals("mapkluss_art_1x1.litematic", SafeNames.litematicFilename("***", 1, 1));
    }
}
