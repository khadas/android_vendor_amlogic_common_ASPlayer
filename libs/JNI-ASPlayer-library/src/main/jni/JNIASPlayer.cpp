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

#define LOG_GET_JNIENV_FAILED() AP_LOGE(" failed to get jni env.\n")
#define LOG_GET_PLAYER_FAILED() AP_LOGE(" failed to get java player instance.\n")

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
    } else {
        AP_LOGE("failed to get JavaVM.");
        return JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }

    AP_LOGI("register jni called.");

    bool initJniRet = JniASPlayerJNI::initASPlayerJNI(env);
    (void)initJniRet;
    AP_LOGI("init jni result: %s", initJniRet ? "success" : "false");
    return JNI_ASPLAYER_OK;
}

/**
 * @brief:        Create JniASPlayer instance.
 *                Set input mode and event mask to JniASPlayer.
 * @param:        params    Init params with input mode and event mask.
 * @param:        *pHandle  JniASPlayer handle.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_create(jni_asplayer_init_params params,
                                        void *tuner,
                                        jni_asplayer_handle *pHandle) {
    LOG_FUNCTION_ENTER();
    JniASPlayer *player = new JniASPlayer();
    if (!player->create(&params, tuner)) {
        AP_LOGE("create java ASPlayer failed.");
        delete player;
        return JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }

    *pHandle = (jni_asplayer_handle)player;
    AP_LOGI("create ASPlayer: %p", player);
    return JNI_ASPLAYER_OK;
}

/**
 * @brief:        Get ASPlayer instance.
 * @param:        handle    JniASPlayer handle.
 * @param:        *pASPlayer ASPlayer instance.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_getJavaASPlayer(jni_asplayer_handle handle, jobject *pASPlayer) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jobject *pJavaPlayer = nullptr;
    if (!player->getJavaASPlayer(&pJavaPlayer)) {
        LOG_GET_PLAYER_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    *pASPlayer = *pJavaPlayer;
    LOG_FUNCTION_END();
    return JNI_ASPLAYER_OK;
}

/**
 * @brief:        Prepare JniASPlayer instance.
 * @param:        handle  JniASPlayer handle.
 * @return:       The JniASPlayer result.
*/
jni_asplayer_result  JniASPlayer_prepare(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->prepare();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Get the instance number of specified JniASPlayer.
 *@param:        handle    JniASPlayer handle.
 *@param:        *numb     JniASPlayer instance number.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getInstanceNo(jni_asplayer_handle handle, int32_t *numb) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!numb) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getInstanceNo(numb);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Get the sync instance number of specified JniASPlayer .
 *@param:        handle    JniASPlayer handle.
 *@param:        *numb     JniASPlayer instance number.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getSyncInstanceNo(jni_asplayer_handle handle, int32_t *numb) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!numb) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getSyncInstanceNo(numb);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Get video basic info of specified JniASPlayer instance.
 *@param:        handle      JniASPlayer handle.
 *@param:        *pInfo      The ptr of video basic info struct .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getVideoInfo(jni_asplayer_handle handle, jni_asplayer_video_info *pInfo) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!pInfo) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getVideoInfo(pInfo);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Start video decoding for specified JniASPlayer instance .
 *@param:        handle      JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startVideoDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->startVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Stop video decoding for specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopVideoDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->stopVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Pause video decoding for specified JniASPlayer instance .
 *@param:        handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_pauseVideoDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->pauseVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Resume video decoding for specified JniASPlayer instance .
 *@param:        handle      JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resumeVideoDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->resumeVideoDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Start audio decoding for specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startAudioDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->startAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Pause audio decoding for specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_pauseAudioDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->pauseAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Resume audio decoding for specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resumeAudioDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->resumeAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Stop audio decoding for specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopAudioDecoding(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->stopAudioDecoding();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set video params need by demuxer and video decoder
 *               for specified JniASPlayer instance.
 *@param:        handle      JniASPlayer handle.
 *@param:        *pParams    Params need by demuxer and video decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setVideoParams(jni_asplayer_handle handle,
                                                jni_asplayer_video_params *pParams) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!pParams) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setVideoParams(pParams);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set audio params need by demuxer and audio decoder
 *               to specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        *pParams   Params need by demuxer and audio decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioParams(jni_asplayer_handle handle,
                                                jni_asplayer_audio_params *pParams) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!pParams) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setAudioParams(pParams);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Switch audio track for specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        *pParams   Params need by demuxer and audio decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_switchAudioTrack(jni_asplayer_handle handle,
                                                  jni_asplayer_audio_params *pParams) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!pParams) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->switchAudioTrack(pParams);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Flush specified JniASPlayer instance.
 *@param:        handle         JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_flush(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->flush();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Flush DvrPlayback of specified JniASPlayer instance.
 *@param:        handle         JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_flushDvr(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->flushDvr();
    LOG_FUNCTION_INT_END(ret);
    return ret;
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
jni_asplayer_result  JniASPlayer_writeData(jni_asplayer_handle handle,
                                           jni_asplayer_input_buffer *buf,
                                           uint64_t timeout_ms) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!buf) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->writeData(buf, timeout_ms);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set Surface ptr to specified JniASPlayer instance.
 *@param:        handle       JniASPlayer handle.
 *@param:        *pSurface    Surface ptr
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setSurface(jni_asplayer_handle handle, void* pSurface) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setSurface(pSurface);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set audio output mute to specified JniASPlayer instance .
 *@param:        handle         JniASPlayer handle.
 *@param:        analog_mute    If analog mute or unmute .
 *@param:        digital_mute   If digital mute or unmute .
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioMute(jni_asplayer_handle handle,
                                              bool_t analog_mute,
                                              bool_t digital_mute) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    bool aMute = analog_mute ? true : false;
    bool dMute = digital_mute ? true : false;
    jni_asplayer_result ret = player->setAudioMute(aMute, dMute);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set audio volume to specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@param:        volume     Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioVolume(jni_asplayer_handle handle, int32_t volume) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setAudioVolume(volume);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Get audio volume value from specified JniASPlayer instance .
 *@param:        handle      JniASPlayer handle.
 *@param:        *volume     Volume value.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioVolume(jni_asplayer_handle handle, int32_t *volume) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!volume) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getAudioVolume(volume);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Start Fast play for specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        scale      Fast play speed.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_startFast(jni_asplayer_handle handle, float scale) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->startFast(scale);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Stop Fast play for specified JniASPlayer instance.
 *@param:        handle       JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_stopFast(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->stopFast();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set trick mode for specified JniASPlayer instance.
 *@param:        handle        JniASPlayer handle.
 *@param:        trickmode     The enum of trick mode type
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setTrickMode(jni_asplayer_handle handle,
                                              jni_asplayer_video_trick_mode trickmode) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setTrickMode(trickmode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set if need keep last frame for video display
 *               for specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        mode       transition mode before.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result JniASPlayer_setTransitionModeBefore(jni_asplayer_handle handle,
                                                        jni_asplayer_transition_mode_before mode) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setTransitionModeBefore(mode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set if need show first image before sync
 *               for specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        mode       transition mode after.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setTransitionModeAfter(jni_asplayer_handle handle,
                                                        jni_asplayer_transition_mode_after mode) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setTransitionModeAfter(mode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set transition preroll rate
 *               for specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        rate       transition preroll rate.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setTransitionPrerollRate(jni_asplayer_handle handle,
                                                          float rate) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setTransitionPrerollRate(rate);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set maximum a/v time difference in ms to start preroll
 *               for specified JniASPlayer instance.
 *               This value limits the max time of preroll duration.
 *@param:        handle         JniASPlayer handle.
 *@param:        milliSecond    the max time of preroll duration
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setTransitionPrerollAVTolerance(jni_asplayer_handle handle,
                                                                 int32_t milliSecond) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setTransitionPrerollAvTolerance(milliSecond);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set video mute or not
 *               for specified JniASPlayer instance.
 *@param:        handle         JniASPlayer handle.
 *@param:        mute           mute or not
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result JniASPlayer_setVideoMute(jni_asplayer_handle handle,
                                             jni_asplayer_video_mute mute) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setVideoMute(mute);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set screen color for specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        mode       screen color mode.
 *@param:        color      screen color.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setScreenColor(jni_asplayer_handle handle,
                                                jni_asplayer_screen_color_mode mode,
                                                jni_asplayer_screen_color color) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setScreenColor(mode, color);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set work mode to specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        mode       The enum of work mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result JniASPlayer_setWorkMode(jni_asplayer_handle handle,
                                            jni_asplayer_work_mode mode) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setWorkMode(mode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Reset work mode to specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_resetWorkMode(jni_asplayer_handle handle) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->resetWorkMode();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set PIP mode to specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        mode       The enum of PIP mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setPIPMode(jni_asplayer_handle handle, jni_asplayer_pip_mode mode) {
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setPIPMode(mode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set audio description params need by demuxer
 *               and audio decoder to specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@param:        *pParams   Params need by demuxer and audio decoder.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setADParams(jni_asplayer_handle handle, jni_asplayer_audio_params *pParams) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!pParams) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setADParams(pParams);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Enable audio description mix with master audio
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_enableADMix(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->enableADMix();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Disable audio description mix with master audio
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_disableADMix(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->disableADMix();
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set audio description volume
 *@param:        handle        JniASPlayer handle.
 *@param:        volumeDb      AD volume in dB.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setADVolumeDB(jni_asplayer_handle handle, float volumeDB) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setADVolumeDB(volumeDB);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Get audio description volume
 *@param:        handle        JniASPlayer handle.
 *@param:        *volumeDB     AD volume in dB.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getADVolumeDB(jni_asplayer_handle handle, float *volumeDB) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!volumeDB) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getADVolumeDB(volumeDB);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Set audio description mix level (ad vol)
 *@param:        handle        JniASPlayer handle.
 *@param:        mixLevel      audio description mix level.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setADMixLevel(jni_asplayer_handle handle, int32_t mixLevel) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setADMixLevel(mixLevel);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Get audio description mix level (ad vol)
 *@param:        handle        JniASPlayer handle.
 *@param:        *mixLevel     audio description mix level.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getADMixLevel(jni_asplayer_handle handle, int32_t *mixLevel) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!mixLevel) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getADMixLevel(mixLevel);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Sets the Dual Mono mode to specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@param:        mode       dual mono mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setAudioDualMonoMode(jni_asplayer_handle handle,
                                                      jni_asplayer_audio_dual_mono_mode mode) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setAudioDualMonoMode(mode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Returns the Dual Mono mode of specified JniASPlayer instance .
 *@param:        handle    JniASPlayer handle.
 *@param:        *pMode    dual mono mode.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getAudioDualMonoMode(jni_asplayer_handle handle,
                                                      jni_asplayer_audio_dual_mono_mode *pMode) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    } else if (!pMode) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->getAudioDualMonoMode(pMode);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

/**
 *@brief:        Release specified JniASPlayer instance.
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_release(jni_asplayer_handle handle) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->release();

    delete player;

    LOG_FUNCTION_END();
    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        set Params for specified JniASPlayer instance .
 *@param:        handle     JniASPlayer handle.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_setParams(jni_asplayer_handle handle,
                                           jni_asplayer_parameter type,
                                           void* arg) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    jni_asplayer_result ret = player->setParameter(type, arg);
    LOG_FUNCTION_INT_END(ret);

    return ret;
}

/**
 *@brief:        Register event callback to specified JniASPlayer
 *@param:        handle    JniASPlayer handle.
 *@param:        pfunc     Event callback function ptr.
 *@param:        *param    Extra data ptr.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_registerCb(jni_asplayer_handle handle,
                                            event_callback pfunc,
                                            void *param) {
    LOG_FUNCTION_ENTER();
    if (handle == 0) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JniASPlayer *player = reinterpret_cast<JniASPlayer*>(handle);
    player->setEventCallback(pfunc, param);

    LOG_FUNCTION_END();
    return JNI_ASPLAYER_OK;
}

/**
 *@brief:        Get event callback to specified JniASPlayer
 *@param:        handle      JniASPlayer handle.
 *@param:        *pfunc      ptr of Event callback function ptr.
 *@param:        *ppParam    Set the callback, with a pointer to the parameter.
 *@return:       The JniASPlayer result.
 */
jni_asplayer_result  JniASPlayer_getCb(jni_asplayer_handle handle,
                                       event_callback *pfunc,
                                       void* *ppParam) {
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