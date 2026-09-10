package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Worker-only, local content-addressed recovery source. No URL or serialized path can be followed. */
public final class LiveBuildSourceCache {
    public record Reference(LiveBuildSessionStore.SourceKind kind, String sha256) {
        public Reference {
            Objects.requireNonNull(kind);
            if (sha256 == null || !sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid source hash");
        }
    }
    public record Loaded(Reference reference, LiveBuildSchematic schematic, LiveBuildTopView projection,
                         LiveBuildBundleWorkspace bundle) implements AutoCloseable {
        @Override public void close() { if (bundle != null) bundle.close(); }
    }
    private final Path directory;
    private LiveBuildSourceCache(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    public static LiveBuildSourceCache forRunDir(Path runDir) {
        return new LiveBuildSourceCache(runDir.resolve("config/mapkluss-companion/live-build-sources"));
    }

    public Loaded importFile(Path source, LiveBuildSessionStore.SourceKind kind) throws IOException {
        return importOwnedBytes(read(source, limit(kind)), kind);
    }
    public Loaded importCatalog(SuppressionBundleCatalog catalog) throws IOException {
        return importOwnedBytes(LiveBuildCatalogSource.encode(catalog), LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP);
    }
    Loaded importBytes(byte[] input, LiveBuildSessionStore.SourceKind kind) throws IOException {
        if (input.length < 1 || input.length > limit(kind)) throw new IOException("Invalid source size");
        return importOwnedBytes(input.clone(), kind);
    }
    /** Transfers exclusive ownership: the caller must not read or mutate bytes after this call. */
    Loaded importOwnedBytes(byte[] bytes, LiveBuildSessionStore.SourceKind kind) throws IOException {
        if (bytes.length < 1 || bytes.length > limit(kind)) throw new IOException("Invalid source size");
        var reference = new Reference(kind, SuppressionHashes.sha256(bytes));
        Loaded loaded = parse(bytes, reference);
        try {
            Path target = path(reference);
            AtomicFiles.withLock(target, () -> {
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    if (!SuppressionHashes.sha256(read(target, limit(kind))).equals(reference.sha256()))
                        throw new IOException("Existing recovery source is corrupt");
                } else AtomicFiles.writePrivateAtomic(target, bytes);
                return null;
            });
            return loaded;
        } catch (IOException | RuntimeException failure) { loaded.close(); throw failure; }
    }
    public Loaded load(Reference reference) throws IOException {
        byte[] bytes = read(path(reference), limit(reference.kind()));
        if (!SuppressionHashes.sha256(bytes).equals(reference.sha256())) throw new IOException("Recovery source checksum mismatch");
        return parse(bytes, reference);
    }
    public byte[] exportBytes(Reference reference)throws IOException{
        byte[] bytes=read(path(reference),limit(reference.kind()));
        if(!SuppressionHashes.sha256(bytes).equals(reference.sha256()))throw new IOException("Source checksum mismatch");
        return bytes;
    }
    private Loaded parse(byte[] bytes, Reference reference) throws IOException {
        if (reference.kind() == LiveBuildSessionStore.SourceKind.TWO_LAYER_ZIP)
            return new Loaded(reference, null, null, LiveBuildBundleWorkspace.read(bytes));
        var schematic = LiveBuildSchematic.read(bytes);
        return new Loaded(reference, schematic, new LiveBuildTopView(schematic.cells(), schematic.artBounds()), null);
    }
    private Path path(Reference reference) {
        return directory.resolve(reference.sha256() + (reference.kind() == LiveBuildSessionStore.SourceKind.LITEMATIC ? ".litematic" : ".zip"));
    }
    private static int limit(LiveBuildSessionStore.SourceKind kind) {
        return kind == LiveBuildSessionStore.SourceKind.LITEMATIC ? LiveBuildSchematic.MAX_FILE_BYTES : SuppressionPlanParser.MAX_BUNDLE_BYTES;
    }
    private static byte[] read(Path path, int limit) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Recovery source unavailable");
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(limit + 1);
            if (bytes.length < 1 || bytes.length > limit) throw new IOException("Invalid source size");
            return bytes;
        }
    }
}
