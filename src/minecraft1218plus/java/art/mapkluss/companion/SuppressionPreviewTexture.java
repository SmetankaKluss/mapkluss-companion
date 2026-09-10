package art.mapkluss.companion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.block.MapColor;

final class SuppressionPreviewTexture implements AutoCloseable {
    private Identifier identifier;
    Identifier get(SuppressionBundleCatalog catalog) {
        if (identifier != null) return identifier;
        int w = catalog.gridWide() * 128, h = catalog.gridTall() * 128;
        int[] pixels = SuppressionPreviewPixels.assemble(catalog, MapColor::getRenderColor);
        NativeImage image = new NativeImage(w, h, false);
        for (int p = 0; p < pixels.length; p++) image.setColorArgb(p % w, p / w, pixels[p]);
        Identifier next = Identifier.of(MapKlussCompanionClient.MOD_ID, "two-layer-preview/" + java.util.UUID.randomUUID());
        try {
            MinecraftClient.getInstance().getTextureManager().registerTexture(next,
                new NativeImageBackedTexture(() -> "MapKluss Two-layer preview", image));
            identifier = next;
        } catch (RuntimeException error) { image.close(); throw error; }
        return identifier;
    }
    public void close() {
        if (identifier != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(identifier);
            identifier = null;
        }
    }
}

