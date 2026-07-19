package art.mapkluss.companion;

import java.text.Normalizer;
import java.util.Locale;

public final class SafeNames {
    private SafeNames() {
    }

    public static String slug(String input) {
        String value = input == null ? "" : input.replace("№", "").trim().toLowerCase(Locale.ROOT);
        value = Normalizer.normalize(value, Normalizer.Form.NFKC);
        value = value.replaceAll("[^a-z0-9а-яё_-]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "mapkluss_art" : value;
    }

    public static String litematicFilename(String title, int wide, int tall) {
        return slug(title) + "_" + wide + "x" + tall + ".litematic";
    }

    public static String conflictFilename(String title, int wide, int tall, String shortId) {
        return slug(title) + "_" + wide + "x" + tall + "_" + shortId + ".litematic";
    }

    public static String conflictFilename(String title, int wide, int tall, int index) {
        return slug(title) + "_" + wide + "x" + tall + "_" + String.format(Locale.ROOT, "%02d", index) + ".litematic";
    }

    public static String tileLitematicFilename(String title, int wide, int tall, int tileIndex) {
        return slug(title) + "_" + wide + "x" + tall + "_map_" + String.format(Locale.ROOT, "%02d", tileIndex) + ".litematic";
    }

    public static String tileConflictFilename(String title, int wide, int tall, int tileIndex, int suffix) {
        return slug(title) + "_" + wide + "x" + tall + "_map_" + String.format(Locale.ROOT, "%02d", tileIndex)
            + "_" + String.format(Locale.ROOT, "%02d", suffix) + ".litematic";
    }

    public static String tileConflictFilename(String title, int wide, int tall, int tileIndex, String shortId) {
        return slug(title) + "_" + wide + "x" + tall + "_map_" + String.format(Locale.ROOT, "%02d", tileIndex)
            + "_" + shortId + ".litematic";
    }

    public static String downloadFilename(String filename, String fallback) {
        String value = filename == null || filename.isBlank() ? fallback : filename;
        value = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replaceAll("[\\\\/:*?\"<>|]+", "_")
            .replaceAll("\\s+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^[_\\.]+|[_\\.]+$", "");
        return value.isEmpty() ? slug(fallback) : value;
    }
}
