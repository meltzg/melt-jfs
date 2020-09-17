package org.meltzg.fs.mtp.types;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MTPDeviceIdentifierTest {
    @Test
    public void testFromString() {
        var deviceId = new MTPDeviceIdentifier(1, 2, "serial");
        assertEquals(deviceId, MTPDeviceIdentifier.fromString(deviceId.toString()));
    }
}