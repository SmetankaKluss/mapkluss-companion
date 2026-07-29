package art.mapkluss.companion;

/** Collects fragmented WebSocket text without allowing unbounded growth. */
final class BoundedTextMessageAccumulator {
    private final int maxCharacters;
    private final StringBuilder buffer = new StringBuilder();
    private boolean discarding;

    BoundedTextMessageAccumulator(int maxCharacters) {
        if (maxCharacters < 1) throw new IllegalArgumentException("Message limit must be positive");
        this.maxCharacters = maxCharacters;
    }

    synchronized Result append(CharSequence fragment, boolean last) {
        CharSequence safe = fragment == null ? "" : fragment;
        if (discarding) {
            if (last) reset();
            return new Result(null, false);
        }
        if (safe.length() > maxCharacters - buffer.length()) {
            buffer.setLength(0);
            discarding = !last;
            return new Result(null, true);
        }
        buffer.append(safe);
        if (!last) return new Result(null, false);
        String complete = buffer.toString();
        reset();
        return new Result(complete, false);
    }

    synchronized void reset() {
        buffer.setLength(0);
        discarding = false;
    }

    record Result(String message, boolean overflow) {
        boolean complete() {
            return message != null;
        }
    }
}
