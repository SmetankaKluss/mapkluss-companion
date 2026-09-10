package art.mapkluss.companion;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

final class ScanPreviewTexture implements AutoCloseable {
    private Identifier identifier;
    Identifier get(MapScanDraft draft) throws java.io.IOException {
        if (identifier != null) return identifier;
        ScanPreviewValidation.validate(draft);
        NativeImage image = NativeImage.read(draft.pngBytes());
        Identifier next = Identifier.of(MapKlussCompanionClient.MOD_ID, "scan-preview/" + java.util.UUID.randomUUID());
        try {
            MinecraftClient.getInstance().getTextureManager().registerTexture(next,
                new NativeImageBackedTexture(image));
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
