/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_DYNAMIC_JNI_ASPLAYER_WRAPPER_H
#define JNI_ASPLAYER_WRAPPER_DYNAMIC_JNI_ASPLAYER_WRAPPER_H

#include <list>
#include <mutex>
#include "BaseJniASPlayerWrapper.h"
#include "PlaybackListenerWrapper.h"

typedef jni_asplayer_result (*JniASPlayer_registerJNI_FUNC)(JNIEnv*);
typedef jni_asplayer_result (*JniASPlayer_create_FUNC)(jni_asplayer_init_params, void *, jni_asplayer_handle *);
typedef jni_asplayer_result (*JniASPlayer_getJavaASPlayer_FUNC)(jni_asplayer_handle, jobject*);
typedef jni_asplayer_result (*JniASPlayer_getVersion_FUNC)(uint32_t *, uint32_t *);
typedef jni_asplayer_result (*JniASPlayer_getInstanceNo_FUNC)(jni_asplayer_handle, int32_t *);
typedef jni_asplayer_result (*JniASPlayer_getSyncInstanceNo_FUNC)(jni_asplayer_handle, int32_t *);
typedef jni_asplayer_result (*JniASPlayer_prepare_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_registerCb_FUNC)(jni_asplayer_handle, event_callback, void*);
typedef jni_asplayer_result (*JniASPlayer_getCb_FUNC)(jni_asplayer_handle, event_callback *, void**);
typedef jni_asplayer_result (*JniASPlayer_release_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_writeFrameData_FUNC)(jni_asplayer_handle, jni_asplayer_input_frame_buffer *, uint64_t);
typedef jni_asplayer_result (*JniASPlayer_writeData_FUNC)(jni_asplayer_handle, jni_asplayer_input_buffer *, uint64_t);
typedef jni_asplayer_result (*JniASPlayer_setWorkMode_FUNC)(jni_asplayer_handle, jni_asplayer_work_mode);
typedef jni_asplayer_result (*JniASPlayer_resetWorkMode_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_setPIPMode_FUNC)(jni_asplayer_handle, jni_asplayer_pip_mode);
typedef jni_asplayer_result (*JniASPlayer_getCurrentTime_FUNC)(jni_asplayer_handle, int64_t *);
typedef jni_asplayer_result (*JniASPlayer_setSyncMode_FUNC)(jni_asplayer_handle, jni_asplayer_avsync_mode);
typedef jni_asplayer_result (*JniASPlayer_getSyncMode_FUNC)(jni_asplayer_handle, jni_asplayer_avsync_mode *);
typedef jni_asplayer_result (*JniASPlayer_setPcrPid_FUNC)(jni_asplayer_handle, uint32_t);
typedef jni_asplayer_result (*JniASPlayer_startFast_FUNC)(jni_asplayer_handle, float);
typedef jni_asplayer_result (*JniASPlayer_stopFast_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_setTrickMode_FUNC)(jni_asplayer_handle, jni_asplayer_video_trick_mode);
typedef jni_asplayer_result (*JniASPlayer_setSurface_FUNC)(jni_asplayer_handle, void*);
typedef jni_asplayer_result (*JniASPlayer_setVideoParams_FUNC)(jni_asplayer_handle, jni_asplayer_video_params *);
typedef jni_asplayer_result (*JniASPlayer_setTransitionModeBefore_FUNC)(jni_asplayer_handle, jni_asplayer_transition_mode_before);
typedef jni_asplayer_result (*JniASPlayer_setTransitionModeAfter_FUNC)(jni_asplayer_handle, jni_asplayer_transition_mode_after);
typedef jni_asplayer_result (*JniASPlayer_setTransitionPrerollRate_FUNC)(jni_asplayer_handle, float);
typedef jni_asplayer_result (*JniASPlayer_setTransitionPrerollAvTolerance_FUNC)(jni_asplayer_handle, int32_t);
typedef jni_asplayer_result (*JniASPlayer_setVideoMute_FUNC)(jni_asplayer_handle, jni_asplayer_video_mute);
typedef jni_asplayer_result (*JniASPlayer_setScreenColor_FUNC)(jni_asplayer_handle, jni_asplayer_screen_color_mode, jni_asplayer_screen_color);
typedef jni_asplayer_result (*JniASPlayer_getVideoInfo_FUNC)(jni_asplayer_handle, jni_asplayer_video_info *);
typedef jni_asplayer_result (*JniASPlayer_startVideoDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_pauseVideoDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_resumeVideoDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_stopVideoDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_setAudioVolume_FUNC)(jni_asplayer_handle, int32_t);
typedef jni_asplayer_result (*JniASPlayer_getAudioVolume_FUNC)(jni_asplayer_handle, int32_t *);
typedef jni_asplayer_result (*JniASPlayer_setAudioDualMonoMode_FUNC)(jni_asplayer_handle, jni_asplayer_audio_dual_mono_mode);
typedef jni_asplayer_result (*JniASPlayer_getAudioDualMonoMode_FUNC)(jni_asplayer_handle, jni_asplayer_audio_dual_mono_mode *);
typedef jni_asplayer_result (*JniASPlayer_setAudioMute_FUNC)(jni_asplayer_handle, bool_t);
typedef jni_asplayer_result (*JniASPlayer_getAudioMute_FUNC)(jni_asplayer_handle Hadl, bool_t *, bool_t *);
typedef jni_asplayer_result (*JniASPlayer_setAudioParams_FUNC)(jni_asplayer_handle, jni_asplayer_audio_params *);
typedef jni_asplayer_result (*JniASPlayer_switchAudioTrack_FUNC)(jni_asplayer_handle, jni_asplayer_audio_params *);
typedef jni_asplayer_result (*JniASPlayer_getAudioInfo_FUNC)(jni_asplayer_handle,  jni_asplayer_audio_info *);
typedef jni_asplayer_result (*JniASPlayer_startAudioDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_pauseAudioDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_resumeAudioDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_stopAudioDecoding_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_getADInfo_FUNC)(jni_asplayer_handle, jni_asplayer_audio_info *);
typedef jni_asplayer_result (*JniASPlayer_setSubPid_FUNC)(jni_asplayer_handle, uint32_t);
typedef jni_asplayer_result (*JniASPlayer_getParams_FUNC)(jni_asplayer_handle, jni_asplayer_parameter, void*);
typedef jni_asplayer_result (*JniASPlayer_setParams_FUNC)(jni_asplayer_handle, jni_asplayer_parameter, void*);
typedef jni_asplayer_result (*JniASPlayer_getState_FUNC)(jni_asplayer_handle, jni_asplayer_state_t*);
typedef jni_asplayer_result (*JniASPlayer_startSub_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_stopSub_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_getFirstPts_FUNC)(jni_asplayer_handle, jni_asplayer_stream_type, uint64_t *);
typedef jni_asplayer_result (*JniASPlayer_flush_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_flushDvr_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_setADParams_FUNC)(jni_asplayer_handle, jni_asplayer_audio_params *);
typedef jni_asplayer_result (*JniASPlayer_enableADMix_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_disableADMix_FUNC)(jni_asplayer_handle);
typedef jni_asplayer_result (*JniASPlayer_setADVolumeDB_FUNC)(jni_asplayer_handle, float);
typedef jni_asplayer_result (*JniASPlayer_getADVolumeDB_FUNC)(jni_asplayer_handle, float *);
typedef jni_asplayer_result (*JniASPlayer_setADMixLevel_FUNC)(jni_asplayer_handle, int32_t);
typedef jni_asplayer_result (*JniASPlayer_getADMixLevel_FUNC)(jni_asplayer_handle, int32_t *);

