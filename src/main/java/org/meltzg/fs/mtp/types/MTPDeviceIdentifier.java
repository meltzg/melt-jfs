package org.meltzg.fs.mtp.types;

import lombok.Value;

@Value
public class MTPDeviceIdentifier {
    int vendor_id;
    int product_id;
    String serial;

    @Override
    public String toString() {
        return String.format("%d:%d:%s", vendor_id, product_id, serial);
    }
}
