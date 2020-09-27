#include <cstring>
#include <iostream>
#include "jni_helpers.h"

jobject toJMTPDeviceIdentifier(JNIEnv *env, MTPDeviceIdentifier deviceId)
{
    jclass deviceIdentifierClass = env->FindClass(JMTPDEVICEIDENTIFIER);
    char sig[1024];
    sprintf(sig, "(II%s)V", JSTRING);
    jmethodID deviceIdentifierConstr = env->GetMethodID(deviceIdentifierClass, JCONSTRUCTOR, sig);

    jint vendorId = deviceId.getVendorId();
    jint productId = deviceId.getProductId();
    jstring serial = env->NewStringUTF(deviceId.getSerial().c_str());

    jobject jDeviceId = env->NewObject(deviceIdentifierClass, deviceIdentifierConstr, vendorId, productId, serial);
    return jDeviceId;
}

MTPDeviceIdentifier fromJMTPDeviceIdentifier(JNIEnv *env, jobject deviceId)
{
    jclass deviceIdentifierClass = env->FindClass(JMTPDEVICEIDENTIFIER);
    jfieldID vendorField = env->GetFieldID(deviceIdentifierClass, "vendorId", "I");
    jfieldID productField = env->GetFieldID(deviceIdentifierClass, "productId", "I");
    jfieldID serialField = env->GetFieldID(deviceIdentifierClass, "serial", JSTRING);

    jint vendorId = env->GetIntField(deviceId, vendorField);
    jint productId = env->GetIntField(deviceId, productField);
    jstring jSerial = (jstring) env->GetObjectField(deviceId, serialField);
    const char *serial = env->GetStringUTFChars(jSerial, nullptr);
    
    MTPDeviceIdentifier cDeviceId = MTPDeviceIdentifier(vendorId, productId, serial);
    env->ReleaseStringUTFChars(jSerial, serial);

    return cDeviceId;
}

jobject toJMTPDeviceConnection(JNIEnv *env, MTPDeviceConnection deviceConn)
{
    jclass deviceConnectionClass = env->FindClass(JMTPDEVICECONNECTION);
    char sig[1024];
    sprintf(sig, "(%sJJ)V", JMTPDEVICEIDENTIFIER);
    jmethodID deviceConnectionConstr = env->GetMethodID(deviceConnectionClass, JCONSTRUCTOR, sig);

    jobject deviceId = toJMTPDeviceIdentifier(env, deviceConn.getDeviceId());
    jlong rawDevice = (jlong) deviceConn.getRawDevice();
    jlong device = (jlong) deviceConn.getDevice();

    jobject jDeviceConn = env->NewObject(deviceConnectionClass, deviceConnectionConstr, deviceId, rawDevice, device);
    return jDeviceConn;
}

MTPDeviceConnection fromJMTPDeviceConnection(JNIEnv *env, jobject deviceConn)
{
    jclass deviceConnectionClass = env->FindClass(JMTPDEVICECONNECTION);
    jfieldID deviceIdField = env->GetFieldID(deviceConnectionClass, "deviceId", JMTPDEVICEIDENTIFIER);
    jfieldID rawDeviceField = env->GetFieldID(deviceConnectionClass, "rawDeviceConn", "J");
    jfieldID deviceField = env->GetFieldID(deviceConnectionClass, "deviceConn", "J");

    jobject jDeviceId = env->GetObjectField(deviceConn, deviceIdField);
    MTPDeviceIdentifier deviceId = fromJMTPDeviceIdentifier(env, jDeviceId);
    LIBMTP_raw_device_t *rawDevice = (LIBMTP_raw_device_t *) env->GetLongField(deviceConn, rawDeviceField);
    LIBMTP_mtpdevice_t *device = (LIBMTP_mtpdevice_t *) env->GetLongField(deviceConn, deviceField);

    MTPDeviceConnection cDeviceConn = MTPDeviceConnection(deviceId, rawDevice, device);
    
    return cDeviceConn;
}

jobject toJMTPDeviceInfo(JNIEnv *env, MTPDeviceInfo deviceInfo)
{
    jclass deviceInfoClass = env->FindClass(JMTPDEVICEINFO);
    char sig[1024];
    sprintf(sig, "(%s%s%s%sJJ)V", JMTPDEVICEIDENTIFIER, JSTRING, JSTRING, JSTRING);
    jmethodID deviceInfoConstr = env->GetMethodID(deviceInfoClass, JCONSTRUCTOR, sig);

    jobject deviceId = toJMTPDeviceIdentifier(env, deviceInfo.getDeviceId());
    jstring friendlyName = env->NewStringUTF(deviceInfo.getFriendlyName().c_str());
    jstring description = env->NewStringUTF(deviceInfo.getDescription().c_str());
    jstring manufacturer = env->NewStringUTF(deviceInfo.getManufacturer().c_str());

    jlong busLocation = deviceInfo.getBusLocation();
    jlong devNum = deviceInfo.getDevNum();

    jobject jDeviceInfo = env->NewObject(deviceInfoClass, deviceInfoConstr, deviceId, friendlyName, description, manufacturer, busLocation, devNum);
    return jDeviceInfo;
}

jobject toJMTPFileStore(JNIEnv *env, MTPDeviceStorage deviceStorage)
{
    jclass deviceStorageClass = env->FindClass(JMTPDEVICESTORAGE);
    char sig[1024];
    sprintf(sig, "(%sJ)V", JSTRING);
    jmethodID deviceStorageConstr = env->GetMethodID(deviceStorageClass, JCONSTRUCTOR, sig);
    
    jstring name = env->NewStringUTF(deviceStorage.getName().c_str());
    jlong storageId = deviceStorage.getStorageId();

    jobject jFileStore = env->NewObject(deviceStorageClass, deviceStorageConstr, name, storageId);
    return jFileStore;
}

jint throwIOException(JNIEnv *env, const char *message)
{
    jclass ioexception_class = env->FindClass(JIOEXCEPTION);
    return env->ThrowNew(ioexception_class, message);
}
