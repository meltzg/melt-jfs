package org.meltzg.fs.mtp.types;

public record MTPDeviceIdentifier(int vendorId, int productId, String serial) {
    public static final String deviceIdStrPattern = "\\d+:\\d+:\\w+";

    public static MTPDeviceIdentifier fromString(String deviceIdStr) {
        var parts = deviceIdStr.split(":");
        return new MTPDeviceIdentifier(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
    }

    @Override
    public String toString() {
        return String.format("%d:%d:%s", vendorId, productId, serial);
    }
}
