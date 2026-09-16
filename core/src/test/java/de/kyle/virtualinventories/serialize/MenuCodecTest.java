package de.kyle.virtualinventories.serialize;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCodecTest {

    private static CompiledForm sampleForm() {
        MenuDefinition.ItemTemplate dynamic = new MenuDefinition.ItemTemplate(
                "DIAMOND", 1, "kills", "Hi %player%", List.of("%balance%", "static"),
                List.of("HIDE_ATTRIBUTES"), 7);
        MenuDefinition.ItemTemplate staticItem =
                MenuDefinition.ItemTemplate.simple("STONE", "Plain");
        MenuDefinition def = new MenuDefinition("shop", 3, "<gold>%player% shop", Map.of(
                10, new MenuDefinition.SlotDefinition(dynamic, "buy"),
                11, new MenuDefinition.SlotDefinition(staticItem, null)));
        return MenuCompiler.compile(def, MenuCompiler.sha256Hex("yaml-source"));
    }

    @Test
    void roundTripPreservesForm() {
        CompiledForm form = sampleForm();
        CompiledForm decoded = MenuCodec.decode(MenuCodec.encode(form));
        assertEquals(form, decoded);
    }

    @Test
    void blobIsGzipped() {
        byte[] blob = MenuCodec.encode(sampleForm());
        assertTrue(blob[0] == (byte) 0x1f && blob[1] == (byte) 0x8b, "expected gzip magic");
    }

    @Test
    void tamperedBlobFails() {
        byte[] blob = MenuCodec.encode(sampleForm());
        blob[blob.length / 2] ^= 0x55;
        assertThrows(MenuCompileException.class, () -> MenuCodec.decode(blob));
    }

    @Test
    void garbageFails() {
        assertThrows(MenuCompileException.class, () -> MenuCodec.decode(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void badMagicFails() throws Exception {
        byte[] blob = MenuCodec.encode(sampleForm());
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(blob))) {
            raw = gzip.readAllBytes();
        }
        raw[0] = 'X';
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(raw);
        }
        assertThrows(MenuCompileException.class, () -> MenuCodec.decode(out.toByteArray()));
    }

    @Test
    void sha256HexIsStable() {
        assertEquals(MenuCompiler.sha256Hex("abc"), MenuCompiler.sha256Hex("abc"));
        assertEquals(64, MenuCompiler.sha256Hex("abc").length());
    }

    @Test
    void anvilRoundTripPreservesTypeAndDeposit() {
        MenuDefinition def = new MenuDefinition("name", 1, "Name it %player%", Map.of(
                0, new MenuDefinition.SlotDefinition(
                        new MenuDefinition.ItemTemplate("AIR", 1, null, null,
                                List.of(), List.of(), null), null),
                2, new MenuDefinition.SlotDefinition(
                        MenuDefinition.ItemTemplate.simple("NAME_TAG", "Confirm"), "confirm_name")),
                "ANVIL", List.of(0));
        CompiledForm form = MenuCompiler.compile(def, MenuCompiler.sha256Hex("yaml"));
        CompiledForm decoded = MenuCodec.decode(MenuCodec.encode(form));
        assertEquals(form, decoded);
        assertEquals("ANVIL", decoded.windowType());
        assertEquals(List.of(0), decoded.depositSlots());
    }

    @Test
    void v1BlobIsRejectedWithRecompileHint() throws Exception {
        byte[] blob = MenuCodec.encode(sampleForm());
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(blob))) {
            raw = gzip.readAllBytes();
        }
        raw[4] = 1; // downgrade version byte to v1
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
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
        MenuCompileException e = assertThrows(MenuCompileException.class,
                () -> MenuCodec.decode(out.toByteArray()));
        assertTrue(e.getMessage().contains("recompile"), "expected recompile hint, got: " + e.getMessage());
    }
}
