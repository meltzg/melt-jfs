package org.meltzg.fs.mtp;

import org.junit.*;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;
import org.meltzg.fs.mtp.types.MTPDeviceInfo;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Integration tests that run against a physically connected MTP device.
 * These are skipped automatically when no device is present or when libmtp is not installed.
 *
 * Usage:
 *   Connect the device, then: ./gradlew test --tests '*IntegrationTest*'
 */
public class MTPDeviceBridgeIntegrationTest {

    @Before
    public void requireRealDevice() throws IOException {
        System.out.println("Running MTPDeviceBridgeIntegrationTest: " + MTPDeviceBridge.getInstance().getDeviceConns());
        assumeTrue("libmtp not available", isNativeLibAvailable());
        MTPDeviceBridge.INSTANCE.close();
        assumeTrue("No MTP device connected",
            !MTPDeviceBridge.getInstance().getDeviceConns().isEmpty());
    }

    @After
    public void releaseBridge() throws IOException {
        MTPDeviceBridge.INSTANCE.close();
    }

    @Test
    public void detectsAtLeastOneDevice() throws IOException {
        var bridge = MTPDeviceBridge.getInstance();
        assertFalse("Expected at least one connected MTP device",
            bridge.getDeviceConns().isEmpty());
    }

    @Test
    public void deviceInfoIsPopulated() throws IOException {
        var bridge = MTPDeviceBridge.getInstance();
        for (MTPDeviceIdentifier id : bridge.getDeviceConns().keySet()) {
            MTPDeviceInfo info = bridge.getDeviceInfo().get(id);
            assertNotNull("DeviceInfo missing for " + id, info);
            assertFalse("Description should not be empty", info.description().isBlank());
            System.out.printf("Found device: vendor=%d product=%d serial=%s description=%s%n",
                id.vendorId(), id.productId(), id.serial(), info.description());
        }
    }

    private static boolean isNativeLibAvailable() {
        try {
            NativeLibMTP.getInstance();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
