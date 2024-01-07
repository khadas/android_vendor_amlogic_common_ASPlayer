/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

#include "config.h"
#include "JniNativeLib.h"
#include "common/utils/Log.h"
#include "BaseJniASPlayerWrapper.h"

#if (USE_SYSTEM_SO == 1)
#include "DynamicJniASPlayerWrapper.h"
#else
#include "JniASPlayerWrapper.h"
#endif
#include "ASPlayerJni.h"
#include "NativeHelper.h"

#define DELETE_LOCAL_REF(env, ref)              \
    do {                                        \
        if (ref != nullptr) {                   \
            if (env != nullptr) {               \
                env->DeleteLocalRef(ref);       \
            }                                   \
            ref = nullptr;                      \
        }                                       \
    } while (0)

#define LOG_GET_PLAYER_FAILED() ALOGE("[%s/%d] failed to get player instance", __FUNCTION__, __LINE__)

const char *CLASS_PATH_NAME = "com/amlogic/asplayer/jni/wrapper/JniASPlayerWrapper";

static const char *KEY_AUDIO_PRESENTATION_ID            = "audio-presentation-id";
static const char *KEY_AUDIO_PROGRAM_ID                 = "audio-program-id";
static const char *KEY_FIRST_LANGUAGE                   = "audio-first-language";
static const char *KEY_SECOND_LANGUAGE                  = "audio-second-language";
static const char *KEY_AUDIO_SPDIF_PROTECTION_MODE      = "spdif-protection-mode";

struct field_t {
    jfieldID context;
};

static field_t gFields;

static ASPlayerJni gASPlayerJni;

static void setASPlayer(JNIEnv *env, jobject jniASPlayerWrapperObj, BaseJniASPlayerWrapper* handle) {
    ALOGD("[%s/%d] setASPlayer start", __func__, __LINE__);
    env->SetLongField(jniASPlayerWrapperObj, gFields.context, (jlong)handle);
    ALOGD("[%s/%d] setASPlayer end", __func__, __LINE__);
    jthrowable thr = env->ExceptionOccurred();
    if (thr) {
        ALOGE("[%s/%d] failed to set asplayer native context", __func__, __LINE__);
        return;
    }
}

static BaseJniASPlayerWrapper* getASPlayer(JNIEnv *env, jobject jniASPlayerWrapperObj) {
    jlong nativeHandle = env->GetLongField(jniASPlayerWrapperObj, gFields.context);
    return (BaseJniASPlayerWrapper*)nativeHandle;
}

