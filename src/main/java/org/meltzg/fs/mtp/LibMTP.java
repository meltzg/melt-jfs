package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPItemInfo;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;

/**
 * FFM bindings for libmtp. Struct layouts are defined for libmtp 1.1.x on x86-64 Linux.
 * For other platforms, use jextract against the installed libmtp.h to regenerate layouts.
 */
class LibMTP {

    static final int LIBMTP_ERROR_NONE = 0;
    static final int LIBMTP_ERROR_NO_DEVICE_ATTACHED = 5;
    // Both storage_id and parent_id use this sentinel to mean "all storages / root"
    static final int LIBMTP_FILES_AND_FOLDERS_ROOT = 0xFFFFFFFF;
    static final int LIBMTP_FILETYPE_FOLDER = 0;

    // Offset of `storage` in LIBMTP_mtpdevice_t:
    //   uint8_t(1) + pad(7) + void*(8) + void*(8) = 24
    private static final long DEVICE_STORAGE_FIELD_OFFSET = 24L;

    // LIBMTP_device_entry_t layout
    private static final StructLayout DEVICE_ENTRY_LAYOUT = MemoryLayout.structLayout(
        ADDRESS.withName("vendor"),
        JAVA_SHORT.withName("vendor_id"),
        MemoryLayout.paddingLayout(6),
        ADDRESS.withName("product"),
        JAVA_SHORT.withName("product_id"),
        MemoryLayout.paddingLayout(2),
        JAVA_INT.withName("device_flags")
    ).withName("LIBMTP_device_entry_t");

    // LIBMTP_raw_device_t layout
    static final StructLayout RAW_DEVICE_LAYOUT = MemoryLayout.structLayout(
        DEVICE_ENTRY_LAYOUT.withName("device_entry"),
        JAVA_INT.withName("bus_location"),
        JAVA_BYTE.withName("devnum"),
        MemoryLayout.paddingLayout(3)
    ).withName("LIBMTP_raw_device_t");

    // LIBMTP_devicestorage_t layout
    private static final StructLayout DEVICE_STORAGE_LAYOUT = MemoryLayout.structLayout(
        JAVA_INT.withName("id"),
        JAVA_SHORT.withName("StorageType"),
        JAVA_SHORT.withName("FilesystemType"),
        JAVA_SHORT.withName("AccessCapability"),
        MemoryLayout.paddingLayout(6),
        JAVA_LONG.withName("MaxCapacity"),
        JAVA_LONG.withName("FreeSpaceInBytes"),
        JAVA_LONG.withName("FreeSpaceInObjects"),
        ADDRESS.withName("StorageDescription"),
        ADDRESS.withName("VolumeIdentifier"),
        ADDRESS.withName("next"),
        ADDRESS.withName("prev")
    ).withName("LIBMTP_devicestorage_t");

    // LIBMTP_file_t layout
    private static final StructLayout FILE_LAYOUT = MemoryLayout.structLayout(
        JAVA_INT.withName("item_id"),
        JAVA_INT.withName("parent_id"),
        JAVA_INT.withName("storage_id"),
        MemoryLayout.paddingLayout(4),
        ADDRESS.withName("filename"),
        JAVA_LONG.withName("filesize"),
        JAVA_LONG.withName("modificationdate"),
        JAVA_INT.withName("filetype"),
        MemoryLayout.paddingLayout(4),
        ADDRESS.withName("next")
    ).withName("LIBMTP_file_t");

    private static final VarHandle RAW_DEVICE_VENDOR_ID =
        RAW_DEVICE_LAYOUT.varHandle(groupElement("device_entry"), groupElement("vendor_id"));
    private static final VarHandle RAW_DEVICE_PRODUCT_ID =
        RAW_DEVICE_LAYOUT.varHandle(groupElement("device_entry"), groupElement("product_id"));
    private static final VarHandle RAW_DEVICE_BUS_LOCATION =
        RAW_DEVICE_LAYOUT.varHandle(groupElement("bus_location"));
    private static final VarHandle RAW_DEVICE_DEVNUM =
        RAW_DEVICE_LAYOUT.varHandle(groupElement("devnum"));

