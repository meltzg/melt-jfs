package org.meltzg.fs.mtp;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

import org.meltzg.fs.mtp.types.MTPDeviceConnection;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;
import org.meltzg.fs.mtp.types.MTPDeviceInfo;

import lombok.Getter;
import org.meltzg.fs.mtp.types.MTPItemInfo;

public enum MTPDeviceBridge implements Closeable {
    INSTANCE;

    private ReentrantReadWriteLock connectionLock;
    @Getter
    private Map<MTPDeviceIdentifier, MTPDeviceInfo> deviceInfo;
    @Getter
    private LinkedHashMap<MTPDeviceIdentifier, MTPDeviceConnection> deviceConns;

    private MTPDeviceBridge() {
        this.connectionLock = new ReentrantReadWriteLock();
        this.deviceInfo = new HashMap<>();
        this.deviceConns = new LinkedHashMap<>();
    }

    public static MTPDeviceBridge getInstance() throws IOException {
        if (INSTANCE.deviceConns.isEmpty()) {
            try {
                INSTANCE.connectionLock.writeLock().lock();
                if (INSTANCE.deviceConns.isEmpty()) {
                    INSTANCE.refreshDeviceListUnsafe();
                }
            } finally {
                INSTANCE.connectionLock.writeLock().unlock();
            }
        }

        return INSTANCE;
    }

    public MTPFileStore getFileStore(MTPDeviceIdentifier deviceId, String path) {
        connectionLock.readLock().lock();
        try {
            var parts = path.replaceFirst("^/", "").split("/");
            if (parts.length == 0) {
                throw new IllegalArgumentException("First path part required");
            }
            synchronized (deviceConns.get(deviceId)) {
                return getFileStore(deviceConns.get(deviceId), parts[0]);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public long getCapacity(MTPDeviceIdentifier deviceId, long storageId) {
        connectionLock.readLock().lock();
        try {
            synchronized (deviceConns.get(deviceId)) {
                return getCapacity(deviceConns.get(deviceId), storageId);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public long getFreeSpace(MTPDeviceIdentifier deviceId, long storageId) {
        connectionLock.readLock().lock();
        try {
            synchronized (deviceConns.get(deviceId)) {
                return getFreeSpace(deviceConns.get(deviceId), storageId);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public byte[] getFileContent(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = path.replaceFirst("^/", "").split("/");
            synchronized (deviceConns.get(deviceId)) {
                var children = getChildItems(deviceConns.get(deviceId), -1);
                Optional<MTPItemInfo> foundPart = Optional.empty();
                for (var part : parts) {
                    foundPart = Arrays.stream(children).filter(mtpItemInfo -> mtpItemInfo.getFilename().equals(part)).findFirst();
                    if (foundPart.isEmpty()) {
                        break;
                    }
                }
                if (foundPart.isEmpty() || !foundPart.get().getFilename().equals(parts[parts.length - 1])) {
                    throw new FileNotFoundException(String.format("%s not found", path));
                }

                if (!foundPart.get().isFile()) {
                    throw new IOException(String.format("%s is not a file", path));
                }

                return getFileContent(deviceConns.get(deviceId), foundPart.get().getItemId());
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            connectionLock.writeLock().lock();
            closeUnsafe();
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    private void refreshDeviceListUnsafe() throws IOException {
        connectionLock.writeLock().lock();
        closeUnsafe();
        for (var conn : getDeviceConnections()) {
            deviceConns.put(conn.getDeviceId(), conn);
            deviceInfo.put(conn.getDeviceId(), getDeviceInfo(conn));
        }
    }

    private void closeUnsafe() throws IOException {
        terminateMTP(deviceConns.values().toArray(new MTPDeviceConnection[deviceConns.size()]));
        deviceInfo.clear();
        deviceConns.clear();
    }

    private static native void initMTP();

    private native void terminateMTP(MTPDeviceConnection[] deviceConn);

    private native MTPDeviceConnection[] getDeviceConnections() throws IOException;

    private native MTPDeviceInfo getDeviceInfo(MTPDeviceConnection deviceConn);

    private native MTPFileStore getFileStore(MTPDeviceConnection deviceConn, String storageName);

    private native long getCapacity(MTPDeviceConnection deviceConn, long storageId);

    private native long getFreeSpace(MTPDeviceConnection deviceConn, long storageId);

    private native MTPItemInfo[] getChildItems(MTPDeviceConnection deviceConn, long itemId);

    private native byte[] getFileContent(MTPDeviceConnection deviceConn, long itemId);

    static {
        System.loadLibrary("jmtp");
        initMTP();
    }
}