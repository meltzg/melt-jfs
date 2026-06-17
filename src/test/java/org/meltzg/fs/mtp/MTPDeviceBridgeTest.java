package org.meltzg.fs.mtp;

import org.junit.*;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;

import static org.junit.Assert.*;

public class MTPDeviceBridgeTest {

    private static final MTPDeviceIdentifier EXPECTED_ID =
        new MTPDeviceIdentifier(FakeLibMTP.VENDOR_ID, FakeLibMTP.PRODUCT_ID, FakeLibMTP.SERIAL);

    @BeforeClass
    public static void injectFake() {
        MTPDeviceBridge.setLibMTP(new FakeLibMTP());
    }

    @AfterClass
    public static void removeFake() throws IOException {
        MTPDeviceBridge.INSTANCE.close();
        MTPDeviceBridge.setLibMTP(null);
    }

    @Before
    public void resetBridge() throws IOException {
        MTPDeviceBridge.INSTANCE.close();
    }

    @Test
    public void testGetConnections() throws IOException {
        try (var bridge = MTPDeviceBridge.getInstance()) {
            assertTrue(bridge.getDeviceConns().size() > 0);
            var deviceInfo = bridge.getDeviceInfo().get(EXPECTED_ID);
            assertNotNull("Device not found", deviceInfo);
            assertEquals(FakeLibMTP.MODEL_NAME, deviceInfo.description());
        }
    }
}
