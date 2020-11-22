#include <string>
#include <cstring>
#include "mtp_helpers.h"

using std::string;
using std::vector;

void initMTP()
{
    LIBMTP_Init();
}

void terminateMTP(vector<MTPDeviceConnection> deviceConns)
{
    if (deviceConns.empty())
    {
        return;
    }
    for (int i = 0; i < deviceConns.size(); i++)
    {
        LIBMTP_Release_Device(deviceConns[i].getDevice());
    }
    free(deviceConns[0].getRawDevice());
}

vector<MTPDeviceConnection> getDeviceConnections()
{
    vector<MTPDeviceConnection> deviceConns;
    int numDevices;

    LIBMTP_raw_device_t *rawDevices = NULL;
    LIBMTP_error_number_t ret = LIBMTP_Detect_Raw_Devices(&rawDevices, &numDevices);

    if (ret == LIBMTP_ERROR_NONE && numDevices > 0)
    {
        for (int i = 0; i < numDevices; i++)
        {
            LIBMTP_mtpdevice_t *device = LIBMTP_Open_Raw_Device_Uncached(&rawDevices[i]);

            char *serial = LIBMTP_Get_Serialnumber(device);

            MTPDeviceIdentifier deviceId = MTPDeviceIdentifier(rawDevices[i].device_entry.vendor_id, rawDevices[i].device_entry.product_id, serial);
            MTPDeviceConnection deviceConn = MTPDeviceConnection(deviceId, &rawDevices[i], device);
            deviceConns.push_back(deviceConn);
            free(serial);
        }
    }

    return deviceConns;
}

MTPDeviceInfo getDeviceInfo(MTPDeviceConnection deviceConn)
{
    LIBMTP_mtpdevice_t *device = deviceConn.getDevice();

    char *friendly = LIBMTP_Get_Friendlyname(deviceConn.getDevice());
    char *description = LIBMTP_Get_Modelname(deviceConn.getDevice());
    char *manufacturer = LIBMTP_Get_Manufacturername(deviceConn.getDevice());

    string safeFriend = friendly == nullptr ? "" : friendly;
    string safeDesc = description == nullptr ? "" : description;
    string safeManu = manufacturer == nullptr ? "" : manufacturer;

    MTPDeviceInfo deviceInfo = MTPDeviceInfo(deviceConn.getDeviceId(), safeFriend, safeDesc, safeManu, deviceConn.getRawDevice()->bus_location, deviceConn.getRawDevice()->devnum);
    free(friendly);
    free(description);
    free(manufacturer);

    return deviceInfo;
}

MTPDeviceStorage getDeviceStorage(MTPDeviceConnection deviceConn, const char *storageName)
{
    for (LIBMTP_devicestorage_t *storage = deviceConn.getDevice()->storage; storage != 0; storage = storage->next)
    {
        if (strcmp(storage->StorageDescription, storageName) == 0)
        {
            return MTPDeviceStorage(deviceConn.getDeviceId(), storageName, storage->id);
        }
    }
    return MTPDeviceStorage(deviceConn.getDeviceId(), "", 0);
}

int64_t getCapacity(MTPDeviceConnection deviceConn, uint32_t storageId)
{
    for (LIBMTP_devicestorage_t *storage = deviceConn.getDevice()->storage; storage != 0; storage = storage->next)
    {
        if (storage->id == storageId)
        {
            return storage->MaxCapacity;
        }
    }
    return -1;
}

int64_t getFreeSpace(MTPDeviceConnection deviceConn, uint32_t storageId)
{
    for (LIBMTP_devicestorage_t *storage = deviceConn.getDevice()->storage; storage != 0; storage = storage->next)
    {
        if (storage->id == storageId)
        {
            return storage->FreeSpaceInBytes;
        }
    }
    return -1;
}

vector<MTPItemInfo> getChildItems(MTPDeviceConnection deviceConn, uint32_t parentId)
{

}
