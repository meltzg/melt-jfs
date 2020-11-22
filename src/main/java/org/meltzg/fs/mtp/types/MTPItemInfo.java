package org.meltzg.fs.mtp.types;

import lombok.Value;

@Value
public class MTPItemInfo {
    long parentId;
    long itemId;
    long storageId;
    boolean isFile;
    long filesize;
    String filename;
}
