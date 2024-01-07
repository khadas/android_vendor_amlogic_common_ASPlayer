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
    virtual jni_asplayer_result getInstanceNo(int32_t *numb) = 0;
    virtual jni_asplayer_result getSyncInstanceNo(int32_t *numb) = 0;
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
    virtual jni_asplayer_result switchAudioTrack(jni_asplayer_audio_params *params) = 0;
    virtual jni_asplayer_result flush() = 0;
    virtual jni_asplayer_result flushDvr() = 0;
    virtual jni_asplayer_result writeData(jni_asplayer_input_buffer *buf, uint64_t timeout_ms) = 0;
    virtual jni_asplayer_result setSurface(void *surface) = 0;
    virtual jni_asplayer_result setAudioMute(bool analogMute, bool digitMute) = 0;
    virtual jni_asplayer_result setAudioVolume(int volume) = 0;
    virtual jni_asplayer_result getAudioVolume(int *volume) = 0;
    virtual jni_asplayer_result startFast(float scale) = 0;
    virtual jni_asplayer_result stopFast() = 0;
    virtual jni_asplayer_result setTrickMode(jni_asplayer_video_trick_mode trickMode) = 0;
    virtual jni_asplayer_result setTransitionModeBefore(jni_asplayer_transition_mode_before mode) = 0;
    virtual jni_asplayer_result setTransitionModeAfter(jni_asplayer_transition_mode_after mode) = 0;
    virtual jni_asplayer_result setTransitionPrerollRate(float rate) = 0;
    virtual jni_asplayer_result setTransitionPrerollAvTolerance(int32_t milliSecond) = 0;
    virtual jni_asplayer_result setVideoMute(jni_asplayer_video_mute mute) = 0;
    virtual jni_asplayer_result setScreenColor(jni_asplayer_screen_color_mode mode,
                                               jni_asplayer_screen_color color) = 0;
    virtual jni_asplayer_result setWorkMode(jni_asplayer_work_mode mode) = 0;
    virtual jni_asplayer_result resetWorkMode() = 0;
    virtual jni_asplayer_result setPIPMode(jni_asplayer_pip_mode mode) = 0;
    virtual jni_asplayer_result setADParams(jni_asplayer_audio_params *params) = 0;
    virtual jni_asplayer_result enableADMix() = 0;
    virtual jni_asplayer_result disableADMix() = 0;
    virtual jni_asplayer_result setADVolumeDB(float volumeDb) = 0;
    virtual jni_asplayer_result getADVolumeDB(float *volumeDb) = 0;
    virtual jni_asplayer_result setADMixLevel(int32_t mixLevel) = 0;
    virtual jni_asplayer_result getADMixLevel(int32_t *mixLevel) = 0;
    virtual jni_asplayer_result setAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode mode) = 0;
    virtual jni_asplayer_result getAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode *mode) = 0;
    virtual jni_asplayer_result getVideoInfo(jni_asplayer_video_info *videoInfo) = 0;
    virtual jni_asplayer_result setParameter(jni_asplayer_parameter type, void *arg) = 0;
    virtual jni_asplayer_result getParameter(jni_asplayer_parameter type, void *arg) = 0;
    virtual jni_asplayer_result release() = 0;

    virtual void notifyPlaybackListeners(jni_asplayer_event *event) = 0;

public:
    BaseJniASPlayerWrapper() { };
    virtual ~BaseJniASPlayerWrapper() { };
};

#endif //JNI_ASPLAYER_WRAPPER_BASE_JNI_ASPLAYERWRAPPER_H
