package org.meltzg.fs.mtp.types;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum MTPDeviceMountProperties {
    DEVICE_ID("deviceId"),
    FRIENDLY_NAME("friendlyName"),
    DESCRIPTION("description"),
    MANUFACTURER("manufacturer"),
    DEV_NUM("devNum"),
    BUS_LOCATION("busLocation");

    private final String value;
}