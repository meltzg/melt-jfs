#ifndef MTP_HELPERS_H
#define MTP_HELPERS_H

#include <libmtp.h>
#include <string>
#include <vector>

class MTPDeviceIdentifier
{
private:
    uint16_t vendorId;
    uint16_t productId;
    std::string serial;

public:
    MTPDeviceIdentifier(uint16_t vendorId, uint16_t productId, std::string serial) : vendorId(vendorId), productId(productId), serial(serial) {}

    uint16_t getVendorId() { return vendorId; }
    uint16_t getProductId() { return productId; }
    std::string getSerial() { return serial; }
};

class MTPDeviceConnection
{
private:
    MTPDeviceIdentifier deviceId;
    LIBMTP_raw_device_t *rawDevice;
    LIBMTP_mtpdevice_t *device;

public:
    MTPDeviceConnection(MTPDeviceIdentifier deviceId, LIBMTP_raw_device_t *rawDevice, LIBMTP_mtpdevice_t *device) : deviceId(deviceId), rawDevice(rawDevice), device(device) {}

    MTPDeviceIdentifier getDeviceId() { return deviceId; }
    LIBMTP_raw_device_t *getRawDevice() { return rawDevice; }
    LIBMTP_mtpdevice_t *getDevice() { return device; }
};

class MTPDeviceInfo
{
private:
    MTPDeviceIdentifier deviceId;
    std::string friendlyName;
    std::string description;
    std::string manufacturer;
    int64_t busLocation;
    int64_t devNum;

public:
    MTPDeviceInfo(MTPDeviceIdentifier deviceId, std::string friendlyName, std::string description, std::string manufacturer, int64_t busLocation, int64_t devNum) : deviceId(deviceId), friendlyName(friendlyName), description(description), manufacturer(manufacturer), busLocation(busLocation), devNum(devNum) {}

    MTPDeviceIdentifier getDeviceId() { return deviceId; }
    std::string getFriendlyName() { return friendlyName; }
    std::string getDescription() { return description; }
    std::string getManufacturer() { return manufacturer; }
    int64_t getBusLocation() { return busLocation; }
    int64_t getDevNum() { return devNum; }
};

class MTPDeviceStorage
{
private:
    MTPDeviceIdentifier deviceId;
    std::string name;
    uint32_t storageId;

public:
    MTPDeviceStorage(MTPDeviceIdentifier deviceId, std::string name, uint32_t storageId) : deviceId(deviceId), name(name), storageId(storageId) {}

    MTPDeviceIdentifier getDeviceId() { return deviceId; }
    std::string getName() { return name; }
    uint32_t getStorageId() { return storageId; }
};

void
initMTP();
void terminateMTP(std::vector<MTPDeviceConnection> deviceConns);
std::vector<MTPDeviceConnection> getDeviceConnections();
MTPDeviceInfo getDeviceInfo(MTPDeviceConnection deviceConn);
MTPDeviceStorage getDeviceStorage(MTPDeviceConnection deviceConn, const char *storageName);
int64_t getCapacity(MTPDeviceConnection deviceConn, uint32_t storageId);
int64_t getFreeSpace(MTPDeviceConnection deviceConn, uint32_t storageId);

#endif