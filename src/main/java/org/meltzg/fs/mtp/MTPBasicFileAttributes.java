package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPItemInfo;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

class MTPBasicFileAttributes implements BasicFileAttributes {
    private final MTPItemInfo item;

    MTPBasicFileAttributes(MTPItemInfo item) {
        this.item = item;
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.from(item.getModificationDate(), TimeUnit.SECONDS);
    }

    @Override
    public FileTime lastAccessTime() {
        return lastModifiedTime();
    }

    @Override
    public FileTime creationTime() {
        return lastModifiedTime();
    }

    @Override
    public boolean isRegularFile() {
        return item.isFile();
    }

    @Override
    public boolean isDirectory() {
        return !item.isFile();
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return item.getFilesize();
    }

    @Override
    public Object fileKey() {
        return item.getItemId();
    }
}
