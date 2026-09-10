package art.mapkluss.companion;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

final class SuppressionPreviewTexture implements AutoCloseable {
    private Identifier identifier;
    Identifier get(SuppressionBundleCatalog catalog) {
        if (identifier != null) return identifier;
        int w = catalog.gridWide() * 128, h = catalog.gridTall() * 128;
        int[] pixels = SuppressionPreviewPixels.assemble(catalog, MapColor::getColorFromPackedId);
        NativeImage image = new NativeImage(w, h, false);
        for (int p = 0; p < pixels.length; p++) image.setPixel(p % w, p / w, pixels[p]);
        Identifier next = Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "two-layer-preview/" + java.util.UUID.randomUUID());
        try {
            Minecraft.getInstance().getTextureManager().register(next,
                new DynamicTexture(() -> "MapKluss Two-layer preview", image));
            identifier = next;
        } catch (RuntimeException error) { image.close(); throw error; }
        return identifier;
    }
    public void close() {
        if (identifier != null) {
            Minecraft.getInstance().getTextureManager().release(identifier);
            identifier = null;
        }
    }
}

