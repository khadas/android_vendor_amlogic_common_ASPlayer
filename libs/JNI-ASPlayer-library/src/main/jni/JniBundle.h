/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_JNI_BUNDLE_H
#define JNI_ASPLAYER_JNI_BUNDLE_H

#include <jni.h>

class JniBundle {
public:
    static bool initJni(JNIEnv *env);

    static bool create(JNIEnv *env, JniBundle **bundle);

    static void release(JNIEnv *env, JniBundle *bundle);

    jobject getJavaBundleObject();

    bool putInt(JNIEnv *env, const char *key, int32_t value);

    bool putLong(JNIEnv *env, const char *key, int64_t value);

    bool putFloat(JNIEnv *env, const char *key, float value);

private:
    JniBundle(jobject bundleObj);
    ~JniBundle();

    static jclass gBundleCls;
    static jmethodID gConstructorMID;
    static jmethodID gPutIntMID;
    static jmethodID gPutLongMID;
    static jmethodID gPutFloatMID;
    static bool gIsInited;

    jobject mBundleObj;
};


#endif //JNI_ASPLAYER_JNI_BUNDLE_H
