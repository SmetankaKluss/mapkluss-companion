package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Pure work runs off-thread; registry resolution is advanced by the client thread in small batches. */
public final class LiveBuildPreparation implements AutoCloseable {
    private final LiveBuildSchematic source;
    private final LiveBuildProgress.Identity identity;
    private final CompletableFuture<List<LiveBuildProgress.State>> palette;
    private final Map<LiveBuildProgress.State, LiveBuildProgress.State> normalized = new HashMap<>();
    private CompletableFuture<LiveBuildAssembly> result;
    private int cursor;
    private volatile boolean cancelled;

    public LiveBuildPreparation(LiveBuildSchematic source, LiveBuildProgress.Identity identity) {
        this.source = source;
        this.identity = identity;
        palette = CompletableFuture.supplyAsync(() -> {
            var unique = new LinkedHashSet<LiveBuildProgress.State>();
            for (var cell : source.cells()) { check(); unique.add(cell.expected()); }
            return List.copyOf(unique);
        });
    }

    /** Never waits for a worker. The caller supplies only a bounded number of registry operations. */
    public LiveBuildAssembly advance(Function<LiveBuildProgress.State, LiveBuildProgress.State> resolver, int budget) {
        check();
        if (budget < 1 || budget > 64) throw new IllegalArgumentException("Invalid preparation budget");
        if (result != null) return result.isDone() ? result.join() : null;
        if (!palette.isDone()) return null;
        var states = palette.join();
        for (int i = 0; i < budget && cursor < states.size(); i++, cursor++) {
            var state = states.get(cursor);
            normalized.put(state, java.util.Objects.requireNonNull(resolver.apply(state)));
        }
        if (cursor == states.size()) {
            var mapping = Map.copyOf(normalized);
            result = CompletableFuture.supplyAsync(() -> {
                var cells = new ArrayList<LiveBuildProgress.Cell>(source.cells().size());
                for (var cell : source.cells()) {
                    check();
                    cells.add(new LiveBuildProgress.Cell(cell.relativePosition(), mapping.get(cell.expected()),cell.requiresAir()));
                }
                check();
                var assembly=new LiveBuildAssembly(new LiveBuildParts(new LiveBuildSchematic(source.sha256(),cells,source.bounds(),source.artBounds())));
                check();
                return assembly;
            });
        }
        return null;
    }

    private void check() { if (cancelled) throw new CancellationException("Build preparation cancelled"); }
    @Override public void close() { cancelled = true; }
}
