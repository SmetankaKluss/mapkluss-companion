package art.mapkluss.companion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

final class LiveBuildPreviewTexture implements AutoCloseable {
    private Identifier identifier;
    private long revision = -1;
    Identifier get(int w, int h, int[] pixels, long currentRevision) {
        if (identifier != null && revision == currentRevision) return identifier;
        close();
        NativeImage image = new NativeImage(w, h, false);
        for (int p = 0; p < pixels.length; p++) image.setColorArgb(p % w, p / w, pixels[p]);
        Identifier next = Identifier.of(MapKlussCompanionClient.MOD_ID, "live-build-preview/" + java.util.UUID.randomUUID());
        try {
            MinecraftClient.getInstance().getTextureManager().registerTexture(next,
                new NativeImageBackedTexture(image));
            identifier = next;
            revision = currentRevision;
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
