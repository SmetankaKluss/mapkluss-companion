package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

public final class AutoFrameTemplateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_TEMPLATES = 100;

    private final Path path;
    private final List<AutoFrameTemplate> templates;
    private String activeArtId;
    private String activeVersionId;

    private AutoFrameTemplateStore(
        Path path,
        List<AutoFrameTemplate> templates,
        String activeArtId,
        String activeVersionId
    ) {
        this.path = Objects.requireNonNull(path, "path");
        this.templates = new ArrayList<>(templates);
        this.activeArtId = activeArtId;
        this.activeVersionId = activeVersionId;
        discardInvalidActive();
    }

    public static AutoFrameTemplateStore load(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) return new AutoFrameTemplateStore(path, List.of(), null, null);

        final JsonObject root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                return new AutoFrameTemplateStore(path, List.of(), null, null);
            }
            root = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException malformed) {
            throw new IOException("Invalid AutoFrame template JSON.", malformed);
        }

        List<AutoFrameTemplate> templates = new ArrayList<>();
        JsonElement templateElement = root.get("templates");
        if (templateElement != null && templateElement.isJsonArray()) {
            for (JsonElement element : templateElement.getAsJsonArray()) {
                AutoFrameTemplate template = saneTemplate(element);
                if (template == null) continue;
                templates.removeIf(existing -> sameKey(existing, template.artId(), template.versionId()));
                templates.add(template);
            }
        }
        return new AutoFrameTemplateStore(
            path,
            templates,
            optionalString(root, "activeArtId"),
            optionalString(root, "activeVersionId")
        );
    }

    public synchronized List<AutoFrameTemplate> templates() {
        return List.copyOf(templates);
    }

    public synchronized Optional<AutoFrameTemplate> find(String artId, String versionId) {
        return templates.stream().filter(template -> sameKey(template, artId, versionId)).findFirst();
    }

    public synchronized void upsert(AutoFrameTemplate template) {
        Objects.requireNonNull(template, "template");
        templates.removeIf(existing -> existing.artId().equals(template.artId()));
        templates.add(template);
        while (templates.size() > MAX_TEMPLATES) templates.remove(0);
        discardInvalidActive();
    }

    public synchronized Optional<AutoFrameTemplate> activeTemplate() {
        if (activeArtId == null || activeVersionId == null) return Optional.empty();
        return find(activeArtId, activeVersionId);
    }

    public synchronized Optional<String> activeArtId() {
        return Optional.ofNullable(activeArtId);
    }

    public synchronized Optional<String> activeVersionId() {
        return Optional.ofNullable(activeVersionId);
    }

    public synchronized void setActive(String artId, String versionId) {
        AutoFrameTemplate template = find(artId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Cannot activate an unknown AutoFrame template."));
        activeArtId = template.artId();
        activeVersionId = template.versionId();
    }

    public synchronized void setActive(AutoFrameTemplate template) {
        Objects.requireNonNull(template, "template");
        setActive(template.artId(), template.versionId());
    }

    public synchronized void clearActive() {
        activeArtId = null;
        activeVersionId = null;
    }

    public synchronized void save() throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(), writer);
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

    private JsonObject toJson() {
        JsonObject root = new JsonObject();
        JsonArray values = new JsonArray();
        for (AutoFrameTemplate template : templates) values.add(GSON.toJsonTree(template));
        root.add("templates", values);
        if (activeArtId != null && activeVersionId != null) {
            root.addProperty("activeArtId", activeArtId);
            root.addProperty("activeVersionId", activeVersionId);
        }
        return root;
    }

    private void discardInvalidActive() {
        if (activeArtId == null || activeVersionId == null || find(activeArtId, activeVersionId).isEmpty()) {
            activeArtId = null;
            activeVersionId = null;
        }
    }

    private static AutoFrameTemplate saneTemplate(JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        try {
            JsonObject value = element.getAsJsonObject();
            JsonArray hashes = value.getAsJsonArray("tileHashes");
            if (hashes == null) return null;
            List<String> tileHashes = new ArrayList<>(hashes.size());
            for (JsonElement hash : hashes) tileHashes.add(hash.getAsString());
            List<Integer> tileMapIds = new ArrayList<>();
            JsonElement mapIds = value.get("tileMapIds");
            if (mapIds != null && mapIds.isJsonArray()) {
                for (JsonElement mapId : mapIds.getAsJsonArray()) tileMapIds.add(mapId.getAsInt());
            }
            return new AutoFrameTemplate(
                requiredString(value, "artId"),
                requiredString(value, "versionId"),
                requiredString(value, "title"),
                value.get("wide").getAsInt(),
                value.get("tall").getAsInt(),
                tileHashes,
                tileMapIds,
                requiredString(value, "updatedAt")
            );
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing " + name);
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String name) {
        try {
            JsonElement value = object.get(name);
            return value == null || value.isJsonNull() ? null : value.getAsString();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean sameKey(AutoFrameTemplate template, String artId, String versionId) {
        return template.artId().equals(artId) && template.versionId().equals(versionId);
    }
}
