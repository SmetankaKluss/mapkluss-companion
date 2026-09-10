package art.mapkluss.companion;

import java.util.UUID;

/** One worker owns a lease. Renewal invalidates all earlier queued uploads. */
public final class LiveBuildPublishingLease {
    private UUID nonce;
    private long deadline;
    private long origin;
    private long sequence;
    private long generation;

    public void accept(String value, long requestStart, long receivedAt) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value) || receivedAt < requestStart
            || Math.subtractExact(receivedAt, requestStart) >= 30_000) throw new IllegalArgumentException("Expired build lease");
        nonce = parsed;
        origin = receivedAt;
        deadline = Math.addExact(requestStart, 30_000);
        sequence = 0;
        generation++;
    }

    public Ticket next(long now) {
        if (nonce == null || now < origin || now >= deadline || sequence >= 67_108_862)
            throw new IllegalStateException("Build lease renewal required");
        return new Ticket(nonce.toString(), ++sequence, generation, origin);
    }

    public boolean current(Ticket ticket, long now) {
        return nonce != null && ticket != null && ticket.generation() == generation && nonce.toString().equals(ticket.nonce())
            && now >= origin && now < deadline;
    }

    public void clear() { nonce = null; generation++; }
    public record Ticket(String nonce, long sequence, long generation, long sampleOrigin) { }
}
