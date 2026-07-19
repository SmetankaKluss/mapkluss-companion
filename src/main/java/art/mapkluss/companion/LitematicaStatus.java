package art.mapkluss.companion;

public record LitematicaStatus(
    boolean litematicaPresent,
    boolean malilibPresent
) {
    public boolean ready() {
        return litematicaPresent && malilibPresent;
    }

    public String warning() {
        if (ready()) return "";
        if (!litematicaPresent && !malilibPresent) return "Litematica and MaLiLib not detected.";
        if (!litematicaPresent) return "Litematica not detected.";
        return "MaLiLib not detected.";
    }
}
