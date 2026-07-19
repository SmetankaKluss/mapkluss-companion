package art.mapkluss.companion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InstalledArtifactIndex {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<InstalledArtifact>>() { }.getType();

    private final Path path;
    private final List<InstalledArtifact> entries;

    private InstalledArtifactIndex(Path path, List<InstalledArtifact> entries) {
        this.path = path;
        this.entries = entries;
    }

    public static InstalledArtifactIndex load(Path path) throws IOException {
        if (!Files.exists(path)) return new InstalledArtifactIndex(path, new ArrayList<>());
        try (Reader reader = Files.newBufferedReader(path)) {
            List<InstalledArtifact> loaded = GSON.fromJson(reader, LIST_TYPE);
            return new InstalledArtifactIndex(path, loaded == null ? new ArrayList<>() : new ArrayList<>(loaded));
        }
    }

    public Optional<InstalledArtifact> findSameArtifact(String artId, String artifactId, String sha256) {
        return entries.stream()
            .filter(entry -> entry.artId().equals(artId))
            .filter(entry -> entry.artifactId().equals(artifactId))
            .filter(entry -> entry.sha256().equals(sha256))
            .findFirst();
    }

    public List<InstalledArtifact> findByArt(String artId) {
        return entries.stream()
            .filter(entry -> entry.artId().equals(artId))
            .toList();
    }

    public List<InstalledArtifact> entries() {
        return List.copyOf(entries);
    }

    public void upsert(InstalledArtifact artifact) {
        entries.removeIf(entry -> entry.artId().equals(artifact.artId()) && entry.artifactId().equals(artifact.artifactId()));
        entries.add(artifact);
    }

    public boolean remove(String artId, String artifactId) {
        return entries.removeIf(entry -> entry.artId().equals(artId) && entry.artifactId().equals(artifactId));
    }

    public void save() throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(entries, LIST_TYPE, writer);
        }
    }
}
