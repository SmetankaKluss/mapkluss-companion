package art.mapkluss.companion;

import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MapStackSlotRenderer {
    private static final int MAP_SIZE = 128;
    private static final int MAX_TEXTURES = 256;
    private static final Map<String, Identifier> TEXTURES = new LinkedHashMap<>(64, 0.75f, true);

    private MapStackSlotRenderer() {
    }

    public static void draw(DrawContext context, ItemStack stack, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || stack == null || stack.isEmpty()) return;
        Optional<MapPreviewStore.Entry> preview = MapStackManager.instance().preview(client, stack);
        preview.ifPresent(entry -> context.drawTexture(
            RenderLayer::getGuiTextured,
            texture(client, entry.hash(), entry.colors()),
            x, y, 0.0F, 0.0F, 16, 16, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE
        ));

        MapStackManager.instance().decoration(stack).ifPresent(decoration -> {
            drawTinyNumber(context, x, y, decoration.tileNumber(), decoration.color());
            drawBorder(context, x, y, decoration.color());
        });
    }

    private static synchronized Identifier texture(MinecraftClient client, String hash, byte[] colors) {
        Identifier existing = TEXTURES.get(hash);
        if (existing != null) return existing;
        NativeImage image = new NativeImage(MAP_SIZE, MAP_SIZE, false);
        for (int pixel = 0; pixel < colors.length; pixel++) {
            int colorByte = colors[pixel] & 255;
            int argb = colorByte == 0 ? 0xFFB8A678 : MapColor.getRenderColor(colorByte);
            image.setColorArgb(pixel % MAP_SIZE, pixel / MAP_SIZE, argb);
        }
        Identifier identifier = Identifier.of(
            MapKlussCompanionClient.MOD_ID,
            "mapstack/" + hash.toLowerCase(java.util.Locale.ROOT)
        );
        client.getTextureManager().registerTexture(
            identifier,
            new NativeImageBackedTexture(image)
        );
        TEXTURES.put(hash, identifier);
        trim(client);
        return identifier;
    }

    private static void trim(MinecraftClient client) {
        while (TEXTURES.size() > MAX_TEXTURES) {
            Iterator<Map.Entry<String, Identifier>> iterator = TEXTURES.entrySet().iterator();
            Map.Entry<String, Identifier> oldest = iterator.next();
            iterator.remove();
            client.getTextureManager().destroyTexture(oldest.getValue());
        }
    }

    private static void drawBorder(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 16, y + 1, color);
        context.fill(x, y + 15, x + 16, y + 16, color);
        context.fill(x, y + 1, x + 1, y + 15, color);
        context.fill(x + 15, y + 1, x + 16, y + 15, color);
    }

    private static void drawTinyNumber(DrawContext context, int x, int y, int tileNumber, int color) {
        if (tileNumber <= 0) return;
        String label = compactTileNumber(tileNumber);
        int gap = label.length() >= 4 ? 0 : 1;
        int width = label.length() * 3 + Math.max(0, label.length() - 1) * gap;
        int startX = x + 15 - width;
        int startY = y + 10;
        context.fill(Math.max(x + 1, startX - 1), y + 9, x + 15, y + 15, 0xD8000000);
        for (int character = 0; character < label.length(); character++) {
            int bits = glyph(label.charAt(character));
            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 3; column++) {
                    int bit = 1 << (14 - row * 3 - column);
                    if ((bits & bit) != 0) {
                        int pixelX = startX + character * (3 + gap) + column;
                        context.fill(pixelX, startY + row, pixelX + 1, startY + row + 1, color);
                    }
                }
            }
        }
    }

    static String compactTileNumber(int tileNumber) {
        if (tileNumber <= 9_999) return Integer.toString(tileNumber);
        return Math.min(99, tileNumber / 1_000) + "K";
    }

    private static int glyph(char value) {
        return switch (value) {
            case '0' -> 0b111_101_101_101_111;
            case '1' -> 0b010_110_010_010_111;
            case '2' -> 0b111_001_111_100_111;
            case '3' -> 0b111_001_111_001_111;
            case '4' -> 0b101_101_111_001_001;
            case '5' -> 0b111_100_111_001_111;
            case '6' -> 0b111_100_111_101_111;
            case '7' -> 0b111_001_010_010_010;
            case '8' -> 0b111_101_111_101_111;
            case '9' -> 0b111_101_111_001_111;
            case 'K' -> 0b101_101_110_101_101;
            default -> 0;
        };
    }
}
