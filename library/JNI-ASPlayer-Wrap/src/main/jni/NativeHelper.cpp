/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <cstring>

#include "NativeHelper.h"

static bool gInited = false;
static jclass gIntegerClass = nullptr;
static jclass gLongClass = nullptr;
static jclass gFloatClass = nullptr;
static jclass gStringClass = nullptr;

static jmethodID gIntValueMID = nullptr;
static jmethodID gLongValueMID = nullptr;
static jmethodID gFloatValueMID = nullptr;

bool NativeHelper::initJniEnv(JNIEnv *env) {
    if (gInited) {
        return true;
    }

    jclass stringClass = env->FindClass("java/lang/String");
    gStringClass = static_cast<jclass>(env->NewGlobalRef(stringClass));
    env->DeleteLocalRef(stringClass);

    jclass integerClass = env->FindClass("java/lang/Integer");
    gIntegerClass = static_cast<jclass>(env->NewGlobalRef(integerClass));
    env->DeleteLocalRef(integerClass);
    gIntValueMID = env->GetMethodID(gIntegerClass, "intValue", "()I");

    jclass longClass = env->FindClass("java/lang/Long");
    gLongClass = static_cast<jclass>(env->NewGlobalRef(longClass));
    env->DeleteLocalRef(longClass);
    gLongValueMID = env->GetMethodID(gLongClass, "longValue", "()J");

    jclass floatClass = env->FindClass("java/lang/Float");
    gFloatClass = static_cast<jclass>(env->NewGlobalRef(floatClass));
    env->DeleteLocalRef(floatClass);
    gFloatValueMID = env->GetMethodID(gFloatClass, "floatValue", "()F");

    gInited = true;

    return gInited;
}

bool NativeHelper::isIntegerInstance(JNIEnv *env, jobject obj) {
    if (!env || !obj || !gIntegerClass) {
        return false;
    }

    return env->IsInstanceOf(obj, gIntegerClass);
}

bool NativeHelper::isLongInstance(JNIEnv *env, jobject obj) {
    if (!env || !obj || !gLongClass) {
        return false;
    }

    return env->IsInstanceOf(obj, gLongClass);
}

bool NativeHelper::isFloatInstance(JNIEnv *env, jobject obj) {
    if (!env || !obj || !gFloatClass) {
        return false;
    }

    return env->IsInstanceOf(obj, gFloatClass);
}

bool NativeHelper::isStringInstance(JNIEnv *env, jobject obj) {
    if (!env || !obj || !gStringClass) {
        return false;
    }

    return env->IsInstanceOf(obj, gStringClass);
}

bool NativeHelper::getIntFromInteger(JNIEnv *env, jobject integerObj, jint *outInt) {
    if (!env || !integerObj || !outInt || !gIntValueMID) {
        return false;
    }

    if (!isIntegerInstance(env, integerObj)) {
        return false;
    }

    *outInt = env->CallIntMethod(integerObj, gIntValueMID);
    return true;
}

bool NativeHelper::getLongFromLongObj(JNIEnv *env, jobject longObj, jlong *outLong) {
    if (!env || !longObj || !outLong || !gLongValueMID) {
        return false;
    }

    if (!isLongInstance(env, longObj)) {
        return false;
    }

    *outLong = env->CallLongMethod(longObj, gLongValueMID);
    return true;
}

bool NativeHelper::getFloatFromFloatObj(JNIEnv *env, jobject floatObj, jfloat *outFloat) {
    if (!env || !floatObj || !outFloat || !gFloatValueMID) {
        return false;
    }

    if (!isFloatInstance(env, floatObj)) {
        return false;
    }

    *outFloat = env->CallFloatMethod(floatObj, gFloatValueMID);
    return true;
}

bool NativeHelper::findObjectValue(JNIEnv *env, jobjectArray keys, jobjectArray values,
                                   const char *keyToFind, jobject *outValue) {
    if (env == NULL || keys == NULL || values == NULL || keyToFind == NULL || outValue == NULL) {
        return false;
    }

    jsize numEntries = env->GetArrayLength(keys);
    if (numEntries != env->GetArrayLength(values)) {
        return false;
    }

    bool success = false;

    for (jsize i = 0; i < numEntries; ++i) {
        jobject keyObj = env->GetObjectArrayElement(keys, i);
        jobject valueObj = env->GetObjectArrayElement(values, i);

        if (keyObj == NULL) {
            continue;
        }

        const char *tmp = env->GetStringUTFChars((jstring)keyObj, NULL);
        if (tmp == NULL) {
            break;
        }

        if (strcmp((char *)keyToFind, tmp) == 0) {
            *outValue = valueObj;
            success = true;
            env->ReleaseStringUTFChars((jstring)keyObj, tmp);
            break;
        }
    }

    return success;
}