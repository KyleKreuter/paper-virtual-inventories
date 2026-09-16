package de.kyle.virtualinventories.serialize;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Public, programmatically callable Base64 API for fully serialized item
 * stacks (material, amount, name, lore, enchantments, NBT — everything).
 *
 * <p>Encoding is {@code serializeAsBytes()} + gzip + Base64. Decoding returns
 * a <b>fresh</b> {@link ItemStack} on every call, safe to mutate and hand to
 * players.</p>
 *
 * <p><b>Version binding:</b> the bytes come from the server's item serializer,
 * so a blob is only guaranteed to load on the same server version (and
 * ideally the same server software) that wrote it. Re-export blobs after a
 * server update. Menu blobs ({@code .vmenu.gz}) stay DTO-portable — only
 * {@code ref:} item blobs carry this coupling.</p>
 */
public final class ItemBlobs {

    /** Max raw (gzipped) blob size accepted on decode and encode. */
    public static final int MAX_BLOB_BYTES = 64 * 1024;

    private ItemBlobs() {
    }

    /**
     * Serializes a stack into a Base64 string for {@code items.yml} or code.
     *
     * @param item stack to export, must not be null
     * @return Base64 blob, ready to store
     * @throws IllegalArgumentException if the serialized form exceeds
     *                                  {@link #MAX_BLOB_BYTES}
     */
    public static String encode(ItemStack item) {
        Objects.requireNonNull(item, "item");
        byte[] nms = item.serializeAsBytes();
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
                gzip.write(nms);
            }
            byte[] gzipped = buf.toByteArray();
            if (gzipped.length > MAX_BLOB_BYTES) {
                throw new IllegalArgumentException("Item blob too large (" + gzipped.length
                        + " bytes, max " + MAX_BLOB_BYTES + ")");
            }
            return Base64.getEncoder().encodeToString(gzipped);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode item blob", e);
        }
    }

    /**
     * Deserializes a blob back into a fresh, mutable stack.
     *
     * @param base64 blob previously produced by {@link #encode(ItemStack)}
     * @return new {@link ItemStack} instance on every call
     * @throws IllegalArgumentException if the blob is blank, not Base64, not
     *                                  gzip data, oversized, or holds no item
     */
    public static ItemStack decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalArgumentException("Item blob is blank");
        }
        final byte[] gzipped;
        try {
            gzipped = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Item blob is not valid Base64", e);
        }
        if (gzipped.length > MAX_BLOB_BYTES) {
            throw new IllegalArgumentException("Item blob too large (" + gzipped.length
                    + " bytes, max " + MAX_BLOB_BYTES + ")");
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            byte[] nms = gzip.readNBytes(MAX_BLOB_BYTES + 1);
            if (nms.length > MAX_BLOB_BYTES) {
                throw new IllegalArgumentException("Item blob too large once decompressed"
                        + " (max " + MAX_BLOB_BYTES + " bytes)");
            }
            try {
                return ItemStack.deserializeBytes(nms);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Item blob does not contain an item: " + e.getMessage(), e);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Item blob is not valid gzip data", e);
        }
    }
}
