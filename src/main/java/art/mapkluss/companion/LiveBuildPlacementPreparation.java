package art.mapkluss.companion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Transforms pure coordinates off-thread, but delegates block-state transforms to bounded native calls. */
public final class LiveBuildPlacementPreparation implements AutoCloseable {
    private final LiveBuildParts.Part part;
    private final LiveBuildProgress.Identity identity;
    private final CompletableFuture<List<LiveBuildProgress.State>> palette;
    private final Map<LiveBuildProgress.State, LiveBuildProgress.State> transformed = new HashMap<>();
    private CompletableFuture<LiveBuildProgress> result;
    private int cursor;
    private volatile boolean cancelled;

    public LiveBuildPlacementPreparation(LiveBuildParts.Part part, LiveBuildProgress.Identity identity) {
        this.part = Objects.requireNonNull(part);
        this.identity = Objects.requireNonNull(identity);
        if (part.empty() || !part.targetSha256().equals(identity.schematicSha256())) {
            throw new IllegalArgumentException("Invalid placement target");
        }
        palette = CompletableFuture.supplyAsync(() -> {
            var unique = new LinkedHashSet<LiveBuildProgress.State>();
            for (var cell : part.cells()) { check(); unique.add(cell.expected()); }
            return List.copyOf(unique);
        });
    }

    /** Call only from the client thread. Never waits for worker completion. */
    public LiveBuildProgress advance(
        BiFunction<LiveBuildProgress.State, LiveBuildTransform, LiveBuildProgress.State> resolver, int budget) {
        check();
        if (budget < 1 || budget > 64) throw new IllegalArgumentException("Invalid placement budget");
        if (result != null) return result.isDone() ? result.join() : null;
        if (!palette.isDone()) return null;
        var states = palette.join();
        for (int i = 0; i < budget && cursor < states.size(); i++, cursor++) {
            check();
            var state = states.get(cursor);
            var next = Objects.requireNonNull(resolver.apply(state, identity.transform()));
            if (next.isAir() || !next.block().equals(state.block())) {
                throw new IllegalArgumentException("Transform changed block identity");
            }
            transformed.put(state, next);
        }
        if (cursor == states.size()) {
            var mapping = Map.copyOf(transformed);
            result = CompletableFuture.supplyAsync(() -> {
                var cells = new ArrayList<LiveBuildProgress.Cell>(part.cells().size());
                // Preserve source-cell order: the full artwork overview addresses these same indexes.
                for (var cell : part.cells()) {
                    check();
                    cells.add(new LiveBuildProgress.Cell(identity.transform().apply(cell.relativePosition()),
                        mapping.get(cell.expected()),cell.requiresAir()));
                }
                return new LiveBuildProgress(identity, cells, () -> cancelled);
            });
        }
        return null;
    }

    private void check() { if (cancelled) throw new CancellationException("Placement preparation cancelled"); }
    @Override public void close() { cancelled = true; }
}
