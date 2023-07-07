/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#if (USE_SYSTEM_SO == 0)
#include "JniASPlayerWrapper.h"
#include "common/utils/Log.h"

#define LOG_PLAYER_OP_FAILED(ret) ALOGE("[%s/%d] %s failed, ret: %d", __func__, __LINE__, __func__, ret)

static void asplayer_callback(void *user_data, jni_asplayer_event *event) {
    ALOGI("%s[%d] event type: %d", __FUNCTION__, __LINE__, event ? event->type : -1);
    asplayer_callback_userdata_t *data = static_cast<asplayer_callback_userdata_t *>(user_data);
    JniASPlayerWrapper *player = data->player;

    if (player == nullptr) {
        ALOGE("%s[%d] notify event failed, failed to get player", __func__, __LINE__);
        return;
    }

    switch (event->type) {
        case JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED:
        case JNI_ASPLAYER_EVENT_TYPE_AUDIO_CHANGED:
        case JNI_ASPLAYER_EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO:
        case JNI_ASPLAYER_EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO:
        case JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO:
        case JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO:
        case JNI_ASPLAYER_EVENT_TYPE_PTS:
            player->notifyPlaybackListeners(event);
            break;
        default:
            break;
    }
}

JniASPlayerWrapper::JniASPlayerWrapper() : mHandle(0), mpCallbackUserData(nullptr) {
    mpCallbackUserData = new asplayer_callback_userdata_t;
    mpCallbackUserData->player = this;
}

JniASPlayerWrapper::~JniASPlayerWrapper() {
    if (mpCallbackUserData) {
        mpCallbackUserData->player = nullptr;
        delete mpCallbackUserData;
        mpCallbackUserData = nullptr;
    }
}

void JniASPlayerWrapper::setHandle(jni_asplayer_handle handle) {
    std::lock_guard<std::mutex> lock(mMutex);
    mHandle = handle;
}

jni_asplayer_handle JniASPlayerWrapper::getHandle() {
    std::lock_guard<std::mutex> lock(mMutex);
    return mHandle;
}

jni_asplayer_result JniASPlayerWrapper::create(jni_asplayer_init_params &params, void *jTuner, jni_asplayer_handle *handle) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_result ret = JniASPlayer_create(params, jTuner, handle);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::getJavaASPlayer(jobject *pPlayer) {
    LOG_FUNCTION_ENTER();
    std::lock_guard<std::mutex> lock(mMutex);
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_getJavaASPlayer(handle, pPlayer);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
        return ret;
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::prepare() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    // register callback
    jni_asplayer_result ret = JniASPlayer_registerCb(handle, asplayer_callback, mpCallbackUserData);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("%s[%d] prepare asplayer error, failed to register callback, error: %d", __func__, __LINE__, ret);
        return ret;
    }

    ret = JniASPlayer_prepare(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
        return ret;
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::addPlaybackListener(JNIEnv *env, jobject listener) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        LOG_FUNCTION_END();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    std::lock_guard<std::mutex> lock(mEventMutex);
    PlaybackListenerWrapper *playbackListenerWrapper = new PlaybackListenerWrapper(env, listener);
    mPlaybackListeners.push_back(playbackListenerWrapper);
    jni_asplayer_result ret = JNI_ASPLAYER_OK;
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::removePlaybackListener(JNIEnv *env, jobject listener) {
    LOG_FUNCTION_ENTER();
    std::lock_guard<std::mutex> lock(mEventMutex);
    // 删除所有相同的callback
    for (std::list<PlaybackListenerWrapper*>::iterator it = mPlaybackListeners.begin(); it != mPlaybackListeners.end(); ) {
        PlaybackListenerWrapper *listenerWrapper = (*it);
        if (env->IsSameObject(listenerWrapper->getJavaListener(), listener)) {
            listenerWrapper->release();
            delete listenerWrapper;
            it = mPlaybackListeners.erase(it);
        } else {
            ++it;
        }
    }
    jni_asplayer_result ret = JNI_ASPLAYER_OK;
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::startVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_startVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::stopVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_stopVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::pauseVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_pauseVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::resumeVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_resumeVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::startAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_startAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::stopAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_stopAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::pauseAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_pauseAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::resumeAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_resumeAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setVideoParams(jni_asplayer_video_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_setVideoParams(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setAudioParams(jni_asplayer_audio_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_setAudioParams(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::switchAudioTrack(jni_asplayer_audio_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_switchAudioTrack(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::flush() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_flush(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::flushDvr() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_flushDvr(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::writeData(jni_asplayer_input_buffer *buf, uint64_t timeout_ms) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_writeData(handle, buf, timeout_ms);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setSurface(void *surface) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }
    jni_asplayer_result ret = JniASPlayer_setSurface(handle, surface);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setAudioMute(bool analogMute, bool digitMute) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    bool_t aMute = analogMute ? 1 : 0;
    bool_t dMute = digitMute ? 1 : 0;
    jni_asplayer_result ret = JniASPlayer_setAudioMute(handle, aMute, dMute);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setAudioVolume(int volume) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_setAudioVolume(handle, (int32_t) volume);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::getAudioVolume(int *volume) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_getAudioVolume(handle, volume);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::startFast(float scale) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_startFast(handle, scale);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::stopFast() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_stopFast(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setTrickMode(jni_asplayer_video_trick_mode trickMode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_setTrickMode(handle, trickMode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setTransitionModeBefore(jni_asplayer_transition_mode_before mode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_setTransitionModeBefore(handle, mode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::setPIPMode(jni_asplayer_pip_mode mode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_setPIPMode(handle, mode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result JniASPlayerWrapper::release() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = JniASPlayer_release(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }

    mHandle = 0;

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

void JniASPlayerWrapper::notifyPlaybackListeners(jni_asplayer_event *event) {
    if (!event) {
        return;
    }

    {
        std::lock_guard<std::mutex> lock(mEventMutex);
        for (std::list<PlaybackListenerWrapper*>::iterator it = mPlaybackListeners.begin(); it != mPlaybackListeners.end(); ++it) {
            PlaybackListenerWrapper *listener = *it;
            listener->notifyPlaybackEvent(event);
        }
    }
}
#endif
