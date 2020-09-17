package org.meltzg.fs.mtp.types;

import lombok.Value;

import java.util.HashMap;
import java.util.Map;

import static org.meltzg.fs.mtp.types.MTPDeviceMountProperties.*;

@Value
public class MTPDeviceInfo {
    MTPDeviceIdentifier deviceId;
    String friendlyName;
    String description;
    String manufacturer;
    String serial;

    long busLocation;
    long devNum;

    public Map<String, String> toMap() {
        var map = new HashMap<String, String>();

        map.put(DEVICE_ID.toString(), deviceId.toString());
        map.put(FRIENDLY_NAME.toString(), friendlyName);
        map.put(DESCRIPTION.toString(), description);
        map.put(MANUFACTURER.toString(), manufacturer);
        map.put(SERIAL.toString(), serial);
        map.put(BUS_LOCATION.toString(), Long.toString(busLocation));
        map.put(DEV_NUM.toString(), Long.toString(devNum));

        return map;
    }
}
