package art.mapkluss.companion;

final class CompanionConfirmation {
    private boolean armed;

    boolean confirmOrArm() {
        if (!armed) {
            armed = true;
            return false;
        }
        armed = false;
        return true;
    }

    boolean armed() {
        return armed;
    }

    void reset() {
        armed = false;
    }
}
