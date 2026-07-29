package art.mapkluss.companion;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CompanionVersion(int major, int minor, int patch) implements Comparable<CompanionVersion> {
    private static final Pattern RELEASE = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");

    public static Optional<CompanionVersion> parse(String value) {
        if (value == null) return Optional.empty();
        Matcher matcher = RELEASE.matcher(value.trim());
        if (!matcher.matches()) return Optional.empty();
        try {
            return Optional.of(new CompanionVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            ));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(CompanionVersion other) {
        int majorOrder = Integer.compare(major, other.major);
        if (majorOrder != 0) return majorOrder;
        int minorOrder = Integer.compare(minor, other.minor);
        return minorOrder != 0 ? minorOrder : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
