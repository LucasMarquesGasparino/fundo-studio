import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Removes MethodParameters emitted by newer javac versions for this old D8. */
public final class StripMethodParameters {
    public static void main(String[] args) throws Exception {
        walk(new File(args[0]));
    }

    private static void walk(File file) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) walk(child);
        } else if (file.getName().endsWith(".class")) {
            byte[] input = read(file);
            byte[] output = strip(input);
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(output);
            stream.close();
        }
    }

    private static byte[] read(File file) throws Exception {
        FileInputStream stream = new FileInputStream(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) >= 0) if (count > 0) out.write(buffer, 0, count);
        stream.close();
        return out.toByteArray();
    }

    private static byte[] strip(byte[] input) throws Exception {
        Reader reader = new Reader(input);
        Writer writer = new Writer();
        writer.bytes(reader.bytes(8));
        int poolCount = reader.u2();
        writer.u2(poolCount);
        String[] names = new String[poolCount];
        for (int i = 1; i < poolCount; i++) {
            int tag = reader.u1();
            writer.u1(tag);
            switch (tag) {
                case 1:
                    int length = reader.u2();
                    writer.u2(length);
                    byte[] text = reader.bytes(length);
                    writer.bytes(text);
                    names[i] = new String(text, StandardCharsets.UTF_8);
                    break;
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18:
                    writer.bytes(reader.bytes(4)); break;
                case 5: case 6:
                    writer.bytes(reader.bytes(8)); i++; break;
                case 7: case 8: case 16: case 19: case 20:
                    writer.bytes(reader.bytes(2)); break;
                case 15:
                    writer.bytes(reader.bytes(3)); break;
                default: throw new IllegalStateException("unknown constant pool tag " + tag);
            }
        }
        writer.bytes(reader.bytes(6));
        copyArray(reader, writer);
        copyMembers(reader, writer, names, false);
        copyMembers(reader, writer, names, true);
        copyAttributes(reader, writer, names, false);
        return writer.data();
    }

    private static void copyArray(Reader r, Writer w) throws Exception {
        int count = r.u2(); w.u2(count);
        for (int i = 0; i < count; i++) w.bytes(r.bytes(2));
    }

    private static void copyMembers(Reader r, Writer w, String[] names, boolean strip) throws Exception {
        int count = r.u2(); w.u2(count);
        for (int i = 0; i < count; i++) {
            w.bytes(r.bytes(6));
            copyAttributes(r, w, names, strip);
        }
    }

    private static void copyAttributes(Reader r, Writer w, String[] names, boolean strip) throws Exception {
        int count = r.u2();
        byte[][] attrs = new byte[count][];
        int[] indexes = new int[count];
        int kept = 0;
        for (int i = 0; i < count; i++) {
            int index = r.u2();
            int length = r.u4();
            byte[] info = r.bytes(length);
            if (!(strip && "MethodParameters".equals(names[index]))) {
                indexes[kept] = index;
                attrs[kept++] = info;
            }
        }
        w.u2(kept);
        for (int i = 0; i < kept; i++) {
            w.u2(indexes[i]); w.u4(attrs[i].length); w.bytes(attrs[i]);
        }
    }

    private static final class Reader {
        final byte[] data; int pos;
        Reader(byte[] data) { this.data = data; }
        int u1() { return data[pos++] & 255; }
        int u2() { return (u1() << 8) | u1(); }
        int u4() { return (u1() << 24) | (u1() << 16) | (u1() << 8) | u1(); }
        byte[] bytes(int count) {
            byte[] result = new byte[count];
            System.arraycopy(data, pos, result, 0, count);
            pos += count;
            return result;
        }
    }

    private static final class Writer {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        void u1(int value) { out.write(value & 255); }
        void u2(int value) { u1(value >>> 8); u1(value); }
        void u4(int value) { u1(value >>> 24); u1(value >>> 16); u1(value >>> 8); u1(value); }
        void bytes(byte[] value) { out.write(value, 0, value.length); }
        byte[] data() { return out.toByteArray(); }
    }
}
