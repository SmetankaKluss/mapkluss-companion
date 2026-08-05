package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CompanionPreviewTextures {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ExecutorService DOWNLOADS = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "mapkluss-preview");
        thread.setDaemon(true);
        return thread;
    });
    private static final int MAX_COMPRESSED_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 16_777_216L;
    private static final ConcurrentMap<String, PreviewTexture> TEXTURES = new ConcurrentHashMap<>();

    private CompanionPreviewTextures() {
    }

    static PreviewTexture request(CompanionManifest manifest) {
        String url = previewUrl(manifest);
        return request(CompanionPreviewKey.forManifest(manifest, url), url);
    }

    static PreviewTexture request(CompanionLibraryItem item) {
        String url = item == null ? null : item.previewUrl();
        return request(CompanionPreviewKey.forLibraryItem(item, url), url);
    }

    private static PreviewTexture request(String cacheKey, String url) {
        if (url == null || url.isBlank()) return PreviewTexture.missing();
        String key = cacheKey == null || cacheKey.isBlank() ? url : cacheKey;
        PreviewTexture texture = TEXTURES.computeIfAbsent(key, ignored -> new PreviewTexture(key, url));
        texture.updateUrl(url);
        texture.start();
        return texture;
    }

    private static String previewUrl(CompanionManifest manifest) {
        if (manifest == null) return null;
        Optional<CompanionArtifact> previewArtifact = manifest.artifacts().stream()
            .filter(artifact -> "preview_png".equals(artifact.kind()))
            .findFirst();
        if (previewArtifact.isPresent() && previewArtifact.get().signedUrl() != null && !previewArtifact.get().signedUrl().isBlank()) {
            return previewArtifact.get().signedUrl();
        }
        return manifest.previewUrl();
    }

    static final class PreviewTexture {
        private final String cacheKey;
        private volatile String url;
        private volatile boolean started;
        private volatile boolean loading;
        private volatile boolean failed;
        private volatile Identifier identifier;
        private volatile int imageWidth;
        private volatile int imageHeight;

        private PreviewTexture(String cacheKey, String url) {
            this.cacheKey = cacheKey;
            this.url = url;
        }

        private static PreviewTexture missing() {
            PreviewTexture texture = new PreviewTexture("", "");
            texture.failed = true;
            return texture;
        }

        synchronized void updateUrl(String nextUrl) {
            if (nextUrl == null || nextUrl.isBlank() || nextUrl.equals(url)) return;
            url = nextUrl;
            if (failed && !loading) {
                failed = false;
                started = false;
            }
        }

        synchronized void start() {
            if (started || url.isBlank()) return;
            String requestedUrl = url;
            started = true;
            loading = true;
            CompletableFuture.supplyAsync(() -> download(requestedUrl), DOWNLOADS)
                .thenAccept(image -> MinecraftClient.getInstance().execute(() -> register(image)))
                .exceptionally(error -> {
                    retryAfterFailure(requestedUrl);
                    return null;
                });
        }

        private synchronized void retryAfterFailure(String requestedUrl) {
            loading = false;
            failed = true;
            started = false;
            if (!requestedUrl.equals(url)) start();
        }

        boolean ready() {
            return identifier != null && imageWidth > 0 && imageHeight > 0;
        }

        boolean loading() {
            return loading;
        }

        boolean failed() {
            return failed;
        }

        Identifier identifier() {
            return identifier;
        }

        int imageWidth() {
            return imageWidth;
        }

        int imageHeight() {
            return imageHeight;
        }

        private void register(NativeImage image) {
            try {
                imageWidth = image.getWidth();
                imageHeight = image.getHeight();
                identifier = Identifier.of("mapkluss-companion", "preview/" + Integer.toUnsignedString(cacheKey.hashCode(), 16));
                MinecraftClient.getInstance().getTextureManager().registerTexture(
                    identifier,
                    new NativeImageBackedTexture(() -> "MapKluss cloud preview", image)
                );
                failed = false;
            } finally {
                loading = false;
            }
        }

        private static NativeImage download(String url) {
            try {
                URI uri = CompanionApiClient.requireTrustedDownloadUri(url);
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    response.body().close();
                    throw new IOException("preview HTTP " + response.statusCode());
                }
                long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (declared > MAX_COMPRESSED_BYTES) {
                    response.body().close();
                    throw new IOException("preview exceeds the compressed size limit");
                }
                byte[] bytes;
                try (InputStream body = response.body()) {
                    bytes = CompanionApiClient.readBounded(body, MAX_COMPRESSED_BYTES);
                }
                validatePngHeader(bytes);
                return NativeImage.read(bytes);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    static void validatePngHeader(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 24
            || bytes[0] != (byte) 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4e || bytes[3] != 0x47
            || bytes[4] != 0x0d || bytes[5] != 0x0a || bytes[6] != 0x1a || bytes[7] != 0x0a) {
            throw new IOException("preview is not a valid PNG");
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
            || (long) width * height > MAX_PIXELS) {
            throw new IOException("preview dimensions exceed the texture safety limit");
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
            | ((bytes[offset + 1] & 0xff) << 16)
            | ((bytes[offset + 2] & 0xff) << 8)
            | (bytes[offset + 3] & 0xff);
    }
}
