#include "org_meltzg_fs_Library.h"

int number = 0;

JNIEXPORT jint JNICALL Java_org_meltzg_fs_Library_foo(JNIEnv *env, jobject obj)
{
    return number++;
}
