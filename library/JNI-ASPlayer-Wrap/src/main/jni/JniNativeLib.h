/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_JNINATIVELIB_H
#define JNI_ASPLAYER_WRAPPER_JNINATIVELIB_H

#include <jni.h>
#include <JNIASPlayer.h>

#ifdef __cplusplus
extern "C" {
#endif

jni_asplayer_result
init_jni_env(JNIEnv *env);

jni_asplayer_result
create_asplayer(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject initParams, jobject jTuner);

jni_asplayer_result
asplayer_get_java_asplayer(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject *outASPlayer);

jni_asplayer_result
asplayer_prepare(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_add_playback_listener(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject jListener);

jni_asplayer_result
asplayer_remove_playback_listener(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject jListener);

jni_asplayer_result
asplayer_start_video_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_stop_video_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_pause_video_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_resume_video_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_start_audio_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_stop_audio_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_pause_audio_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_resume_audio_decoding(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_set_video_params(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject videoParams);

jni_asplayer_result
asplayer_set_audio_params(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject audioParams);

jni_asplayer_result
asplayer_switch_audio_track(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject audioParams);

jni_asplayer_result
asplayer_flush(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_flush_dvr(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_write_data(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject jInputBuffer,
                    jlong jTimeoutMillSecond);

jni_asplayer_result
asplayer_set_surface(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject jSurface);

jni_asplayer_result
asplayer_set_audio_mute(JNIEnv *env, jobject jniASPlayerWrapperObj, jboolean jAnalogMute,
                        jboolean jDigitMute);

jni_asplayer_result
asplayer_set_audio_volume(JNIEnv *env, jobject jniASPlayerWrapperObj, jint volume);

jint
asplayer_get_audio_volume(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_start_fast(JNIEnv *env, jobject jniASPlayerWrapperObj, jfloat scale);

jni_asplayer_result
asplayer_stop_fast(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_set_trick_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jTrickMode);

jni_asplayer_result
asplayer_set_transition_mode_before(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jMode);

jni_asplayer_result
asplayer_set_pip_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint mode);

jni_asplayer_result
asplayer_set_ad_params(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject audioParams);

jni_asplayer_result
asplayer_enable_ad_mix(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_disable_ad_mix(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_set_work_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint mode);

jni_asplayer_result
asplayer_reset_work_mode(JNIEnv *env, jobject jniASPlayerWrapperObj);

jni_asplayer_result
asplayer_release(JNIEnv *env, jobject jniASPlayerWrapperObj);

#ifdef __cplusplus
};
#endif

#endif //JNI_ASPLAYER_WRAPPER_JNINATIVELIB_H
