package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPItemInfo;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;

interface LibMTP {

    int LIBMTP_ERROR_NONE = 0;
    int LIBMTP_ERROR_NO_DEVICE_ATTACHED = 5;
    int LIBMTP_FILES_AND_FOLDERS_ROOT = 0xFFFFFFFF;
    int LIBMTP_FILETYPE_FOLDER = 0;
    // Neutral "generic file" type; index of LIBMTP_FILETYPE_UNKNOWN in libmtp 1.1.x's enum.
    int LIBMTP_FILETYPE_UNKNOWN = 44;

    record RawDeviceResult(MemorySegment allocation, int count) {}

    record StorageResult(String name, long storageId) {}

    RawDeviceResult detectRawDevices() throws IOException;

    MemorySegment rawDeviceAt(MemorySegment allocation, int index);

    short getVendorId(MemorySegment rawDevice);

    short getProductId(MemorySegment rawDevice);

    int getBusLocation(MemorySegment rawDevice);

    byte getDevNum(MemorySegment rawDevice);

    MemorySegment openRawDevice(MemorySegment rawDevice);

    String getSerialNumber(MemorySegment device);

    String getFriendlyName(MemorySegment device);

    String getModelName(MemorySegment device);

    String getManufacturerName(MemorySegment device);

    StorageResult findStorage(MemorySegment device, String storageName);

    long getCapacity(MemorySegment device, long storageId);

    long getFreeSpace(MemorySegment device, long storageId);

    List<StorageResult> listStorages(MemorySegment device);

    MTPItemInfo[] getChildItems(MemorySegment device, long storageId, long parentId) throws IOException;

    long createFolder(MemorySegment device, String name, long parentId, long storageId) throws IOException;

    void deleteObject(MemorySegment device, long itemId) throws IOException;

    /** Streams the device object {@code itemId} directly to the local file {@code destPath}. */
    void getFile(MemorySegment device, long itemId, String destPath) throws IOException;

    /**
     * Uploads {@code localPath} to the device as a new file named {@code filename} under
     * {@code parentId} on {@code storageId}. Returns the new item's id.
     */
    long sendFile(MemorySegment device, String localPath, String filename,
                  long parentId, long storageId, long filesize) throws IOException;

    /** Relocates an object to a new {@code parentId} on {@code storageId}, keeping its id. */
    void moveObject(MemorySegment device, long itemId, long storageId, long parentId) throws IOException;

    /** Renames an object in place. */
    void setFileName(MemorySegment device, long itemId, String newName) throws IOException;

    void releaseDevice(MemorySegment device);

    void free(MemorySegment ptr);
}
