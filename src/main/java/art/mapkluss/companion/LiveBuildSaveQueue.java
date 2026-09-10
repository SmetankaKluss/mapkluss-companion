package art.mapkluss.companion;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** One in-flight operation, one clear barrier and one replaceable snapshot. Production uses a worker executor. */
public final class LiveBuildSaveQueue {
    interface Writer {
        void save(LiveBuildSessionStore.Snapshot snapshot) throws IOException;
        void clear() throws IOException;
    }
    public record Session(long epoch, LiveBuildSourceCache.Reference source) { }
    private record Pending(LiveBuildSessionStore.Snapshot snapshot, CompletableFuture<Boolean> result) { }
    private final Executor executor;
    private final Writer writer;
    private long epoch;
    private Session current;
    private Pending pending;
    private CompletableFuture<Boolean> clearBarrier;
    private boolean running;
    private CompletableFuture<Void> idle = CompletableFuture.completedFuture(null);

    public LiveBuildSaveQueue(LiveBuildSessionStore store) {
        this(new Writer() {
            public void save(LiveBuildSessionStore.Snapshot snapshot) throws IOException { store.save(snapshot); }
            public void clear() throws IOException { store.clear(); }
        }, java.util.concurrent.ForkJoinPool.commonPool());
    }
    LiveBuildSaveQueue(Writer writer, Executor executor) {
        this.writer = Objects.requireNonNull(writer); this.executor = Objects.requireNonNull(executor);
    }
    /** Call only after successful source caching. Old import/scan callbacks cannot submit under this new token. */
    public synchronized Session begin(LiveBuildSourceCache.Reference source) {
        Objects.requireNonNull(source);
        Pending old = pending;
        pending = null;
        Session next = current = new Session(++epoch, source);
        if (old != null) old.result().complete(false);
        return next;
    }
    public synchronized CompletableFuture<Boolean> save(Session session, LiveBuildSessionStore.Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (!Objects.equals(current, session) || session == null) return CompletableFuture.completedFuture(false);
        if (!session.source().sha256().equals(snapshot.sourceSha256()) || session.source().kind() != snapshot.kind())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Snapshot source changed"));
        return enqueue(snapshot);
    }
    /** Terminal for this token: later callbacks cannot recreate the stopped session. Does not delete source files. */
    public synchronized CompletableFuture<Boolean> stop(Session session) {
        if (!Objects.equals(current, session) || session == null) return CompletableFuture.completedFuture(false);
        current = null; epoch++;
        return enqueue(null);
    }
    /** Join only at controlled shutdown/tests, never during a client tick. Failures belong to each save future. */
    public synchronized CompletableFuture<Void> idle() { return idle; }
    private CompletableFuture<Boolean> enqueue(LiveBuildSessionStore.Snapshot snapshot) {
        Pending old = pending;
        CompletableFuture<Boolean> result;
        if (snapshot == null) {
            pending = null;
            if (clearBarrier == null) clearBarrier = new CompletableFuture<>();
            result = clearBarrier;
        } else {
            result = new CompletableFuture<>(); pending = new Pending(snapshot, result);
        }
        if (old != null) old.result().complete(false);
        if (!running) {
            running = true; idle = new CompletableFuture<>();
            try { executor.execute(this::drain); }
            catch (RuntimeException rejected) {
                running = false;
                if (pending != null) pending.result().completeExceptionally(rejected);
                if (clearBarrier != null) clearBarrier.completeExceptionally(rejected);
                pending = null; clearBarrier = null; idle.complete(null);
            }
        }
        return result;
    }
    private void drain() {
        while (true) {
            Pending next;
            synchronized (this) {
                if (clearBarrier != null) { next = new Pending(null, clearBarrier); clearBarrier = null; }
                else { next = pending; pending = null; }
                if (next == null) { running = false; idle.complete(null); return; }
            }
            try {
                if (next.snapshot() == null) writer.clear(); else writer.save(next.snapshot());
                next.result().complete(true);
            } catch (IOException | RuntimeException failure) { next.result().completeExceptionally(failure); }
        }
    }
}
