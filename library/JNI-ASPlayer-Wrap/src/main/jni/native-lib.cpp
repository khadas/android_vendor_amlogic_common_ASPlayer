/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <jni.h>

#include <unistd.h>
#include <dlfcn.h>

#include "config.h"

#include "common/utils/Log.h"
#include "common/utils/AutoEnv.h"

#include "JniNativeLib.h"
#include "NativeHelper.h"

#define LOG_NULL_PARAM(ptr) ALOGE("[%s/%d] failed, null pointer" #ptr "", __FUNCTION__, __LINE__)
#define LOG_GET_JNIENV_FAILED() ALOGE("[%s/%d] failed to get jni env", __FUNCTION__, __LINE__)

extern char *CLASS_PATH_NAME;

#ifdef __cplusplus
extern "C" {
#endif


static void
native_init(JNIEnv* env) {
    jni_asplayer_result ret = init_jni_env(env);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("%s[%d] failed to init jni", __func__, __LINE__);
    } else {
        ALOGD("%s[%d] init jni success", __func__, __LINE__);
    }
}

static void
native_create(JNIEnv* env, jobject thiz, jobject initParams, jobject tuner) {
    jni_asplayer_result ret = create_asplayer(env, thiz, initParams, tuner);

    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed to create asplayer, ret: %d", __func__, __LINE__, ret);
    }
}

static jobject
native_getJavaASPlayer(JNIEnv* env, jobject thiz) {
    jobject asplayer = nullptr;

    jni_asplayer_result ret = asplayer_get_java_asplayer(env, thiz, &asplayer);

    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("%s[%d] failed to get java ASPlayer, ret: %d", __func__, __LINE__, ret);
    }

    return asplayer;
}

static jint
native_prepare(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_prepare(env, thiz);

    return result;
}

static jint
native_get_instance_no(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = JNI_ASPLAYER_OK;
    int32_t numb = -1;

    result = asplayer_get_instance_no(env, thiz, &numb);

    if (result == JNI_ASPLAYER_OK) {
        return (jint) numb;
    } else {
        return -1;
    }
}

static jint
native_get_sync_instance_no(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = JNI_ASPLAYER_OK;
    int32_t numb = -1;

    result = asplayer_get_sync_instance_no(env, thiz, &numb);

    if (result == JNI_ASPLAYER_OK) {
        return (jint) numb;
    } else {
        return -1;
    }
}

static void
native_add_playback_listener(JNIEnv* env, jobject thiz, jobject jListener) {
    jni_asplayer_result result = asplayer_add_playback_listener(env, thiz, jListener);

    if (result != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] add playback listener failed, result: %d", __func__, __LINE__, result);
    }
}

static void
native_remove_playback_listener(JNIEnv* env, jobject thiz, jobject jListener) {
    jni_asplayer_result result = asplayer_remove_playback_listener(env, thiz, jListener);

    if (result != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] remove playback listener failed, result: %d", __func__, __LINE__, result);
    }
}

static jint
native_start_video_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_start_video_decoding(env, thiz);

    return result;
}

static jint
native_stop_video_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = JNI_ASPLAYER_OK;

    result = asplayer_stop_video_decoding(env, thiz);

    return result;
}

static jint
native_pause_video_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_pause_video_decoding(env, thiz);

    return result;
}

static jint
native_resume_video_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_resume_video_decoding(env, thiz);

    return result;
}

static jint
native_start_audio_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_start_audio_decoding(env, thiz);

    return result;
}

static jint
native_stop_audio_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_stop_audio_decoding(env, thiz);

    return result;
}

static jint
native_pause_audio_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_pause_audio_decoding(env, thiz);

    return result;
}

static jint
native_resume_audio_decoding(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_resume_audio_decoding(env, thiz);

    return result;
}

static jint
native_set_video_params(JNIEnv* env, jobject thiz, jobject videoParams) {
    jni_asplayer_result result = asplayer_set_video_params(env, thiz, videoParams);

    return result;
}

static jint
native_set_audio_params(JNIEnv* env, jobject thiz, jobject audioParams) {
    jni_asplayer_result result = asplayer_set_audio_params(env, thiz, audioParams);

    return result;
}

static jint
native_switch_audio_track(JNIEnv* env, jobject thiz, jobject audioParams) {
    jni_asplayer_result result = asplayer_switch_audio_track(env, thiz, audioParams);

    return result;
}

