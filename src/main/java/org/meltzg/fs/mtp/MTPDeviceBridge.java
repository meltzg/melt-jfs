package org.meltzg.fs.mtp;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
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

    // Allows tests to inject a fake LibMTP without loading native libmtp
    private static volatile LibMTP libOverride = null;

    private MTPDeviceBridge() {
        this.connectionLock = new ReentrantReadWriteLock();
        this.deviceInfo = new HashMap<>();
        this.deviceConns = new LinkedHashMap<>();
        this.rawDevicesAllocation = MemorySegment.NULL;
    }

    static void setLibMTP(LibMTP impl) {
        libOverride = impl;
    }

    private static LibMTP lib() {
        var o = libOverride;
        return o != null ? o : NativeLibMTP.getInstance();
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

    public MTPFileStore getFileStore(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
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

    /**
     * Resolves a path to its MTPItemInfo.
     * Returns null if path is "/" (device root).
     * For storage-level paths like "/Storage Name", returns a pseudo-item with isFile=false.
     */
    public MTPItemInfo resolveItem(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            synchronized (deviceConns.get(deviceId)) {
                return resolveItemUnsafe(deviceConns.get(deviceId), parts);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /** Lists the children of a path. At "/" returns storages as pseudo-directory items. */
    public MTPItemInfo[] listChildren(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            synchronized (deviceConns.get(deviceId)) {
                return listChildrenUnsafe(deviceConns.get(deviceId), parts);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public void createDirectory(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            if (parts.length < 2) {
                throw new IOException("Cannot create directory at device root or storage level: " + path);
            }
            synchronized (deviceConns.get(deviceId)) {
                var conn = deviceConns.get(deviceId);
                var libMtp = lib();
                var storage = libMtp.findStorage(conn.getDeviceConn(), parts[0]);
                if (storage == null) throw new NoSuchFileException("/" + parts[0]);
                long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
                if (parts.length > 2) {
                    var parentParts = Arrays.copyOf(parts, parts.length - 1);
                    var parentItem = resolveItemUnsafe(conn, parentParts);
                    parentId = parentItem.getItemId();
                }
                libMtp.createFolder(conn.getDeviceConn(), parts[parts.length - 1], parentId, storage.storageId());
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public void delete(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            synchronized (deviceConns.get(deviceId)) {
                var conn = deviceConns.get(deviceId);
                var item = resolveItemUnsafe(conn, parts);
                if (item == null) throw new NoSuchFileException(path);
                lib().deleteObject(conn.getDeviceConn(), item.getItemId());
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public byte[] getFileContent(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            synchronized (deviceConns.get(deviceId)) {
                var conn = deviceConns.get(deviceId);
                var item = resolveItemUnsafe(conn, parts);
                if (item == null) throw new NoSuchFileException(path);
                if (!item.isFile()) throw new IOException(path + " is not a file");
                return getFileContent(conn, item.getItemId());
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
        var libMtp = lib();
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
        var libMtp = lib();
        var result = libMtp.detectRawDevices();
        rawDevicesAllocation = result.allocation();

        var connections = new ArrayList<MTPDeviceConnection>(result.count());
        for (int i = 0; i < result.count(); i++) {
            var rawDevice = libMtp.rawDeviceAt(result.allocation(), i);
            var device = libMtp.openRawDevice(rawDevice);
            if (MemorySegment.NULL.equals(device)) {
                continue;
            }
            var serial = libMtp.getSerialNumber(device);
            var vendorId = Short.toUnsignedInt(libMtp.getVendorId(rawDevice));
            var productId = Short.toUnsignedInt(libMtp.getProductId(rawDevice));
            connections.add(new MTPDeviceConnection(
                new MTPDeviceIdentifier(vendorId, productId, serial), rawDevice, device));
        }
        return connections.toArray(new MTPDeviceConnection[0]);
    }

    private MTPDeviceInfo getDeviceInfo(MTPDeviceConnection conn) {
        var libMtp = lib();
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

    private MTPFileStore getFileStore(MTPDeviceConnection conn, String storageName) throws IOException {
        var result = lib().findStorage(conn.getDeviceConn(), storageName);
        if (result == null) {
            throw new NoSuchFileException("/" + storageName);
        }
        return new MTPFileStore(result.name(), conn.getDeviceId(), result.storageId());
    }

    private long getCapacity(MTPDeviceConnection conn, long storageId) {
        return lib().getCapacity(conn.getDeviceConn(), storageId);
    }

    private long getFreeSpace(MTPDeviceConnection conn, long storageId) {
        return lib().getFreeSpace(conn.getDeviceConn(), storageId);
    }

    /**
     * Resolves path parts to an MTPItemInfo.
     * parts=[] → null (device root)
     * parts=["Storage"] → pseudo-item for the storage root directory
     * parts=["Storage","a","b"] → the actual item at Storage/a/b
     */
    private MTPItemInfo resolveItemUnsafe(MTPDeviceConnection conn, String[] parts) throws IOException {
        if (parts.length == 0) return null;
        var libMtp = lib();
        var storage = libMtp.findStorage(conn.getDeviceConn(), parts[0]);
        if (storage == null) throw new NoSuchFileException("/" + parts[0]);
        if (parts.length == 1) {
            return new MTPItemInfo(0, storage.storageId(), storage.storageId(), false, 0, 0, parts[0]);
        }
        long storageId = storage.storageId();
        long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
        MTPItemInfo found = null;
        for (int i = 1; i < parts.length; i++) {
            var children = libMtp.getChildItems(conn.getDeviceConn(), storageId, parentId);
            final String name = parts[i];
            found = Arrays.stream(children).filter(c -> c.getFilename().equals(name)).findFirst().orElse(null);
            if (found == null) {
                throw new NoSuchFileException("/" + String.join("/", parts));
            }
            parentId = found.getItemId();
        }
        return found;
    }

    private MTPItemInfo[] listChildrenUnsafe(MTPDeviceConnection conn, String[] parts) throws IOException {
        var libMtp = lib();
        if (parts.length == 0) {
            return libMtp.listStorages(conn.getDeviceConn()).stream()
                .map(s -> new MTPItemInfo(0, s.storageId(), s.storageId(), false, 0, 0, s.name()))
                .toArray(MTPItemInfo[]::new);
        }
        var storage = libMtp.findStorage(conn.getDeviceConn(), parts[0]);
        if (storage == null) throw new NoSuchFileException("/" + parts[0]);
        long storageId = storage.storageId();
        long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
        if (parts.length > 1) {
            var dirItem = resolveItemUnsafe(conn, parts);
            if (dirItem.isFile()) throw new NotDirectoryException("/" + String.join("/", parts));
            parentId = dirItem.getItemId();
        }
        return libMtp.getChildItems(conn.getDeviceConn(), storageId, parentId);
    }

    private byte[] getFileContent(MTPDeviceConnection conn, long itemId) throws IOException {
        return lib().getFileContent(conn.getDeviceConn(), itemId);
    }

    static String[] pathParts(String path) {
        return Arrays.stream(path.split("/")).filter(p -> !p.isEmpty()).toArray(String[]::new);
    }
}
