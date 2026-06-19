package org.meltzg.fs.mtp.types;

/**
 * A file or folder on an MTP device. Identifiers are opaque {@link String}s shared across backends
 * (see {@code MtpBackend}): libmtp 32-bit handles rendered as unsigned decimals, or WPD object ids
 * verbatim.
 */
public record MTPItemInfo(
        String parentId,
        String itemId,
        String storageId,
        boolean isFile,
        long filesize,
        long modificationDate,
        String filename) {
}