    private static final VarHandle STORAGE_ID =
        DEVICE_STORAGE_LAYOUT.varHandle(groupElement("id"));
    private static final VarHandle STORAGE_DESCRIPTION =
        DEVICE_STORAGE_LAYOUT.varHandle(groupElement("StorageDescription"));
    private static final VarHandle STORAGE_MAX_CAPACITY =
        DEVICE_STORAGE_LAYOUT.varHandle(groupElement("MaxCapacity"));
    private static final VarHandle STORAGE_FREE_SPACE_BYTES =
        DEVICE_STORAGE_LAYOUT.varHandle(groupElement("FreeSpaceInBytes"));
    private static final VarHandle STORAGE_NEXT =
        DEVICE_STORAGE_LAYOUT.varHandle(groupElement("next"));

    private static final VarHandle FILE_ITEM_ID =
        FILE_LAYOUT.varHandle(groupElement("item_id"));
    private static final VarHandle FILE_PARENT_ID =
        FILE_LAYOUT.varHandle(groupElement("parent_id"));
    private static final VarHandle FILE_STORAGE_ID =
        FILE_LAYOUT.varHandle(groupElement("storage_id"));
    private static final VarHandle FILE_FILENAME =
        FILE_LAYOUT.varHandle(groupElement("filename"));
    private static final VarHandle FILE_FILESIZE =
        FILE_LAYOUT.varHandle(groupElement("filesize"));
    private static final VarHandle FILE_MODIFICATIONDATE =
        FILE_LAYOUT.varHandle(groupElement("modificationdate"));
    private static final VarHandle FILE_FILETYPE =
        FILE_LAYOUT.varHandle(groupElement("filetype"));
    private static final VarHandle FILE_NEXT =
        FILE_LAYOUT.varHandle(groupElement("next"));

    private final MethodHandle init;
    private final MethodHandle releaseDevice;
    private final MethodHandle detectRawDevices;
    private final MethodHandle openRawDeviceUncached;
    private final MethodHandle getSerialNumber;
    private final MethodHandle getFriendlyName;
    private final MethodHandle getModelName;
    private final MethodHandle getManufacturerName;
    private final MethodHandle getFilesAndFolders;
    private final MethodHandle getFileToFile;
    private final MethodHandle destroyFile;
    private final MethodHandle createFolderFn;
    private final MethodHandle deleteObjectFn;
    private final MethodHandle freeFn;

    private static final LibMTP INSTANCE = new LibMTP();

    static LibMTP getInstance() {
        return INSTANCE;
    }

