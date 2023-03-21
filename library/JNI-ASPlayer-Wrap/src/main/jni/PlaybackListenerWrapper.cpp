/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "ASPlayerJni.h"
#include "common/utils/Log.h"
#include "PlaybackListenerWrapper.h"

PlaybackListenerWrapper::PlaybackListenerWrapper(JNIEnv *env, jobject jListener) {
    if (env != nullptr && jListener != nullptr) {
        mJavaListener = env->NewGlobalRef(jListener);
    }
}

PlaybackListenerWrapper::~PlaybackListenerWrapper() {
    JNIEnv *env = AutoEnv::GetJniEnv();
    if (mJavaListener != nullptr) {
        env->DeleteGlobalRef(mJavaListener);
        mJavaListener = nullptr;
    }
}

jobject PlaybackListenerWrapper::getJavaListener() {
    return mJavaListener;
}

void PlaybackListenerWrapper::notifyPlaybackEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    switch (event->type) {
        case JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED:
            notifyVideoFormatChangeEvent(event);
            break;
        default:
            break;
    }
}

void PlaybackListenerWrapper::notifyVideoFormatChangeEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("%s[%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject videoFormatChangeEvent = nullptr;
    if (!ASPlayerJni::createVideoFormatChangeEvent(env, event, &videoFormatChangeEvent)) {
        ALOGE("%s[%d] error, failed to create java VideoFormatChangeEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyVideoFormatChangeEvent(env, mJavaListener, videoFormatChangeEvent);
    env->DeleteLocalRef(videoFormatChangeEvent);
}

void PlaybackListenerWrapper::release() {
    JNIEnv *env = AutoEnv::GetJniEnv();
    if (mJavaListener != nullptr) {
        env->DeleteGlobalRef(mJavaListener);
        mJavaListener = nullptr;
    }
}