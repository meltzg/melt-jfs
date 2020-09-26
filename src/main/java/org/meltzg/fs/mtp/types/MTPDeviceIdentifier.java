package org.meltzg.fs.mtp.types;

import lombok.Value;

@Value
public class MTPDeviceIdentifier {
    public static final String deviceIdStrPattern = "\\d+:\\d+:\\w+";

    int vendorId;
    int productId;
    String serial;

    public static MTPDeviceIdentifier fromString(String deviceIdStr) {
        var parts = deviceIdStr.split(":");
        var vendorId = Integer.parseInt(parts[0]);
        var productId = Integer.parseInt(parts[1]);
        var serial = parts[2];
        return new MTPDeviceIdentifier(vendorId, productId, serial);
    }

    @Override
    public String toString() {
        return String.format("%d:%d:%s", vendorId, productId, serial);
    }
}
