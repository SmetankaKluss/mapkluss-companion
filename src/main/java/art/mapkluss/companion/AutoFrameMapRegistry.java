package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class AutoFrameMapRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_BINDINGS = 20_000;

    private final Path path;
    private final List<Binding> bindings;

    private AutoFrameMapRegistry(Path path, List<Binding> bindings) {
        this.path = Objects.requireNonNull(path, "path");
        this.bindings = new ArrayList<>(bindings);
    }

    static AutoFrameMapRegistry load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) return new AutoFrameMapRegistry(path, List.of());
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) return new AutoFrameMapRegistry(path, List.of());
            List<Binding> values = new ArrayList<>();
            JsonElement entries = parsed.getAsJsonObject().get("bindings");
            if (entries != null && entries.isJsonArray()) {
                for (JsonElement entry : entries.getAsJsonArray()) {
                    Binding binding = sane(entry);
                    if (binding == null) continue;
                    values.removeIf(existing -> existing.sameMap(binding.connectionKey(), binding.mapId()));
                    values.add(binding);
                }
            }
            return new AutoFrameMapRegistry(path, values);
        } catch (JsonParseException | IllegalStateException malformed) {
            throw new IOException("Invalid AutoFrame map registry JSON.", malformed);
        }
    }

    synchronized Optional<Binding> find(String connectionKey, int mapId) {
        return bindings.stream().filter(binding -> binding.sameMap(connectionKey, mapId)).findFirst();
    }

    synchronized void remember(String connectionKey, int mapId, AutoFrameTemplate template, int tileIndex) {
        Objects.requireNonNull(template, "template");
        if (tileIndex < 0 || tileIndex >= template.tileHashes().size()) return;
        remember(connectionKey, mapId, template, tileIndex, template.tileHashes().get(tileIndex));
    }

    synchronized void remember(
        String connectionKey,
        int mapId,
        AutoFrameTemplate template,
        int tileIndex,
        String tileHash
    ) {
        Objects.requireNonNull(template, "template");
        if (connectionKey == null || connectionKey.isBlank() || mapId < 0 || tileIndex < 0
            || tileIndex >= template.tileHashes().size()) return;
        String expectedHash = template.tileHashes().get(tileIndex);
        if (!expectedHash.equals(tileHash)) return;
        bindings.removeIf(binding -> binding.sameMap(connectionKey, mapId));
        bindings.add(new Binding(connectionKey, mapId, template.artId(), template.versionId(), tileIndex, expectedHash));
        while (bindings.size() > MAX_BINDINGS) bindings.removeFirst();
    }

    synchronized List<Binding> findAll(String connectionKey, Iterable<Integer> mapIds) {
        List<Integer> requested = new ArrayList<>();
        for (Integer mapId : mapIds) if (mapId != null) requested.add(mapId);
        return bindings.stream().filter(binding ->
            binding.connectionKey().equals(connectionKey) && requested.contains(binding.mapId())
        ).toList();
    }

    synchronized boolean forget(String connectionKey, int mapId) {
        return bindings.removeIf(binding -> binding.sameMap(connectionKey, mapId));
    }

    synchronized void save() throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            JsonObject root = new JsonObject();
            root.add("bindings", GSON.toJsonTree(bindings));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Binding sane(JsonElement element) {
        try {
            Binding binding = GSON.fromJson(element, Binding.class);
            if (binding == null || binding.connectionKey() == null || binding.connectionKey().isBlank()
                || binding.artId() == null || binding.artId().isBlank()
                || binding.versionId() == null || binding.versionId().isBlank()
                || binding.mapId() < 0 || binding.tileIndex() < 0
                || (binding.tileHash() != null && !MapColorFingerprint.isValid(binding.tileHash()))) return null;
            return binding;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    record Binding(String connectionKey, int mapId, String artId, String versionId, int tileIndex, String tileHash) {
        Binding(String connectionKey, int mapId, String artId, String versionId, int tileIndex) {
            this(connectionKey, mapId, artId, versionId, tileIndex, null);
        }

        String groupKey() {
            return artId + "|" + versionId;
        }

        private boolean sameMap(String candidateKey, int candidateMapId) {
            return mapId == candidateMapId && connectionKey.equals(candidateKey);
        }
    }
}
