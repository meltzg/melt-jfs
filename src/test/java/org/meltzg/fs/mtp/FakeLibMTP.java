package org.meltzg.fs.mtp;

import org.meltzg.fs.mtp.types.MTPItemInfo;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * In-memory LibMTP implementation for unit tests. Returns fixed data matching the AK100 II fixture.
 * No native libraries are loaded. Swap in via MTPDeviceBridge.setLibMTP(new FakeLibMTP()).
 */
class FakeLibMTP implements LibMTP {

    static final int VENDOR_ID = 16642;       // 0x4102
    static final int PRODUCT_ID = 4497;       // 0x1191
    static final String SERIAL = "F2000018D562F2A412B4";
    static final String FRIENDLY_NAME = "AK100 II";
    static final String MODEL_NAME = "AK100_II";
    static final String MANUFACTURER = "iriver";
    static final String STORAGE_NAME = "Internal storage";
    static final long STORAGE_ID = 0x00010001L;
    static final long CAPACITY = 512_000_000_000L;   // 512 GB (> 50 GB threshold in MTPFileStoreTest)
    static final long FREE_SPACE = 128_000_000_000L;

    // Heap-backed segments: non-null, non-zero address, no native access required
    private static final MemorySegment FAKE_ALLOCATION = MemorySegment.ofArray(new long[1]);
    private static final MemorySegment FAKE_RAW_DEVICE = MemorySegment.ofArray(new long[1]);
    private static final MemorySegment FAKE_DEVICE = MemorySegment.ofArray(new long[1]);

    @Override
    public RawDeviceResult detectRawDevices() throws IOException {
        return new RawDeviceResult(FAKE_ALLOCATION, 1);
    }

    @Override
    public MemorySegment rawDeviceAt(MemorySegment allocation, int index) {
        return FAKE_RAW_DEVICE;
    }

    @Override
    public short getVendorId(MemorySegment rawDevice) {
        return (short) VENDOR_ID;
    }

    @Override
    public short getProductId(MemorySegment rawDevice) {
        return (short) PRODUCT_ID;
    }

    @Override
    public int getBusLocation(MemorySegment rawDevice) {
        return 1;
    }

    @Override
    public byte getDevNum(MemorySegment rawDevice) {
        return 17;
    }

    @Override
    public MemorySegment openRawDevice(MemorySegment rawDevice) {
        return FAKE_DEVICE;
    }

    @Override
    public String getSerialNumber(MemorySegment device) {
        return SERIAL;
    }

    @Override
    public String getFriendlyName(MemorySegment device) {
        return FRIENDLY_NAME;
    }

    @Override
    public String getModelName(MemorySegment device) {
        return MODEL_NAME;
    }

    @Override
    public String getManufacturerName(MemorySegment device) {
        return MANUFACTURER;
    }

    @Override
    public StorageResult findStorage(MemorySegment device, String storageName) {
        if (STORAGE_NAME.equals(storageName)) {
            return new StorageResult(STORAGE_NAME, STORAGE_ID);
        }
        return null;
    }

    @Override
    public long getCapacity(MemorySegment device, long storageId) {
        return storageId == STORAGE_ID ? CAPACITY : -1;
    }

    @Override
    public long getFreeSpace(MemorySegment device, long storageId) {
        return storageId == STORAGE_ID ? FREE_SPACE : -1;
    }

    @Override
    public List<StorageResult> listStorages(MemorySegment device) {
        return List.of(new StorageResult(STORAGE_NAME, STORAGE_ID));
    }

    @Override
    public MTPItemInfo[] getChildItems(MemorySegment device, long storageId, long parentId) throws IOException {
        return new MTPItemInfo[0];
    }

    @Override
    public long createFolder(MemorySegment device, String name, long parentId, long storageId) throws IOException {
        return 1L;
    }

    @Override
    public void deleteObject(MemorySegment device, long itemId) throws IOException {}

    @Override
    public byte[] getFileContent(MemorySegment device, long itemId) throws IOException {
        return new byte[0];
    }

    @Override
    public void releaseDevice(MemorySegment device) {}

    @Override
    public void free(MemorySegment ptr) {}
}
