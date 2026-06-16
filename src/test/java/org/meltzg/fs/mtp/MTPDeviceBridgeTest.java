package org.meltzg.fs.mtp;

import org.junit.*;
import org.meltzg.fs.mtp.types.MTPDeviceIdentifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class MTPDeviceBridgeTest {
    private static final int EXPECTED_VENDOR_ID = 16642;
    private static final int EXPECTED_PRODUCT_ID = 4497;
    private static final String EXPECTED_SERIAL = "F2000018D562F2A412B4";

    private static final Path FIXTURE_DIR = Path.of("src/test/resources/umockdev");
    private static final Path UMOCKDEV_FILE = FIXTURE_DIR.resolve("ak100ii.umockdev");
    private static final Path IOCTL_FILE = FIXTURE_DIR.resolve("ak100ii.ioctl");

    private static UMockdevTestbed testbed;

    @BeforeClass
    public static void setUpTestbed() throws IOException {
        assumeTrue("libumockdev not available", UMockdevTestbed.isAvailable());
        assumeTrue("Fixtures missing — run record-fixture.sh with device connected",
            Files.exists(UMOCKDEV_FILE) && Files.exists(IOCTL_FILE));

        // Parse the device node from the N: line in the umockdev file (e.g. "N: bus/usb/001/005")
        String devNode = Files.lines(UMOCKDEV_FILE)
            .filter(l -> l.startsWith("N: "))
            .map(l -> "/dev/" + l.substring(3).trim())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No N: line in " + UMOCKDEV_FILE));

        testbed = new UMockdevTestbed();
        testbed.addFromFile(UMOCKDEV_FILE);
        testbed.loadIoctl(devNode, IOCTL_FILE);
    }

    @AfterClass
    public static void tearDownTestbed() {
        if (testbed != null) {
            testbed.close();
            testbed = null;
        }
    }

    @Before
    public void resetBridge() throws IOException {
        // Clear any existing connections so getInstance() re-detects via the testbed
        MTPDeviceBridge.INSTANCE.close();
    }

    @Test
    public void testGetConnections() throws IOException {
        try (var bridge = MTPDeviceBridge.getInstance()) {
            assertTrue(bridge.getDeviceConns().size() > 0);
            var expectedId = new MTPDeviceIdentifier(EXPECTED_VENDOR_ID, EXPECTED_PRODUCT_ID, EXPECTED_SERIAL);
            var deviceInfo = bridge.getDeviceInfo().get(expectedId);
            assertNotNull("Device not found in testbed", deviceInfo);
            assertEquals("AK100_II", deviceInfo.getDescription());
        }
    }
}
