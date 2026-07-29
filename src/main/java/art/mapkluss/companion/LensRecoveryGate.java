package art.mapkluss.companion;

import java.util.concurrent.atomic.AtomicLong;

/** Keeps recovery work from one Minecraft world from mutating another world. */
final class LensRecoveryGate {
    private final AtomicLong sequence = new AtomicLong();
    private Token active;

    synchronized Token tryBegin() {
        if (active != null) return null;
        Token token = new Token(sequence.incrementAndGet());
        active = token;
        return token;
    }

    synchronized boolean isCurrent(Token token) {
        return token != null && active == token;
    }

    synchronized boolean isLatest(Token token) {
        return token != null && sequence.get() == token.sequence();
    }

    synchronized void invalidate() {
        sequence.incrementAndGet();
        active = null;
    }

    synchronized boolean runIfCurrent(Token token, Runnable action) {
        if (token == null || active != token) return false;
        action.run();
        return true;
    }

    synchronized boolean finish(Token token, Runnable currentOnlyFinalizer) {
        if (token == null || active != token) return false;
        try {
            if (currentOnlyFinalizer != null) currentOnlyFinalizer.run();
            return true;
        } finally {
            active = null;
        }
    }

    record Token(long sequence) {
    }
}
