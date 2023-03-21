/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "JniMediaFormat.h"

bool JniMediaFormat::sInited = false;
jclass JniMediaFormat::sMediaFormatCls = nullptr;
jmethodID JniMediaFormat::sConstructorMID = nullptr;
jmethodID JniMediaFormat::sGetIntegerMID = nullptr;
jmethodID JniMediaFormat::sGetIntegerDefaultValueMID = nullptr;
jmethodID JniMediaFormat::sGetKeysMID = nullptr;
jmethodID JniMediaFormat::sGetValueTypeForKeyMID = nullptr;


JniMediaFormat::JniMediaFormat() {

}

JniMediaFormat::~JniMediaFormat() {

}

bool JniMediaFormat::initJni(JNIEnv *env) {
    if (sInited) {
        return true;
    }

    jclass mediaFormatCls = env->FindClass("android/media/MediaFormat");
    sMediaFormatCls = static_cast<jclass>(env->NewGlobalRef(mediaFormatCls));
    env->DeleteLocalRef(mediaFormatCls);
    sConstructorMID = env->GetMethodID(sMediaFormatCls, "<init>", "()V");
    sGetIntegerMID = env->GetMethodID(sMediaFormatCls, "getInteger", "(Ljava/lang/String;)I");
    sGetIntegerDefaultValueMID = env->GetMethodID(sMediaFormatCls, "getInteger", "(Ljava/lang/String;I)I");
    sGetKeysMID = env->GetMethodID(sMediaFormatCls, "getKeys", "()Ljava/util/Set;");
    sGetValueTypeForKeyMID = env->GetMethodID(sMediaFormatCls, "getValueTypeForKey",
                                              "(Ljava/lang/String;)I");

    sInited = true;
    return sInited;
}

bool JniMediaFormat::createJavaMediaFormat(JNIEnv *env, jobject *outMediaFormat) {
    if (env == nullptr) {
        return false;
    } else if (outMediaFormat == nullptr) {
        return false;
    }

    jobject mediaFormat = env->NewObject(sMediaFormatCls, sConstructorMID);
    *outMediaFormat = mediaFormat;

    return true;
}

bool JniMediaFormat::getInteger(JNIEnv *env, jobject jMediaFormat, const char *key, int32_t *outValue) {
    if (env == nullptr) {
        return false;
    } else if (jMediaFormat == nullptr || key == nullptr || outValue == nullptr) {
        return false;
    }

    jobject keyStr = env->NewStringUTF(key);
    jint value = env->CallIntMethod(jMediaFormat, sGetIntegerMID, keyStr);
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

    jobject keyStr = env->NewStringUTF(key);
    jint value = env->CallIntMethod(jMediaFormat, sGetIntegerDefaultValueMID, keyStr, defaultValue);
    env->DeleteLocalRef(keyStr);

    return value;
}
