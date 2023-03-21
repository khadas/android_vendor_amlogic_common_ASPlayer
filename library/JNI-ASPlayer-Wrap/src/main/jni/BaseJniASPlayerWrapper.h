/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_BASE_JNI_ASPLAYERWRAPPER_H
#define JNI_ASPLAYER_WRAPPER_BASE_JNI_ASPLAYERWRAPPER_H

#include <jni.h>
#include "JNIASPlayer.h"

class BaseJniASPlayerWrapper {

public:
    virtual void setHandle(jni_asplayer_handle handle) = 0;
    virtual jni_asplayer_handle getHandle() = 0;

    virtual jni_asplayer_result create(jni_asplayer_init_params &params, void *jTuner, jni_asplayer_handle *handle) = 0;
    virtual jni_asplayer_result getJavaASPlayer(jobject *pPlayer) = 0;
    virtual jni_asplayer_result prepare() = 0;
    virtual jni_asplayer_result addPlaybackListener(JNIEnv *env, jobject listener) = 0;
    virtual jni_asplayer_result removePlaybackListener(JNIEnv *env, jobject listener) = 0;
    virtual jni_asplayer_result startVideoDecoding() = 0;
    virtual jni_asplayer_result stopVideoDecoding() = 0;
    virtual jni_asplayer_result pauseVideoDecoding() = 0;
    virtual jni_asplayer_result resumeVideoDecoding() = 0;
    virtual jni_asplayer_result startAudioDecoding() = 0;
    virtual jni_asplayer_result stopAudioDecoding() = 0;
    virtual jni_asplayer_result pauseAudioDecoding() = 0;
    virtual jni_asplayer_result resumeAudioDecoding() = 0;
    virtual jni_asplayer_result setVideoParams(jni_asplayer_video_params *params) = 0;
    virtual jni_asplayer_result setAudioParams(jni_asplayer_audio_params *params) = 0;
    virtual jni_asplayer_result flush() = 0;
    virtual jni_asplayer_result writeData(jni_asplayer_input_buffer *buf, uint64_t timeout_ms) = 0;
    virtual jni_asplayer_result setSurface(void *surface) = 0;
    virtual jni_asplayer_result setAudioMute(bool analogMute, bool digitMute) = 0;
    virtual jni_asplayer_result setAudioVolume(int volume) = 0;
    virtual jni_asplayer_result getAudioVolume(int *volume) = 0;
    virtual jni_asplayer_result release() = 0;

    virtual void notifyPlaybackListeners(jni_asplayer_event *event) = 0;

public:
    BaseJniASPlayerWrapper() { };
    virtual ~BaseJniASPlayerWrapper() { };
};

#endif //JNI_ASPLAYER_WRAPPER_BASE_JNI_ASPLAYERWRAPPER_H
