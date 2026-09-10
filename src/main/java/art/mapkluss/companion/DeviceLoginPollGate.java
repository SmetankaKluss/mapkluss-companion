package art.mapkluss.companion;

/** Serializes manual/automatic polling and retains terminal results until UI dispatch. */
final class DeviceLoginPollGate {
    private Integer active;
    private Integer completed;

    synchronized boolean acquire(int generation) {
        if (active != null || completed(generation)) return false;
        active = generation;
        return true;
    }

    synchronized boolean completed(int generation) {
        return completed != null && completed == generation;
    }

    synchronized void release(int generation, boolean terminal) {
        if (active == null || active != generation) return;
        if (terminal) completed = generation;
        active = null;
    }
}
