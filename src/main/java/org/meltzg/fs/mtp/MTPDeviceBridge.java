package org.meltzg.fs.mtp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;
import org.meltzg.fs.mtp.types.MTPDeviceInfo;

public enum MTPDeviceBridge {
    INSTANCE;

    private ReentrantReadWriteLock rawDeviceLock;
    private Long rawDevicePtr;
    private Map<MTPDeviceIdentifier, MTPDeviceInfo> deviceInfo;
    private Map<MTPDeviceIdentifier, Long> devicePtrs;

    private MTPDeviceBridge() {
        this.rawDeviceLock = new ReentrantReadWriteLock();
        this.rawDevicePtr = 0L;
        this.deviceInfo = new HashMap<>();
        this.devicePtrs = new HashMap<>();
    }

    public MTPDeviceBridge getInstance() {
        return INSTANCE;
    }

    public void refreshDeviceList() {
        try {
            rawDeviceLock.writeLock().lock();

        } finally {
            rawDeviceLock.writeLock().unlock();
        }
    }

    private static native void initMTP();

    private native long getDeviceConnection(long rawDevicePtr, int vendorId, int productId, String serial) throws IOException;
    
    private native void closeDevice(long rawDevicePtr, long devicePtr);

    static {
        System.loadLibrary("jmtp");
        initMTP();
    }
}