#include <stdlib.h>
#include "mtp_helpers.h"

void freeMTPDeviceIdentifier(MTPDeviceIdentifier_t deviceId)
{
    free(deviceId.serial);
}

void freeMTPDeviceConnection(MTPDeviceConnection_t deviceConn)
{
    LIBMTP_Release_Device(deviceConn.device);
    freeMTPDeviceIdentifier(deviceConn.deviceId);
}

void freeMTPDeviceInfo(MTPDeviceInfo_t deviceInfo)
{
    free(deviceInfo.friendlyName);
    free(deviceInfo.description);
    free(deviceInfo.manufacturer);
    freeMTPDeviceIdentifier(deviceInfo.deviceId);
}

void initMTP()
{
    LIBMTP_Init();
}

void terminateMTP(MTPDeviceConnection_t deviceConns[], int numConns)
{
    if (numConns <= 0)
    {
        return;
    }
    for (int i = 0; i < numConns; i++)
    {
        LIBMTP_Release_Device(deviceConns[i].device);
    }
    free(deviceConns[0].rawDevice);
}
