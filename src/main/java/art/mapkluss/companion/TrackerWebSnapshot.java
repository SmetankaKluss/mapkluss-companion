package art.mapkluss.companion;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.*;

/** Owner-thread, budgeted material accounting. Geometry may already be dormant. */
public final class TrackerWebSnapshot {
    private final List<LiveBuildProgress> targets;
    private final Map<String, Integer> materials = new TreeMap<>();
    private int tile, cell;
    public TrackerWebSnapshot(List<LiveBuildProgress> targets) { this.targets = new ArrayList<>(targets); }
    public boolean matches(List<LiveBuildProgress> current) { return targets.equals(current); }
    public Map<String,Integer> materialCounts() {
        if(tile!=targets.size())throw new IllegalStateException("Accounting unfinished");
        return Map.copyOf(materials);
    }
    public boolean advance(long now, int budget) {
        long deadline = System.nanoTime() + 1_000_000;
        while (tile < targets.size() && budget-- > 0 && System.nanoTime() < deadline) {
            var target = targets.get(tile);
            if (target == null || cell >= target.size()) { tile++; cell = 0; continue; }
            String material = target.material(cell);
            if (material != null && target.displayObservation(cell, now, LiveBuildProgress.LIVE_FRESHNESS_MS).status() == LiveBuildProgress.Status.CORRECT)
                materials.merge(material, 1, Integer::sum);
            cell++;
        }
        return tile == targets.size();
    }
    public static JsonArray summary(LiveBuildProgress.Summary s) {
        var a = new JsonArray();
        for (int n : new int[]{s.total(),s.correct(),s.missing(),s.wrong(),s.unknown(),s.stale()}) a.add(n);
        return a;
    }
    public JsonObject capture(int width, int height, int[] pixels, LiveBuildProgress.Summary total, long now) {
        return freeze(width,height,pixels,total,now).get();
    }
    public java.util.function.Supplier<JsonObject> freeze(int width, int height, int[] pixels, LiveBuildProgress.Summary total, long now) {
        if (width < 1 || height < 1 || width > 1280 || height > 1280 || pixels.length != width * height || tile != targets.size())
            throw new IllegalArgumentException("Invalid tracker snapshot");
        var result = new JsonObject();
        result.addProperty("width",width); result.addProperty("height",height);
        result.add("summary",summary(total));
        var parts=new JsonArray();
        for(var target:targets)parts.add(target==null?JsonNull.INSTANCE:summary(target.liveSummary(now)));
        result.add("parts",parts);
        var counts=new JsonObject();materials.forEach(counts::addProperty);result.add("materials",counts);
        int[] owned=pixels.clone();
        return () -> encode(result,owned);
    }
    private static JsonObject encode(JsonObject result,int[] pixels) {
        var colours = new LinkedHashMap<Integer,Integer>();
        byte[] indices = new byte[pixels.length * 2];
        for (int i=0;i<pixels.length;i++) {
            int id=colours.computeIfAbsent(pixels[i], key -> colours.size());
            if(id>=1024)throw new IllegalArgumentException("Invalid tracker palette");
            indices[i*2]=(byte)id;indices[i*2+1]=(byte)(id>>>8);
        }
        var palette=new JsonArray();colours.keySet().forEach(palette::add);
        result.add("palette",palette);result.addProperty("pixels",Base64.getEncoder().encodeToString(indices));
        return result;
    }
}
