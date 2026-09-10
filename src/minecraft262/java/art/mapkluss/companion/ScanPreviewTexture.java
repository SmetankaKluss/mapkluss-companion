package art.mapkluss.companion;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

final class ScanPreviewTexture implements AutoCloseable {
    private Identifier identifier;
    Identifier get(MapScanDraft draft) throws java.io.IOException {
        if (identifier != null) return identifier;
        ScanPreviewValidation.validate(draft);
        NativeImage image = NativeImage.read(draft.pngBytes());
        Identifier next = Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, "scan-preview/" + java.util.UUID.randomUUID());
        try {
            Minecraft.getInstance().getTextureManager().register(next,
                new DynamicTexture(() -> "MapKluss scan preview", image));
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
