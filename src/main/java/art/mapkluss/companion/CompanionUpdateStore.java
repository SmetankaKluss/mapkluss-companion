package art.mapkluss.companion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CompanionUpdateStore {
    private static final int FORMAT = 1;

    private final Path path;
    private String lastShownVersion;

    private CompanionUpdateStore(Path path, String lastShownVersion) {
        this.path = path;
        this.lastShownVersion = lastShownVersion == null ? "" : lastShownVersion;
    }

    public static CompanionUpdateStore load(Path path) {
        if (path == null || !Files.isRegularFile(path)) return new CompanionUpdateStore(path, "");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!json.has("format") || json.get("format").getAsInt() != FORMAT) {
                return new CompanionUpdateStore(path, "");
            }
            String version = json.has("lastShownVersion") && json.get("lastShownVersion").isJsonPrimitive()
                ? json.get("lastShownVersion").getAsString()
                : "";
            return new CompanionUpdateStore(path, CompanionVersion.parse(version).map(Object::toString).orElse(""));
        } catch (Exception ignored) {
            return new CompanionUpdateStore(path, "");
        }
    }

    public boolean shouldShow(String version) {
        return CompanionVersion.parse(version).isPresent() && !version.equals(lastShownVersion);
    }

    public void markShown(String version) throws IOException {
        String normalized = CompanionVersion.parse(version)
            .map(Object::toString)
            .orElseThrow(() -> new IOException("Invalid Companion release version."));
        lastShownVersion = normalized;
        save();
    }

    private void save() throws IOException {
        if (path == null) throw new IOException("Update state path is unavailable.");
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("lastShownVersion", lastShownVersion);
        AtomicFiles.writePrivateUtf8(path, json.toString());
    }
}
