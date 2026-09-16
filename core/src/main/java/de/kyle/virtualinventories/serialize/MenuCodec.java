package de.kyle.virtualinventories.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Binary codec for {@link CompiledForm} blobs ({@code .vmenu.gz}).
 *
 * <pre>
 * gzip( magic "VMNU" | version u8 | sourceSha 32B | menuId utf
 *     | rows u8 | windowType utf | depositCount u8 | depositSlots u8...
 *     | dataCount u8 | (property u16, value i32)...
 *     | outputCount u8 | outputSlots u8...
 *     | buttonCount u8 | (buttonId u16, action utf)...
 *     | tradeCount u8 | trades...
 *     | title segments | slotCount u16 | slots... | crc32 )
 * trade = buyA utf | buyACount u8 | buyB nullableUtf | buyBCount u8
 *       | result utf | resultCount u8 | maxUses u16
 * slot = slot u8 | isRef bool
 *      | ref: refId utf | amount u8
 *      | template: material utf | amount u8 | amountPlaceholder nullableUtf
 *        | name segments | loreCount u16 | lore... | flagCount u8 | flags...
 *        | hasCmd bool | [cmd i32] | action nullableUtf
 * </pre>
 *
 * <p>Older blobs are rejected with a clear recompile hint.</p>
 *
 * <p>Pure logic, no Bukkit dependency (unit-testable).</p>
 */
public final class MenuCodec {

    public static final int FORMAT_VERSION = 4;
    private static final byte[] MAGIC = {'V', 'M', 'N', 'U'};

    private MenuCodec() {
    }

