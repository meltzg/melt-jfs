package org.meltzg.fs.mtp.types;

public record MTPItemInfo(
        long parentId,
        long itemId,
        long storageId,
        boolean isFile,
        long filesize,
        long modificationDate,
        String filename) {
}
