package art.mapkluss.companion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MapColorFingerprint {
    public static final int COLOR_COUNT = 128 * 128;
    private static final Pattern SHA_256 = Pattern.compile("[0-9A-F]{64}");

    private MapColorFingerprint() {
    }

    public static String sha256(byte[] colors) {
        Objects.requireNonNull(colors, "colors");
        if (colors.length != COLOR_COUNT) {
            throw new IllegalArgumentException("Map colors must contain exactly " + COLOR_COUNT + " bytes.");
        }
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(colors));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    public static String of(byte[] colors) {
        return sha256(colors);
    }

    public static boolean isValid(String fingerprint) {
        return fingerprint != null && SHA_256.matcher(fingerprint).matches();
    }
}
