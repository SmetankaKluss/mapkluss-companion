package art.mapkluss.companion;

final class CompanionPreviewKey {
    private CompanionPreviewKey() {
    }

    static String forManifest(CompanionManifest manifest, String fallbackUrl) {
        if (manifest == null || isBlank(manifest.artId()) || isBlank(manifest.versionId())) return fallbackUrl;
        return "manifest:" + manifest.artId() + ":" + manifest.versionId();
    }

    static String forLibraryItem(CompanionLibraryItem item, String fallbackUrl) {
        if (item == null || isBlank(item.artId()) || isBlank(item.currentVersionId())) return fallbackUrl;
        return "library:" + item.artId() + ":" + item.currentVersionId();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