static jint
native_flush(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_flush(env, thiz);

    return result;
}

static jint
native_flush_dvr(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_flush_dvr(env, thiz);

    return result;
}

static jint
native_write_data(JNIEnv* env, jobject thiz, jobject jInputBuffer, jlong jTimeoutMillSecond) {
    jint result = asplayer_write_data(env, thiz, jInputBuffer, jTimeoutMillSecond);
    return result;
}

static jint
native_set_surface(JNIEnv* env, jobject thiz, jobject jSurface) {
    jni_asplayer_result result = asplayer_set_surface(env, thiz, jSurface);

    return result;
}

static jint
native_set_audio_mute(JNIEnv* env, jobject thiz, jboolean jMute) {
    jni_asplayer_result result = asplayer_set_audio_mute(env, thiz, jMute);

    return result;
}

static jint
native_set_audio_volume(JNIEnv* env, jobject thiz, jint volume) {
    jni_asplayer_result result = asplayer_set_audio_volume(env, thiz, volume);

    return result;
}

static jint
native_get_audio_volume(JNIEnv *env, jobject thiz) {
    jint result = asplayer_get_audio_volume(env, thiz);

    return result;
}

static jint
native_start_fast(JNIEnv *env, jobject thiz, jfloat scale) {
    jni_asplayer_result result = asplayer_start_fast(env, thiz, scale);

    return result;
}

static jint
native_stop_fast(JNIEnv *env, jobject thiz) {
    jni_asplayer_result result = asplayer_stop_fast(env, thiz);

    return result;
}

static jint
native_set_trick_mode(JNIEnv *env, jobject thiz, jint jTrickMode) {
    jni_asplayer_result result = asplayer_set_trick_mode(env, thiz, jTrickMode);

    return result;
}

static jint
native_set_transition_mode_before(JNIEnv *env, jobject thiz, jint jMode) {
    jni_asplayer_result result = asplayer_set_transition_mode_before(env, thiz, jMode);

    return result;
}

static jint
native_set_transition_mode_after(JNIEnv *env, jobject thiz, jint jMode) {
    jni_asplayer_result result = asplayer_set_transition_mode_after(env, thiz, jMode);

    return result;
}

static jint
native_set_transition_preroll_rate(JNIEnv *env, jobject thiz, jfloat jRate) {
    jni_asplayer_result result = asplayer_set_transition_preroll_rate(env, thiz, jRate);

    return result;
}

static jint
native_set_transition_preroll_av_tolerance(JNIEnv *env, jobject thiz, jint jMilliSecond) {
    jni_asplayer_result result = asplayer_set_transition_preroll_av_tolerance(env, thiz, jMilliSecond);

    return result;
}

static jint
native_set_video_mute(JNIEnv *env, jobject thiz, jint jMute) {
    jni_asplayer_result result = asplayer_set_video_mute(env, thiz, jMute);

    return result;
}

static jint
native_set_screen_color(JNIEnv *env, jobject thiz, jint jMode, jint jColor) {
    jni_asplayer_result result = asplayer_set_screen_color(env, thiz, jMode, jColor);

    return result;
}

static jint
native_set_pip_mode(JNIEnv *env, jobject thiz, jint pipMode) {
    jni_asplayer_result result = asplayer_set_pip_mode(env, thiz, pipMode);

    return result;
}

static jint
native_set_work_mode(JNIEnv *env, jobject thiz, jint workMode) {
    jni_asplayer_result result = asplayer_set_work_mode(env, thiz, workMode);

    return result;
}

static jint
native_reset_work_mode(JNIEnv *env, jobject thiz) {
    jni_asplayer_result result = asplayer_reset_work_mode(env, thiz);

    return result;
}

static jint
native_set_ad_params(JNIEnv* env, jobject thiz, jobject audioParams) {
    jni_asplayer_result result = asplayer_set_ad_params(env, thiz, audioParams);

    return result;
}

static jint
native_enable_ad_mix(JNIEnv *env, jobject thiz) {
    jni_asplayer_result result = asplayer_enable_ad_mix(env, thiz);

    return result;
}

static jint
native_disable_ad_mix(JNIEnv *env, jobject thiz) {
    jni_asplayer_result result = asplayer_disable_ad_mix(env, thiz);

    return result;
}

