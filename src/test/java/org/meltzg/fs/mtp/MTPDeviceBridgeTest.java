package org.meltzg.fs.mtp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

public class MTPDeviceBridgeTest {
    private static final int expectedVendorId = 16642;
    private static final int expectedProductId = 4497;
    private static final String expectedSerial = "F2000018D562F2A412B4";

    @Test
    public void testGetConnections() throws IOException {
        try (var bridge = MTPDeviceBridge.getInstance()) {
            assertTrue(bridge.getDeviceConns().size() > 0);
            var expectedId = new MTPDeviceIdentifier(expectedVendorId, expectedProductId, expectedSerial);
            var deviceInfo = bridge.getDeviceInfo().get(expectedId);
            assertEquals("AK100_II", deviceInfo.getDescription());
        }
    }
}
