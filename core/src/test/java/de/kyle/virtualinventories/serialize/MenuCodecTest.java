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
}
