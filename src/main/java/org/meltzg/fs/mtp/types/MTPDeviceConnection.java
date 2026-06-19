package org.meltzg.fs.mtp.types;

import org.meltzg.fs.mtp.MtpBackend;

/** A live, opened connection to one device: its stable identity plus an opaque backend handle. */
public record MTPDeviceConnection(MTPDeviceIdentifier deviceId, MtpBackend.DeviceHandle handle) {
}