static jint
native_set_ad_volume_db(JNIEnv* env, jobject thiz, jfloat volumeDb) {
    jni_asplayer_result result = asplayer_set_ad_volume_db(env, thiz, volumeDb);

    return result;
}

static jfloat
native_get_ad_volume_db(JNIEnv *env, jobject thiz) {
    float volume = 0.f;

    jni_asplayer_result result = asplayer_get_ad_volume_db(env, thiz, &volume);

    if (result == JNI_ASPLAYER_OK) {
        return (jfloat)volume;
    } else {
        return 0.f;
    }
}

static jint
native_set_ad_mix_level(JNIEnv* env, jobject thiz, jint mixLevel) {
    jni_asplayer_result result = asplayer_set_ad_mix_level(env, thiz, mixLevel);

    return result;
}

static jint
native_get_ad_mix_level(JNIEnv *env, jobject thiz) {
    jint mixLevel = 0;

    jni_asplayer_result result = asplayer_get_ad_mix_level(env, thiz, &mixLevel);

    if (result == JNI_ASPLAYER_OK) {
        return mixLevel;
    } else {
        return 0;
    }
}

static jint
native_set_audio_dual_mono_mode(JNIEnv* env, jobject thiz, jint mode) {
    jni_asplayer_result result = asplayer_set_audio_dual_mono_mode(env, thiz, mode);

    return result;
}

static jint
native_get_audio_dual_mono_mode(JNIEnv *env, jobject thiz) {
    jint mode = 0;

    jni_asplayer_result result = asplayer_get_audio_dual_mono_mode(env, thiz, &mode);

    if (result == JNI_ASPLAYER_OK) {
        return mode;
    } else {
        return -1;
    }
}

static jint
native_set_parameters(JNIEnv *env, jobject thiz, jobjectArray keys, jobjectArray values) {
    jni_asplayer_result result = asplayer_set_parameters(env, thiz, keys, values);

    return result;
}

static jobject
native_get_parameters(JNIEnv *env, jobject thiz, jobjectArray jKeys) {
    jobject result = asplayer_get_parameters(env, thiz, jKeys);
    return result;
}

static jobject
native_get_video_info(JNIEnv *env, jobject thiz) {
    jobject result = asplayer_get_video_info(env, thiz);
    return result;
}

static void
native_release(JNIEnv* env, jobject thiz) {
    jni_asplayer_result result = asplayer_release(env, thiz);
}

