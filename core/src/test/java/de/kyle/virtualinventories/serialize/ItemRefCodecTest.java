package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Codec v4: reference slots survive the round trip, v3 blobs are rejected. */
class ItemRefCodecTest {

    private static final String YAML = """
            rows: 3
            title: "Refs"
            slots:
              10:
                material: DIAMOND
                name: "Plain"
              12:
                ref: demo_crown
              13:
                ref: arrows
                amount: 32
            """;

    private static CompiledForm sampleForm() {
        return MenuCompiler.compile(
                DefinitionParser.parse("refs", YAML), MenuCompiler.sha256Hex("yaml-source"));
    }

    @Test
    void refRoundTripPreservesIds() {
        CompiledForm decoded = MenuCodec.decode(MenuCodec.encode(sampleForm()));
        assertEquals(sampleForm(), decoded);
        assertEquals("demo_crown", decoded.slots().get(1).itemRef());
        assertEquals(32, decoded.slots().get(2).amount());
    }

    @Test
    void v3BlobIsRejectedWithRecompileHint() throws Exception {
        byte[] v4 = MenuCodec.encode(sampleForm());
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(v4))) {
            raw = gzip.readAllBytes();
        }
        raw[4] = 3; // downgrade version byte, then repair the inner CRC
        CRC32 crc = new CRC32();
        crc.update(raw, 0, raw.length - 4);
        long value = crc.getValue();
        raw[raw.length - 4] = (byte) (value >> 24);
        raw[raw.length - 3] = (byte) (value >> 16);
        raw[raw.length - 2] = (byte) (value >> 8);
        raw[raw.length - 1] = (byte) value;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        MenuCompileException e = assertThrows(MenuCompileException.class,
                () -> MenuCodec.decode(out.toByteArray()));
        assertTrue(e.getMessage().contains("recompile"), e.getMessage());
    }
}
