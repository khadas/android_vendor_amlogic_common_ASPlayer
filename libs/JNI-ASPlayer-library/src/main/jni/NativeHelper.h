/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_NATIVEHELPER_H
#define JNI_ASPLAYER_NATIVEHELPER_H

#include <jni.h>
#include "log.h"

class NativeHelper {
public:
    static int registerNativeMethods(JNIEnv* env, const char* className,
                              const JNINativeMethod* gMethods, int numMethods);

    static inline jclass FindClass(JNIEnv* env, const char* class_name) {
        jclass clazz = env->FindClass(class_name);
        AP_LOGE_IF(clazz == NULL, "Unable to find class %s", class_name);
        return clazz;
    }

    static inline jfieldID GetFieldID(JNIEnv* env, jclass clazz, const char* field_name,
                                           const char* field_signature) {
        jfieldID res = env->GetFieldID(clazz, field_name, field_signature);
        AP_LOGE_IF(res == NULL, "Unable to find static field %s with signature %s", field_name,
                            field_signature);
        return res;
    }

    static inline jmethodID GetMethodID(JNIEnv* env, jclass clazz, const char* method_name,
                                             const char* method_signature) {
        jmethodID res = env->GetMethodID(clazz, method_name, method_signature);
        AP_LOGE_IF(res == NULL, "Unable to find method %s with signature %s", method_name,
                            method_signature);
        return res;
    }

    static inline jfieldID GetStaticFieldID(JNIEnv* env, jclass clazz, const char* field_name,
                                                 const char* field_signature) {
        jfieldID res = env->GetStaticFieldID(clazz, field_name, field_signature);
        AP_LOGE_IF(res == NULL, "Unable to find static field %s with signature %s", field_name,
                            field_signature);
        return res;
    }

    static inline jmethodID GetStaticMethodID(JNIEnv* env, jclass clazz, const char* method_name,
                                                   const char* method_signature) {
        jmethodID res = env->GetStaticMethodID(clazz, method_name, method_signature);
        AP_LOGE_IF(res == NULL, "Unable to find static method %s with signature %s",
                            method_name, method_signature);
        return res;
    }

    template <typename T>
    static inline T MakeGlobalRef(JNIEnv* env, T in) {
        jobject res = env->NewGlobalRef(in);
        AP_LOGE_IF(res == NULL, "Unable to create global reference.");
        return static_cast<T>(res);
    }
};


#endif //JNI_ASPLAYER_NATIVEHELPER_H