class DynamicJniASPlayerWrapper;

struct dasplayer_callback_userdata_t {
    DynamicJniASPlayerWrapper *player;
};

class DynamicJniASPlayerWrapper : public BaseJniASPlayerWrapper {

public:
    static jni_asplayer_result registerJni(JNIEnv *env);

    void setHandle(jni_asplayer_handle handle);
    jni_asplayer_handle getHandle();

    jni_asplayer_result create(jni_asplayer_init_params &params, void *jTuner, jni_asplayer_handle *handle);
    jni_asplayer_result getJavaASPlayer(jobject *pPlayer);
    jni_asplayer_result prepare();
    jni_asplayer_result getInstanceNo(int32_t *numb);
    jni_asplayer_result getSyncInstanceNo(int32_t *numb);
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
    jni_asplayer_result setAudioMute(bool mute);
    jni_asplayer_result setAudioVolume(int volume);
    jni_asplayer_result getAudioVolume(int *volume);
    jni_asplayer_result startFast(float scale);
    jni_asplayer_result stopFast();
    jni_asplayer_result setTrickMode(jni_asplayer_video_trick_mode trickMode);
    jni_asplayer_result setTransitionModeBefore(jni_asplayer_transition_mode_before mode);
    jni_asplayer_result setTransitionModeAfter(jni_asplayer_transition_mode_after mode);
    jni_asplayer_result setTransitionPrerollRate(float rate);
    jni_asplayer_result setTransitionPrerollAvTolerance(int32_t milliSecond);
    jni_asplayer_result setVideoMute(jni_asplayer_video_mute mute);
    jni_asplayer_result setScreenColor(jni_asplayer_screen_color_mode mode,
                                       jni_asplayer_screen_color color);
    jni_asplayer_result setWorkMode(jni_asplayer_work_mode mode);
    jni_asplayer_result resetWorkMode();
    jni_asplayer_result setPIPMode(jni_asplayer_pip_mode mode);
    jni_asplayer_result setADParams(jni_asplayer_audio_params *params);
    jni_asplayer_result enableADMix();
    jni_asplayer_result disableADMix();
    jni_asplayer_result setADVolumeDB(float volumeDb);
    jni_asplayer_result getADVolumeDB(float *volumeDb);
    jni_asplayer_result setADMixLevel(int32_t mixLevel);
    jni_asplayer_result getADMixLevel(int32_t *mixLevel);
    jni_asplayer_result setAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode mode);
    jni_asplayer_result getAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode *mode);
    jni_asplayer_result getVideoInfo(jni_asplayer_video_info *videoInfo);
    jni_asplayer_result setParameter(jni_asplayer_parameter type, void *arg);
    jni_asplayer_result getParameter(jni_asplayer_parameter type, void *arg);
    jni_asplayer_result release();

    void notifyPlaybackListeners(jni_asplayer_event *event);