    private LibMTP() {
        var linker = Linker.nativeLinker();
        var libmtp = SymbolLookup.libraryLookup("libmtp.so.9", Arena.global());

        init = bind(linker, libmtp, "LIBMTP_Init",
            FunctionDescriptor.ofVoid());
        releaseDevice = bind(linker, libmtp, "LIBMTP_Release_Device",
            FunctionDescriptor.ofVoid(ADDRESS));
        detectRawDevices = bind(linker, libmtp, "LIBMTP_Detect_Raw_Devices",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        openRawDeviceUncached = bind(linker, libmtp, "LIBMTP_Open_Raw_Device_Uncached",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        getSerialNumber = bind(linker, libmtp, "LIBMTP_Get_Serialnumber",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        getFriendlyName = bind(linker, libmtp, "LIBMTP_Get_Friendlyname",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        getModelName = bind(linker, libmtp, "LIBMTP_Get_Modelname",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        getManufacturerName = bind(linker, libmtp, "LIBMTP_Get_Manufacturername",
            FunctionDescriptor.of(ADDRESS, ADDRESS));
        getFilesAndFolders = bind(linker, libmtp, "LIBMTP_Get_Files_And_Folders",
            FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
        getFileToFile = bind(linker, libmtp, "LIBMTP_Get_File_To_File",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        destroyFile = bind(linker, libmtp, "LIBMTP_destroy_file_t",
            FunctionDescriptor.ofVoid(ADDRESS));
        createFolderFn = bind(linker, libmtp, "LIBMTP_Create_Folder",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
        deleteObjectFn = bind(linker, libmtp, "LIBMTP_Delete_Object",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        freeFn = linker.downcallHandle(
            linker.defaultLookup().find("free").orElseThrow(),
            FunctionDescriptor.ofVoid(ADDRESS));

        try {
            init.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to initialize libmtp", t);
        }
    }

    private static MethodHandle bind(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor desc) {
        return linker.downcallHandle(
            lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name)),
            desc);
    }

    record RawDeviceResult(MemorySegment allocation, int count) {}

    RawDeviceResult detectRawDevices() throws IOException {
        try (var arena = Arena.ofConfined()) {
            var ptrOut = arena.allocate(ADDRESS);
            var countOut = arena.allocate(JAVA_INT);
            int ret = (int) detectRawDevices.invokeExact(ptrOut, countOut);
            if (ret != LIBMTP_ERROR_NONE && ret != LIBMTP_ERROR_NO_DEVICE_ATTACHED) {
                throw new IOException("LIBMTP_Detect_Raw_Devices failed with code " + ret);
            }
            int count = countOut.get(JAVA_INT, 0);
            if (count == 0) {
                return new RawDeviceResult(MemorySegment.NULL, 0);
            }
            MemorySegment allocation = ptrOut.get(ADDRESS, 0)
                .reinterpret(RAW_DEVICE_LAYOUT.byteSize() * count);
            return new RawDeviceResult(allocation, count);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Failed to detect MTP devices", t);
        }
    }

    MemorySegment rawDeviceAt(MemorySegment allocation, int index) {
        return allocation.asSlice(index * RAW_DEVICE_LAYOUT.byteSize(), RAW_DEVICE_LAYOUT.byteSize());
    }

    short getVendorId(MemorySegment rawDevice) {
        return (short) RAW_DEVICE_VENDOR_ID.get(rawDevice, 0L);
    }

    short getProductId(MemorySegment rawDevice) {
        return (short) RAW_DEVICE_PRODUCT_ID.get(rawDevice, 0L);
    }

    int getBusLocation(MemorySegment rawDevice) {
        return (int) RAW_DEVICE_BUS_LOCATION.get(rawDevice, 0L);
    }

    byte getDevNum(MemorySegment rawDevice) {
        return (byte) RAW_DEVICE_DEVNUM.get(rawDevice, 0L);
    }

    MemorySegment openRawDevice(MemorySegment rawDevice) {
        try {
            return (MemorySegment) openRawDeviceUncached.invokeExact(rawDevice);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to open MTP device", t);
        }
    }

    String getSerialNumber(MemorySegment device) {
        return readCString(invokeReturningAddress(getSerialNumber, device));
    }

    String getFriendlyName(MemorySegment device) {
        return readCString(invokeReturningAddress(getFriendlyName, device));
    }

    String getModelName(MemorySegment device) {
        return readCString(invokeReturningAddress(getModelName, device));
    }

    String getManufacturerName(MemorySegment device) {
        return readCString(invokeReturningAddress(getManufacturerName, device));
    }

    record StorageResult(String name, long storageId) {}

    StorageResult findStorage(MemorySegment device, String storageName) {
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            var descPtr = (MemorySegment) STORAGE_DESCRIPTION.get(storage, 0L);
            if (storageName.equals(readCString(descPtr))) {
                long storageId = Integer.toUnsignedLong((int) STORAGE_ID.get(storage, 0L));
                return new StorageResult(storageName, storageId);
            }
            storage = (MemorySegment) STORAGE_NEXT.get(storage, 0L);
        }
        return null;
    }

    long getCapacity(MemorySegment device, long storageId) {
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            if (Integer.toUnsignedLong((int) STORAGE_ID.get(storage, 0L)) == storageId) {
                return (long) STORAGE_MAX_CAPACITY.get(storage, 0L);
            }
            storage = (MemorySegment) STORAGE_NEXT.get(storage, 0L);
        }
        return -1;
    }

    long getFreeSpace(MemorySegment device, long storageId) {
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            if (Integer.toUnsignedLong((int) STORAGE_ID.get(storage, 0L)) == storageId) {
                return (long) STORAGE_FREE_SPACE_BYTES.get(storage, 0L);
            }
            storage = (MemorySegment) STORAGE_NEXT.get(storage, 0L);
        }
        return -1;
    }

    List<StorageResult> listStorages(MemorySegment device) {
        var results = new ArrayList<StorageResult>();
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            var descPtr = (MemorySegment) STORAGE_DESCRIPTION.get(storage, 0L);
            long id = Integer.toUnsignedLong((int) STORAGE_ID.get(storage, 0L));
            results.add(new StorageResult(readCString(descPtr), id));
            storage = (MemorySegment) STORAGE_NEXT.get(storage, 0L);
        }
        return results;
    }

    MTPItemInfo[] getChildItems(MemorySegment device, long storageId, long parentId) throws IOException {
        try {
            var filePtr = (MemorySegment) getFilesAndFolders.invokeExact(
                device, (int) storageId, (int) parentId);

            var items = new ArrayList<MTPItemInfo>();
            while (!MemorySegment.NULL.equals(filePtr)) {
                var file = filePtr.reinterpret(FILE_LAYOUT.byteSize());
                var nextPtr = (MemorySegment) FILE_NEXT.get(file, 0L);
                items.add(new MTPItemInfo(
                    Integer.toUnsignedLong((int) FILE_PARENT_ID.get(file, 0L)),
                    Integer.toUnsignedLong((int) FILE_ITEM_ID.get(file, 0L)),
                    Integer.toUnsignedLong((int) FILE_STORAGE_ID.get(file, 0L)),
                    (int) FILE_FILETYPE.get(file, 0L) != LIBMTP_FILETYPE_FOLDER,
                    (long) FILE_FILESIZE.get(file, 0L),
                    (long) FILE_MODIFICATIONDATE.get(file, 0L),
                    readCString((MemorySegment) FILE_FILENAME.get(file, 0L))
                ));
                destroyFile.invokeExact(filePtr);
                filePtr = nextPtr;
            }
            return items.toArray(new MTPItemInfo[0]);
        } catch (Throwable t) {
            throw new IOException("Failed to list files and folders", t);
        }
    }

    long createFolder(MemorySegment device, String name, long parentId, long storageId) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var nameSeg = arena.allocateUtf8String(name);
            int folderId = (int) createFolderFn.invokeExact(device, nameSeg, (int) parentId, (int) storageId);
            if (folderId == 0) throw new IOException("LIBMTP_Create_Folder failed for: " + name);
            return Integer.toUnsignedLong(folderId);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Failed to create folder: " + name, t);
        }
    }

    void deleteObject(MemorySegment device, long itemId) throws IOException {
        try {
            int ret = (int) deleteObjectFn.invokeExact(device, (int) itemId);
            if (ret != 0) throw new IOException("LIBMTP_Delete_Object failed for id: " + itemId);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Failed to delete object: " + itemId, t);
        }
    }

    byte[] getFileContent(MemorySegment device, long itemId) throws IOException {
        Path tempFile = Files.createTempFile("mtp-", ".tmp");
        try (var arena = Arena.ofConfined()) {
            var pathSeg = arena.allocateUtf8String(tempFile.toString());
            int ret;
            try {
                ret = (int) getFileToFile.invokeExact(
                    device, (int) itemId, pathSeg, MemorySegment.NULL, MemorySegment.NULL);
            } catch (Throwable t) {
                throw new IOException("Failed to retrieve file from device", t);
            }
            if (ret != 0) {
                throw new IOException("LIBMTP_Get_File_To_File failed with code " + ret);
            }
            return Files.readAllBytes(tempFile);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    void releaseDevice(MemorySegment device) {
        try {
            releaseDevice.invokeExact(device);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to release MTP device", t);
        }
    }

    void free(MemorySegment ptr) {
        try {
            freeFn.invokeExact(ptr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to free memory", t);
        }
    }

    private MemorySegment firstStorage(MemorySegment device) {
        return device.reinterpret(DEVICE_STORAGE_FIELD_OFFSET + ADDRESS.byteSize())
            .get(ADDRESS, DEVICE_STORAGE_FIELD_OFFSET);
    }

    private MemorySegment invokeReturningAddress(MethodHandle handle, MemorySegment arg) {
        try {
            return (MemorySegment) handle.invokeExact(arg);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private String readCString(MemorySegment ptr) {
        if (ptr == null || MemorySegment.NULL.equals(ptr)) return "";
        return ptr.reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }
}
