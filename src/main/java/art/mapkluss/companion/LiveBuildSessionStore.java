package art.mapkluss.companion;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Private local metadata/observations. Source bytes are addressed by hash, never by a serialized path. */
public final class LiveBuildSessionStore {
    static final int VERSION = 4;
    static final int MAX_BYTES = 3 * 1024 * 1024;
    static final int MAX_BUNDLE_OBSERVATIONS = 8_000_000;
    private static final int MAGIC = 0x4d4b4254;
    public enum SourceKind { LITEMATIC, TWO_LAYER_ZIP }

    public record Placement(int tile, int phase, boolean followsTwoLayer,
                            LiveBuildProgress.Identity identity, byte[] states) {
        public Placement {
            Objects.requireNonNull(identity);
            if (tile < 0 || tile >= 100 || phase < -1 || phase > 63 || states.length == 0
                || states.length > LiveBuildProgress.MAX_CELLS) throw new IllegalArgumentException("Invalid placement");
            var p = identity.origin();
            if (Math.abs((long)p.x()) > 30_000_000 || Math.abs((long)p.z()) > 30_000_000
                || Math.abs((long)p.y()) > 4096) throw new IllegalArgumentException("Invalid placement origin");
            states = states.clone();
            for (byte state : states) if (state < 0 || state > 3) throw new IllegalArgumentException("Invalid observation");
        }
        @Override public byte[] states() { return states.clone(); }
    }

    public record GroupLink(String id,long revision,List<LiveBuildSharedPlacement> placements) {
        public GroupLink {
            if(id==null||!UUID.fromString(id).toString().equals(id)||revision<1||placements.size()>100)
                throw new IllegalArgumentException("Invalid saved group");
            placements=List.copyOf(placements);
            var tiles=new HashSet<Integer>();
            for(var p:placements)if(!tiles.add(p.tile())||p.revision()<1||p.revision()>revision)throw new IllegalArgumentException("Invalid saved association");
        }
    }
    public record CloudLink(String art, String version) {
        public CloudLink { UUID.fromString(art); UUID.fromString(version); }
    }
    public record Snapshot(SourceKind kind, String sourceSha256, String worldHash, String dimension,
                           int gridWide, int gridTall, int selected, long savedAt, List<Placement> placements,GroupLink group,CloudLink cloud) {
        public Snapshot(SourceKind kind,String sourceSha256,String worldHash,String dimension,int gridWide,int gridTall,int selected,long savedAt,List<Placement> placements,GroupLink group){
            this(kind,sourceSha256,worldHash,dimension,gridWide,gridTall,selected,savedAt,placements,group,null);
        }
        public Snapshot(SourceKind kind,String sourceSha256,String worldHash,String dimension,int gridWide,int gridTall,int selected,long savedAt,List<Placement> placements){
            this(kind,sourceSha256,worldHash,dimension,gridWide,gridTall,selected,savedAt,placements,null);
        }
        public Snapshot withGroup(GroupLink link){return new Snapshot(kind,sourceSha256,worldHash,dimension,gridWide,gridTall,selected,savedAt,placements,link,cloud);}
        public Snapshot withCloud(CloudLink link){return new Snapshot(kind,sourceSha256,worldHash,dimension,gridWide,gridTall,selected,savedAt,placements,group,link);}
        public Snapshot {
            Objects.requireNonNull(kind);
            if (!hash(sourceSha256) || !hash(worldHash) || dimension == null || dimension.length() > 256
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || gridWide < 1 || gridWide > 10
                || gridTall < 1 || gridTall > 10 || selected < 0 || selected >= gridWide * gridTall || savedAt < 0)
                throw new IllegalArgumentException("Invalid build session");
            placements = List.copyOf(placements);
            if (placements.size() > gridWide * gridTall) throw new IllegalArgumentException("Too many placements");
            var seen = new HashSet<Integer>();
            int total = 0, bound = 0;
            for (var placement : placements) {
                if (!seen.add(placement.tile()) || placement.tile() >= gridWide * gridTall
                    || !worldHash.equals(placement.identity().worldKey()) || !dimension.equals(placement.identity().dimension())
                    || (kind == SourceKind.LITEMATIC && (placement.phase() != -1 || placement.followsTwoLayer())))
                    throw new IllegalArgumentException("Placement identity mismatch");
                total = Math.addExact(total, placement.states.length);
                if (placement.followsTwoLayer()) bound++;
            }
            if (total > observationBudget(kind) || bound > 1) throw new IllegalArgumentException("Session budget exceeded");
            if(group!=null)for(var remote:group.placements()){
                var local=placements.stream().filter(p->p.tile()==remote.tile()).findFirst().orElseThrow();
                if(!remote.matchesLocal(local.identity(),local.phase(),local.states.length))throw new IllegalArgumentException("Saved group target mismatch");
            }
        }
    }

