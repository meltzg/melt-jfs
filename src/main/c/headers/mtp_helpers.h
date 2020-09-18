#ifndef MTP_HELPERS_H
#define MTP_HELPERS_H

#include <stdint.h>
#include <libmtp.h>

typedef struct MTPDeviceIdentifier_struct MTPDeviceIdentifier_t;
typedef struct MTPDeviceConnection_struct MTPDeviceConnection_t;
typedef struct MTPDeviceInfo_struct MTPDeviceInfo_t;

struct MTPDeviceIdentifier_struct {
    uint16_t vendorId;
    uint16_t productId;
    char *serial;
};

struct MTPDeviceConnection_struct {
    MTPDeviceIdentifier_t *deviceId;
    LIBMTP_raw_device_t *rawDevice;
    LIBMTP_mtpdevice_t * device;
};

struct MTPDeviceInfo_struct {
    MTPDeviceIdentifier_t *deviceId;
    char *friendlyName;
    char *description;
    char *manufacturer;

    int64_t busLocation;
    int64_t devNum;
};

void freeMTPDeviceIdentifier(MTPDeviceIdentifier_t deviceId);
void freeMTPDeviceConnection(MTPDeviceConnection_t deviceConn);
void freeMTPDeviceInfo(MTPDeviceInfo_t deviceInfo);

void initMTP();
void terminateMTP(MTPDeviceConnection_t **deviceConns, int numConns);
MTPDeviceConnection_t *getDeviceConnections(int *numDevices);

#endif