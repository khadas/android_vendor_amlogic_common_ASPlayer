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
#include <JNIASPlayer.h>
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

    // returns true if an exception is set (and dumps it out to the Log)
    static bool hasException(JNIEnv *env);

    static bool createJniASPlayer(JNIEnv *env, jni_asplayer_init_params params, void *tuner, jobject *outJniASPlayer);
    static bool createInitParams(JNIEnv *env, jni_asplayer_init_params params, jobject *outJInitParams);
    static bool createVideoParams(JNIEnv *env, jni_asplayer_video_params *params, jobject *outJVideoParams);
    static bool createAudioParams(JNIEnv *env, jni_asplayer_audio_params *params, jobject *outJAudioParams);
    static bool createInputBuffer(JNIEnv *env, jni_asplayer_input_buffer *inputBuffer, jobject *outJInputBuffer);

    static bool initASPlayerJNI(JNIEnv *env);

    static jclass findClass(const char *name);

protected:
    /* JNI JavaVM pointer */
    static JavaVM* mJavaVM;

private:
    static int initJNIEnv(JNIEnv *env);

    static int initASPlayerNotify(JNIEnv *env);
};

class JniASPlayer {
public:
    JniASPlayer();
    virtual ~JniASPlayer();

public:
    bool create(jni_asplayer_init_params params, void* tuner);

    bool getJavaASPlayer(jobject **pPlayer);

    int prepare();

    int addPlaybackListener(jobject listener);

    int removePlaybackListener(jobject listener);

    int startVideoDecoding();

    int stopVideoDecoding();

    int pauseVideoDecoding();

    int resumeVideoDecoding();

    int startAudioDecoding();

    int pauseAudioDecoding();

    int resumeAudioDecoding();

    int stopAudioDecoding();

    int setVideoParams(jni_asplayer_video_params *params);

    int setAudioParams(jni_asplayer_audio_params *params);

    void flush();

    void flushDvr();

    int writeData(jni_asplayer_input_buffer *buffer, uint64_t timeout_ms);

    int setSurface(void *surface);

    int setAudioMute(bool analogMute, bool digitMute);

    void setAudioVolume(int32_t volume);

    bool getAudioVolume(int *volume);

    int startFast(float scale);

    int stopFast();

    void setEventCallback(event_callback callback, void *eventUserData);

    bool getEventCallback(event_callback *callback, void **userdata);

    void release();

private:
    int setJavaASPlayerHandle(JNIEnv *env, jobject javaPlayer);

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
