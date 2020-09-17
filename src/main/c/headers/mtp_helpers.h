#ifndef MTP_HELPERS_H
#define MTP_HELPERS_H

#include <stdint.h>
#include <libmtp.h>

typedef struct MTPDeviceIdentifier_struct MTPDeviceIdentifier_t;
typedef struct MTPDeviceConnection_struct MTPDeviceConnection_t;

struct MTPDeviceIdentifier_struct {
    uint16_t vendorId;
    uint16_t productId;
    char *serial;
};

struct MTPDeviceConnection_struct {
    MTPDeviceIdentifier_t deviceId;
    LIBMTP_raw_device_t *rawDevice;
    LIBMTP_mtpdevice_t * device;
};

void freeMTPDeviceIdentifier(MTPDeviceIdentifier_t deviceId);
void freeMTPDeviceConnection(MTPDeviceConnection_t deviceConn);

void initMTP();

#endif