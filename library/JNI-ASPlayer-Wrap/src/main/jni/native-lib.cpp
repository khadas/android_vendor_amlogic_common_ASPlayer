/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <jni.h>

#include <thread>
#include <unistd.h>
#include <dlfcn.h>

#include <JNIASPlayer.h>
#include "common/utils/Log.h"
#include "common/utils/AutoEnv.h"
#include "BaseJniASPlayerWrapper.h"
#if (USE_SYSTEM_SO == 1)
#include "DynamicJniASPlayerWrapper.h"
#else
#include "JniASPlayerWrapper.h"
#endif
#include "PlaybackListenerWrapper.h"
#include "ASPlayerJni.h"

#define LOG_GET_JNIENV_FAILED() ALOGE("%s[%d] failed to get jni env", __func__, __LINE__)
#define LOG_GET_PLAYER_FAILED() ALOGE("%s[%d] failed to get player instance", __func__, __LINE__)


#ifdef __cplusplus
extern "C" {
#endif

static const char *CLASS_PATH_NAME = "com/amlogic/asplayer/jni/wrapper/JniASPlayerWrapper";

struct field_t {
    jfieldID context;
};

static field_t gFields;

static ASPlayerJni gASPlayerJni;


static void setASPlayer(JNIEnv *env, jobject thiz, BaseJniASPlayerWrapper* handle) {
    ALOGD("%s[%d] setASPlayer start", __func__, __LINE__);
    env->SetLongField(thiz, gFields.context, (jlong)handle);
    ALOGD("%s[%d] setASPlayer end", __func__, __LINE__);
    jthrowable thr = env->ExceptionOccurred();
    if (thr) {
        ALOGE("%s[%d] failed to set asplayer native context", __func__, __LINE__);
        return;
    }
}

static BaseJniASPlayerWrapper* getASPlayer(JNIEnv *env, jobject thiz) {
    jlong nativeHandle = env->GetLongField(thiz, gFields.context);
    return (BaseJniASPlayerWrapper*)nativeHandle;
}

static void
native_init(JNIEnv* env) {
    jclass cls = env->FindClass(CLASS_PATH_NAME);
    if (cls == nullptr) {
        ALOGE("%s[%d] failed to find class: JniASPlayerWrapper", __func__, __LINE__);
        return;
    }

    gFields.context = env->GetFieldID(cls, "mNativeContext", "J");
    if (gFields.context == nullptr) {
        ALOGE("%s[%d] failed to find field mNativeContext", __func__, __LINE__);
        return;
    }

    bool ret = gASPlayerJni.initJni(env);
    if (!ret) {
        ALOGE("%s[%d] failed to init jni", __func__, __LINE__);
    } else {
        ALOGD("%s[%d] init jni success", __func__, __LINE__);
    }
}

static void create_asplayer(JNIEnv *env, jobject thiz, jobject initParams, jobject jTuner) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return;
    }

#if (USE_SYSTEM_SO == 1)
    BaseJniASPlayerWrapper *asPlayer = new DynamicJniASPlayerWrapper();
#else
    BaseJniASPlayerWrapper *asPlayer = new JniASPlayerWrapper();
#endif

    setASPlayer(env, thiz, asPlayer);

    jni_asplayer_init_params params;
    if (!gASPlayerJni.convertInitParams(env, initParams, &params)) {
        ALOGE("%s[%d] failed to create ASPlayer, get init parameter error", __func__, __LINE__);
        return;
    } else {
        ALOGD("%s[%d] convert InitParams success", __func__, __LINE__);
    }

    jni_asplayer_handle handle = 0;
    ALOGD("%s[%d] JniASPlayer_create start", __func__, __LINE__);
    jni_asplayer_result ret = asPlayer->create(params, (void*)jTuner, &handle);
    ALOGD("%s[%d] JniASPlayer_create end", __func__, __LINE__);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("failed to create ASPlayer, ret: %d", ret);
        return;
    }

    ALOGV("%s[%d] ASPlayer created, ret: %d", __func__, __LINE__, ret);
    asPlayer->setHandle(handle);
    LOG_FUNCTION_ENTER();
}

static void
native_create(JNIEnv* env, jobject thiz, jobject initParams, jobject tuner) {
    LOG_FUNCTION_ENTER();
    create_asplayer(env, thiz, initParams, tuner);
    LOG_FUNCTION_END();
}

static bool
asplayer_get_java_as_player(JNIEnv* env, jobject thiz, jobject *result) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return false;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return false;
    }

    jni_asplayer_result ret = player->getJavaASPlayer(result);
    LOG_FUNCTION_INT_END(ret);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("%s[%d] failed to get java ASPlayer, ret: %d", __func__, __LINE__, ret);
        return false;
    }
    return true;
}

