package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPItemInfo;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;

/**
 * FFM bindings for libmtp. Struct layouts are defined for libmtp 1.1.x on x86-64 Linux.
 * For other platforms, use jextract against the installed libmtp.h to regenerate layouts.
 */
class NativeLibMTP implements LibMTP {

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
    private final MethodHandle sendFileFromFile;
    private final MethodHandle destroyFile;
    private final MethodHandle createFolderFn;
    private final MethodHandle moveObjectFn;
    private final MethodHandle setFileNameFn;
    private final MethodHandle deleteObjectFn;
    private final MethodHandle freeFn;

    private static final NativeLibMTP INSTANCE = new NativeLibMTP();

    static NativeLibMTP getInstance() {
        return INSTANCE;
    }

    private NativeLibMTP() {
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
        sendFileFromFile = bind(linker, libmtp, "LIBMTP_Send_File_From_File",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        destroyFile = bind(linker, libmtp, "LIBMTP_destroy_file_t",
            FunctionDescriptor.ofVoid(ADDRESS));
        createFolderFn = bind(linker, libmtp, "LIBMTP_Create_Folder",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
        moveObjectFn = bind(linker, libmtp, "LIBMTP_Move_Object",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
        setFileNameFn = bind(linker, libmtp, "LIBMTP_Set_File_Name",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
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

    @Override
    public RawDeviceResult detectRawDevices() throws IOException {
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

    @Override
    public MemorySegment rawDeviceAt(MemorySegment allocation, int index) {
        return allocation.asSlice(index * RAW_DEVICE_LAYOUT.byteSize(), RAW_DEVICE_LAYOUT.byteSize());
    }

    @Override
    public short getVendorId(MemorySegment rawDevice) {
        return (short) RAW_DEVICE_VENDOR_ID.get(rawDevice);
    }

    @Override
    public short getProductId(MemorySegment rawDevice) {
        return (short) RAW_DEVICE_PRODUCT_ID.get(rawDevice);
    }

    @Override
    public int getBusLocation(MemorySegment rawDevice) {
        return (int) RAW_DEVICE_BUS_LOCATION.get(rawDevice);
    }

    @Override
    public byte getDevNum(MemorySegment rawDevice) {
        return (byte) RAW_DEVICE_DEVNUM.get(rawDevice);
    }

    @Override
    public MemorySegment openRawDevice(MemorySegment rawDevice) {
        try {
            return (MemorySegment) openRawDeviceUncached.invokeExact(rawDevice);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to open MTP device", t);
        }
    }

    @Override
    public String getSerialNumber(MemorySegment device) {
        return readCString(invokeReturningAddress(getSerialNumber, device));
    }

    @Override
    public String getFriendlyName(MemorySegment device) {
        return readCString(invokeReturningAddress(getFriendlyName, device));
    }

    @Override
    public String getModelName(MemorySegment device) {
        return readCString(invokeReturningAddress(getModelName, device));
    }

    @Override
    public String getManufacturerName(MemorySegment device) {
        return readCString(invokeReturningAddress(getManufacturerName, device));
    }

    @Override
    public StorageResult findStorage(MemorySegment device, String storageName) {
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            var descPtr = (MemorySegment) STORAGE_DESCRIPTION.get(storage);
            if (storageName.equals(readCString(descPtr))) {
                long storageId = Integer.toUnsignedLong((int) STORAGE_ID.get(storage));
                return new StorageResult(storageName, storageId);
            }
            storage = (MemorySegment) STORAGE_NEXT.get(storage);
        }
        return null;
    }

    @Override
    public long getCapacity(MemorySegment device, long storageId) {
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            if (Integer.toUnsignedLong((int) STORAGE_ID.get(storage)) == storageId) {
                return (long) STORAGE_MAX_CAPACITY.get(storage);
            }
            storage = (MemorySegment) STORAGE_NEXT.get(storage);
        }
        return -1;
    }

    @Override
    public long getFreeSpace(MemorySegment device, long storageId) {
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            if (Integer.toUnsignedLong((int) STORAGE_ID.get(storage)) == storageId) {
                return (long) STORAGE_FREE_SPACE_BYTES.get(storage);
            }
            storage = (MemorySegment) STORAGE_NEXT.get(storage);
        }
        return -1;
    }

    @Override
    public List<StorageResult> listStorages(MemorySegment device) {
        var results = new ArrayList<StorageResult>();
        var storage = firstStorage(device);
        while (!MemorySegment.NULL.equals(storage)) {
            storage = storage.reinterpret(DEVICE_STORAGE_LAYOUT.byteSize());
            var descPtr = (MemorySegment) STORAGE_DESCRIPTION.get(storage);
            long id = Integer.toUnsignedLong((int) STORAGE_ID.get(storage));
            results.add(new StorageResult(readCString(descPtr), id));
            storage = (MemorySegment) STORAGE_NEXT.get(storage);
        }
        return results;
    }

    @Override
    public MTPItemInfo[] getChildItems(MemorySegment device, long storageId, long parentId) throws IOException {
        try {
            var filePtr = (MemorySegment) getFilesAndFolders.invokeExact(
                device, (int) storageId, (int) parentId);

            var items = new ArrayList<MTPItemInfo>();
            while (!MemorySegment.NULL.equals(filePtr)) {
                var file = filePtr.reinterpret(FILE_LAYOUT.byteSize());
                var nextPtr = (MemorySegment) FILE_NEXT.get(file);
                items.add(new MTPItemInfo(
                    Integer.toUnsignedLong((int) FILE_PARENT_ID.get(file)),
                    Integer.toUnsignedLong((int) FILE_ITEM_ID.get(file)),
                    Integer.toUnsignedLong((int) FILE_STORAGE_ID.get(file)),
                    (int) FILE_FILETYPE.get(file) != LIBMTP_FILETYPE_FOLDER,
                    (long) FILE_FILESIZE.get(file),
                    (long) FILE_MODIFICATIONDATE.get(file),
                    readCString((MemorySegment) FILE_FILENAME.get(file))
                ));
                destroyFile.invokeExact(filePtr);
                filePtr = nextPtr;
            }
            return items.toArray(new MTPItemInfo[0]);
        } catch (Throwable t) {
            throw new IOException("Failed to list files and folders", t);
        }
    }

    @Override
    public long createFolder(MemorySegment device, String name, long parentId, long storageId) throws IOException {
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

    @Override
    public void deleteObject(MemorySegment device, long itemId) throws IOException {
        try {
            int ret = (int) deleteObjectFn.invokeExact(device, (int) itemId);
            if (ret != 0) throw new IOException("LIBMTP_Delete_Object failed for id: " + itemId);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Failed to delete object: " + itemId, t);
        }
    }

    @Override
    public void getFile(MemorySegment device, long itemId, String destPath) throws IOException {
        try (var arena = Arena.ofConfined()) {
            var pathSeg = arena.allocateUtf8String(destPath);
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
        }
    }

    @Override
    public long sendFile(MemorySegment device, String localPath, String filename,
                         long parentId, long storageId, long filesize) throws IOException {
        try (var arena = Arena.ofConfined()) {
            // Arena.allocate zero-fills, so item_id, padding and next are 0/NULL.
            var fileData = arena.allocate(FILE_LAYOUT);
            FILE_PARENT_ID.set(fileData, (int) parentId);
            FILE_STORAGE_ID.set(fileData, (int) storageId);
            FILE_FILENAME.set(fileData, arena.allocateUtf8String(filename));
            FILE_FILESIZE.set(fileData, filesize);
            FILE_FILETYPE.set(fileData, LIBMTP_FILETYPE_UNKNOWN);

            var pathSeg = arena.allocateUtf8String(localPath);
            int ret;
            try {
                ret = (int) sendFileFromFile.invokeExact(
                    device, pathSeg, fileData, MemorySegment.NULL, MemorySegment.NULL);
            } catch (Throwable t) {
                throw new IOException("Failed to send file to device", t);
            }
            if (ret != 0) {
                throw new IOException("LIBMTP_Send_File_From_File failed with code " + ret + " for: " + filename);
            }
            return Integer.toUnsignedLong((int) FILE_ITEM_ID.get(fileData));
        }
    }

    @Override
    public void moveObject(MemorySegment device, long itemId, long storageId, long parentId) throws IOException {
        try {
            int ret = (int) moveObjectFn.invokeExact(device, (int) itemId, (int) storageId, (int) parentId);
            if (ret != 0) throw new IOException("LIBMTP_Move_Object failed with code " + ret + " for id: " + itemId);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("Failed to move object: " + itemId, t);
        }
    }

    @Override
    public void setFileName(MemorySegment device, long itemId, String newName) throws IOException {
        try (var arena = Arena.ofConfined()) {
            // Set_File_Name only reads filedata->item_id; the rest is zero-filled by allocate.
            var fileData = arena.allocate(FILE_LAYOUT);
            FILE_ITEM_ID.set(fileData, (int) itemId);
            FILE_FILETYPE.set(fileData, LIBMTP_FILETYPE_UNKNOWN);
            var nameSeg = arena.allocateUtf8String(newName);
            int ret;
            try {
                ret = (int) setFileNameFn.invokeExact(device, fileData, nameSeg);
            } catch (Throwable t) {
                throw new IOException("Failed to rename object on device", t);
            }
            if (ret != 0) throw new IOException("LIBMTP_Set_File_Name failed with code " + ret + " for id: " + itemId);
        }
    }

    @Override
    public void releaseDevice(MemorySegment device) {
        try {
            releaseDevice.invokeExact(device);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to release MTP device", t);
        }
    }

    @Override
    public void free(MemorySegment ptr) {
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