jni_asplayer_result
init_jni_env(JNIEnv *env) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jclass cls = env->FindClass(CLASS_PATH_NAME);
    if (cls == nullptr) {
        ALOGE("[%s/%d] failed to find class: JniASPlayerWrapper", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    gFields.context = env->GetFieldID(cls, "mNativeContext", "J");
    if (gFields.context == nullptr) {
        ALOGE("[%s/%d] failed to find field mNativeContext", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    bool ret = gASPlayerJni.initJni(env);
    if (!ret) {
        ALOGE("[%s/%d] failed to init jni", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else {
        ALOGD("[%s/%d] init jni success", __func__, __LINE__);
        return JNI_ASPLAYER_OK;
    }
}

jni_asplayer_result
create_asplayer(JNIEnv *env, jobject jniASPlayerWrapperObj, jobject initParams, jobject jTuner) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

#if (USE_SYSTEM_SO == 1)
    BaseJniASPlayerWrapper *asPlayer = new DynamicJniASPlayerWrapper();
#else
    BaseJniASPlayerWrapper *asPlayer = new JniASPlayerWrapper();
#endif

    setASPlayer(env, jniASPlayerWrapperObj, asPlayer);

    jni_asplayer_init_params params;
    if (!gASPlayerJni.convertInitParams(env, initParams, &params)) {
        ALOGE("[%s/%d] failed to create ASPlayer, get init parameter error", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else {
        ALOGD("[%s/%d] convert InitParams success", __func__, __LINE__);
    }

    jni_asplayer_handle handle = 0;
    ALOGD("[%s/%d] JniASPlayer_create start", __func__, __LINE__);
    jni_asplayer_result ret = asPlayer->create(params, (void*)jTuner, &handle);
    ALOGD("[%s/%d] JniASPlayer_create end", __func__, __LINE__);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("failed to create ASPlayer, ret: %d", ret);
        return ret;
    }

    ALOGV("[%s/%d] ASPlayer created, ret: %d", __func__, __LINE__, ret);
    asPlayer->setHandle(handle);
    LOG_FUNCTION_ENTER();
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result
asplayer_get_java_asplayer(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject *outASPlayer) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->getJavaASPlayer(outASPlayer);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_prepare(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->prepare();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_get_instance_no(JNIEnv *env, jobject jniASPlayerWrapperObj, int32_t *numb) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (numb == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->getInstanceNo(numb);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed to getInstancesNo, ret: %d", __func__, __LINE__, ret);
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_get_sync_instance_no(JNIEnv *env, jobject jniASPlayerWrapperObj, int32_t *numb) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (numb == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->getSyncInstanceNo(numb);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed to get SyncInstancesNo, ret: %d", __func__, __LINE__, ret);
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_add_playback_listener(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject jListener) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->addPlaybackListener(env, jListener);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_remove_playback_listener(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject jListener) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->removePlaybackListener(env, jListener);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_start_video_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->startVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_stop_video_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->stopVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_pause_video_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->pauseVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_resume_video_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->resumeVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_start_audio_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->startAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_stop_audio_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->stopAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_pause_audio_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->pauseAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_resume_audio_decoding(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->resumeAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_set_video_params(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject videoParams) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_video_params params;
    if (!gASPlayerJni.convertVideoParams(env, videoParams, &params)) {
        ALOGE("[%s/%d] failed to setVideoParams, failed to convert video params", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setVideoParams(&params);

    if (params.mimeType) {
        delete[] params.mimeType;
        params.mimeType = nullptr;
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_set_audio_params(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject audioParams) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_audio_params params;
    if (!gASPlayerJni.convertAudioParams(env, audioParams, &params)) {
        ALOGE("[%s/%d] failed to setAudioParams, failed to convert audio params", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setAudioParams(&params);

    if (params.mimeType) {
        delete[] params.mimeType;
        params.mimeType = nullptr;
    }

    if (params.extraInfoJson) {
        delete[] params.extraInfoJson;
        params.extraInfoJson = nullptr;
    }

    return ret;
}

jni_asplayer_result
asplayer_switch_audio_track(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject audioParams) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_audio_params params;
    if (!gASPlayerJni.convertAudioParams(env, audioParams, &params)) {
        ALOGE("[%s/%d] failed to setADParams, failed to convert ad params", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->switchAudioTrack(&params);

    if (params.mimeType) {
        delete[] params.mimeType;
        params.mimeType = nullptr;
    }

    return ret;
}

jni_asplayer_result
asplayer_flush(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->flush();
    return ret;
}

jni_asplayer_result
asplayer_flush_dvr(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->flushDvr();
    return ret;
}

jni_asplayer_result
asplayer_write_data(JNIEnv* env, jobject jniASPlayerWrapperObj,
                    jobject jInputBuffer, jlong jTimeoutMillSecond) {
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_input_buffer inputBuffer;
    if (!gASPlayerJni.convertInputBuffer(env, jInputBuffer, &inputBuffer)) {
        ALOGE("[%s/%d] failed to writeData, failed to convert input buffer", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    uint64_t timeout_us = jTimeoutMillSecond;
    jni_asplayer_result ret = player->writeData(&inputBuffer, timeout_us);
    if (inputBuffer.buf_data) {
        delete[] (jbyte*)inputBuffer.buf_data;
    }

    return ret;
}

jni_asplayer_result
asplayer_set_surface(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject jSurface) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setSurface(jSurface);
    return ret;
}

jni_asplayer_result
asplayer_set_audio_mute(JNIEnv* env, jobject jniASPlayerWrapperObj,
                        jboolean jAnalogMute, jboolean jDigitMute) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setAudioMute(jAnalogMute, jDigitMute);
    return ret;
}

jni_asplayer_result
asplayer_set_audio_volume(JNIEnv* env, jobject jniASPlayerWrapperObj, jint volume) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setAudioVolume(volume);
    return ret;
}

jint
asplayer_get_audio_volume(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int volume = -1;
    jni_asplayer_result ret = player->getAudioVolume(&volume);
    LOG_FUNCTION_INT_END(ret);
    if (ret == JNI_ASPLAYER_OK) {
        return volume;
    } else {
        ALOGE("[%s/%d] failed to get audio volume, ret: %d", __func__, __LINE__, ret);
        return ret;
    }
}

jni_asplayer_result
asplayer_start_fast(JNIEnv *env, jobject jniASPlayerWrapperObj, jfloat scale) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->startFast(scale);
    return ret;
}

jni_asplayer_result
asplayer_stop_fast(JNIEnv *env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->stopFast();
    return ret;
}

jni_asplayer_result
asplayer_set_trick_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jTrickMode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_video_trick_mode trickMode = static_cast<jni_asplayer_video_trick_mode>(jTrickMode);
    jni_asplayer_result ret = player->setTrickMode(trickMode);
    return ret;
}

jni_asplayer_result
asplayer_set_transition_mode_before(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jMode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_transition_mode_before mode = static_cast<jni_asplayer_transition_mode_before>(jMode);
    jni_asplayer_result ret = player->setTransitionModeBefore(mode);
    return ret;
}

jni_asplayer_result
asplayer_set_transition_mode_after(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jMode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_transition_mode_after mode = static_cast<jni_asplayer_transition_mode_after>(jMode);
    jni_asplayer_result ret = player->setTransitionModeAfter(mode);
    return ret;
}

jni_asplayer_result
asplayer_set_transition_preroll_rate(JNIEnv *env, jobject jniASPlayerWrapperObj, jfloat jRate) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setTransitionPrerollRate((float)jRate);
    return ret;
}

jni_asplayer_result
asplayer_set_transition_preroll_av_tolerance(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jMillSecond) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setTransitionPrerollAvTolerance((int32_t)jMillSecond);
    return ret;
}

jni_asplayer_result
asplayer_set_video_mute(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jMute) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setVideoMute(static_cast<jni_asplayer_video_mute>(jMute));
    return ret;
}

jni_asplayer_result
asplayer_set_screen_color(JNIEnv *env, jobject jniASPlayerWrapperObj, jint jMode, jint jColor) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_screen_color_mode mode = static_cast<jni_asplayer_screen_color_mode>(jMode);
    jni_asplayer_screen_color color = static_cast<jni_asplayer_screen_color>(jColor);
    jni_asplayer_result ret = player->setScreenColor(mode, color);
    return ret;
}

jni_asplayer_result
asplayer_set_pip_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint mode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setPIPMode(static_cast<jni_asplayer_pip_mode>(mode));
    return ret;
}

jni_asplayer_result
asplayer_set_ad_params(JNIEnv* env, jobject jniASPlayerWrapperObj, jobject audioParams) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_audio_params params;
    if (!gASPlayerJni.convertAudioParams(env, audioParams, &params)) {
        ALOGE("[%s/%d] failed to setADParams, failed to convert ad params", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setADParams(&params);

    if (params.mimeType) {
        delete[] params.mimeType;
        params.mimeType = nullptr;
    }

    return ret;
}

jni_asplayer_result
asplayer_enable_ad_mix(JNIEnv *env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->enableADMix();
    return ret;
}

jni_asplayer_result
asplayer_disable_ad_mix(JNIEnv *env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->disableADMix();
    return ret;
}

jni_asplayer_result
asplayer_set_ad_volume_db(JNIEnv *env, jobject jniASPlayerWrapperObj, jfloat volumeDb) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setADVolumeDB(volumeDb);
    return ret;
}

jni_asplayer_result
asplayer_get_ad_volume_db(JNIEnv *env, jobject jniASPlayerWrapperObj, jfloat *volumeDb) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (volumeDb == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->getADVolumeDB(volumeDb);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed to get ad volume, ret: %d", __func__, __LINE__, ret);
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_set_ad_mix_level(JNIEnv *env, jobject jniASPlayerWrapperObj, jint mixLevel) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setADMixLevel((int32_t)mixLevel);
    return ret;
}

jni_asplayer_result
asplayer_get_ad_mix_level(JNIEnv *env, jobject jniASPlayerWrapperObj, jint *mixLevel) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (mixLevel == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->getADMixLevel(mixLevel);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed to get ad mix level, ret: %d", __func__, __LINE__, ret);
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_set_audio_dual_mono_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint mode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_audio_dual_mono_mode monoMode = static_cast<jni_asplayer_audio_dual_mono_mode>(mode);
    jni_asplayer_result ret = player->setAudioDualMonoMode(monoMode);
    return ret;
}

jni_asplayer_result
asplayer_get_audio_dual_mono_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint *mode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (mode == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_audio_dual_mono_mode monoMode = JNI_ASPLAYER_DUAL_MONO_OFF;
    jni_asplayer_result ret = player->getAudioDualMonoMode(&monoMode);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed to get audio dual mono mode, ret: %d", __func__, __LINE__, ret);
    } else {
        *mode = (jint)monoMode;
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
asplayer_set_work_mode(JNIEnv *env, jobject jniASPlayerWrapperObj, jint mode) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->setWorkMode(static_cast<jni_asplayer_work_mode>(mode));
    return ret;
}

jni_asplayer_result
asplayer_reset_work_mode(JNIEnv *env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->resetWorkMode();
    return ret;
}

jobject
asplayer_get_video_info(JNIEnv *env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return nullptr;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return nullptr;
    }

    jni_asplayer_video_info *videoInfo = new jni_asplayer_video_info;
    jni_asplayer_result ret = player->getVideoInfo(videoInfo);

    jobject mediaFormat = nullptr;

    if (!ASPlayerJni::createMediaFormat(env, videoInfo, &mediaFormat)) {
        ALOGE("[%s/%d] error, failed to create mediaformat from videoinfo", __func__, __LINE__);
        delete videoInfo;
        return nullptr;
    }

    delete videoInfo;

    return mediaFormat;
}

jni_asplayer_result
asplayer_set_parameters(JNIEnv *env, jobject jniASPlayerWrapperObj,
                        jobjectArray keys, jobjectArray values) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jsize numEntries = 0;

    if (keys == NULL) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (values == NULL) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else {
        numEntries = env->GetArrayLength(keys);

        if (numEntries != env->GetArrayLength(values)) {
            return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
        }
    }

    ALOGI("[%s/%d] setParameters numEntries: %d", __func__, __LINE__, numEntries);

    jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OPERATION;

    jobject keyObj = nullptr;
    const char *stringKey = nullptr;
    jobject valueObj = nullptr;
    jint intValue = 0;
    jlong longValue = 0;
    jfloat floatValue = 0;
    const char *stringValue = nullptr;

    do {
        for (jsize i = 0; i < numEntries; ++i) {
            keyObj = env->GetObjectArrayElement(keys, i);
            if (env->IsSameObject(keyObj, nullptr)) {
                ALOGE("[%s/%d] setParameters failed, key is null", __func__, __LINE__);
                ret = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
                break;
            }

            if (!NativeHelper::isStringInstance(env, keyObj)) {
                ALOGE("[%s/%d] setParameters failed, key is not string", __func__, __LINE__);
                ret = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
                break;
            }

            stringKey = env->GetStringUTFChars((jstring)keyObj, NULL);
            if (stringKey == NULL) {
                ret = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
                break;
            }

            ALOGI("[%s/%d] setParameters key: %s", __func__, __LINE__, stringKey);

            valueObj = env->GetObjectArrayElement(values, i);
            if (env->IsSameObject(valueObj, nullptr)) {
                ALOGE("[%s/%d] setParameters failed, value is null, key: %s", __func__, __LINE__, stringKey);
                ret = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
                break;
            }
            if (NativeHelper::isStringInstance(env, valueObj)) {
                const char *value = env->GetStringUTFChars((jstring)valueObj, NULL);
                if (value == NULL) {
                    ALOGE("[%s/%d] setParameters failed, failed to get string value, key: %s",
                          __func__, __LINE__, stringKey);
                    ret = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
                    break;
                }

                stringValue = value;
                ALOGI("[%s/%d] setParameters %s=%s", __func__, __LINE__, stringKey, stringValue);
            } else if (NativeHelper::isIntegerInstance(env, valueObj)) {
                jint value = 0;
                if (!NativeHelper::getIntFromInteger(env, valueObj, &value)) {
                    ALOGE("[%s/%d] setParameters failed, failed to get int value, key: %s",
                          __func__, __LINE__, stringKey);
                    ret = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
                    break;
                }

                intValue = value;
                ALOGI("[%s/%d] setParameters %s=%d", __func__, __LINE__, stringKey, intValue);
            } else if (NativeHelper::isLongInstance(env, valueObj)) {
                jlong value = 0;
                if (!NativeHelper::getLongFromLongObj(env, valueObj, &value)) {
                    ALOGE("[%s/%d] setParameters failed, failed to get long value, key: %s",
                          __func__, __LINE__, stringKey);
                    ret = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
                    break;
                }

                longValue = value;
                ALOGI("[%s/%d] setParameters %s=%" PRId64 "", __func__, __LINE__, stringKey, longValue);
            } else if (NativeHelper::isFloatInstance(env, valueObj)) {
                jfloat value = 0;
                if (!NativeHelper::getFloatFromFloatObj(env, valueObj, &value)) {
                    ALOGE("[%s/%d] setParameters failed, failed to get float value, key: %s",
                          __func__, __LINE__, stringKey);
                    ret = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
                    break;
                }

                floatValue = value;
                ALOGI("[%s/%d] setParameters %s=%f", __func__, __LINE__, stringKey, floatValue);
            }

            env->ReleaseStringUTFChars((jstring)keyObj, stringKey);
            stringKey = NULL;

            if (stringValue) {
                env->ReleaseStringUTFChars((jstring)valueObj, stringValue);
            }
            stringValue = NULL;
        }
    } while (0);

    if (stringKey) {
        env->ReleaseStringUTFChars((jstring)keyObj, stringKey);
        keyObj = NULL;
        stringKey = NULL;
    }

    if (stringValue) {
        env->ReleaseStringUTFChars((jstring)valueObj, stringValue);
        valueObj = NULL;
        stringValue = NULL;
    }

    return ret;
}

jobject
asplayer_get_parameters(JNIEnv *env, jobject jniASPlayerWrapperObj, jobjectArray jKeys) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr || jKeys == nullptr) {
        return nullptr;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return nullptr;
    }

    jsize numEntries = env->GetArrayLength(jKeys);

    jobject bundleObj = nullptr;
    if (!ASPlayerJni::createBundleObject(env, &bundleObj)) {
        ALOGE("[%s/%d] getParameters failed, create bundle failed", __func__, __LINE__);
        return nullptr;
    }

    for (jsize i = 0; i < numEntries; ++i) {
        jobject keyObj = env->GetObjectArrayElement(jKeys, i);
        if (env->IsSameObject(keyObj, nullptr)) {
            ALOGE("[%s/%d] getParameters failed, key is null", __func__, __LINE__);
            continue;
        }

        if (!NativeHelper::isStringInstance(env, keyObj)) {
            ALOGE("[%s/%d] getParameters failed, key is not string", __func__, __LINE__);
            continue;
        }

        const char* stringKey = env->GetStringUTFChars((jstring)keyObj, NULL);
        if (stringKey == NULL) {
            ALOGE("[%s/%d] getParameters failed, failed to get key string", __func__, __LINE__);
            continue;
        }

        ALOGI("[%s/%d] getParameters, key: %s", __func__, __LINE__, stringKey);

        env->ReleaseStringUTFChars((jstring)keyObj, stringKey);
    }

    return bundleObj;
}

jni_asplayer_result
asplayer_release(JNIEnv* env, jobject jniASPlayerWrapperObj) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    BaseJniASPlayerWrapper *player = getASPlayer(env, jniASPlayerWrapperObj);
    if (player == nullptr) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jni_asplayer_result ret = player->release();
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] error, failed to release asplayer ret: %d", __func__, __LINE__, ret);
        return JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }

    // reset Java mNativeContext
    setASPlayer(env, jniASPlayerWrapperObj, nullptr);

    delete player;

    return ret;
}
