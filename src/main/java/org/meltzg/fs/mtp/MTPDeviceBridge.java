package org.meltzg.fs.mtp;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.meltzg.fs.mtp.types.MTPDeviceConnection;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;
import org.meltzg.fs.mtp.types.MTPDeviceInfo;

public enum MTPDeviceBridge implements Closeable {
    INSTANCE;

    private ReentrantReadWriteLock connectionLock;
    private Map<MTPDeviceIdentifier, MTPDeviceInfo> deviceInfo;
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
                    INSTANCE.refreshDeviceList();
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

    public void refreshDeviceList() throws IOException {
        try {
            connectionLock.writeLock().lock();
            closeUnsafe();
            for (var conn : getDeviceConnections()) {
                deviceConns.put(conn.getDeviceId(), conn);
                deviceInfo.put(conn.getDeviceId(), getDeviceInfo(conn));
            }
        } finally {
            connectionLock.writeLock().unlock();
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