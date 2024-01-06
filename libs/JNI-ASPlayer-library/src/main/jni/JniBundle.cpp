/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "log.h"
#include "NativeHelper.h"
#include "JniBundle.h"

#define BREAK_IF_NULLPTR(obj)   \
{                               \
    if (!obj) {                 \
        break;                  \
    }                           \
}

jclass JniBundle::gBundleCls = nullptr;
jmethodID JniBundle::gConstructorMID = nullptr;
jmethodID JniBundle::gPutIntMID = nullptr;
jmethodID JniBundle::gPutLongMID = nullptr;
jmethodID JniBundle::gPutFloatMID = nullptr;
bool JniBundle::gIsInited = false;

JniBundle::JniBundle(jobject bundleObj) : mBundleObj(bundleObj) {

}

JniBundle::~JniBundle() {
    mBundleObj = nullptr;
}

bool JniBundle::initJni(JNIEnv *env) {
    if (gIsInited) {
        return true;
    }

    bool success = false;
    jclass bundleCls = nullptr;

    do {
        bundleCls = NativeHelper::FindClass(env, "android/os/Bundle");
        BREAK_IF_NULLPTR(bundleCls);

        gBundleCls = NativeHelper::MakeGlobalRef(env, bundleCls, "class<android/os/Bundle>");
        BREAK_IF_NULLPTR(gBundleCls);

        gConstructorMID = NativeHelper::GetMethodID(
                env, gBundleCls, "<init>", "()V");
        BREAK_IF_NULLPTR(gConstructorMID);

        gPutIntMID = NativeHelper::GetMethodID(
                env, gBundleCls, "putInt", "(Ljava/lang/String;I)V");
        BREAK_IF_NULLPTR(gPutIntMID);

        gPutLongMID = NativeHelper::GetMethodID(
                env, gBundleCls, "putLong", "(Ljava/lang/String;J)V");
        BREAK_IF_NULLPTR(gPutLongMID);

        gPutFloatMID = NativeHelper::GetMethodID(
                env, gBundleCls, "putFloat", "(Ljava/lang/String;F)V");
        BREAK_IF_NULLPTR(gPutFloatMID);

        success = true;
    } while (0);

    DELETE_LOCAL_REF(env, bundleCls);

    gIsInited = success;
    return gIsInited;
}

bool JniBundle::create(JNIEnv *env, JniBundle **bundle) {
    if (!env) {
        return false;
    } else if (!gIsInited) {
        return false;
    } else if (!bundle) {
        return false;
    }

    jobject bundleObj = env->NewObject(gBundleCls, gConstructorMID);
    if (env->IsSameObject(bundleObj, nullptr)) {
        AP_LOGE("create JniBundle failed, failed to alloc java object");
        return false;
    }

    JniBundle *temp = new JniBundle(bundleObj);
    if (temp == nullptr) {
        AP_LOGE("create JniBundle failed, OOM?");
        return false;
    }

    *bundle = temp;

    return true;
}

jobject JniBundle::getJavaBundleObject() {
    return mBundleObj;
}

bool JniBundle::putInt(JNIEnv *env, const char *key, int32_t value) {
    if (!env || !key) {
        return false;
    } else if (!gIsInited || !mBundleObj) {
        return false;
    }

    jstring jKey = env->NewStringUTF(key);
    if (env->IsSameObject(jKey, nullptr)) {
        return false;
    }

    env->CallVoidMethod(mBundleObj, gPutIntMID, jKey, (jint)value);
    env->DeleteLocalRef(jKey);

    return true;
}

bool JniBundle::putLong(JNIEnv *env, const char *key, int64_t value) {
    if (!env || !key) {
        return false;
    } else if (!gIsInited || !mBundleObj) {
        return false;
    }

    jstring jKey = env->NewStringUTF(key);
    if (env->IsSameObject(jKey, nullptr)) {
        return false;
    }

    env->CallVoidMethod(mBundleObj, gPutLongMID, jKey, (jlong)value);
    env->DeleteLocalRef(jKey);

    return true;
}

bool JniBundle::putFloat(JNIEnv *env, const char *key, float value) {
    if (!env || !key) {
        return false;
    } else if (!gIsInited || !mBundleObj) {
        return false;
    }

    jstring jKey = env->NewStringUTF(key);
    if (env->IsSameObject(jKey, nullptr)) {
        return false;
    }

    env->CallVoidMethod(mBundleObj, gPutFloatMID, jKey, (jfloat)value);
    env->DeleteLocalRef(jKey);

    return true;
}

void JniBundle::release(JNIEnv *env, JniBundle *bundle) {
    if (bundle == nullptr) {
        return;
    }

    if (bundle->mBundleObj && env) {
        env->DeleteLocalRef(bundle->mBundleObj);
        bundle->mBundleObj = nullptr;
    }

    delete bundle;
}
