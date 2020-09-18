#include "org_meltzg_fs_mtp_MTPDeviceBridge.h"
#include "jni_helpers.h"
#include "mtp_helpers.h"

JNIEXPORT void JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_initMTP(JNIEnv *env, jclass obj)
{
    initMTP();
}

JNIEXPORT void JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_terminateMTP(JNIEnv *env, jobject obj, jobjectArray deviceConns)
{
    jsize numConns = (*env)->GetArrayLength(env, deviceConns);
    MTPDeviceConnection_t cDeviceConns[numConns];
    for (int i = 0; i < numConns; i++)
    {
        cDeviceConns[i] = fromJMTPDeviceConnection(env, (*env)->GetObjectArrayElement(env, deviceConns, i));
    }
    terminateMTP(cDeviceConns, numConns);
    for (int i = 0; i < numConns; i++)
    {
        freeMTPDeviceConnection(cDeviceConns[i]);
    }
}

JNIEXPORT jobject JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getDeviceConnections(JNIEnv *env, jobject obj)
{
    return NULL;
}

JNIEXPORT jobject JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getDeviceInfo(JNIEnv *env, jobject obj, jobject deviceConn)
{
    return NULL;
}
