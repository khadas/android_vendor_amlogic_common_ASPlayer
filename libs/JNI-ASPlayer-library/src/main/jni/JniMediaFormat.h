/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_MEDIAFORMAT_H
#define JNI_ASPLAYER_MEDIAFORMAT_H

#include <jni.h>

class JniMediaFormat {

public:
    static bool initJni(JNIEnv *env);

    static bool createJavaMediaFormat(JNIEnv *env, jobject *outMediaFormat);

    static bool getInteger(JNIEnv *env, jobject jMediaFormat, const char *key, int32_t *outValue);

    static int32_t getInteger(JNIEnv *env, jobject jMediaFormat, const char *key, int32_t defaultValue);

    static bool getFloat(JNIEnv *env, jobject jMediaFormat, const char *key, float *outValue);

    static float getFloat(JNIEnv *env, jobject jMediaFormat, const char *key, float defaultValue);

    static long getLong(JNIEnv *env, jobject jMediaFormat, const char *key, long defaultValue);

public:
    JniMediaFormat();
    virtual ~JniMediaFormat();

private:
    static bool gInited;
    static jclass gMediaFormatCls;
    static jmethodID gConstructorMID;
    static jmethodID gGetIntegerMID;
    static jmethodID gGetIntegerDefaultValueMID;
    static jmethodID gGetFloatMID;
    static jmethodID gGetFloatDefaultValueMID;
    static jmethodID gGetLongDefaultValueMID;
    static jmethodID gGetKeysMID;
    static jmethodID gGetValueTypeForKeyMID;
};


#endif //JNI_ASPLAYER_MEDIAFORMAT_H
