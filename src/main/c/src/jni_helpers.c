#include "jni_helpers.h"

jobject getNewArrayList(JNIEnv *env)
{
    jclass arrayListClass = (*env)->FindClass(env, JARRLIST);
    jmethodID arrayListConstructor = (*env)->GetMethodID(env, arrayListClass, "<init>", "()V");
    return (*env)->NewObject(env, arrayListClass, arrayListConstructor);
}

void arrayListAdd(JNIEnv *env, jobject list, jobject element)
{
    jclass arrayListClass = (*env)->FindClass(env, JARRLIST);
    jmethodID arrayListAdd = (*env)->GetMethodID(env, arrayListClass, "add", "(Ljava/lang/Object;)Z");
    jboolean val = (*env)->CallBooleanMethod(env, list, arrayListAdd, element);
}

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

MTPDeviceIdentifier_t fromJMTPDeviceIdentifier(JNIEnv *env, jobject deviceId);

jobject toJMTPDeviceConnection(JNIEnv *env, MTPDeviceConnection_t deviceConn)
{
    jclass deviceConnectionClass = (*env)->FindClass(env, JMTPDEVICECONNECTION);
    char sig[1024];
    sprintf(sig, "(%sJJ)V", JMTPDEVICEIDENTIFIER);
    jmethodID deviceConnectionConstr = (*env)->GetMethodID(env, deviceConnectionClass, JCONSTRUCTOR, sig);

    jobject deviceId = toJMTPDeviceIdentifier(env, deviceConn.deviceId);
    jlong rawDevice = deviceConn.rawDevice;
    jlong device = deviceConn.device;

    jobject jDeviceConn = (*env)->NewObject(env, deviceConnectionClass, deviceConnectionConstr, deviceId, rawDevice, deviceConn);
    return jDeviceConn;
}

MTPDeviceConnection_t fromJMTPDeviceConnection(JNIEnv *env, jobject deviceConn);

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
