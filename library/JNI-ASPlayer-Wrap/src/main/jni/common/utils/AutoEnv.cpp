/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <unistd.h>
#include <pthread.h>

#include "AutoEnv.h"

#include "Log.h"


JavaVM *AutoEnv::g_JavaVM = nullptr;

void AutoEnv::DeferThreadDetach(JNIEnv *env) {
    static pthread_key_t thread_key;

    // Set up a Thread Specific Data key, and a callback that
    // will be executed when a thread is destroyed.
    // This is only done once, across all threads, and the value
    // associated with the key for any given thread will initially
    // be NULL.
    static auto run_once = [] {
        const auto err = pthread_key_create(&thread_key, [] (void *ts_env) {
            if (ts_env) {
                if (g_JavaVM != nullptr) {
                    g_JavaVM->DetachCurrentThread();
                }
            }
        });
        if (err) {
            // Failed to create TSD key. Throw an exception if you want to.
        }
        return 0;
    }();
    (void)run_once;

    // For the callback to actually be executed when a thread exits
    // we need to associate a non-NULL value with the key on that thread.
    // We can use the JNIEnv* as that value.
    const auto ts_env = pthread_getspecific(thread_key);
    if (!ts_env) {
        if (pthread_setspecific(thread_key, env)) {
            // Failed to set thread-specific value for key. Throw an exception if you want to.
        }
    }
}

/**
 * Get a JNIEnv* valid for this thread, regardless of whether
 * we're on a native thread or a Java thread.
 * If the calling thread is not currently attached to the JVM
 * it will be attached, and then automatically detached when the
 * thread is destroyed.
 */
JNIEnv *AutoEnv::GetJniEnv() {
    JNIEnv *env = nullptr;
    // We still call GetEnv first to detect if the thread already
    // is attached. This is done to avoid setting up a DetachCurrentThread
    // call on a Java thread.
    if (g_JavaVM == nullptr) {
        return nullptr;
    }

    // g_vm is a global.
    auto get_env_result = g_JavaVM->GetEnv((void**)&env, JNI_VERSION_1_4);
    if (get_env_result == JNI_EDETACHED) {
        if (g_JavaVM->AttachCurrentThread(&env, NULL) == JNI_OK) {
            AutoEnv::DeferThreadDetach(env);
        } else {
            // Failed to attach thread. Throw an exception if you want to.
        }
    } else if (get_env_result == JNI_EVERSION) {
        // Unsupported JNI version. Throw an exception if you want to.
    }
    return env;
}

void AutoEnv::setJavaVM(JavaVM *jvm) {
    g_JavaVM = jvm;
}