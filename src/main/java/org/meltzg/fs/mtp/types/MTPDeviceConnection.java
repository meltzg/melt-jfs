package org.meltzg.fs.mtp.types;

import lombok.Value;

@Value
public class MTPDeviceConnection {
    MTPDeviceIdentifier deviceId;
    long rawDeviceConn;
    long deviceConn;
}
