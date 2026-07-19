package art.mapkluss.companion;

public record SuppressionBundle(
    SuppressionPlanParser.Parsed parsed,
    byte[] planBytes,
    byte[] litematicBytes,
    String planSha256,
    String litematicSha256,
    String artId,
    String versionId,
    String title,
    String source
) {
    public SuppressionBundle {
        planBytes = planBytes.clone();
        litematicBytes = litematicBytes.clone();
    }

    @Override public byte[] planBytes() { return planBytes.clone(); }
    @Override public byte[] litematicBytes() { return litematicBytes.clone(); }
}
