package org.meltzg.fs.mtp;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.meltzg.fs.mtp.types.MTPDeviceConnection;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;
import org.meltzg.fs.mtp.types.MTPDeviceInfo;

import lombok.Getter;

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

    static {
        System.loadLibrary("jmtp");
        initMTP();
    }
}