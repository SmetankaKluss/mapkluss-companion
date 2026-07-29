package art.mapkluss.companion;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;

/** Runs mutations in order and coalesces only adjacent pending mutations with the same non-empty key. */
final class SerialLatestQueue<T> {
    private final ArrayDeque<T> pending = new ArrayDeque<>();
    private final Consumer<T> runner;
    private final Function<T, String> replacementKey;
    private final Executor executor;
    private boolean running;

    SerialLatestQueue(Consumer<T> runner, Function<T, String> replacementKey) {
        this(runner, replacementKey, ForkJoinPool.commonPool());
    }

    SerialLatestQueue(Consumer<T> runner, Function<T, String> replacementKey, Executor executor) {
        this.runner = Objects.requireNonNull(runner);
        this.replacementKey = Objects.requireNonNull(replacementKey);
        this.executor = Objects.requireNonNull(executor);
    }

    synchronized void submit(T item) {
        String key = replacementKey.apply(item);
        T last = pending.peekLast();
        if (last != null && key != null && !key.isBlank() && key.equals(replacementKey.apply(last))) {
            pending.removeLast();
        }
        pending.addLast(item);
        if (running) return;
        running = true;
        executor.execute(this::drain);
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    private void drain() {
        while (true) {
            T next;
            synchronized (this) {
                next = pending.pollFirst();
                if (next == null) {
                    running = false;
                    return;
                }
            }
            runner.accept(next);
        }
    }
}
