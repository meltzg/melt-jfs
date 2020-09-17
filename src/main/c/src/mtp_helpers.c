#include "mtp_helpers.h"

void freeMTPDeviceIdentifier(MTPDeviceIdentifier_t deviceId)
{
    free(deviceId.serial);
}

void freeMTPDeviceConnection(MTPDeviceConnection_t deviceConn)
{
    LIBMTP_Release_Device(deviceConn.device);
}

void initMTP()
{
    LIBMTP_Init();
}
