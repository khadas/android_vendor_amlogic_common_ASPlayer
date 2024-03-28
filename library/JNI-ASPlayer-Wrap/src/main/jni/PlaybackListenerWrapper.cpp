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

//    ALOGD("[%s/%d] notifyPlaybackEvent: %d", __FUNCTION__, __LINE__, event->type);

    switch (event->type) {
        case JNI_ASPLAYER_EVENT_TYPE_PTS:
            notifyPtsEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED:
            notifyVideoFormatChangeEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_AUDIO_CHANGED:
            notifyAudioFormatChangeEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO:
            notifyVideoFirstFrameEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO:
            notifyAudioFirstFrameEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO:
            notifyDecodeFirstVideoFrameEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO:
            notifyDecodeFirstAudioFrameEvent(event);
            break;
        case JNI_ASPLAYER_EVENT_TYPE_DECODER_INIT_COMPLETED:
        case JNI_ASPLAYER_EVENT_TYPE_DECODER_DATA_LOSS:
        case JNI_ASPLAYER_EVENT_TYPE_DECODER_DATA_RESUME:
            notifyPlaybackInfoEvent(event);
            break;
        default:
            break;
    }
}

void PlaybackListenerWrapper::notifyPtsEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject ptsEvent = nullptr;
    if (!ASPlayerJni::createPtsEvent(env, event, &ptsEvent)) {
        ALOGE("[%s/%d] error, failed to create java PtsEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, ptsEvent);

    env->DeleteLocalRef(ptsEvent);
}

void PlaybackListenerWrapper::notifyVideoFormatChangeEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject videoFormatChangeEvent = nullptr;
    if (!ASPlayerJni::createVideoFormatChangeEvent(env, event, &videoFormatChangeEvent)) {
        ALOGE("[%s/%d] error, failed to create java VideoFormatChangeEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, videoFormatChangeEvent);

    env->DeleteLocalRef(videoFormatChangeEvent);
}

void PlaybackListenerWrapper::notifyAudioFormatChangeEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject audioFormatChangeEvent = nullptr;
    if (!ASPlayerJni::createAudioFormatChangeEvent(env, event, &audioFormatChangeEvent)) {
        ALOGE("[%s/%d] error, failed to create java AudioFormatChangeEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, audioFormatChangeEvent);

    env->DeleteLocalRef(audioFormatChangeEvent);
}

void PlaybackListenerWrapper::notifyVideoFirstFrameEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject videoFirstFrameEvent = nullptr;
    if (!ASPlayerJni::createVideoFirstFrameEvent(env, event, &videoFirstFrameEvent)) {
        ALOGE("[%s/%d] error, failed to create java VideoFirstFrameEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, videoFirstFrameEvent);

    env->DeleteLocalRef(videoFirstFrameEvent);
}

void PlaybackListenerWrapper::notifyAudioFirstFrameEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject audioFirstFrameEvent = nullptr;
    if (!ASPlayerJni::createAudioFirstFrameEvent(env, event, &audioFirstFrameEvent)) {
        ALOGE("[%s/%d] error, failed to create java AudioFirstFrameEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, audioFirstFrameEvent);

    env->DeleteLocalRef(audioFirstFrameEvent);
}

void PlaybackListenerWrapper::notifyDecodeFirstVideoFrameEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject decodeFirstVideoFrameEvent = nullptr;
    if (!ASPlayerJni::createDecodeFirstVideoFrameEvent(env, event, &decodeFirstVideoFrameEvent)) {
        ALOGE("[%s/%d] error, failed to create java DecodeFirstVideoFrameEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, decodeFirstVideoFrameEvent);

    env->DeleteLocalRef(decodeFirstVideoFrameEvent);
}

void PlaybackListenerWrapper::notifyDecodeFirstAudioFrameEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject decodeFirstAudioFrameEvent = nullptr;
    if (!ASPlayerJni::createDecodeFirstVideoFrameEvent(env, event, &decodeFirstAudioFrameEvent)) {
        ALOGE("[%s/%d] error, failed to create java DecodeFirstAudioFrameEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, decodeFirstAudioFrameEvent);

    env->DeleteLocalRef(decodeFirstAudioFrameEvent);
}

void PlaybackListenerWrapper::notifyPlaybackInfoEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    }

    JNIEnv *env = AutoEnv::GetJniEnv();
    if (env == nullptr) {
        ALOGE("[%s/%d] error, failed to get JNIEnv", __func__, __LINE__);
        return;
    }

    jobject playbackInfoEvent = nullptr;
    if (!ASPlayerJni::createPlaybackInfoEvent(env, event, &playbackInfoEvent)) {
        ALOGE("[%s/%d] error, failed to create java PlaybackInfoEvent", __func__, __LINE__);
        return;
    }

    ASPlayerJni::notifyPlaybackEvent(env, mJavaListener, playbackInfoEvent);

    env->DeleteLocalRef(playbackInfoEvent);
}

void PlaybackListenerWrapper::release() {
    JNIEnv *env = AutoEnv::GetJniEnv();
    if (mJavaListener != nullptr) {
        env->DeleteGlobalRef(mJavaListener);
        mJavaListener = nullptr;
    }
}