package art.mapkluss.companion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record BuildSessionState(
    String id,
    CompanionManifest.Grid map_grid,
    String image_preview,
    List<BuildSessionMaterial> materials,
    Map<String, Integer> gathered,
    Map<String, Integer> placed,
    String mode,
    BuildSessionInfo info,
    String art_id,
    String art_version_id
) {
    public int totalBlocks() {
        if (materials == null) return 0;
        return materials.stream().mapToInt(BuildSessionMaterial::count).sum();
    }

    public int placedBlocks() {
        if (materials == null || placed == null) return 0;
        return materials.stream().mapToInt(material -> Math.min(placed.getOrDefault(material.nbtName(), 0), material.count())).sum();
    }

    public int gatheredBlocks() {
        if (materials == null || gathered == null) return 0;
        return materials.stream().mapToInt(material -> Math.min(gathered.getOrDefault(material.nbtName(), 0), material.count())).sum();
    }

    public Map<String, Integer> currentProgress() {
        return "building".equals(mode) ? safeMap(placed) : safeMap(gathered);
    }

    public BuildSessionState withProgress(String nbtName, int delta) {
        if (materials == null) return this;
        BuildSessionMaterial material = materials.stream()
            .filter(candidate -> candidate.nbtName().equals(nbtName))
            .findFirst()
            .orElse(null);
        if (material == null) return this;
        Map<String, Integer> next = currentProgress();
        int value = Math.max(0, Math.min(material.count(), next.getOrDefault(nbtName, 0) + delta));
        next.put(nbtName, value);
        if ("building".equals(mode)) {
            return new BuildSessionState(id, map_grid, image_preview, materials, safeMap(gathered), next, mode, info, art_id, art_version_id);
        }
        return new BuildSessionState(id, map_grid, image_preview, materials, next, safeMap(placed), mode, info, art_id, art_version_id);
    }

    public BuildSessionState withAbsoluteProgress(String nbtName, int value) {
        if (materials == null) return this;
        BuildSessionMaterial material = materials.stream()
            .filter(candidate -> candidate.nbtName().equals(nbtName))
            .findFirst()
            .orElse(null);
        if (material == null) return this;
        int clamped = Math.max(0, Math.min(material.count(), value));
        Map<String, Integer> next = currentProgress();
        next.put(nbtName, clamped);
        if ("building".equals(mode)) {
            return new BuildSessionState(id, map_grid, image_preview, materials, safeMap(gathered), next, mode, info, art_id, art_version_id);
        }
        return new BuildSessionState(id, map_grid, image_preview, materials, next, safeMap(placed), mode, info, art_id, art_version_id);
    }

    public BuildSessionState withMode(String nextMode) {
        if (!("gathering".equals(nextMode) || "building".equals(nextMode)) || nextMode.equals(mode)) return this;
        return new BuildSessionState(
            id, map_grid, image_preview, materials, safeMap(gathered), safeMap(placed), nextMode, info, art_id, art_version_id
        );
    }

    private static Map<String, Integer> safeMap(Map<String, Integer> source) {
        return source == null ? new HashMap<>() : new HashMap<>(source);
    }
}
