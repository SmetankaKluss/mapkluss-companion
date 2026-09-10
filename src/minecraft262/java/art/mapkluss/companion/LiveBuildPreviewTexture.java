package art.mapkluss.companion;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

final class LiveBuildPreviewTexture implements AutoCloseable {
    private Identifier identifier;
    private long revision = -1;
    Identifier get(int w, int h, int[] pixels, long currentRevision) {
        if (identifier != null && revision == currentRevision) return identifier;
        close();
        NativeImage image = new NativeImage(w, h, false);
        for (int p = 0; p < pixels.length; p++) image.setPixel(p % w, p / w, pixels[p]);
        Identifier next = Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "live-build-preview/" + java.util.UUID.randomUUID());
        try {
            Minecraft.getInstance().getTextureManager().register(next,
                new DynamicTexture(() -> "MapKluss Build preview", image));
            identifier = next;
            revision = currentRevision;
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