    private final Path path;
    private LiveBuildSessionStore(Path path) { this.path = path; }
    public static LiveBuildSessionStore forRunDir(Path runDir) {
        return new LiveBuildSessionStore(runDir.resolve("config/mapkluss-companion/live-build-session.bin"));
    }

    public void save(Snapshot snapshot) throws IOException {
        byte[] bytes = encode(snapshot);
        AtomicFiles.writePrivateAtomic(path, bytes);
    }

    /** Explicit Stop only; world-exit suspension must retain the session. Source cache is untouched. */
    public void clear() throws IOException {
        AtomicFiles.withLock(path, () -> { Files.deleteIfExists(path); return null; });
    }

    public Snapshot load() throws IOException {
        return AtomicFiles.withLock(path, () -> {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid session file");
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                return decode(bytes);
            }
        });
    }

    static byte[] encode(Snapshot snapshot) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeByte(snapshot.kind().ordinal());
            out.writeUTF(snapshot.sourceSha256()); out.writeUTF(snapshot.worldHash()); out.writeUTF(snapshot.dimension());
            out.writeInt(snapshot.gridWide()); out.writeInt(snapshot.gridTall()); out.writeInt(snapshot.selected());
            out.writeLong(snapshot.savedAt()); out.writeInt(snapshot.placements().size());
            for (var placement : snapshot.placements()) {
                out.writeInt(placement.tile()); out.writeInt(placement.phase()); out.writeBoolean(placement.followsTwoLayer());
                var id = placement.identity();
                out.writeUTF(id.schematicSha256());
                out.writeInt(id.origin().x()); out.writeInt(id.origin().y()); out.writeInt(id.origin().z());
                out.writeLong(id.placementRevision()); out.writeByte(id.transform().quarterTurns()); out.writeBoolean(id.transform().mirrorX());
                out.writeInt(placement.states.length);
                for (int start = 0; start < placement.states.length; start += 4) {
                    int packed = 0;
                    for (int j = 0; j < 4 && start + j < placement.states.length; j++)
                        packed |= placement.states[start + j] << (j * 2);
                    out.writeByte(packed);
                }
            }
            out.writeBoolean(snapshot.group()!=null);
            if(snapshot.group()!=null){
                var group=snapshot.group();out.writeUTF(group.id());out.writeLong(group.revision());
                var array=new com.google.gson.JsonArray();
                for(var p:group.placements()){var value=p.input();value.addProperty("revision",p.revision());value.addProperty("build_id",group.id());array.add(value);}
                byte[] json=array.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(json.length>100000)throw new IOException("Group metadata too large");out.writeInt(json.length);out.write(json);
            }
            out.writeBoolean(snapshot.cloud()!=null);
            if(snapshot.cloud()!=null){out.writeUTF(snapshot.cloud().art());out.writeUTF(snapshot.cloud().version());}
        }
        byte[] body = bytes.toByteArray();
        if (body.length > MAX_BYTES - 32) throw new IOException("Session exceeds size limit");
        bytes.write(digest(body));
        return bytes.toByteArray();
    }

    static Snapshot decode(byte[] bytes) throws IOException {
        if (bytes.length < 40 || bytes.length > MAX_BYTES) throw new IOException("Invalid session size");
        byte[] body = Arrays.copyOf(bytes, bytes.length - 32);
        if (!MessageDigest.isEqual(digest(body), Arrays.copyOfRange(bytes, body.length, bytes.length)))
            throw new IOException("Session checksum mismatch");
        try (var in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC) throw new IOException("Invalid session magic");
            int version = in.readInt();
            if (version < 1 || version > VERSION) throw new IOException("Unsupported session version");
            int kind = in.readUnsignedByte();
            if (kind >= SourceKind.values().length) throw new IOException("Invalid source kind");
            String source = in.readUTF(), world = in.readUTF(), dimension = in.readUTF();
            int wide = in.readInt(), tall = in.readInt(), selected = in.readInt();
            long time = in.readLong(); int count = in.readInt();
            if (count < 0 || count > 100) throw new IOException("Invalid placement count");
            var placements = new ArrayList<Placement>(count);
            int remaining = version == 1 ? LiveBuildProgress.MAX_CELLS : observationBudget(SourceKind.values()[kind]);
            for (int n = 0; n < count; n++) {
                int tile = in.readInt(), phase = in.readInt(); boolean follows = readBoolean(in);
                String target = in.readUTF();
                var origin = new LiveBuildProgress.Position(in.readInt(), in.readInt(), in.readInt());
                long revision = in.readLong();
                var transform = new LiveBuildTransform(in.readUnsignedByte(), readBoolean(in));
                int length = in.readInt();
                if (length < 1 || length > remaining || length > LiveBuildProgress.MAX_CELLS)
                    throw new IOException("Invalid observation count");
                int payloadLength = version == 1 ? length : (length + 3) / 4;
                if (payloadLength > in.available()) throw new IOException("Truncated observations");
                remaining -= length;
                var identity = new LiveBuildProgress.Identity(target, world, dimension, origin, revision, transform);
                byte[] states;
                if (version == 1) states = in.readNBytes(length);
                else {
                    states = new byte[length];
                    for (int start = 0; start < length; start += 4) {
                        int packed = in.readUnsignedByte();
                        int used = Math.min(4, length - start);
                        if ((packed >>> (used * 2)) != 0) throw new IOException("Invalid observation padding");
                        for (int j = 0; j < used; j++) states[start + j] = (byte)((packed >>> (j * 2)) & 3);
                    }
                }
                placements.add(new Placement(tile, phase, follows, identity, states));
            }
            GroupLink group=null;
            if(version>=3&&readBoolean(in)){
                String id=in.readUTF();long revision=in.readLong();int length=in.readInt();
                if(length<2||length>100000||length>in.available())throw new IOException("Invalid saved group size");
                var value=new com.google.gson.JsonObject();value.add("placements",com.google.gson.JsonParser.parseString(new String(in.readNBytes(length),java.nio.charset.StandardCharsets.UTF_8)));
                var metadata=new LiveBuildGroupController.Group(id,"member",revision,new LiveBuildGroupController.Source(source,wide,tall));
                group=new GroupLink(id,revision,LiveBuildSharedPlacement.decode(value,metadata));
            }
            CloudLink cloud=version>=4&&readBoolean(in)?new CloudLink(in.readUTF(),in.readUTF()):null;
            if (in.available() != 0) throw new IOException("Unexpected session data");
            return new Snapshot(SourceKind.values()[kind], source, world, dimension, wide, tall, selected, time, placements,group,cloud);
        } catch (RuntimeException e) {
            throw new IOException("Invalid stored build session", e);
        }
    }

    private static int observationBudget(SourceKind kind) {
        return kind == SourceKind.TWO_LAYER_ZIP ? MAX_BUNDLE_OBSERVATIONS : LiveBuildProgress.MAX_CELLS;
    }

    private static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte();
        if (value > 1) throw new IOException("Invalid boolean");
        return value == 1;
    }
    private static boolean hash(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
