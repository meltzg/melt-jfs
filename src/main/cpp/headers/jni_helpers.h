#ifndef JNI_HELPERS_H
#define JNI_HELPERS_H

#include <jni.h>
#include <mtp_helpers.h>

static const char *const JCONSTRUCTOR = "<init>";
static const char *const JSTRING = "Ljava/lang/String;";
static const char *const JIOEXCEPTION = "Ljava/io/IOException;";

static const char *const JMTPDEVICEIDENTIFIER = "Lorg/meltzg/fs/mtp/types/MTPDeviceIdentifier;";
static const char *const JMTPDEVICECONNECTION = "Lorg/meltzg/fs/mtp/types/MTPDeviceConnection;";
static const char *const JMTPDEVICEINFO = "Lorg/meltzg/fs/mtp/types/MTPDeviceInfo;";
static const char *const JMTPDEVICESTORAGE = "Lorg/meltzg/fs/mtp/MTPFileStore;";

jobject toJMTPDeviceIdentifier(JNIEnv *env, MTPDeviceIdentifier deviceId);
MTPDeviceIdentifier fromJMTPDeviceIdentifier(JNIEnv *env, jobject deviceId);
jobject toJMTPDeviceConnection(JNIEnv *env, MTPDeviceConnection deviceConn);
MTPDeviceConnection fromJMTPDeviceConnection(JNIEnv *env, jobject deviceConn);
jobject toJMTPDeviceInfo(JNIEnv *env, MTPDeviceInfo deviceInfo);
jobject toJMTPFileStore(JNIEnv *env, MTPDeviceStorage deviceStorage);

jint throwIOException(JNIEnv *env, const char *message);

#endif