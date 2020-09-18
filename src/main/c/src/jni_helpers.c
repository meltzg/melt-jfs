#include <stdlib.h>
#include <string.h>
#include "jni_helpers.h"

jobject toJMTPDeviceIdentifier(JNIEnv *env, MTPDeviceIdentifier_t deviceId)
{
    jclass deviceIdentifierClass = (*env)->FindClass(env, JMTPDEVICEIDENTIFIER);
    char sig[1024];
    sprintf(sig, "(II%s)V", JSTRING);
    jmethodID deviceIdentifierConstr = (*env)->GetMethodID(env, deviceIdentifierClass, JCONSTRUCTOR, sig);

    jint vendorId = deviceId.vendorId;
    jint productId = deviceId.productId;
    jstring serial = (*env)->NewStringUTF(env, deviceId.serial);

    jobject jDeviceId = (*env)->NewObject(env, deviceIdentifierClass, deviceIdentifierConstr, vendorId, productId, serial);
    return jDeviceId;
}

MTPDeviceIdentifier_t fromJMTPDeviceIdentifier(JNIEnv *env, jobject deviceId)
{
    MTPDeviceIdentifier_t cDeviceId;

    jclass deviceIdentifierClass = (*env)->FindClass(env, JMTPDEVICEIDENTIFIER);
    jfieldID vendorField = (*env)->GetFieldID(env, deviceIdentifierClass, "vendorId", "I");
    jfieldID productField = (*env)->GetFieldID(env, deviceIdentifierClass, "productId", "I");
    jfieldID serialField = (*env)->GetFieldID(env, deviceIdentifierClass, "serial", JSTRING);

    cDeviceId.vendorId = (*env)->GetIntField(env, deviceId, vendorField);
    cDeviceId.productId = (*env)->GetIntField(env, deviceId, productField);
    
    jstring jSerial = (*env)->GetObjectField(env, deviceId, serialField);
    cDeviceId.serial = fromJString(env, jSerial);

    return cDeviceId;
}

jobject toJMTPDeviceConnection(JNIEnv *env, MTPDeviceConnection_t deviceConn)
{
    jclass deviceConnectionClass = (*env)->FindClass(env, JMTPDEVICECONNECTION);
    char sig[1024];
    sprintf(sig, "(%sJJ)V", JMTPDEVICEIDENTIFIER);
    jmethodID deviceConnectionConstr = (*env)->GetMethodID(env, deviceConnectionClass, JCONSTRUCTOR, sig);

    jobject deviceId = toJMTPDeviceIdentifier(env, deviceConn.deviceId);
    jlong rawDevice = (jlong) deviceConn.rawDevice;
    jlong device = (jlong) deviceConn.device;

    jobject jDeviceConn = (*env)->NewObject(env, deviceConnectionClass, deviceConnectionConstr, deviceId, rawDevice, device);
    return jDeviceConn;
}

MTPDeviceConnection_t fromJMTPDeviceConnection(JNIEnv *env, jobject deviceConn)
{
    MTPDeviceConnection_t cDeviceConn;

    jclass deviceConnectionClass = (*env)->FindClass(env, JMTPDEVICECONNECTION);
    jfieldID deviceIdField = (*env)->GetFieldID(env, deviceConnectionClass, "deviceId", JMTPDEVICEIDENTIFIER);
    jfieldID rawDeviceField = (*env)->GetFieldID(env, deviceConnectionClass, "rawDeviceConn", "J");
    jfieldID deviceField = (*env)->GetFieldID(env, deviceConnectionClass, "deviceConn", "J");

    jobject deviceId = (*env)->GetObjectField(env, deviceConn, deviceIdField);
    cDeviceConn.deviceId = fromJMTPDeviceIdentifier(env, deviceId);
    cDeviceConn.rawDevice = (LIBMTP_raw_device_t *) (*env)->GetLongField(env, deviceConn, rawDeviceField);
    cDeviceConn.device = (LIBMTP_mtpdevice_t *) (*env)->GetLongField(env, deviceConn, deviceField);

    return cDeviceConn;
}

jobject toJMTPDeviceInfo(JNIEnv *env, MTPDeviceInfo_t deviceInfo)
{
    jclass deviceInfoClass = (*env)->FindClass(env, JMTPDEVICEINFO);
    char sig[1024];
    sprintf(sig, "(%s%s%s%sJJ)V", JMTPDEVICEIDENTIFIER, JSTRING, JSTRING, JSTRING);
    jmethodID deviceInfoConstr = (*env)->GetMethodID(env, deviceInfoClass, JCONSTRUCTOR, sig);

    jobject deviceId = toJMTPDeviceIdentifier(env, deviceInfo.deviceId);
    jstring friendlyName = (*env)->NewStringUTF(env, deviceInfo.friendlyName);
    jstring description = (*env)->NewStringUTF(env, deviceInfo.description);
    jstring manufacturer = (*env)->NewStringUTF(env, deviceInfo.manufacturer);

    jlong busLocation = deviceInfo.busLocation;
    jlong devNum = deviceInfo.devNum;

    jobject jDeviceInfo = (*env)->NewObject(env, deviceInfoClass, deviceInfoConstr, deviceId, friendlyName, description, manufacturer, busLocation, devNum);
    return jDeviceInfo;
}

char *fromJString(JNIEnv *env, jstring string)
{
    const char * original = (*env)->GetStringUTFChars(env, string, NULL);
    char *cStr = (char *) malloc(strlen(original) + 1);
    strcpy(cStr, original);
    (*env)->ReleaseStringUTFChars(env, string, original);
    return cStr;
}
