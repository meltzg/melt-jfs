#ifndef JNI_HELPERS_H
#define JNI_HELPERS_H

#include <jni.h>
#include <mtp_helpers.h>

static const char *const JCONSTRUCTOR = "<init>";
static const char *const JSTRING = "Ljava/lang/String;";
static const char *const JARRLIST = "Ljava/util/ArrayList;";
static const char *const JLIST = "Ljava/util/List;";
static const char *const JCOLLECTION = "Ljava/util/Collection;";

static const char *const JMTPDEVICEIDENTIFIER = "Lorg/meltzg/fs/mtp/types/MTPDeviceIdentifier;";
static const char *const JMTPDEVICECONNECTION = "Lorg/meltzg/fs/mtp/types/MTPDeviceConnection;";

jobject getNewArrayList(JNIEnv *env);
void arrayListAdd(JNIEnv *env, jobject list, jobject element);

jobject toJMTPDeviceIdentifier(JNIEnv *env, jobject obj, MTPDeviceIdentifier_t deviceId);
jobject toJMTPDeviceConnection(JNIEnv *env, jobject obj, MTPDeviceConnection_t deviceConn);

#endif