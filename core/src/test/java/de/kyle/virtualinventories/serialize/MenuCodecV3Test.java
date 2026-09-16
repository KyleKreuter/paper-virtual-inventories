package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCodecV3Test {

    private static final String MERCHANT_YAML = """
            type: MERCHANT
            title: "Trader %player%"
            slots:
              0:
                material: AIR
              1:
                material: AIR
              2:
                material: AIR
            deposit: [0, 1]
            output: [2]
            trades:
              - buy_a: EMERALD
                buy_a_count: 5
                result: DIAMOND_SWORD
                max_uses: 3
              - buy_a: COAL
                buy_a_count: 16
                buy_b: IRON_INGOT
                buy_b_count: 2
                result: DIAMOND
                result_count: 2
                max_uses: 12
            """;

    private static CompiledForm merchantForm() {
        return MenuCompiler.compile(
                DefinitionParser.parse("trader", MERCHANT_YAML), MenuCompiler.sha256Hex("yaml"));
    }

    @Test
    void roundTripPreservesV3Features() {
        CompiledForm form = merchantForm();
        CompiledForm decoded = MenuCodec.decode(MenuCodec.encode(form));
        assertEquals(form, decoded);
        assertEquals("MERCHANT", decoded.windowType());
        assertEquals(List.of(0, 1), decoded.depositSlots());
        assertEquals(List.of(2), decoded.outputSlots());
        assertEquals(2, decoded.trades().size());
        assertEquals("EMERALD", decoded.trades().get(0).buyA());
        assertEquals(5, decoded.trades().get(0).buyACount());
        assertEquals("COAL", decoded.trades().get(1).buyA());
        assertEquals("IRON_INGOT", decoded.trades().get(1).buyB());
        assertEquals(2, decoded.trades().get(1).buyBCount());
        assertEquals(2, decoded.trades().get(1).resultCount());
    }

    @Test
    void roundTripPreservesContainerData() {
        String yaml = """
                type: BREWING_STAND
                title: "Brewing"
                slots:
                  0:
                    material: AIR
                deposit: [0]
                data:
                  0: 400
                  1: 20
                """;
        CompiledForm form = MenuCompiler.compile(
                DefinitionParser.parse("brewing", yaml), MenuCompiler.sha256Hex("yaml"));
        CompiledForm decoded = MenuCodec.decode(MenuCodec.encode(form));
        assertEquals(Map.of(0, 400, 1, 20), decoded.containerData());
    }

    @Test
    void v2BlobIsRejectedWithRecompileHint() throws Exception {
        byte[] blob = MenuCodec.encode(merchantForm());
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(blob))) {
            raw = gzip.readAllBytes();
        }
        // Layout: magic(4) version(1) ... body ... crc32(4). Downgrade to v2 + fix CRC.
        raw[4] = 2;
        CRC32 crc = new CRC32();
        crc.update(raw, 0, raw.length - 4);
        long value = crc.getValue();
        raw[raw.length - 4] = (byte) (value >>> 24);
        raw[raw.length - 3] = (byte) (value >>> 16);
        raw[raw.length - 2] = (byte) (value >>> 8);
        raw[raw.length - 1] = (byte) value;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        MenuCompileException failure = assertThrows(
                MenuCompileException.class, () -> MenuCodec.decode(out.toByteArray()));
        assertTrue(failure.getMessage().toLowerCase().contains("ecompile"),
                "expected recompile hint, got: " + failure.getMessage());
    }
}
