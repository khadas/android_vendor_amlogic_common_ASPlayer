/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_NATIVE_HELPER_H
#define JNI_ASPLAYER_WRAPPER_NATIVE_HELPER_H

#include <jni.h>

class NativeHelper {
public:
    static bool initJniEnv(JNIEnv *env);

    static bool isIntegerInstance(JNIEnv *env, jobject obj);

    static bool isLongInstance(JNIEnv *env, jobject obj);

    static bool isFloatInstance(JNIEnv *env, jobject obj);

    static bool isStringInstance(JNIEnv *env, jobject obj);

    static bool getIntFromInteger(JNIEnv *env, jobject integerObj, jint *outInt);

    static bool getLongFromLongObj(JNIEnv *env, jobject longObj, jlong *outLong);

    static bool getFloatFromFloatObj(JNIEnv *env, jobject floatObj, jfloat *outFloat);

    static bool findObjectValue(JNIEnv *env, jobjectArray keys, jobjectArray values,
                                const char *keyToFind, jobject *outValue);
};


#endif //JNI_ASPLAYER_WRAPPER_NATIVE_HELPER_H