static JNINativeMethod methods[] = {
        {"native_init", "()V", (void*)native_init },
        {"native_create", "(Lcom/amlogic/asplayer/api/InitParams;Landroid/media/tv/tuner/Tuner;)V", (void*)native_create },
        {"native_getJavaASPlayer", "()Ljava/lang/Object;", (void*)native_getJavaASPlayer },
        {"native_addPlaybackListener", "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V", (void*)native_add_playback_listener },
        {"native_removePlaybackListener", "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V", (void*)native_remove_playback_listener },
        {"native_prepare", "()I", (void*)native_prepare },
        {"native_getInstanceNo", "()I", (void*)native_get_instance_no },
        {"native_getSyncInstanceNo", "()I", (void*)native_get_sync_instance_no },
        {"native_startVideoDecoding", "()I", (void*)native_start_video_decoding },
        {"native_stopVideoDecoding", "()I", (void*)native_stop_video_decoding },
        {"native_pauseVideoDecoding", "()I", (void*)native_pause_video_decoding },
        {"native_resumeVideoDecoding", "()I", (void*)native_resume_video_decoding },
        {"native_startAudioDecoding", "()I", (void*)native_start_audio_decoding },
        {"native_stopAudioDecoding", "()I", (void*)native_stop_audio_decoding },
        {"native_pauseAudioDecoding", "()I", (void*)native_pause_audio_decoding },
        {"native_resumeAudioDecoding", "()I", (void*)native_resume_audio_decoding },
        {"native_setVideoParams", "(Lcom/amlogic/asplayer/api/VideoParams;)I", (void*)native_set_video_params },
        {"native_setAudioParams", "(Lcom/amlogic/asplayer/api/AudioParams;)I", (void*)native_set_audio_params },
        {"native_switchAudioTrack", "(Lcom/amlogic/asplayer/api/AudioParams;)I", (void*)native_switch_audio_track },
        {"native_flush", "()I", (void*)native_flush },
        {"native_flushDvr", "()I", (void*)native_flush_dvr },
        {"native_writeData", "(Lcom/amlogic/asplayer/api/InputBuffer;J)I", (void*)native_write_data },
        {"native_setSurface", "(Landroid/view/Surface;)I", (void*)native_set_surface },
        {"native_setAudioMute", "(Z)I", (void*)native_set_audio_mute },
        {"native_setAudioVolume", "(I)I", (void*)native_set_audio_volume },
        {"native_getAudioVolume", "()I", (void*)native_get_audio_volume },
        {"native_startFast", "(F)I", (void*)native_start_fast },
        {"native_stopFast", "()I", (void*)native_stop_fast },
        {"native_setTrickMode", "(I)I", (void*)native_set_trick_mode },
        {"native_setTransitionModeBefore", "(I)I", (void*) native_set_transition_mode_before },
        {"native_setTransitionModeAfter", "(I)I", (void*) native_set_transition_mode_after },
        {"native_setTransitionPrerollRate", "(F)I", (void*) native_set_transition_preroll_rate },
        {"native_setTransitionPrerollAvTolerance", "(I)I", (void*) native_set_transition_preroll_av_tolerance },
        {"native_setVideoMute", "(I)I", (void*) native_set_video_mute },
        {"native_setScreenColor", "(II)I", (void*) native_set_screen_color },
        {"native_setWorkMode", "(I)I", (void*)native_set_work_mode },
        {"native_resetWorkMode", "()I", (void*)native_reset_work_mode },
        {"native_setPIPMode", "(I)I", (void*)native_set_pip_mode },
        {"native_setADParams", "(Lcom/amlogic/asplayer/api/AudioParams;)I", (void*)native_set_ad_params },
        {"native_enableADMix", "()I", (void*)native_enable_ad_mix },
        {"native_disableADMix", "()I", (void*)native_disable_ad_mix },
        {"native_setADVolumeDB", "(F)I", (void*)native_set_ad_volume_db },
        {"native_getADVolumeDB", "()F", (void*)native_get_ad_volume_db },
        {"native_setADMixLevel", "(I)I", (void*)native_set_ad_mix_level },
        {"native_getADMixLevel", "()I", (void*)native_get_ad_mix_level },
        {"native_setAudioDualMonoMode", "(I)I", (void*)native_set_audio_dual_mono_mode },
        {"native_getAudioDualMonoMode", "()I", (void*)native_get_audio_dual_mono_mode },
        {"native_setParameters", "([Ljava/lang/String;[Ljava/lang/Object;)I", (void*)native_set_parameters },
        {"native_getParameters", "([Ljava/lang/String;)Landroid/os/Bundle;", (void*)native_get_parameters },
        {"native_getVideoInfo", "()Landroid/media/MediaFormat;", (void*) native_get_video_info },
        {"native_release", "()V", (void*)native_release },
};

/*
 * Register several native methods for one class.
 */
static int registerNativeMethods(JNIEnv* env, const char* className,
                                 JNINativeMethod* gMethods, int numMethods)
{
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

/*
 * Register native methods for all classes we know about.
 *
 * returns JNI_TRUE on success.
 */
static int registerNatives(JNIEnv* env)
{
    if (!registerNativeMethods(env, CLASS_PATH_NAME,
                               methods, sizeof(methods) / sizeof(methods[0]))) {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {

    jint result = -1;
    JNIEnv* env = NULL;

    if (vm->GetEnv((void**)&env, JNI_VERSION_1_4) != JNI_OK) {
        ALOGE("ERROR: GetEnv failed");
        return result;
    }

    NativeHelper::initJniEnv(env);

#if (USE_SYSTEM_SO == 1)
    jni_asplayer_result ret = DynamicJniASPlayerWrapper::registerJni(env);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("ERROR: JniASPlayer register jni env failed");
    }
//#else
//    jni_asplayer_result ret = JniASPlayer_registerJNI(env);
#endif

//    if (ret != JNI_ASPLAYER_OK) {
//        ALOGE("ERROR: JniASPlayer register jni env failed");
//    }

    if (registerNatives(env) != JNI_TRUE) {
        ALOGE("ERROR: registerNatives failed");
        return result;
    }

    AutoEnv::setJavaVM(vm);

    result = JNI_VERSION_1_4;

    return result;
}

#ifdef __cplusplus
};
#endif