    public static byte[] encode(CompiledForm form) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.write(MAGIC);
            out.writeByte(FORMAT_VERSION);
            out.write(hexToBytes(form.sourceShaHex()));
            out.writeUTF(form.menuId());
            out.writeByte(form.rows());
            out.writeUTF(form.windowType());
            out.writeByte(form.depositSlots().size());
            for (int deposit : form.depositSlots()) {
                out.writeByte(deposit);
            }
            out.writeByte(form.containerData().size());
            for (Map.Entry<Integer, Integer> entry : form.containerData().entrySet()) {
                out.writeShort(entry.getKey());
                out.writeInt(entry.getValue());
            }
            out.writeByte(form.outputSlots().size());
            for (int output : form.outputSlots()) {
                out.writeByte(output);
            }
            out.writeByte(form.buttonActions().size());
            for (Map.Entry<Integer, String> entry : form.buttonActions().entrySet()) {
                out.writeShort(entry.getKey());
                out.writeUTF(entry.getValue());
            }
            out.writeByte(form.trades().size());
            for (CompiledForm.CompiledTrade trade : form.trades()) {
                writeTrade(out, trade);
            }
            writeSegments(out, form.title());
            out.writeShort(form.slots().size());
            for (CompiledForm.CompiledSlot slot : form.slots()) {
                writeSlot(out, slot);
            }
            out.flush();
            byte[] body = raw.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(body);
            out.writeInt((int) crc.getValue());
            out.flush();
            return gzip(raw.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode compiled menu " + form.menuId(), e);
        }
    }

    public static CompiledForm decode(byte[] blob) {
        byte[] raw;
        try {
            raw = gunzip(blob);
        } catch (IOException e) {
            throw new MenuCompileException("Not a compiled menu blob (gzip error): " + e.getMessage());
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'V' || magic[1] != 'M' || magic[2] != 'N' || magic[3] != 'U') {
                throw new MenuCompileException("Not a compiled menu blob (bad magic)");
            }
            int version = in.readUnsignedByte();
            if (version < FORMAT_VERSION) {
                throw new MenuCompileException("Compiled menu was built with format v" + version
                        + " — please recompile: delete compiled/*.vmenu.gz "
                        + "or run /vreload");
            }
            if (version != FORMAT_VERSION) {
                throw new MenuCompileException("Unsupported compiled menu version " + version
                        + " (lib supports " + FORMAT_VERSION + ")");
            }
            byte[] sha = new byte[32];
            in.readFully(sha);
            String menuId = in.readUTF();
            int rows = in.readUnsignedByte();
            String windowType = in.readUTF();
            int depositCount = in.readUnsignedByte();
            List<Integer> depositSlots = new ArrayList<>(depositCount);
            for (int i = 0; i < depositCount; i++) {
                depositSlots.add(in.readUnsignedByte());
            }
            int dataCount = in.readUnsignedByte();
            Map<Integer, Integer> containerData = new LinkedHashMap<>(dataCount);
            for (int i = 0; i < dataCount; i++) {
                containerData.put(in.readUnsignedShort(), in.readInt());
            }
            int outputCount = in.readUnsignedByte();
            List<Integer> outputSlots = new ArrayList<>(outputCount);
            for (int i = 0; i < outputCount; i++) {
                outputSlots.add(in.readUnsignedByte());
            }
            int buttonCount = in.readUnsignedByte();
            Map<Integer, String> buttonActions = new LinkedHashMap<>(buttonCount);
            for (int i = 0; i < buttonCount; i++) {
                buttonActions.put(in.readUnsignedShort(), in.readUTF());
            }
            int tradeCount = in.readUnsignedByte();
            List<CompiledForm.CompiledTrade> trades = new ArrayList<>(tradeCount);
            for (int i = 0; i < tradeCount; i++) {
                trades.add(readTrade(in));
            }
            List<Segment> title = readSegments(in);
            int slotCount = in.readUnsignedShort();
            List<CompiledForm.CompiledSlot> slots = new ArrayList<>(slotCount);
            for (int i = 0; i < slotCount; i++) {
                slots.add(readSlot(in));
            }
            int expectedCrc = in.readInt();
            CRC32 crc = new CRC32();
            crc.update(raw, 0, raw.length - 4);
            if ((int) crc.getValue() != expectedCrc) {
                throw new MenuCompileException("[" + menuId + "] compiled menu blob is corrupt (CRC mismatch)");
            }
          Set<String> keys = new TreeSet<>(Segments.keys(title));
            for (CompiledForm.CompiledSlot slot : slots) {
                keys.addAll(Segments.keys(slot.name()));
                for (List<Segment> line : slot.lore()) {
                    keys.addAll(Segments.keys(line));
                }
                if (slot.amountPlaceholder() != null) {
                    keys.add(slot.amountPlaceholder());
                }
            }
            return new CompiledForm(menuId, rows, windowType, depositSlots, title, slots, keys,
                    bytesToHex(sha), containerData, outputSlots, buttonActions, trades);
        } catch (EOFException e) {
            throw new MenuCompileException("Compiled menu blob is truncated");
        } catch (IOException e) {
            throw new MenuCompileException("Failed to decode compiled menu blob: " + e.getMessage());
        }
    }

    private static void writeTrade(DataOutputStream out, CompiledForm.CompiledTrade trade)
            throws IOException {
        out.writeUTF(trade.buyA());
        out.writeByte(trade.buyACount());
        writeNullableUtf(out, trade.buyB());
        out.writeByte(trade.buyBCount() == null ? 0 : trade.buyBCount());
        out.writeUTF(trade.result());
        out.writeByte(trade.resultCount());
        out.writeShort(trade.maxUses());
    }

    private static CompiledForm.CompiledTrade readTrade(DataInputStream in) throws IOException {
        String buyA = in.readUTF();
        int buyACount = in.readUnsignedByte();
        String buyB = readNullableUtf(in);
        int buyBCountRaw = in.readUnsignedByte();
        String result = in.readUTF();
        int resultCount = in.readUnsignedByte();
        int maxUses = in.readUnsignedShort();
        return new CompiledForm.CompiledTrade(buyA, buyACount, buyB,
                buyB == null ? null : buyBCountRaw, result, resultCount, maxUses);
    }

    private static void writeSlot(DataOutputStream out, CompiledForm.CompiledSlot slot) throws IOException {
        out.writeByte(slot.slot());
        out.writeBoolean(slot.itemRef() != null);
        if (slot.itemRef() != null) {
            out.writeUTF(slot.itemRef());
            out.writeByte(slot.amount());
            writeNullableUtf(out, slot.action());
            return;
        }
        out.writeUTF(slot.material());
        out.writeByte(slot.amount());
        writeNullableUtf(out, slot.amountPlaceholder());
        writeSegments(out, slot.name());
        out.writeShort(slot.lore().size());
        for (List<Segment> line : slot.lore()) {
            writeSegments(out, line);
        }
        out.writeByte(slot.flags().size());
        for (String flag : slot.flags()) {
            out.writeUTF(flag);
        }
        out.writeBoolean(slot.customModelData() != null);
        if (slot.customModelData() != null) {
            out.writeInt(slot.customModelData());
        }
        writeNullableUtf(out, slot.action());
    }

    private static CompiledForm.CompiledSlot readSlot(DataInputStream in) throws IOException {
        int slot = in.readUnsignedByte();
        if (in.readBoolean()) {
            String itemRef = in.readUTF();
            int amount = in.readUnsignedByte();
            String action = readNullableUtf(in);
            return new CompiledForm.CompiledSlot(slot, null, amount, null,
                    List.of(), List.of(), List.of(), null, action, itemRef);
        }
        String material = in.readUTF();
        int amount = in.readUnsignedByte();
        String amountPlaceholder = readNullableUtf(in);
        List<Segment> name = readSegments(in);
        int loreLines = in.readUnsignedShort();
        List<List<Segment>> lore = new ArrayList<>(loreLines);
        for (int i = 0; i < loreLines; i++) {
            lore.add(readSegments(in));
        }
        int flagCount = in.readUnsignedByte();
        List<String> flags = new ArrayList<>(flagCount);
        for (int i = 0; i < flagCount; i++) {
            flags.add(in.readUTF());
        }
        Integer cmd = in.readBoolean() ? in.readInt() : null;
        String action = readNullableUtf(in);
        return new CompiledForm.CompiledSlot(slot, material, amount, amountPlaceholder,
                name, lore, flags, cmd, action, null);
    }

    private static void writeSegments(DataOutputStream out, List<Segment> segments) throws IOException {
        out.writeShort(segments.size());
        for (Segment segment : segments) {
            if (segment instanceof Segment.Placeholder(String key)) {
                out.writeByte(1);
                out.writeUTF(key);
            } else if (segment instanceof Segment.Literal(String text)) {
                out.writeByte(0);
                out.writeUTF(text);
            } else {
                throw new IOException("Unknown segment type " + segment.getClass());
            }
        }
    }

    private static List<Segment> readSegments(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        List<Segment> segments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int kind = in.readUnsignedByte();
            String text = in.readUTF();
            if (kind == 1) {
                segments.add(new Segment.Placeholder(text));
            } else if (kind == 0) {
                segments.add(new Segment.Literal(text));
            } else {
                throw new IOException("Unknown segment kind " + kind);
            }
        }
        return segments;
    }

    private static void writeNullableUtf(DataOutputStream out, String value) throws IOException {
        out.writeBoolean(value != null);
        if (value != null) {
            out.writeUTF(value);
        }
    }

    private static String readNullableUtf(DataInputStream in) throws IOException {
        return in.readBoolean() ? in.readUTF() : null;
    }

    private static byte[] gzip(byte[] raw) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] blob) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(blob))) {
            return gzip.readAllBytes();
        }
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() != 64) {
            throw new IllegalArgumentException("sourceShaHex must be 64 hex chars");
        }
        byte[] out = new byte[32];
        for (int i = 0; i < 32; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

}
