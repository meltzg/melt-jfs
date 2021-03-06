package org.meltzg.fs.mtp;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

@EqualsAndHashCode(callSuper = true)
@Value
public class MTPFileStore extends FileStore {
    String name;
    MTPDeviceIdentifier deviceId;
    long storageId;

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return null;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public long getTotalSpace() throws IOException {
        return MTPDeviceBridge.getInstance().getCapacity(deviceId, storageId);
    }

    @Override
    public long getUsableSpace() throws IOException {
        return MTPDeviceBridge.getInstance().getFreeSpace(deviceId, storageId);
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        return 0;
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return false;
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        return false;
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return null;
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        return null;
    }
}
