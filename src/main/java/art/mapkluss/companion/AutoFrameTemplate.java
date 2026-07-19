package art.mapkluss.companion;

import java.util.List;
import java.util.Objects;

public record AutoFrameTemplate(
    String artId,
    String versionId,
    String title,
    int wide,
    int tall,
    List<String> tileHashes,
    List<Integer> tileMapIds,
    String updatedAt
) {
    public AutoFrameTemplate(
        String artId,
        String versionId,
        String title,
        int wide,
        int tall,
        List<String> tileHashes,
        String updatedAt
    ) {
        this(artId, versionId, title, wide, tall, tileHashes, List.of(), updatedAt);
    }

    public AutoFrameTemplate {
        artId = requireText(artId, "artId");
        versionId = requireText(versionId, "versionId");
        title = Objects.requireNonNull(title, "title");
        updatedAt = requireText(updatedAt, "updatedAt");
        if (wide <= 0 || tall <= 0) {
            throw new IllegalArgumentException("Template dimensions must be positive.");
        }
        int expected;
        try {
            expected = Math.multiplyExact(wide, tall);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Template dimensions are too large.", overflow);
        }
        tileHashes = List.copyOf(Objects.requireNonNull(tileHashes, "tileHashes"));
        if (tileHashes.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " tile hashes, got " + tileHashes.size() + ".");
        }
        for (String hash : tileHashes) {
            if (!MapColorFingerprint.isValid(hash)) {
                throw new IllegalArgumentException("Tile hashes must be uppercase SHA-256 hex strings.");
            }
        }
        tileMapIds = List.copyOf(Objects.requireNonNull(tileMapIds, "tileMapIds"));
        if (!tileMapIds.isEmpty()) {
            if (tileMapIds.size() != expected) {
                throw new IllegalArgumentException("Expected " + expected + " map IDs, got " + tileMapIds.size() + ".");
            }
            if (tileMapIds.stream().anyMatch(mapId -> mapId == null || mapId < 0)
                || tileMapIds.stream().distinct().count() != tileMapIds.size()) {
                throw new IllegalArgumentException("Template map IDs must be unique non-negative integers.");
            }
        }
    }

    public String hashAt(int column, int rowFromBottom) {
        return tileHashes.get(rowMajorIndex(column, rowFromBottom));
    }

    public int rowMajorIndex(int column, int rowFromBottom) {
        if (column < 0 || column >= wide || rowFromBottom < 0 || rowFromBottom >= tall) {
            throw new IndexOutOfBoundsException(
                "Tile coordinate outside " + wide + "x" + tall + ": " + column + "," + rowFromBottom
            );
        }
        return (tall - 1 - rowFromBottom) * wide + column;
    }

    public String bottomLeftHash() {
        return hashAt(0, 0);
    }

    public int tileIndexForMapId(int mapId) {
        return tileMapIds.indexOf(mapId);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank.");
        return value;
    }
}
