/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "NativeHelper.h"
#include "log.h"


int NativeHelper::registerNativeMethods(JNIEnv* env, const char* className,
                                 const JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;
    if (env == NULL) {
        return JNI_FALSE;
    }

    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("RegisterNatives failed, unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        env->DeleteLocalRef(clazz);
        return JNI_FALSE;
    }

    env->DeleteLocalRef(clazz);
    return JNI_TRUE;
}
