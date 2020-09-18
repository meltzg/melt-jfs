#include <stdlib.h>
#include "mtp_helpers.h"

void freeMTPDeviceIdentifier(MTPDeviceIdentifier_t deviceId)
{
    free(deviceId.serial);
}

void freeMTPDeviceConnection(MTPDeviceConnection_t deviceConn)
{
    LIBMTP_Release_Device(deviceConn.device);
    freeMTPDeviceIdentifier(*(deviceConn.deviceId));
    free(deviceConn.deviceId);
}

void freeMTPDeviceInfo(MTPDeviceInfo_t deviceInfo)
{
    free(deviceInfo.friendlyName);
    free(deviceInfo.description);
    free(deviceInfo.manufacturer);
    freeMTPDeviceIdentifier(*(deviceInfo.deviceId));
    free(deviceInfo.deviceId);
}

void initMTP()
{
    LIBMTP_Init();
}

void terminateMTP(MTPDeviceConnection_t **deviceConns, int numConns)
{
    if (numConns <= 0)
    {
        return;
    }
    for (int i = 0; i < numConns; i++)
    {
        LIBMTP_Release_Device(deviceConns[i]->device);
    }
    free(deviceConns[0]->rawDevice);
}

MTPDeviceConnection_t *getDeviceConnections(int *numDevices)
{
    *numDevices = 0;
    MTPDeviceConnection_t *deviceConns = NULL;

    LIBMTP_raw_device_t *rawDevices = NULL;
    LIBMTP_error_number_t ret = LIBMTP_Detect_Raw_Devices(&rawDevices, numDevices);

    if (ret == LIBMTP_ERROR_NONE && *numDevices > 0)
    {
        deviceConns = (MTPDeviceConnection_t *) malloc(*numDevices * sizeof(MTPDeviceConnection_t));
        for (int i = 0; i < *numDevices; i++)
        {
            LIBMTP_mtpdevice_t *device = LIBMTP_Open_Raw_Device_Uncached(&rawDevices[i]);
            
        }
    }

    return deviceConns;
}
