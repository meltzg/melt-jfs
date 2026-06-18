package org.meltzg.fs.mtp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Verifies that the bridge keeps its connected-device view fresh: it picks up devices that appear,
 * drops devices that disappear, and reopens devices that were unplugged and replugged.
 */
public class MTPDeviceBridgeFreshnessTest {

    private static final MTPDeviceIdentifier ID =
        new MTPDeviceIdentifier(FakeLibMTP.VENDOR_ID, FakeLibMTP.PRODUCT_ID, FakeLibMTP.SERIAL);

    private FakeLibMTP fake;

    @Before
    public void setUp() throws IOException {
        fake = new FakeLibMTP();
        MTPDeviceBridge.setLibMTP(fake);
        MTPDeviceBridge.INSTANCE.close();
    }

    @After
    public void tearDown() throws IOException {
        MTPDeviceBridge.INSTANCE.close();
        MTPDeviceBridge.setLibMTP(null);
    }

    @Test
    public void dropsDeviceOnUnplugAndReopensOnReconnect() throws IOException {
        var bridge = MTPDeviceBridge.getInstance();
        assertTrue("device should be connected initially", bridge.getDeviceConns().containsKey(ID));

        // Unplugged.
        fake.devicePresent = false;
        bridge.refresh();
        assertTrue("device view should be empty after unplug", bridge.getDeviceConns().isEmpty());

        // Operations fail cleanly (no NPE) while the device is gone.
        var disconnected = assertThrows(IOException.class, () -> bridge.listChildren(ID, "/"));
        assertTrue(disconnected.getMessage().contains("not connected"));

        // Replugged.
        fake.devicePresent = true;
        bridge.refresh();
        assertTrue("device should be reconnected after replug", bridge.getDeviceConns().containsKey(ID));
    }

    @Test
    public void picksUpDeviceConnectedAfterAnEmptyScan() throws IOException {
        fake.devicePresent = false;
        var bridge = MTPDeviceBridge.getInstance();
        assertTrue("no device should be present yet", bridge.getDeviceConns().isEmpty());

        fake.devicePresent = true;
        bridge.refresh();
        assertTrue("newly connected device should be detected", bridge.getDeviceConns().containsKey(ID));
    }
}
