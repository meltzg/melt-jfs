package org.meltzg.fs.mtp.types;

import lombok.Value;

import java.lang.foreign.MemorySegment;

@Value
public class MTPDeviceConnection {
    MTPDeviceIdentifier deviceId;
    MemorySegment rawDeviceConn;
    MemorySegment deviceConn;
}
