package org.meltzg.fs.mtp.types;

import java.lang.foreign.MemorySegment;

public record MTPDeviceConnection(MTPDeviceIdentifier deviceId, MemorySegment rawDeviceConn, MemorySegment deviceConn) {
}
