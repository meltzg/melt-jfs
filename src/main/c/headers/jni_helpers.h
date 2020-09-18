#ifndef JNI_HELPERS_H
#define JNI_HELPERS_H

#include <jni.h>
#include <mtp_helpers.h>

static const char *const JCONSTRUCTOR = "<init>";
static const char *const JSTRING = "Ljava/lang/String;";

static const char *const JMTPDEVICEIDENTIFIER = "Lorg/meltzg/fs/mtp/types/MTPDeviceIdentifier;";
static const char *const JMTPDEVICECONNECTION = "Lorg/meltzg/fs/mtp/types/MTPDeviceConnection;";
static const char *const JMTPDEVICEINFO = "Lorg/meltzg/fs/mtp/types/MTPDeviceInfo;";

jobject toJMTPDeviceIdentifier(JNIEnv *env, MTPDeviceIdentifier_t deviceId);
MTPDeviceIdentifier_t *fromJMTPDeviceIdentifier(JNIEnv *env, jobject deviceId);
jobject toJMTPDeviceConnection(JNIEnv *env, MTPDeviceConnection_t deviceConn);
MTPDeviceConnection_t *fromJMTPDeviceConnection(JNIEnv *env, jobject deviceConn);
jobject toJMTPDeviceInfo(JNIEnv *env, MTPDeviceInfo_t deviceInfo);

char *fromJString(JNIEnv *env, jstring string);

#endif