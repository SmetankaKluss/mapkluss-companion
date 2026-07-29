package art.mapkluss.companion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Small, bounded NBT reader/writer used only for pinned Two-layer schematics. */
final class SuppressionNbt {
    private static final int COMPOUND = 10;
    private static final int MAX_UNCOMPRESSED_BYTES = 64 * 1024 * 1024;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_TAGS = 1_000_000;
    private static final int MAX_LIST_LENGTH = 1_000_000;

    private SuppressionNbt() { }

    static Document readCompressed(byte[] compressed) throws IOException {
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            raw = gzip.readNBytes(MAX_UNCOMPRESSED_BYTES + 1);
        }
        if (raw.length > MAX_UNCOMPRESSED_BYTES) throw new IOException("Two-layer Litematic expands beyond the safe limit");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
            Parser parser = new Parser(input);
            int type = input.readUnsignedByte();
            if (type != COMPOUND) throw new IOException("Two-layer Litematic root must be a compound");
            String name = input.readUTF();
            Tag root = parser.readPayload(type, 0);
            if (input.available() != 0) throw new IOException("Trailing bytes in Two-layer Litematic");
            return new Document(name, root);
        }
    }

    static byte[] writeCompressed(Document document) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes); DataOutputStream output = new DataOutputStream(gzip)) {
            output.writeByte(COMPOUND);
            output.writeUTF(document.name());
            writePayload(output, document.root(), 0);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Tag> compound(Tag tag, String label) throws IOException {
        if (tag == null || tag.type() != COMPOUND) throw new IOException(label + " must be a compound");
        return (Map<String, Tag>) tag.value();
    }

    static int intValue(Tag tag, String label) throws IOException {
        if (tag == null || tag.type() != 3) throw new IOException(label + " must be an int");
        return (Integer) tag.value();
    }

    static String stringValue(Tag tag, String label) throws IOException {
        if (tag == null || tag.type() != 8) throw new IOException(label + " must be a string");
        return (String) tag.value();
    }

    static long[] longArray(Tag tag, String label) throws IOException {
        if (tag == null || tag.type() != 12) throw new IOException(label + " must be a long array");
        return (long[]) tag.value();
    }

    static NbtList list(Tag tag, int elementType, String label) throws IOException {
        if (tag == null || tag.type() != 9) throw new IOException(label + " must be a list");
        NbtList list = (NbtList) tag.value();
        if (list.elementType() != elementType) throw new IOException(label + " has an unexpected element type");
        return list;
    }

    static Tag stringTag(String value) {
        return new Tag(8, value);
    }

    static Tag intTag(int value) {
        return new Tag(3, value);
    }

    static Tag longArrayTag(long[] value) {
        return new Tag(12, value);
    }

    static Tag compoundTag(Map<String, Tag> value) {
        return new Tag(10, new LinkedHashMap<>(value));
    }

    static Tag listTag(int elementType, List<Tag> values) {
        return new Tag(9, new NbtList(elementType, values));
    }

    private static void writePayload(DataOutputStream output, Tag tag, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting exceeds the safe limit");
        switch (tag.type()) {
            case 1 -> output.writeByte((Byte) tag.value());
            case 2 -> output.writeShort((Short) tag.value());
            case 3 -> output.writeInt((Integer) tag.value());
            case 4 -> output.writeLong((Long) tag.value());
            case 5 -> output.writeFloat((Float) tag.value());
            case 6 -> output.writeDouble((Double) tag.value());
            case 7 -> {
                byte[] values = (byte[]) tag.value();
                output.writeInt(values.length);
                output.write(values);
            }
            case 8 -> output.writeUTF((String) tag.value());
            case 9 -> {
                NbtList list = (NbtList) tag.value();
                output.writeByte(list.elementType());
                output.writeInt(list.values().size());
                for (Tag value : list.values()) writePayload(output, value, depth + 1);
            }
            case 10 -> {
                @SuppressWarnings("unchecked")
                Map<String, Tag> compound = (Map<String, Tag>) tag.value();
                for (Map.Entry<String, Tag> entry : compound.entrySet()) {
                    output.writeByte(entry.getValue().type());
                    output.writeUTF(entry.getKey());
                    writePayload(output, entry.getValue(), depth + 1);
                }
                output.writeByte(0);
            }
            case 11 -> {
                int[] values = (int[]) tag.value();
                output.writeInt(values.length);
                for (int value : values) output.writeInt(value);
            }
            case 12 -> {
                long[] values = (long[]) tag.value();
                output.writeInt(values.length);
                for (long value : values) output.writeLong(value);
            }
            default -> throw new IOException("Unsupported NBT tag " + tag.type());
        }
    }

    record Document(String name, Tag root) { }

    record Tag(int type, Object value) { }

    record NbtList(int elementType, List<Tag> values) {
        NbtList {
            values = List.copyOf(values);
        }
    }

    private static final class Parser {
        private final DataInputStream input;
        private int tags;

        private Parser(DataInputStream input) {
            this.input = input;
        }

        private Tag readPayload(int type, int depth) throws IOException {
            if (depth > MAX_DEPTH) throw new IOException("NBT nesting exceeds the safe limit");
            if (++tags > MAX_TAGS) throw new IOException("NBT tag count exceeds the safe limit");
            return switch (type) {
                case 1 -> new Tag(type, input.readByte());
                case 2 -> new Tag(type, input.readShort());
                case 3 -> new Tag(type, input.readInt());
                case 4 -> new Tag(type, input.readLong());
                case 5 -> new Tag(type, input.readFloat());
                case 6 -> new Tag(type, input.readDouble());
                case 7 -> {
                    int length = safeLength(input.readInt(), 1, "byte array");
                    yield new Tag(type, input.readNBytes(length));
                }
                case 8 -> new Tag(type, input.readUTF());
                case 9 -> {
                    int elementType = input.readUnsignedByte();
                    int length = safeLength(input.readInt(), 1, "list");
                    if (elementType == 0 && length != 0) throw new IOException("Non-empty NBT list uses END elements");
                    if (length > MAX_LIST_LENGTH || (long) tags + length > MAX_TAGS) {
                        throw new IOException("NBT list exceeds the safe tag limit");
                    }
                    List<Tag> values = new ArrayList<>(length);
                    for (int index = 0; index < length; index++) values.add(readPayload(elementType, depth + 1));
                    yield new Tag(type, new NbtList(elementType, values));
                }
                case 10 -> {
                    Map<String, Tag> compound = new LinkedHashMap<>();
                    while (true) {
                        int childType = input.readUnsignedByte();
                        if (childType == 0) break;
                        String name = input.readUTF();
                        if (compound.putIfAbsent(name, readPayload(childType, depth + 1)) != null) {
                            throw new IOException("Duplicate NBT key " + name);
                        }
                    }
                    yield new Tag(type, compound);
                }
                case 11 -> {
                    int length = safeLength(input.readInt(), 4, "int array");
                    int[] values = new int[length];
                    for (int index = 0; index < length; index++) values[index] = input.readInt();
                    yield new Tag(type, values);
                }
                case 12 -> {
                    int length = safeLength(input.readInt(), 8, "long array");
                    long[] values = new long[length];
                    for (int index = 0; index < length; index++) values[index] = input.readLong();
                    yield new Tag(type, values);
                }
                default -> throw new IOException("Unsupported NBT tag " + type);
            };
        }

        private int safeLength(int length, int bytesPerElement, String label) throws IOException {
            if (length < 0 || (long) length * bytesPerElement > input.available()) {
                throw new IOException("Invalid NBT " + label + " length");
            }
            return length;
        }
    }
}
