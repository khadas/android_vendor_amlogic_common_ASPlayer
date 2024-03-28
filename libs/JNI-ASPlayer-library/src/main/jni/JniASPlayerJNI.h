/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_JNI_H
#define JNI_ASPLAYER_JNI_H

#include <jni.h>
#include <mutex>
#include "JNIASPlayer.h"
#include "JniBundle.h"
#include "JniPlaybackListener.h"

#ifdef __cplusplus
extern "C" {
#endif

class JniASPlayerJNI {

public:
    static void setJavaVM(JavaVM* javaVM);

    /** returns a pointer to the JavaVM provided when we initialized the module */
    static JavaVM* getJavaVM() { return mJavaVM; }

    /** return a pointer to the JNIEnv for this thread */
    static JNIEnv* getJNIEnv();

    /** create a JNIEnv* for this thread or assert if one already exists */
    static JNIEnv* attachJNIEnv(const char* envName);

    /** detach the current thread from the JavaVM */
    static void detachJNIEnv();

    static JNIEnv* getOrAttachJNIEnvironment();

    static bool createJniASPlayer(JNIEnv *env, jni_asplayer_init_params *params, void *tuner, jobject *outJniASPlayer);
    static bool createInitParams(JNIEnv *env, jni_asplayer_init_params *params, jobject *outJInitParams);
    static bool createVideoParams(JNIEnv *env, jni_asplayer_video_params *params, jobject *outJVideoParams);
    static bool createAudioPresentation(JNIEnv *env, jni_asplayer_audio_presentation *presentation, jobject *outAudioPresentation);
    static bool createAudioLanguage(JNIEnv *env, jni_asplayer_audio_lang *lang, jobject *outJAudioLang);
    static bool createAudioParams(JNIEnv *env, jni_asplayer_audio_params *params, jobject *outJAudioParams);
    static bool createInputBuffer(JNIEnv *env, jni_asplayer_input_buffer *inputBuffer, jobject *outJInputBuffer);

    static bool initASPlayerJNI(JNIEnv *env);

protected:
    /* JNI JavaVM pointer */
    static JavaVM* mJavaVM;

private:

    static int initASPlayerNotify(JNIEnv *env);
};

class JniASPlayer {
public:
    JniASPlayer();
    virtual ~JniASPlayer();

public:
    bool create(jni_asplayer_init_params *params, void* tuner);

    bool getJavaASPlayer(jobject **pPlayer);

    jni_asplayer_result prepare();

    jni_asplayer_result getInstanceNo(int32_t *numb);

    jni_asplayer_result getSyncInstanceNo(int32_t *numb);

    jni_asplayer_result addPlaybackListener(jobject listener);

    jni_asplayer_result removePlaybackListener(jobject listener);

    jni_asplayer_result startVideoDecoding();

    jni_asplayer_result stopVideoDecoding();

    jni_asplayer_result pauseVideoDecoding();

    jni_asplayer_result resumeVideoDecoding();

    jni_asplayer_result startAudioDecoding();

    jni_asplayer_result pauseAudioDecoding();

    jni_asplayer_result resumeAudioDecoding();

    jni_asplayer_result stopAudioDecoding();

    jni_asplayer_result setVideoParams(jni_asplayer_video_params *params);

    jni_asplayer_result setAudioParams(jni_asplayer_audio_params *params);

    jni_asplayer_result switchAudioTrack(jni_asplayer_audio_params *params);

    jni_asplayer_result flush();

    jni_asplayer_result flushDvr();

    jni_asplayer_result writeData(jni_asplayer_input_buffer *buffer, uint64_t timeout_ms);

    jni_asplayer_result setSurface(void *surface);

    jni_asplayer_result setAudioMute(bool mute);

    jni_asplayer_result setAudioVolume(int32_t volume);

    jni_asplayer_result getAudioVolume(int *volume);

    jni_asplayer_result startFast(float scale);

    jni_asplayer_result stopFast();

    jni_asplayer_result setTrickMode(jni_asplayer_video_trick_mode trickMode);

    jni_asplayer_result setTransitionModeBefore(jni_asplayer_transition_mode_before mode);

    jni_asplayer_result setTransitionModeAfter(jni_asplayer_transition_mode_after mode);

    jni_asplayer_result setTransitionPrerollRate(float rate);

    jni_asplayer_result setTransitionPrerollAvTolerance(int32_t milliSecond);

    jni_asplayer_result setVideoMute(jni_asplayer_video_mute mute);

    jni_asplayer_result setScreenColor(jni_asplayer_screen_color_mode mode, jni_asplayer_screen_color color);

    void setEventCallback(event_callback callback, void *eventUserData);

    bool getEventCallback(event_callback *callback, void **userdata);

    void release();

    jni_asplayer_result setWorkMode(jni_asplayer_work_mode mode);

    jni_asplayer_result resetWorkMode();

    jni_asplayer_result setPIPMode(jni_asplayer_pip_mode mode);

    jni_asplayer_result setADParams(jni_asplayer_audio_params *params);

    jni_asplayer_result enableADMix();

    jni_asplayer_result disableADMix();

    jni_asplayer_result setADVolumeDB(float volumeDB);

    jni_asplayer_result getADVolumeDB(float *volumeDB);

    jni_asplayer_result setADMixLevel(int32_t mixLevel);

    jni_asplayer_result getADMixLevel(int32_t *mixLevel);

    jni_asplayer_result setAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode mode);

    jni_asplayer_result getAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode *mode);

    jni_asplayer_result getVideoInfo(jni_asplayer_video_info *videoInfo);

    jni_asplayer_result setParameter(jni_asplayer_parameter type, void *arg);

    jni_asplayer_result getParameter(jni_asplayer_parameter type, void *arg);

private:
    int setJavaASPlayerHandle(JNIEnv *env, jobject javaPlayer);

    /* audio */
    jni_asplayer_result setAudioPresentationId(jni_asplayer_audio_presentation *audioPresentation);
    jni_asplayer_result getAudioPresentationId(jni_asplayer_audio_presentation *audioPresentation);
    jni_asplayer_result setAudioLanguage(jni_asplayer_audio_lang *lang);
    jni_asplayer_result setAudioSpdifProtectionMode(int32_t mode);

    jni_asplayer_result setBundleParameters(JNIEnv *env, JniBundle *bundle);
    jni_asplayer_result setParameters(JNIEnv *env, jobject bundleObj);

private:
    jobject mJavaPlayer;
    JniPlaybackListener *mPlaybackListener;
    event_callback mEventCallback;
    void *mEventUserData;
    std::mutex mMutex;
    std::mutex mEventMutex;
};

#ifdef __cplusplus
};
#endif

#endif //JNI_ASPLAYER_JNI_H
