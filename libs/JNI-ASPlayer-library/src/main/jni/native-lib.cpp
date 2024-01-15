/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <jni.h>
#include "JNIASPlayer.h"
#include "log.h"

#ifdef __cplusplus
extern "C" {
#endif

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = NULL;

    ALOGD("JNI_OnLoad, enter");
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("JNI_OnLoad error: GetEnv failed");
        return -1;
    }

    jni_asplayer_result result = JniASPlayer_registerJNI(env);
    if (result == JNI_ASPLAYER_OK) {
        ALOGE("JNI_OnLoad JniASPlayer_registerJNI success");
    } else {
        ALOGE("JNI_OnLoad JniASPlayer_registerJNI failed, result: %d", result);
    }

    return JNI_VERSION_1_4;
}

#ifdef __cplusplus
};
#endif

