#include "org_meltzg_fs_mtp_MTPDeviceBridge.h"
#include "jni_helpers.h"
#include "mtp_helpers.h"

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
