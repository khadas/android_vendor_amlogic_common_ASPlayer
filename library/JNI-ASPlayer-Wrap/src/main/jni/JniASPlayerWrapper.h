/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_JNI_ASPLAYER_WRAPPER_H
#define JNI_ASPLAYER_WRAPPER_JNI_ASPLAYER_WRAPPER_H

#if (USE_SYSTEM_SO == 0)

#include <list>
#include <mutex>
#include "BaseJniASPlayerWrapper.h"
#include "PlaybackListenerWrapper.h"

class JniASPlayerWrapper;

struct asplayer_callback_userdata_t {
    JniASPlayerWrapper *player;
};

class JniASPlayerWrapper : public BaseJniASPlayerWrapper {

public:
    void setHandle(jni_asplayer_handle handle);
    jni_asplayer_handle getHandle();

    jni_asplayer_result create(jni_asplayer_init_params &params, void *jTuner, jni_asplayer_handle *handle);
    jni_asplayer_result getJavaASPlayer(jobject *pPlayer);
    jni_asplayer_result prepare();
    jni_asplayer_result addPlaybackListener(JNIEnv *env, jobject listener);
    jni_asplayer_result removePlaybackListener(JNIEnv *env, jobject listener);
    jni_asplayer_result startVideoDecoding();
    jni_asplayer_result stopVideoDecoding();
    jni_asplayer_result pauseVideoDecoding();
    jni_asplayer_result resumeVideoDecoding();
    jni_asplayer_result startAudioDecoding();
    jni_asplayer_result stopAudioDecoding();
    jni_asplayer_result pauseAudioDecoding();
    jni_asplayer_result resumeAudioDecoding();
    jni_asplayer_result setVideoParams(jni_asplayer_video_params *params);
    jni_asplayer_result setAudioParams(jni_asplayer_audio_params *params);
    jni_asplayer_result switchAudioTrack(jni_asplayer_audio_params *params);
    jni_asplayer_result flush();
    jni_asplayer_result flushDvr();
    jni_asplayer_result writeData(jni_asplayer_input_buffer *buf, uint64_t timeout_ms);
    jni_asplayer_result setSurface(void *surface);
    jni_asplayer_result setAudioMute(bool analogMute, bool digitMute);
    jni_asplayer_result setAudioVolume(int volume);
    jni_asplayer_result getAudioVolume(int *volume);
    jni_asplayer_result startFast(float scale);
    jni_asplayer_result stopFast();
    jni_asplayer_result setTrickMode(jni_asplayer_video_trick_mode trickMode);
    jni_asplayer_result setTransitionModeBefore(jni_asplayer_transition_mode_before mode);
    jni_asplayer_result setPIPMode(jni_asplayer_pip_mode mode);
    jni_asplayer_result setADParams(jni_asplayer_audio_params *params);
    jni_asplayer_result enableADMix();
    jni_asplayer_result disableADMix();
    jni_asplayer_result release();

    void notifyPlaybackListeners(jni_asplayer_event *event);

public:
    JniASPlayerWrapper();
    virtual ~JniASPlayerWrapper();

private:
    jni_asplayer_handle mHandle;
    asplayer_callback_userdata_t *mpCallbackUserData;
    std::mutex mMutex;
    std::mutex mEventMutex;

    std::list<PlaybackListenerWrapper*> mPlaybackListeners;
};

#endif


#endif //JNI_ASPLAYER_WRAPPER_JNI_ASPLAYER_WRAPPER_H
