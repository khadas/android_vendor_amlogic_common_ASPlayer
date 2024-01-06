/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "JniMediaFormat.h"

bool JniMediaFormat::gInited = false;
jclass JniMediaFormat::gMediaFormatCls = nullptr;
jmethodID JniMediaFormat::gConstructorMID = nullptr;
jmethodID JniMediaFormat::gGetIntegerMID = nullptr;
jmethodID JniMediaFormat::gGetIntegerDefaultValueMID = nullptr;
jmethodID JniMediaFormat::gGetFloatMID = nullptr;
jmethodID JniMediaFormat::gGetFloatDefaultValueMID = nullptr;
jmethodID JniMediaFormat::gGetLongDefaultValueMID = nullptr;
jmethodID JniMediaFormat::gGetKeysMID = nullptr;
jmethodID JniMediaFormat::gGetValueTypeForKeyMID = nullptr;


JniMediaFormat::JniMediaFormat() {

}

JniMediaFormat::~JniMediaFormat() {

}

bool JniMediaFormat::initJni(JNIEnv *env) {
    if (gInited) {
        return true;
    }

    jclass mediaFormatCls = env->FindClass("android/media/MediaFormat");
    gMediaFormatCls = static_cast<jclass>(env->NewGlobalRef(mediaFormatCls));
    env->DeleteLocalRef(mediaFormatCls);
    gConstructorMID = env->GetMethodID(gMediaFormatCls, "<init>", "()V");
    gGetIntegerMID = env->GetMethodID(gMediaFormatCls, "getInteger", "(Ljava/lang/String;)I");
    gGetIntegerDefaultValueMID = env->GetMethodID(gMediaFormatCls, "getInteger", "(Ljava/lang/String;I)I");
    gGetFloatMID = env->GetMethodID(gMediaFormatCls, "getFloat", "(Ljava/lang/String;)F");
    gGetFloatDefaultValueMID = env->GetMethodID(gMediaFormatCls, "getFloat", "(Ljava/lang/String;F)F");
    gGetLongDefaultValueMID = env->GetMethodID(gMediaFormatCls, "getLong", "(Ljava/lang/String;J)J");
    gGetKeysMID = env->GetMethodID(gMediaFormatCls, "getKeys", "()Ljava/util/Set;");
    gGetValueTypeForKeyMID = env->GetMethodID(gMediaFormatCls, "getValueTypeForKey",
                                              "(Ljava/lang/String;)I");

    gInited = true;
    return gInited;
}

bool JniMediaFormat::createJavaMediaFormat(JNIEnv *env, jobject *outMediaFormat) {
    if (env == nullptr) {
        return false;
    } else if (outMediaFormat == nullptr) {
        return false;
    }

    jobject mediaFormat = env->NewObject(gMediaFormatCls, gConstructorMID);
    *outMediaFormat = mediaFormat;

    return true;
}

bool JniMediaFormat::getInteger(JNIEnv *env, jobject jMediaFormat, const char *key, int32_t *outValue) {
    if (env == nullptr) {
        return false;
    } else if (jMediaFormat == nullptr || key == nullptr || outValue == nullptr) {
        return false;
    }

    jstring keyStr = env->NewStringUTF(key);
    jint value = env->CallIntMethod(jMediaFormat, gGetIntegerMID, keyStr);
    env->DeleteLocalRef(keyStr);

    *outValue = value;
    return true;
}

int32_t JniMediaFormat::getInteger(JNIEnv *env, jobject jMediaFormat, const char *key, int32_t defaultValue) {
    if (env == nullptr) {
        return defaultValue;
    } else if (jMediaFormat == nullptr || key == nullptr) {
        return defaultValue;
    }

    jstring keyStr = env->NewStringUTF(key);
    jint value = env->CallIntMethod(jMediaFormat, gGetIntegerDefaultValueMID, keyStr, defaultValue);
    env->DeleteLocalRef(keyStr);

    return value;
}

bool JniMediaFormat::getFloat(JNIEnv *env, jobject jMediaFormat, const char *key, float *outValue) {
    if (env == nullptr) {
        return false;
    } else if (jMediaFormat == nullptr || key == nullptr || outValue == nullptr) {
        return false;
    }

    jstring keyStr = env->NewStringUTF(key);
    jfloat value = env->CallFloatMethod(jMediaFormat, gGetFloatMID, keyStr);
    env->DeleteLocalRef(keyStr);

    *outValue = value;
    return true;
}

float JniMediaFormat::getFloat(JNIEnv *env, jobject jMediaFormat, const char *key, float defaultValue) {
    if (env == nullptr) {
        return defaultValue;
    } else if (jMediaFormat == nullptr || key == nullptr) {
        return defaultValue;
    }

    jstring keyStr = env->NewStringUTF(key);
    jfloat value = env->CallFloatMethod(jMediaFormat, gGetFloatDefaultValueMID, keyStr, defaultValue);
    env->DeleteLocalRef(keyStr);

    return value;
}

long
JniMediaFormat::getLong(JNIEnv *env, jobject jMediaFormat, const char *key, long defaultValue) {
    if (env == nullptr) {
        return defaultValue;
    } else if (jMediaFormat == nullptr || key == nullptr) {
        return defaultValue;
    }

    jstring keyStr = env->NewStringUTF(key);
    jlong value = env->CallLongMethod(jMediaFormat, gGetLongDefaultValueMID, keyStr, defaultValue);
    env->DeleteLocalRef(keyStr);

    return value;
}