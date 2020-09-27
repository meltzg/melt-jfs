#include "org_meltzg_fs_mtp_MTPDeviceBridge.h"
#include "jni_helpers.h"
#include "mtp_helpers.h"

using std::string;
using std::vector;

JNIEXPORT void JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_initMTP(JNIEnv *env, jclass obj)
{
    initMTP();
}

JNIEXPORT void JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_terminateMTP(JNIEnv *env, jobject obj, jobjectArray deviceConns)
{
    jsize numConns = env->GetArrayLength(deviceConns);
    vector<MTPDeviceConnection> cDeviceConns;
    for (int i = 0; i < numConns; i++)
    {
        cDeviceConns.push_back(fromJMTPDeviceConnection(env, env->GetObjectArrayElement(deviceConns, i)));
    }
    terminateMTP(cDeviceConns);
}

JNIEXPORT jobjectArray JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getDeviceConnections(JNIEnv *env, jobject obj)
{
    vector<MTPDeviceConnection> deviceConns = getDeviceConnections();
    jobjectArray jConns = env->NewObjectArray(deviceConns.size(), env->FindClass(JMTPDEVICECONNECTION), nullptr);
    for (auto i = 0; i < deviceConns.size(); i++)
    {
        jobject jConn = toJMTPDeviceConnection(env, deviceConns[i]);
        env->SetObjectArrayElement(jConns, i, jConn);
    }
    return jConns;
}

JNIEXPORT jobject JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getDeviceInfo(JNIEnv *env, jobject obj, jobject deviceConn)
{
    MTPDeviceConnection cDeviceConn = fromJMTPDeviceConnection(env, deviceConn);
    MTPDeviceInfo deviceInfo = getDeviceInfo(cDeviceConn);
    jobject jDeviceInfo = toJMTPDeviceInfo(env, deviceInfo);
    return jDeviceInfo;
}

JNIEXPORT jobject JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getFileStore(JNIEnv *env, jobject obj, jobject deviceConn, jstring storageName)
{
    MTPDeviceConnection cDeviceConn = fromJMTPDeviceConnection(env, deviceConn);
    const char *cStorageName = env->GetStringUTFChars(storageName, nullptr);
    MTPDeviceStorage deviceStorage = getDeviceStorage(cDeviceConn, cStorageName);
    if (deviceStorage.getName().length() == 0)
    {
        throwIOException(env, "Could not find storage device ");
    }

    jobject jDeviceStorage = toJMTPFileStore(env, deviceStorage);
    env->ReleaseStringUTFChars(storageName, cStorageName);
    return jDeviceStorage;
}

JNIEXPORT jlong JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getCapacity(JNIEnv *env, jobject obj, jobject deviceConn, jlong storageId)
{
    MTPDeviceConnection cDeviceConn = fromJMTPDeviceConnection(env, deviceConn);
    jlong capacity = getCapacity(cDeviceConn, storageId);
    if (capacity < 0)
    {
        throwIOException(env, "Could not find filestore");
    }
    return capacity;
}

JNIEXPORT jlong JNICALL Java_org_meltzg_fs_mtp_MTPDeviceBridge_getFreeSpace(JNIEnv *env, jobject obj, jobject deviceConn, jlong storageId)
{
    MTPDeviceConnection cDeviceConn = fromJMTPDeviceConnection(env, deviceConn);
    jlong freespace = getFreeSpace(cDeviceConn, storageId);
    if (freespace < 0)
    {
        throwIOException(env, "Could not find filestore");
    }
    return freespace;
}
