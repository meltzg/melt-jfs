package org.meltzg.fs.mtp;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.meltzg.fs.mtp.types.MTPDeviceConnection;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;
import org.meltzg.fs.mtp.types.MTPDeviceInfo;

import org.meltzg.fs.mtp.types.MTPItemInfo;

public enum MTPDeviceBridge implements Closeable {
    INSTANCE;

    // How long a device scan is trusted before getInstance() re-detects attached devices.
    private static final long DETECT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);

    private ReentrantReadWriteLock connectionLock;
    private Map<MTPDeviceIdentifier, MTPDeviceInfo> deviceInfo;
    private LinkedHashMap<MTPDeviceIdentifier, MTPDeviceConnection> deviceConns;
    private MemorySegment rawDevicesAllocation;

    // Identity (vendor/product/bus/devnum) of the raw devices seen at the last scan, used to detect
    // hot-plug/unplug/reconnect without reopening still-present devices.
    private Set<RawDeviceSignature> lastSignature = Set.of();
    private volatile boolean devicesDetected = false;
    private volatile long lastDetectNanos = 0L;

    // Allows tests to inject a fake LibMTP without loading native libmtp
    private static volatile LibMTP libOverride = null;

    /** A physical device's USB identity, stable while attached, used to detect topology changes. */
    private record RawDeviceSignature(int vendorId, int productId, int busLocation, int devNum) {}

    private MTPDeviceBridge() {
        this.connectionLock = new ReentrantReadWriteLock();
        this.deviceInfo = new HashMap<>();
        this.deviceConns = new LinkedHashMap<>();
        this.rawDevicesAllocation = MemorySegment.NULL;
    }

    public Map<MTPDeviceIdentifier, MTPDeviceInfo> getDeviceInfo() {
        return deviceInfo;
    }

    public LinkedHashMap<MTPDeviceIdentifier, MTPDeviceConnection> getDeviceConns() {
        return deviceConns;
    }

    static void setLibMTP(LibMTP impl) {
        libOverride = impl;
    }

    private static LibMTP lib() {
        var o = libOverride;
        return o != null ? o : NativeLibMTP.getInstance();
    }

    public static MTPDeviceBridge getInstance() throws IOException {
        INSTANCE.ensureFresh();
        return INSTANCE;
    }

    /**
     * Refreshes the connected-device view, throttled so that the underlying USB scan runs at most
     * once per {@link #DETECT_INTERVAL_NANOS}. Picks up newly attached devices, drops disconnected
     * ones, and reopens devices that were unplugged and replugged.
     */
    public void refresh() throws IOException {
        connectionLock.writeLock().lock();
        try {
            reconcileDevicesUnsafe();
            lastDetectNanos = System.nanoTime();
            devicesDetected = true;
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    private void ensureFresh() throws IOException {
        if (devicesDetected && System.nanoTime() - lastDetectNanos < DETECT_INTERVAL_NANOS) {
            return; // recently scanned; avoid hammering the USB bus on every operation
        }
        refresh();
    }

    /** Returns the live connection for {@code deviceId}, or throws if it is no longer attached. */
    private MTPDeviceConnection requireConnection(MTPDeviceIdentifier deviceId) throws IOException {
        var conn = deviceConns.get(deviceId);
        if (conn == null) {
            throw new IOException("MTP device is not connected: " + deviceId);
        }
        return conn;
    }

    public MTPFileStore getFileStore(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            if (parts.length == 0) {
                throw new IllegalArgumentException("First path part required");
            }
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                return getFileStore(conn, parts[0]);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public long getCapacity(MTPDeviceIdentifier deviceId, long storageId) throws IOException {
        connectionLock.readLock().lock();
        try {
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                return getCapacity(conn, storageId);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public long getFreeSpace(MTPDeviceIdentifier deviceId, long storageId) throws IOException {
        connectionLock.readLock().lock();
        try {
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                return getFreeSpace(conn, storageId);
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
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                return resolveItemUnsafe(conn, parts);
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
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                return listChildrenUnsafe(conn, parts);
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
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                var libMtp = lib();
                var storage = libMtp.findStorage(conn.deviceConn(), parts[0]);
                if (storage == null) throw new NoSuchFileException("/" + parts[0]);
                long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
                if (parts.length > 2) {
                    var parentParts = Arrays.copyOf(parts, parts.length - 1);
                    var parentItem = resolveItemUnsafe(conn, parentParts);
                    parentId = parentItem.itemId();
                }
                libMtp.createFolder(conn.deviceConn(), parts[parts.length - 1], parentId, storage.storageId());
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    public void delete(MTPDeviceIdentifier deviceId, String path) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                var item = resolveItemUnsafe(conn, parts);
                if (item == null) throw new NoSuchFileException(path);
                lib().deleteObject(conn.deviceConn(), item.itemId());
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * Uploads {@code localFile} to {@code path} on the device, replacing any existing file with
     * the same name. The path must be at least {@code /Storage/file} (cannot write at the device
     * root or storage level). Returns the new item's id.
     */
    public long writeFile(MTPDeviceIdentifier deviceId, String path, java.nio.file.Path localFile) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            if (parts.length < 2) {
                throw new IOException("Cannot write a file at device root or storage level: " + path);
            }
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                var libMtp = lib();
                var storage = libMtp.findStorage(conn.deviceConn(), parts[0]);
                if (storage == null) throw new NoSuchFileException("/" + parts[0]);
                long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
                if (parts.length > 2) {
                    var parentParts = Arrays.copyOf(parts, parts.length - 1);
                    var parentItem = resolveItemUnsafe(conn, parentParts);
                    if (parentItem.isFile()) {
                        throw new NotDirectoryException("/" + String.join("/", parentParts));
                    }
                    parentId = parentItem.itemId();
                }
                var name = parts[parts.length - 1];
                var existing = findChildUnsafe(conn, storage.storageId(), parentId, name);
                if (existing != null) {
                    if (!existing.isFile()) {
                        throw new IOException("Target exists and is a directory: " + path);
                    }
                    libMtp.deleteObject(conn.deviceConn(), existing.itemId());
                }
                long size = java.nio.file.Files.size(localFile);
                return libMtp.sendFile(conn.deviceConn(), localFile.toString(),
                    name, parentId, storage.storageId(), size);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /**
     * Moves {@code sourcePath} to {@code targetPath} on the same device, combining a relocation
     * (when the parent folder changes) with a rename (when the filename changes). Both paths must
     * be at least {@code /Storage/name}. When {@code replace} is false and the target exists, a
     * {@link FileAlreadyExistsException} is thrown.
     */
    public void move(MTPDeviceIdentifier deviceId, String sourcePath, String targetPath, boolean replace) throws IOException {
        connectionLock.readLock().lock();
        try {
            var srcParts = pathParts(sourcePath);
            var tgtParts = pathParts(targetPath);
            if (srcParts.length < 2) {
                throw new IOException("Cannot move the device root or a storage: " + sourcePath);
            }
            if (tgtParts.length < 2) {
                throw new IOException("Cannot move to the device root or storage level: " + targetPath);
            }
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                var libMtp = lib();
                var source = resolveItemUnsafe(conn, srcParts);

                var tgtStorage = libMtp.findStorage(conn.deviceConn(), tgtParts[0]);
                if (tgtStorage == null) throw new NoSuchFileException("/" + tgtParts[0]);
                long tgtParentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
                if (tgtParts.length > 2) {
                    var parentParts = Arrays.copyOf(tgtParts, tgtParts.length - 1);
                    var parentItem = resolveItemUnsafe(conn, parentParts);
                    if (parentItem.isFile()) {
                        throw new NotDirectoryException("/" + String.join("/", parentParts));
                    }
                    tgtParentId = parentItem.itemId();
                }

                var tgtName = tgtParts[tgtParts.length - 1];
                var existingTgt = findChildUnsafe(conn, tgtStorage.storageId(), tgtParentId, tgtName);
                if (existingTgt != null) {
                    if (existingTgt.itemId() == source.itemId()) {
                        return; // source and target are the same object
                    }
                    if (!replace) throw new FileAlreadyExistsException(targetPath);
                    if (!existingTgt.isFile() && hasChildrenUnsafe(conn, existingTgt)) {
                        throw new DirectoryNotEmptyException(targetPath);
                    }
                }

                // libmtp reports an inconsistent parent_id for storage-root items, so detect a pure
                // rename by comparing parent paths rather than the reported parent handle.
                var srcParentParts = Arrays.copyOf(srcParts, srcParts.length - 1);
                var tgtParentParts = Arrays.copyOf(tgtParts, tgtParts.length - 1);
                boolean sameDirectory = Arrays.equals(srcParentParts, tgtParentParts);

                try {
                    if (existingTgt != null) {
                        libMtp.deleteObject(conn.deviceConn(), existingTgt.itemId());
                    }
                    if (!sameDirectory) {
                        libMtp.moveObject(conn.deviceConn(), source.itemId(), tgtStorage.storageId(), tgtParentId);
                    }
                    if (!source.filename().equals(tgtName)) {
                        libMtp.setFileName(conn.deviceConn(), source.itemId(), tgtName);
                    }
                } catch (IOException nativeError) {
                    // Many devices do not implement MoveObject/SetObjectName; let the caller emulate.
                    throw new MTPOperationUnsupportedException(
                        "Native move failed for " + sourcePath + " -> " + targetPath, nativeError);
                }
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    /** Streams the file at {@code path} on the device directly into {@code localFile}. */
    public void getFile(MTPDeviceIdentifier deviceId, String path, java.nio.file.Path localFile) throws IOException {
        connectionLock.readLock().lock();
        try {
            var parts = pathParts(path);
            var conn = requireConnection(deviceId);
            synchronized (conn) {
                var item = resolveItemUnsafe(conn, parts);
                if (item == null) throw new NoSuchFileException(path);
                if (!item.isFile()) throw new IOException(path + " is not a file");
                lib().getFile(conn.deviceConn(), item.itemId(), localFile.toString());
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

    /**
     * Detects currently attached raw devices and, only if the set has changed since the last scan,
     * tears down the existing connections and reopens from scratch. When the device set is
     * unchanged the open connections are left intact (reopening would needlessly reclaim the USB
     * interface). Caller must hold the write lock.
     */
    private void reconcileDevicesUnsafe() throws IOException {
        var libMtp = lib();
        var detection = libMtp.detectRawDevices();
        boolean ownsAllocation = false;
        try {
            var signature = signatureOf(detection);
            if (signature.equals(lastSignature) && !deviceConns.isEmpty()) {
                return; // nothing changed; keep the live connections
            }
            closeUnsafe();
            rawDevicesAllocation = detection.allocation();
            ownsAllocation = true;
            openDevicesUnsafe(detection);
            lastSignature = signature;
        } finally {
            if (!ownsAllocation && !MemorySegment.NULL.equals(detection.allocation())) {
                libMtp.free(detection.allocation());
            }
        }
    }

    private Set<RawDeviceSignature> signatureOf(LibMTP.RawDeviceResult detection) {
        var libMtp = lib();
        var signature = new HashSet<RawDeviceSignature>();
        for (int i = 0; i < detection.count(); i++) {
            var rawDevice = libMtp.rawDeviceAt(detection.allocation(), i);
            signature.add(new RawDeviceSignature(
                Short.toUnsignedInt(libMtp.getVendorId(rawDevice)),
                Short.toUnsignedInt(libMtp.getProductId(rawDevice)),
                libMtp.getBusLocation(rawDevice),
                Byte.toUnsignedInt(libMtp.getDevNum(rawDevice))));
        }
        return signature;
    }

    private void openDevicesUnsafe(LibMTP.RawDeviceResult detection) {
        var libMtp = lib();
        for (int i = 0; i < detection.count(); i++) {
            var rawDevice = libMtp.rawDeviceAt(detection.allocation(), i);
            var device = libMtp.openRawDevice(rawDevice);
            if (MemorySegment.NULL.equals(device)) {
                continue;
            }
            var serial = libMtp.getSerialNumber(device);
            var vendorId = Short.toUnsignedInt(libMtp.getVendorId(rawDevice));
            var productId = Short.toUnsignedInt(libMtp.getProductId(rawDevice));
            var conn = new MTPDeviceConnection(
                new MTPDeviceIdentifier(vendorId, productId, serial), rawDevice, device);
            deviceConns.put(conn.deviceId(), conn);
            deviceInfo.put(conn.deviceId(), getDeviceInfo(conn));
        }
    }

    private void closeUnsafe() {
        var libMtp = lib();
        for (var conn : deviceConns.values()) {
            libMtp.releaseDevice(conn.deviceConn());
        }
        if (!MemorySegment.NULL.equals(rawDevicesAllocation)) {
            libMtp.free(rawDevicesAllocation);
            rawDevicesAllocation = MemorySegment.NULL;
        }
        deviceInfo.clear();
        deviceConns.clear();
        lastSignature = Set.of();
        devicesDetected = false;
    }

    private MTPDeviceInfo getDeviceInfo(MTPDeviceConnection conn) {
        var libMtp = lib();
        var device = conn.deviceConn();
        var rawDevice = conn.rawDeviceConn();
        return new MTPDeviceInfo(
            conn.deviceId(),
            libMtp.getFriendlyName(device),
            libMtp.getModelName(device),
            libMtp.getManufacturerName(device),
            Integer.toUnsignedLong(libMtp.getBusLocation(rawDevice)),
            Byte.toUnsignedLong(libMtp.getDevNum(rawDevice))
        );
    }

    private MTPFileStore getFileStore(MTPDeviceConnection conn, String storageName) throws IOException {
        var result = lib().findStorage(conn.deviceConn(), storageName);
        if (result == null) {
            throw new NoSuchFileException("/" + storageName);
        }
        return new MTPFileStore(result.name(), conn.deviceId(), result.storageId());
    }

    private long getCapacity(MTPDeviceConnection conn, long storageId) {
        return lib().getCapacity(conn.deviceConn(), storageId);
    }

    private long getFreeSpace(MTPDeviceConnection conn, long storageId) {
        return lib().getFreeSpace(conn.deviceConn(), storageId);
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
        var storage = libMtp.findStorage(conn.deviceConn(), parts[0]);
        if (storage == null) throw new NoSuchFileException("/" + parts[0]);
        if (parts.length == 1) {
            return new MTPItemInfo(0, storage.storageId(), storage.storageId(), false, 0, 0, parts[0]);
        }
        long storageId = storage.storageId();
        long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
        MTPItemInfo found = null;
        for (int i = 1; i < parts.length; i++) {
            var children = libMtp.getChildItems(conn.deviceConn(), storageId, parentId);
            final String name = parts[i];
            found = Arrays.stream(children).filter(c -> c.filename().equals(name)).findFirst().orElse(null);
            if (found == null) {
                throw new NoSuchFileException("/" + String.join("/", parts));
            }
            parentId = found.itemId();
        }
        return found;
    }

    private MTPItemInfo[] listChildrenUnsafe(MTPDeviceConnection conn, String[] parts) throws IOException {
        var libMtp = lib();
        if (parts.length == 0) {
            return libMtp.listStorages(conn.deviceConn()).stream()
                .map(s -> new MTPItemInfo(0, s.storageId(), s.storageId(), false, 0, 0, s.name()))
                .toArray(MTPItemInfo[]::new);
        }
        var storage = libMtp.findStorage(conn.deviceConn(), parts[0]);
        if (storage == null) throw new NoSuchFileException("/" + parts[0]);
        long storageId = storage.storageId();
        long parentId = LibMTP.LIBMTP_FILES_AND_FOLDERS_ROOT;
        if (parts.length > 1) {
            var dirItem = resolveItemUnsafe(conn, parts);
            if (dirItem.isFile()) throw new NotDirectoryException("/" + String.join("/", parts));
            parentId = dirItem.itemId();
        }
        return libMtp.getChildItems(conn.deviceConn(), storageId, parentId);
    }

    /** Returns true if the given directory item contains any children. */
    private boolean hasChildrenUnsafe(MTPDeviceConnection conn, MTPItemInfo dir) throws IOException {
        return lib().getChildItems(conn.deviceConn(), dir.storageId(), dir.itemId()).length > 0;
    }

    /** Returns the named child of (storageId, parentId), or null if no such child exists. */
    private MTPItemInfo findChildUnsafe(MTPDeviceConnection conn, long storageId, long parentId, String name) throws IOException {
        var children = lib().getChildItems(conn.deviceConn(), storageId, parentId);
        return Arrays.stream(children).filter(c -> c.filename().equals(name)).findFirst().orElse(null);
    }

    static String[] pathParts(String path) {
        return Arrays.stream(path.split("/")).filter(p -> !p.isEmpty()).toArray(String[]::new);
    }
}
