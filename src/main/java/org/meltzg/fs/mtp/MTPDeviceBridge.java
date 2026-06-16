package org.meltzg.fs.mtp;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
    private MemorySegment rawDevicesAllocation;

    private MTPDeviceBridge() {
        this.connectionLock = new ReentrantReadWriteLock();
        this.deviceInfo = new HashMap<>();
        this.deviceConns = new LinkedHashMap<>();
        this.rawDevicesAllocation = MemorySegment.NULL;
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

    private void closeUnsafe() {
        var libMtp = LibMTP.getInstance();
        for (var conn : deviceConns.values()) {
            libMtp.releaseDevice(conn.getDeviceConn());
        }
        if (!MemorySegment.NULL.equals(rawDevicesAllocation)) {
            libMtp.free(rawDevicesAllocation);
            rawDevicesAllocation = MemorySegment.NULL;
        }
        deviceInfo.clear();
        deviceConns.clear();
    }

    private MTPDeviceConnection[] getDeviceConnections() throws IOException {
        var libMtp = LibMTP.getInstance();
        var result = libMtp.detectRawDevices();
        rawDevicesAllocation = result.allocation();

        var connections = new MTPDeviceConnection[result.count()];
        for (int i = 0; i < result.count(); i++) {
            var rawDevice = libMtp.rawDeviceAt(result.allocation(), i);
            var device = libMtp.openRawDevice(rawDevice);
            var serial = libMtp.getSerialNumber(device);
            var vendorId = Short.toUnsignedInt(libMtp.getVendorId(rawDevice));
            var productId = Short.toUnsignedInt(libMtp.getProductId(rawDevice));
            connections[i] = new MTPDeviceConnection(
                new MTPDeviceIdentifier(vendorId, productId, serial), rawDevice, device);
        }
        return connections;
    }

    private MTPDeviceInfo getDeviceInfo(MTPDeviceConnection conn) {
        var libMtp = LibMTP.getInstance();
        var device = conn.getDeviceConn();
        var rawDevice = conn.getRawDeviceConn();
        return new MTPDeviceInfo(
            conn.getDeviceId(),
            libMtp.getFriendlyName(device),
            libMtp.getModelName(device),
            libMtp.getManufacturerName(device),
            Integer.toUnsignedLong(libMtp.getBusLocation(rawDevice)),
            Byte.toUnsignedLong(libMtp.getDevNum(rawDevice))
        );
    }

    private MTPFileStore getFileStore(MTPDeviceConnection conn, String storageName) {
        var result = LibMTP.getInstance().findStorage(conn.getDeviceConn(), storageName);
        if (result == null) {
            throw new IllegalArgumentException("Storage not found: " + storageName);
        }
        return new MTPFileStore(result.name(), conn.getDeviceId(), result.storageId());
    }

    private long getCapacity(MTPDeviceConnection conn, long storageId) {
        return LibMTP.getInstance().getCapacity(conn.getDeviceConn(), storageId);
    }

    private long getFreeSpace(MTPDeviceConnection conn, long storageId) {
        return LibMTP.getInstance().getFreeSpace(conn.getDeviceConn(), storageId);
    }

    private MTPItemInfo[] getChildItems(MTPDeviceConnection conn, long itemId) throws IOException {
        return LibMTP.getInstance().getChildItems(conn.getDeviceConn(), itemId);
    }

    private byte[] getFileContent(MTPDeviceConnection conn, long itemId) throws IOException {
        return LibMTP.getInstance().getFileContent(conn.getDeviceConn(), itemId);
    }
}
