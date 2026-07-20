package art.mapkluss.companion;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

public final class LensWorldIdentity {
    private LensWorldIdentity() {
    }

    public static String serverHash(Minecraft client) {
        if (client == null) return null;
        if (client.isLocalServer()) {
            if (client.getSingleplayerServer() == null) return null;
            return localWorldHash(client.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
        }
        ServerData info = client.getCurrentServer();
        if (info == null || info.ip == null || info.ip.isBlank()) return null;
        return sha256(normalizeAddress(info.ip));
    }

    static String localWorldHash(Path savePath) {
        if (savePath == null) return null;
        return sha256("singleplayer:" + savePath.toAbsolutePath().normalize());
    }

    static String normalizeAddress(String address) {
        String value = address.trim().toLowerCase(Locale.ROOT);
        String host;
        int port = 25565;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            host = end > 0 ? value.substring(1, end) : value;
            if (end > 0 && end + 2 < value.length() && value.charAt(end + 1) == ':') {
                port = parsePort(value.substring(end + 2));
            }
            host = "[" + host + "]";
        } else {
            int separator = value.lastIndexOf(':');
            if (separator > 0 && value.indexOf(':') == separator) {
                host = value.substring(0, separator);
                port = parsePort(value.substring(separator + 1));
            } else {
                host = value;
            }
            host = IDN.toASCII(host);
        }
        return host + ":" + port;
    }

    public static String dimensionId(Minecraft client) {
        return client == null || client.level == null ? null : client.level.dimension().identifier().toString();
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : 25565;
        } catch (NumberFormatException ignored) {
            return 25565;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