public:
    DynamicJniASPlayerWrapper();
    virtual ~DynamicJniASPlayerWrapper();

private:
    static void openJniASPlayerSo();
    static void initSymbols();
    static void closeJniASPlayerSo();

private:
    jni_asplayer_handle mHandle;
    dasplayer_callback_userdata_t *mpCallbackUserData;
    std::mutex mMutex;
    std::mutex mEventMutex;

    std::list<PlaybackListenerWrapper*> mPlaybackListeners;

    static bool sInited;
    static void *sSoHandle;
    static JniASPlayer_registerJNI_FUNC ASPlayer_registerJni;
    static JniASPlayer_create_FUNC ASPlayer_create;
    static JniASPlayer_getJavaASPlayer_FUNC ASPlayer_getJavaASPlayer;
    static JniASPlayer_prepare_FUNC ASPlayer_prepare;
    static JniASPlayer_getVersion_FUNC ASPlayer_getVersion;
    static JniASPlayer_getInstanceNo_FUNC ASPlayer_getInstanceNo;
    static JniASPlayer_getSyncInstanceNo_FUNC ASPlayer_getSyncInstanceNo;
    static JniASPlayer_registerCb_FUNC ASPlayer_registerCb;
    static JniASPlayer_getCb_FUNC ASPlayer_getCb;
    static JniASPlayer_release_FUNC ASPlayer_release;
    static JniASPlayer_writeFrameData_FUNC ASPlayer_writeFrameData;
    static JniASPlayer_writeData_FUNC ASPlayer_writeData;
    static JniASPlayer_setWorkMode_FUNC ASPlayer_setWorkMode;
    static JniASPlayer_resetWorkMode_FUNC ASPlayer_resetWorkMode;
    static JniASPlayer_setPIPMode_FUNC ASPlayer_setPIPMode;
    static JniASPlayer_getCurrentTime_FUNC ASPlayer_getCurrentTime;
    static JniASPlayer_setSyncMode_FUNC ASPlayer_setSyncMode;
    static JniASPlayer_getSyncMode_FUNC ASPlayer_getSyncMode;
    static JniASPlayer_setPcrPid_FUNC ASPlayer_setPcrPid;
    static JniASPlayer_startFast_FUNC ASPlayer_startFast;
    static JniASPlayer_stopFast_FUNC ASPlayer_stopFast;
    static JniASPlayer_setTrickMode_FUNC ASPlayer_setTrickMode;
    static JniASPlayer_setSurface_FUNC ASPlayer_setSurface;
    static JniASPlayer_setVideoParams_FUNC ASPlayer_setVideoParams;
    static JniASPlayer_setTransitionModeBefore_FUNC ASPlayer_setTransitionModeBefore;
    static JniASPlayer_setTransitionModeAfter_FUNC ASPlayer_setTransitionModeAfter;
    static JniASPlayer_setScreenColor_FUNC ASPlayer_setScreenColor;
    static JniASPlayer_setTransitionPrerollRate_FUNC ASPlayer_setTransitionPrerollRate;
    static JniASPlayer_setTransitionPrerollAvTolerance_FUNC ASPlayer_setTransitionPrerollAvTolerance;
    static JniASPlayer_setVideoMute_FUNC ASPlayer_setVideoMute;
    static JniASPlayer_getVideoInfo_FUNC ASPlayer_getVideoInfo;
    static JniASPlayer_startVideoDecoding_FUNC ASPlayer_startVideoDecoding;
    static JniASPlayer_pauseVideoDecoding_FUNC ASPlayer_pauseVideoDecoding;
    static JniASPlayer_resumeVideoDecoding_FUNC ASPlayer_resumeVideoDecoding;
    static JniASPlayer_stopVideoDecoding_FUNC ASPlayer_stopVideoDecoding;
    static JniASPlayer_setAudioVolume_FUNC ASPlayer_setAudioVolume;
    static JniASPlayer_getAudioVolume_FUNC ASPlayer_getAudioVolume;
    static JniASPlayer_setAudioDualMonoMode_FUNC ASPlayer_setAudioDualMonoMode;
    static JniASPlayer_getAudioDualMonoMode_FUNC ASPlayer_getAudioDualMonoMode;
    static JniASPlayer_setAudioMute_FUNC ASPlayer_setAudioMute;
    static JniASPlayer_getAudioMute_FUNC ASPlayer_getAudioMute;
    static JniASPlayer_setAudioParams_FUNC ASPlayer_setAudioParams;
    static JniASPlayer_switchAudioTrack_FUNC ASPlayer_switchAudioTrack;
    static JniASPlayer_getAudioInfo_FUNC ASPlayer_getAudioInfo;
    static JniASPlayer_startAudioDecoding_FUNC ASPlayer_startAudioDecoding;
    static JniASPlayer_pauseAudioDecoding_FUNC ASPlayer_pauseAudioDecoding;
    static JniASPlayer_resumeAudioDecoding_FUNC ASPlayer_resumeAudioDecoding;
    static JniASPlayer_stopAudioDecoding_FUNC ASPlayer_stopAudioDecoding;
    static JniASPlayer_getADInfo_FUNC ASPlayer_getADInfo;
    static JniASPlayer_setSubPid_FUNC ASPlayer_setSubPid;
    static JniASPlayer_getParams_FUNC ASPlayer_getParams;
    static JniASPlayer_setParams_FUNC ASPlayer_setParams;
    static JniASPlayer_getState_FUNC ASPlayer_getState;
    static JniASPlayer_startSub_FUNC ASPlayer_startSub;
    static JniASPlayer_stopSub_FUNC ASPlayer_stopSub;
    static JniASPlayer_getFirstPts_FUNC ASPlayer_getFirstPts;
    static JniASPlayer_flush_FUNC ASPlayer_flush;
    static JniASPlayer_flushDvr_FUNC ASPlayer_flushDvr;
    static JniASPlayer_setADParams_FUNC ASPlayer_setADParams;
    static JniASPlayer_enableADMix_FUNC ASPlayer_enableADMix;
    static JniASPlayer_disableADMix_FUNC ASPlayer_disableADMix;
    static JniASPlayer_setADVolumeDB_FUNC ASPlayer_setADVolumeDB;
    static JniASPlayer_getADVolumeDB_FUNC ASPlayer_getADVolumeDB;
    static JniASPlayer_setADMixLevel_FUNC ASPlayer_setADMixLevel;
    static JniASPlayer_getADMixLevel_FUNC ASPlayer_getADMixLevel;
};


#endif //JNI_ASPLAYER_WRAPPER_DYNAMIC_JNI_ASPLAYER_WRAPPER_H
