import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reproducible OFL bitmap conversion, never loaded by the game. */
class GenerateWorkshopFont {
    public static void main(String[] args) throws Exception {
        Path assets = Path.of("src/main/resources/assets/mapkluss-companion");
        Font font = Font.createFont(Font.TRUETYPE_FONT, Path.of("scripts/assets/press-start-2p.ttf").toFile()).deriveFont(8f);
        StringBuilder chars = new StringBuilder();
        for (char c = 33; c < 127; c++) chars.append(c);
        for (char c = '\u0410'; c <= '\u044f'; c++) chars.append(c);
        chars.append("\u0401\u0451\u00b7\u00d7\u2013\u2014\u2026\u00ab\u00bb\u2116");
        int rows = (chars.length() + 15) / 16;
        BufferedImage atlas = new BufferedImage(256, rows * 16, BufferedImage.TYPE_INT_ARGB);
        var g = atlas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setFont(font); g.setColor(Color.WHITE);
        StringBuilder json = new StringBuilder("{\n  \"providers\": [\n    {\"type\":\"space\",\"advances\":{\" \":4}},\n    {\"type\":\"bitmap\",\"file\":\"mapkluss-companion:font/workshop.png\",\"height\":16,\"ascent\":8,\"chars\":[\n");
        for (int row = 0; row < rows; row++) {
            json.append("      \"");
            for (int col = 0; col < 16; col++) {
                int i = row * 16 + col;
                char c = i < chars.length() ? chars.charAt(i) : 0;
                if (c != 0) {
                    if (!font.canDisplay(c)) throw new IllegalStateException("Missing glyph U+" + Integer.toHexString(c));
                    g.drawString(String.valueOf(c), col * 16, row * 16 + 8);
                }
                json.append(String.format("\\u%04x", (int)c));
            }
            json.append(row + 1 == rows ? "\"\n" : "\",\n");
        }
        g.dispose();
        json.append("    ]},\n    {\"type\":\"reference\",\"id\":\"minecraft:default\"}\n  ]\n}\n");
        Files.createDirectories(assets.resolve("textures/font"));
        ImageIO.write(atlas, "png", assets.resolve("textures/font/workshop.png").toFile());
        Files.writeString(assets.resolve("font/workshop.json"), json);
        System.out.println("Generated " + chars.length() + " glyphs; Latin, Russian and punctuation verified.");
    }
}
