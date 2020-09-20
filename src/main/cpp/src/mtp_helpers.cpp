#include <string>
#include "mtp_helpers.h"

using std::string;
using std::vector;

MTPDeviceConnection::~MTPDeviceConnection()
{
    LIBMTP_Release_Device(device);
}

void initMTP()
{
    LIBMTP_Init();
}

void terminateMTP(vector<MTPDeviceConnection> deviceConns, int numConns)
{
    if (numConns <= 0)
    {
        return;
    }
    for (int i = 0; i < numConns; i++)
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
        }
    }

    return deviceConns;
}
