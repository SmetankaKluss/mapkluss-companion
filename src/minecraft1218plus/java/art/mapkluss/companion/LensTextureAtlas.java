package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class LensTextureAtlas implements AutoCloseable {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(15);
    static final int MAX_COMPRESSED_BYTES = 8 * 1024 * 1024;
    static final int MAX_TEXTURE_DIMENSION = 4_096;
    static final long MAX_TEXTURE_PIXELS = 16_777_216L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
    private static final LensTextureBudget TEXTURE_BUDGET = new LensTextureBudget(
        LensTextureBudget.DEFAULT_MAX_BYTES
    );

    private final String sessionId;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object queueLock = new Object();
    private volatile long revision = -1;
    private volatile long requestedRevision = -1;
    private volatile Identifier identifier;
    private volatile long reservedBytes;
    private PendingRequest pending;
    private boolean downloading;

    LensTextureAtlas(String sessionId) {
        this.sessionId = sessionId;
    }

    long revision() {
        return revision;
    }

    Identifier identifier() {
        return identifier;
    }

    boolean readyFor(long expectedRevision) {
        return identifier != null && revision == expectedRevision;
    }

    void request(String signedUrl, long nextRevision, int expectedWidth, int expectedHeight) {
        if (closed.get() || signedUrl == null || signedUrl.isBlank() || nextRevision <= revision) return;
        PendingRequest next = null;
        synchronized (queueLock) {
            if (closed.get() || nextRevision <= requestedRevision) return;
            requestedRevision = nextRevision;
            pending = new PendingRequest(signedUrl, nextRevision, expectedWidth, expectedHeight);
            if (!downloading) {
                downloading = true;
                next = pending;
                pending = null;
            }
        }
        if (next != null) download(next);
    }

    private void download(PendingRequest request) {
        CompletableFuture.supplyAsync(() -> downloadImage(request))
            .thenAccept(image -> MinecraftClient.getInstance().execute(() -> {
                try {
                    install(image, request);
                } finally {
                    finishDownload();
                }
            }))
            .exceptionally(error -> {
                MapKlussCompanionClient.LOGGER.warn(
                    "Lens preview revision {} failed for session {}.", request.revision(), sessionId, error
                );
                finishDownload();
                return null;
            });
    }

    private void finishDownload() {
        PendingRequest next;
        synchronized (queueLock) {
            if (closed.get()) {
                pending = null;
                downloading = false;
                return;
            }
            next = pending;
            pending = null;
            if (next == null && requestedRevision > revision) requestedRevision = revision;
            downloading = next != null;
        }
        if (next != null) download(next);
    }

    private void install(NativeImage image, PendingRequest request) {
        long nextRevision = request.revision();
        if (closed.get() || nextRevision != requestedRevision || nextRevision <= revision) {
            image.close();
            return;
        }
        if (image.getWidth() != request.expectedWidth() || image.getHeight() != request.expectedHeight()) {
            image.close();
            MapKlussCompanionClient.LOGGER.warn(
                "Rejected Lens revision {} for {}: expected {}x{}, got a different atlas size.",
                nextRevision, sessionId, request.expectedWidth(), request.expectedHeight()
            );
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        long nextReservedBytes = (long) image.getWidth() * image.getHeight() * 4;
        long previousReservedBytes = reservedBytes;
        if (!TEXTURE_BUDGET.resize(previousReservedBytes, nextReservedBytes)) {
            image.close();
            MapKlussCompanionClient.LOGGER.warn(
                "Rejected Lens revision {} for {}: the shared 256 MiB texture budget is full.",
                nextRevision, sessionId
            );
            return;
        }
        Identifier nextIdentifier = Identifier.of(
            MapKlussCompanionClient.MOD_ID,
            "lens/" + Integer.toUnsignedString(sessionId.hashCode(), 16) + "/" + nextRevision
        );
        NativeImageBackedTexture texture = new NativeImageBackedTexture(
            () -> "MapKluss Lens " + sessionId + " r" + nextRevision, image
        );
        try {
            client.getTextureManager().registerTexture(nextIdentifier, texture);
        } catch (RuntimeException error) {
            texture.close();
            TEXTURE_BUDGET.resize(nextReservedBytes, previousReservedBytes);
            throw error;
        }
        Identifier previous = identifier;
        reservedBytes = nextReservedBytes;
        identifier = nextIdentifier;
        revision = nextRevision;
        if (previous != null) client.getTextureManager().destroyTexture(previous);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Identifier current = identifier;
        long releasedBytes = reservedBytes;
        reservedBytes = 0;
        identifier = null;
        revision = -1;
        synchronized (queueLock) {
            requestedRevision = -1;
            pending = null;
        }
        if (current != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(current);
        TEXTURE_BUDGET.release(releasedBytes);
    }

    private static NativeImage downloadImage(PendingRequest request) {
        try {
            validateExpectedDimensions(request.expectedWidth(), request.expectedHeight());
            HttpRequest httpRequest = HttpRequest.newBuilder(CompanionApiClient.requireTrustedDownloadUri(request.signedUrl()))
                .timeout(DOWNLOAD_TIMEOUT).GET().build();
            HttpResponse<InputStream> response = HTTP.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("Lens preview HTTP " + response.statusCode());
            }
            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > MAX_COMPRESSED_BYTES) {
                response.body().close();
                throw new IOException("Lens preview exceeds the compressed size limit");
            }
            byte[] bytes;
            try (InputStream body = response.body()) {
                bytes = body.readNBytes(MAX_COMPRESSED_BYTES + 1);
            }
            if (bytes.length > MAX_COMPRESSED_BYTES) throw new IOException("Lens preview exceeds the compressed size limit");
            validatePngHeader(bytes, request.expectedWidth(), request.expectedHeight());
            return NativeImage.read(bytes);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static void validateExpectedDimensions(int width, int height) throws IOException {
        if (width < 1 || height < 1 || width > MAX_TEXTURE_DIMENSION || height > MAX_TEXTURE_DIMENSION
            || (long) width * height > MAX_TEXTURE_PIXELS) {
            throw new IOException("Lens preview dimensions exceed the texture safety limit");
        }
    }

    static void validatePngHeader(byte[] bytes, int expectedWidth, int expectedHeight) throws IOException {
        if (bytes == null || bytes.length < 24
            || bytes[0] != (byte) 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4e || bytes[3] != 0x47
            || bytes[4] != 0x0d || bytes[5] != 0x0a || bytes[6] != 0x1a || bytes[7] != 0x0a) {
            throw new IOException("Lens preview is not a valid PNG");
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width != expectedWidth || height != expectedHeight) {
            throw new IOException("Lens preview dimensions do not match the session");
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
            | (bytes[offset + 2] & 0xff) << 8 | bytes[offset + 3] & 0xff;
    }

    private record PendingRequest(String signedUrl, long revision, int expectedWidth, int expectedHeight) {
    }
}
