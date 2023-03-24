/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <unistd.h>

#include "include/JNIASPlayer.h"
#include "JniASPlayerJNI.h"
#include "log.h"


#ifdef __cplusplus
extern "C" {
#endif

jni_asplayer_result JniASPlayer_registerJNI(JNIEnv *env) {
    if (env == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JavaVM *jvm = nullptr;

    jint ret = env->GetJavaVM(&jvm);
    if (ret == JNI_OK) {
        JniASPlayerJNI::setJavaVM(jvm);
    }

    ALOGI("register jni called");

    bool initJniRet = JniASPlayerJNI::initASPlayerJNI(env);
    (void)initJniRet;
    ALOGV("%d init jni result: %s", gettid(), initJniRet ? "success" : "false");
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result  JniASPlayer_create(jni_asplayer_init_params params, void *tuner, jni_asplayer_handle *pHandle) {
    ALOGD("%s[%d] start", __func__, __LINE__);
    JniASPlayer *player = new JniASPlayer();
    if (!player->create(params, tuner)) {
        ALOGW("create java ts player failed");
        delete player;
        return JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }

    *pHandle = (jni_asplayer_handle)player;
    ALOGV("%s create ts player: %p", __func__, player);
    return JNI_ASPLAYER_OK;
}

/**
 * @brief:        Get ASPlayer instance.
 * @param:        handle    JniASPlayer handle.
 * @param:        *pASPlayer ASPlayer instance.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_getJavaASPlayer(jni_asplayer_handle handle, jobject *pASPlayer) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jobject *pJavaPlayer = nullptr;
    if (!player->getJavaASPlayer(&pJavaPlayer)) {
        ALOGE("%s[%d] failed to get Java Ts Player instance", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    *pASPlayer = *pJavaPlayer;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result  JniASPlayer_prepare(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->prepare();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Start video decoding for specified JniASPlayer instance .
 *@param:        Handle      JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startVideoDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->startVideoDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Stop video decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopVideoDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->stopVideoDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Pause video decoding for specified JniASPlayer instance .
 *@param:        Handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_pauseVideoDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->pauseVideoDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Resume video decoding for specified JniASPlayer instance .
 *@param:        Handle      JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resumeVideoDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->resumeVideoDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Start audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startAudioDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->startAudioDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Pause audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_pauseAudioDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->pauseAudioDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Resume audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resumeAudioDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->resumeAudioDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Stop audio decoding for specified JniASPlayer instance .
 *@param:        Handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopAudioDecoding(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->stopAudioDecoding();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Set video params need by demuxer and video decoder
 *               for specified JniASPlayer instance.
 *@param:        Handle      JniASPlayer handle.
 *@param:        *pParams    Params need by demuxer and video decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setVideoParams(jni_asplayer_handle handle, jni_asplayer_video_params *pParams) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->setVideoParams(pParams);
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Set audio params need by demuxer and audio decoder
 *               to specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        *pParams   Params need by demuxer and audio decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioParams(jni_asplayer_handle handle, jni_asplayer_audio_params *pParams) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->setAudioParams(pParams);
    ALOGV("%s[%d] return: %d", __func__, __LINE__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Flush specified JniASPlayer instance.
 *@param:        handle         JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_flush(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->flush();
    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        Write data to specified JniASPlayer instance.
 *               It will only work when TS input's source type is TS_MEMORY.
 *@param:        handle         JniASPlayer handle.
 *@param:        *buf           Input buffer struct (1.Buffer type:secure/no
 *                              2.secure buffer ptr 3.buffer len).
 *@param:        timeout_ms     Time out limit .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_writeData(jni_asplayer_handle handle, jni_asplayer_input_buffer *buf, uint64_t timeout_ms) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->writeData(buf, timeout_ms);
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Set Surface ptr to specified JniASPlayer instance.
 *@param:        handle       JniASPlayer handle.
 *@param:        *pSurface    Surface ptr
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setSurface(jni_asplayer_handle handle, void* pSurface) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->setSurface(pSurface);
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Set audio output mute to specified JniASPlayer instance .
 *@param:        handle         JniASPlayer handle.
 *@param:        analog_mute    If analog mute or unmute .
 *@param:        digital_mute   If digital mute or unmute .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioMute(jni_asplayer_handle handle, bool_t analog_mute, bool_t digital_mute) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    bool aMute = analog_mute ? true : false;
    bool dMute = digital_mute ? true : false;
    int ret = player->setAudioMute(aMute, dMute);
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Set audio volume to specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@param:        volume     Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioVolume(jni_asplayer_handle handle, int32_t volume) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->setAudioVolume(volume);
    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        Get audio volume value from specified JniASPlayer instance .
 *@param:        handle      JniASPlayer handle.
 *@param:        *volume     Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioVolume(jni_asplayer_handle handle, int32_t *volume) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->getAudioVolume(volume);
    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        Start Fast play for specified JniASPlayer instance.
 *@param:        Handle     JniASPlayer handle.
 *@param:        scale      Fast play speed.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startFast(jni_asplayer_handle handle, float scale) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->startFast(scale);
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Stop Fast play for specified JniASPlayer instance.
 *@param:        Handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopFast(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    int ret = player->stopFast();
    ALOGV("%s return: %d", __func__, ret);
    return static_cast<jni_asplayer_result>(ret);
}

/**
 *@brief:        Release specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_release(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->release();

    delete player;

    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        Register event callback to specified JniASPlayer
 *@param:        Handle    JniASPlayer handle.
 *@param:        pfunc     Event callback function ptr.
 *@param:        *param    Extra data ptr.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_registerCb(jni_asplayer_handle handle, event_callback pfunc, void *param) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->setEventCallback(pfunc, param);

    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        Get event callback to specified JniASPlayer
 *@param:        Handle      JniASPlayer handle.
 *@param:        *pfunc      ptr of Event callback function ptr.
 *@param:        *ppParam    Set the callback, with a pointer to the parameter.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getCb(jni_asplayer_handle handle, event_callback *pfunc, void* *ppParam) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->getEventCallback(pfunc, ppParam);

    return JNI_ASPLAYER_OK;
}

#ifdef __cplusplus
};
#endif
