package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Objects;

public class MTPFileStore extends FileStore {
    private final String name;
    private final MTPDeviceIdentifier deviceId;
    private final long storageId;

    public MTPFileStore(String name, MTPDeviceIdentifier deviceId, long storageId) {
        this.name = name;
        this.deviceId = deviceId;
        this.storageId = storageId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "mtp";
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
        return getUsableSpace();
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
        return switch (attribute) {
            case "totalSpace" -> getTotalSpace();
            case "usableSpace" -> getUsableSpace();
            case "unallocatedSpace" -> getUnallocatedSpace();
            default -> throw new UnsupportedOperationException("Attribute not supported: " + attribute);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MTPFileStore other)) return false;
        if (!super.equals(o)) return false;
        return storageId == other.storageId
            && Objects.equals(name, other.name)
            && Objects.equals(deviceId, other.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, deviceId, storageId);
    }
}