static jobject
native_getJavaASPlayer(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    jobject asplayer = nullptr;
    bool success = asplayer_get_java_as_player(env, thiz, &asplayer);
    if (success) {
        return asplayer;
    } else {
        return nullptr;
    }
    LOG_FUNCTION_END();
}

static void
asplayer_prepare(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->prepare();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_prepare(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_prepare(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_add_playback_listener(JNIEnv* env, jobject thiz, jobject jListener) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->addPlaybackListener(env, jListener);
    LOG_FUNCTION_INT_END(ret);
}

static void
native_add_playback_listener(JNIEnv* env, jobject thiz, jobject jListener) {
    LOG_FUNCTION_ENTER();
    asplayer_add_playback_listener(env, thiz, jListener);
    LOG_FUNCTION_END();
}

static void
asplayer_remove_playback_listener(JNIEnv* env, jobject thiz, jobject jListener) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->removePlaybackListener(env, jListener);
    LOG_FUNCTION_INT_END(ret);
}

static void
native_remove_playback_listener(JNIEnv* env, jobject thiz, jobject jListener) {
    LOG_FUNCTION_ENTER();
    asplayer_remove_playback_listener(env, thiz, jListener);
    LOG_FUNCTION_END();
}

static void
asplayer_start_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->startVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_start_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_start_video_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_stop_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->stopVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_stop_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_stop_video_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_pause_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->pauseVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_pause_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_pause_video_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_resume_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->resumeVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_resume_video_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_resume_video_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_start_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->startAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_start_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_start_audio_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_stop_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->stopAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_stop_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_stop_audio_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_pause_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->pauseAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_pause_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_pause_audio_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_resume_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->resumeAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_resume_audio_decoding(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_resume_audio_decoding(env, thiz);
    LOG_FUNCTION_END();
    return 0;
}

static void
asplayer_set_video_params(JNIEnv* env, jobject thiz, jobject videoParams, jint *result) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    jni_asplayer_video_params params;
    if (!gASPlayerJni.convertVideoParams(env, videoParams, &params)) {
        ALOGE("%s[%d] failed to setVideoParams, failed to convert video params", __func__, __LINE__);
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    jni_asplayer_result ret = player->setVideoParams(&params);

    if (params.mimeType) {
        delete[] params.mimeType;
        params.mimeType = nullptr;
    }

    LOG_FUNCTION_INT_END(ret);

    *result = ret;
}

static jint
native_set_video_params(JNIEnv* env, jobject thiz, jobject videoParams) {
    LOG_FUNCTION_ENTER();
    jint result = JNI_ASPLAYER_OK;
    asplayer_set_video_params(env, thiz, videoParams, &result);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_set_audio_params(JNIEnv* env, jobject thiz, jobject audioParams, jint *result) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    jni_asplayer_audio_params params;
    if (!gASPlayerJni.convertAudioParams(env, audioParams, &params)) {
        ALOGE("%s[%d] failed to setVideoParams, failed to convert video params", __func__, __LINE__);
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    jni_asplayer_result ret = player->setAudioParams(&params);

    if (params.mimeType) {
        delete[] params.mimeType;
        params.mimeType = nullptr;
    }

    LOG_FUNCTION_INT_END(ret);

    *result = ret;
}

static jint
native_set_audio_params(JNIEnv* env, jobject thiz, jobject audioParams) {
    LOG_FUNCTION_ENTER();
    jint result = JNI_ASPLAYER_OK;
    asplayer_set_audio_params(env, thiz, audioParams, &result);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_flush(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    player->flush();
    LOG_FUNCTION_END();
}

static void
native_flush(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_flush(env, thiz);
    LOG_FUNCTION_END();
}

static void
asplayer_flush_dvr(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    player->flushDvr();
    LOG_FUNCTION_END();
}

static void
native_flush_dvr(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_flush_dvr(env, thiz);
    LOG_FUNCTION_END();
}

static void
asplayer_write_data(JNIEnv* env, jobject thiz, jobject jInputBuffer, jlong jTimeoutMillSecond, int32_t *result) {
//    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        *result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_input_buffer inputBuffer;
    if (!gASPlayerJni.convertInputBuffer(env, jInputBuffer, &inputBuffer)) {
        ALOGE("%s[%d] failed to writeData, failed to convert input buffer", __func__, __LINE__);
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    uint64_t timeout_us = jTimeoutMillSecond;
    jni_asplayer_result ret = player->writeData(&inputBuffer, timeout_us);
    if (inputBuffer.buf_data) {
        delete[] (jbyte*)inputBuffer.buf_data;
    }
    *result = ret;
//    LOG_FUNCTION_INT_END(ret);
}

static jint
native_write_data(JNIEnv* env, jobject thiz, jobject jInputBuffer, jlong jTimeoutMillSecond) {
    int32_t result = 0;
    asplayer_write_data(env, thiz, jInputBuffer, jTimeoutMillSecond, &result);
    return result;
}

static void
asplayer_set_surface(JNIEnv* env, jobject thiz, jobject jSurface, int32_t *result) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    jni_asplayer_result ret = player->setSurface(jSurface);
    *result = ret;
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_set_surface(JNIEnv* env, jobject thiz, jobject jSurface) {
    LOG_FUNCTION_ENTER();
    int32_t result = 0;
    asplayer_set_surface(env, thiz, jSurface, &result);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_set_audio_mute(JNIEnv* env, jobject thiz, jboolean jAnalogMute, jboolean jDigitMute, int32_t *result) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        return;
    }

    jni_asplayer_result ret = player->setAudioMute(jAnalogMute, jDigitMute);
    *result = ret;
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_set_audio_mute(JNIEnv* env, jobject thiz, jboolean jAnalogMute, jboolean jDigitMute) {
    LOG_FUNCTION_ENTER();
    int32_t result = 0;
    asplayer_set_audio_mute(env, thiz, jAnalogMute, jDigitMute, &result);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_set_audio_volume(JNIEnv* env, jobject thiz, jint volume) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->setAudioVolume(volume);
    LOG_FUNCTION_INT_END(ret);
}

static void
native_set_audio_volume(JNIEnv* env, jobject thiz, jint volume) {
    LOG_FUNCTION_ENTER();
    asplayer_set_audio_volume(env, thiz, volume);
    LOG_FUNCTION_END();
}

static void
asplayer_get_audio_volume(JNIEnv* env, jobject thiz, jint *result) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        *result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        *result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_GET_PLAYER_FAILED();
        return;
    }

    int volume = -1;
    jni_asplayer_result ret = player->getAudioVolume(&volume);
    LOG_FUNCTION_INT_END(ret);
    if (ret == JNI_ASPLAYER_OK) {
        *result = volume;
    } else {
        ALOGE("%s[%d] failed to get audio volume, ret: %d", __func__, __LINE__, ret);
        *result = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }
}

static jint
native_get_audio_volume(JNIEnv *env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    jint result = JNI_ASPLAYER_OK;
    asplayer_get_audio_volume(env, thiz, &result);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_start_fast(JNIEnv *env, jobject thiz, jfloat scale) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->startFast(scale);
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_start_fast(JNIEnv *env, jobject thiz, jfloat scale) {
    LOG_FUNCTION_ENTER();
    jint result = JNI_ASPLAYER_OK;
    asplayer_start_fast(env, thiz, scale);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_stop_fast(JNIEnv *env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->stopFast();
    LOG_FUNCTION_INT_END(ret);
}

static jint
native_stop_fast(JNIEnv *env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    jint result = JNI_ASPLAYER_OK;
    asplayer_stop_fast(env, thiz);
    LOG_FUNCTION_END();
    return result;
}

static void
asplayer_release(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, thiz);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return;
    }

    jni_asplayer_result ret = player->release();
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("%s[%d] error, failed to release asplayer ret: %d", __func__, __LINE__, ret);
        return;
    }

    // reset Java mNativeContext
    setASPlayer(env, thiz, nullptr);

    delete player;

    LOG_FUNCTION_INT_END(ret);
}

static void
native_release(JNIEnv* env, jobject thiz) {
    LOG_FUNCTION_ENTER();
    asplayer_release(env, thiz);
    LOG_FUNCTION_END();
}

static JNINativeMethod methods[] = {
        {"native_init", "()V", (void*)native_init },
        {"native_create", "(Lcom/amlogic/asplayer/api/InitParams;Landroid/media/tv/tuner/Tuner;)V", (void*)native_create },
        {"native_getJavaASPlayer", "()Ljava/lang/Object;", (void*)native_getJavaASPlayer },
        {"native_addPlaybackListener", "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V", (void*)native_add_playback_listener },
        {"native_removePlaybackListener", "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V", (void*)native_remove_playback_listener },
        {"native_prepare", "()I", (void*)native_prepare },
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
        {"native_flush", "()V", (void*)native_flush },
        {"native_flushDvr", "()V", (void*)native_flush_dvr },
        {"native_writeData", "(Lcom/amlogic/asplayer/api/InputBuffer;J)I", (void*)native_write_data },
        {"native_setSurface", "(Landroid/view/Surface;)I", (void*)native_set_surface },
        {"native_setAudioMute", "(ZZ)I", (void*)native_set_audio_mute },
        {"native_setAudioVolume", "(I)V", (void*)native_set_audio_volume },
        {"native_getAudioVolume", "()I", (void*)native_get_audio_volume },
        {"native_startFast", "(F)I", (void*)native_start_fast },
        {"native_stopFast", "()I", (void*)native_stop_fast },
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

#if (USE_SYSTEM_SO == 1)
    jni_asplayer_result ret = DynamicJniASPlayerWrapper::registerJni(env);
#else
    jni_asplayer_result ret = JniASPlayer_registerJNI(env);
#endif

    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("ERROR: JniASPlayer register jni env failed");
    }

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