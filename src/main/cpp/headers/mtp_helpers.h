#ifndef MTP_HELPERS_H
#define MTP_HELPERS_H

#include <libmtp.h>
#include <string>
#include <vector>

const uint32_t MTP_ROOT = LIBMTP_FILES_AND_FOLDERS_ROOT;

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

class MTPItemInfo
{
private:
    uint32_t parentId;
    uint32_t itemId;
    uint32_t storageId;
    bool isFile;
    uint64_t filesize;
    std::string filename;

public:
    MTPItemInfo(uint32_t parentId, uint32_t itemId, uint32_t storageId, bool isFile, uint64_t filesize, std::string filename) : parentId(parentId), itemId(itemId), storageId(storageId), isFile(isFile), filesize(filesize), filename(filename) {}

    uint32_t getParentId() { return parentId; }
    uint32_t getItemId() { return itemId; }
    uint32_t getStorageId() { return storageId; }
    bool getIsFile() { return isFile; }
    uint64_t getFilesize() { return filesize; }
    std::string getFilename() { return filename; }
};

void initMTP();
void terminateMTP(std::vector<MTPDeviceConnection> deviceConns);
std::vector<MTPDeviceConnection> getDeviceConnections();
MTPDeviceInfo getDeviceInfo(MTPDeviceConnection deviceConn);
MTPDeviceStorage getDeviceStorage(MTPDeviceConnection deviceConn, const char *storageName);
int64_t getCapacity(MTPDeviceConnection deviceConn, uint32_t storageId);
int64_t getFreeSpace(MTPDeviceConnection deviceConn, uint32_t storageId);
std::vector<MTPItemInfo> getChildItems(MTPDeviceConnection deviceConn, uint32_t parentId);

#endif