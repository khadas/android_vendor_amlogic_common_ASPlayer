/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <dlfcn.h>
#include "DynamicJniASPlayerWrapper.h"

#include "common/utils/Log.h"


#define LOG_PLAYER_OP_FAILED(ret) ALOGE("%s[%d] %s failed, ret: %d", __func__, __LINE__, __func__, ret)
#define NUMEL(arr) (sizeof(arr) / sizeof(arr[0]))

#define CHECK_SYMBOL(sym,name) \
if (sym == nullptr) { \
    ALOGE("%s SYMBOL %s not found, error: %s", __func__, name, dlerror()); \
} else { \
    ALOGE("%s SYMBOL %s found", __func__, name); \
}

static const char *JNI_ASPLAYER_LIB_PATHS[] = {
    "/system_ext/lib/libjniasplayer-jni.so",
    "/vendor/lib/libjniasplayer-jni.so",
    "/system/lib/libjniasplayer-jni.so",
    "/product/lib/libjniasplayer-jni.so"
};

bool DynamicJniASPlayerWrapper::sInited = false;
void* DynamicJniASPlayerWrapper::sSoHandle = nullptr;

JniASPlayer_registerJNI_FUNC DynamicJniASPlayerWrapper::ASPlayer_registerJni = nullptr;
JniASPlayer_create_FUNC DynamicJniASPlayerWrapper::ASPlayer_create = nullptr;
JniASPlayer_getJavaASPlayer_FUNC DynamicJniASPlayerWrapper::ASPlayer_getJavaASPlayer = nullptr;
JniASPlayer_prepare_FUNC DynamicJniASPlayerWrapper::ASPlayer_prepare = nullptr;
JniASPlayer_getVersion_FUNC DynamicJniASPlayerWrapper::ASPlayer_getVersion = nullptr;
JniASPlayer_getInstanceNo_FUNC DynamicJniASPlayerWrapper::ASPlayer_getInstanceNo = nullptr;
JniASPlayer_getSyncInstanceNo_FUNC DynamicJniASPlayerWrapper::ASPlayer_getSyncInstanceNo = nullptr;
JniASPlayer_registerCb_FUNC DynamicJniASPlayerWrapper::ASPlayer_registerCb = nullptr;
JniASPlayer_getCb_FUNC DynamicJniASPlayerWrapper::ASPlayer_getCb = nullptr;
JniASPlayer_release_FUNC DynamicJniASPlayerWrapper::ASPlayer_release = nullptr;
JniASPlayer_writeFrameData_FUNC DynamicJniASPlayerWrapper::ASPlayer_writeFrameData = nullptr;
JniASPlayer_writeData_FUNC DynamicJniASPlayerWrapper::ASPlayer_writeData = nullptr;
JniASPlayer_setWorkMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_setWorkMode = nullptr;
JniASPlayer_resetWorkMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_resetWorkMode = nullptr;
JniASPlayer_setPIPMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_setPIPMode = nullptr;
JniASPlayer_getCurrentTime_FUNC DynamicJniASPlayerWrapper::ASPlayer_getCurrentTime = nullptr;
JniASPlayer_setSyncMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_setSyncMode = nullptr;
JniASPlayer_getSyncMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_getSyncMode = nullptr;
JniASPlayer_setPcrPid_FUNC DynamicJniASPlayerWrapper::ASPlayer_setPcrPid = nullptr;
JniASPlayer_startFast_FUNC DynamicJniASPlayerWrapper::ASPlayer_startFast = nullptr;
JniASPlayer_stopFast_FUNC DynamicJniASPlayerWrapper::ASPlayer_stopFast = nullptr;
JniASPlayer_setTrickMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_setTrickMode = nullptr;
JniASPlayer_setSurface_FUNC DynamicJniASPlayerWrapper::ASPlayer_setSurface = nullptr;
JniASPlayer_setVideoParams_FUNC DynamicJniASPlayerWrapper::ASPlayer_setVideoParams = nullptr;
JniASPlayer_setTransitionModeBefore_FUNC DynamicJniASPlayerWrapper::ASPlayer_setTransitionModeBefore = nullptr;
JniASPlayer_getVideoInfo_FUNC DynamicJniASPlayerWrapper::ASPlayer_getVideoInfo = nullptr;
JniASPlayer_startVideoDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_startVideoDecoding = nullptr;
JniASPlayer_pauseVideoDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_pauseVideoDecoding = nullptr;
JniASPlayer_resumeVideoDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_resumeVideoDecoding = nullptr;
JniASPlayer_stopVideoDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_stopVideoDecoding = nullptr;
JniASPlayer_setAudioVolume_FUNC DynamicJniASPlayerWrapper::ASPlayer_setAudioVolume = nullptr;
JniASPlayer_getAudioVolume_FUNC DynamicJniASPlayerWrapper::ASPlayer_getAudioVolume = nullptr;
JniASPlayer_setAudioStereoMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_setAudioStereoMode = nullptr;
JniASPlayer_getAudioStereoMode_FUNC DynamicJniASPlayerWrapper::ASPlayer_getAudioStereoMode = nullptr;
JniASPlayer_setAudioMute_FUNC DynamicJniASPlayerWrapper::ASPlayer_setAudioMute = nullptr;
JniASPlayer_getAudioMute_FUNC DynamicJniASPlayerWrapper::ASPlayer_getAudioMute = nullptr;
JniASPlayer_setAudioParams_FUNC DynamicJniASPlayerWrapper::ASPlayer_setAudioParams = nullptr;
JniASPlayer_switchAudioTrack_FUNC DynamicJniASPlayerWrapper::ASPlayer_switchAudioTrack = nullptr;
JniASPlayer_getAudioInfo_FUNC DynamicJniASPlayerWrapper::ASPlayer_getAudioInfo = nullptr;
JniASPlayer_startAudioDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_startAudioDecoding = nullptr;
JniASPlayer_pauseAudioDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_pauseAudioDecoding = nullptr;
JniASPlayer_resumeAudioDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_resumeAudioDecoding = nullptr;
JniASPlayer_stopAudioDecoding_FUNC DynamicJniASPlayerWrapper::ASPlayer_stopAudioDecoding = nullptr;
JniASPlayer_getADInfo_FUNC DynamicJniASPlayerWrapper::ASPlayer_getADInfo = nullptr;
JniASPlayer_setSubPid_FUNC DynamicJniASPlayerWrapper::ASPlayer_setSubPid = nullptr;
JniASPlayer_getParams_FUNC DynamicJniASPlayerWrapper::ASPlayer_getParams = nullptr;
JniASPlayer_setParams_FUNC DynamicJniASPlayerWrapper::ASPlayer_setParams = nullptr;
JniASPlayer_getState_FUNC DynamicJniASPlayerWrapper::ASPlayer_getState = nullptr;
JniASPlayer_startSub_FUNC DynamicJniASPlayerWrapper::ASPlayer_startSub = nullptr;
JniASPlayer_stopSub_FUNC DynamicJniASPlayerWrapper::ASPlayer_stopSub = nullptr;
JniASPlayer_getFirstPts_FUNC DynamicJniASPlayerWrapper::ASPlayer_getFirstPts = nullptr;
JniASPlayer_flush_FUNC DynamicJniASPlayerWrapper::ASPlayer_flush = nullptr;
JniASPlayer_flushDvr_FUNC DynamicJniASPlayerWrapper::ASPlayer_flushDvr = nullptr;
JniASPlayer_setADParams_FUNC DynamicJniASPlayerWrapper::ASPlayer_setADParams = nullptr;
JniASPlayer_enableADMix_FUNC DynamicJniASPlayerWrapper::ASPlayer_enableADMix = nullptr;
JniASPlayer_disableADMix_FUNC DynamicJniASPlayerWrapper::ASPlayer_disableADMix = nullptr;
JniASPlayer_setADVolumeDB_FUNC DynamicJniASPlayerWrapper::ASPlayer_setADVolumeDB = nullptr;
JniASPlayer_getADVolumeDB_FUNC DynamicJniASPlayerWrapper::ASPlayer_getADVolumeDB = nullptr;


static void asplayer_callback(void *user_data, jni_asplayer_event *event) {
    if (event && event->type != JNI_ASPLAYER_EVENT_TYPE_PTS) {
        ALOGI("%s[%d] event type: %d", __func__, __LINE__, event ? event->type : -1);
    }

    dasplayer_callback_userdata_t *data = static_cast<dasplayer_callback_userdata_t *>(user_data);
    DynamicJniASPlayerWrapper *player = data->player;

    if (player == nullptr) {
        ALOGE("%s[%d] notify event failed, failed to get player", __func__, __LINE__);
        return;
    }

    switch (event->type) {
        case JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED:
            player->notifyPlaybackListeners(event);
            break;
        default:
            break;
    }
}

DynamicJniASPlayerWrapper::DynamicJniASPlayerWrapper() : mHandle(0), mpCallbackUserData(nullptr) {
    mpCallbackUserData = new dasplayer_callback_userdata_t;
    mpCallbackUserData->player = this;
}

DynamicJniASPlayerWrapper::~DynamicJniASPlayerWrapper() {
    if (mpCallbackUserData) {
        delete mpCallbackUserData;
        mpCallbackUserData = nullptr;
    }
}

jni_asplayer_result DynamicJniASPlayerWrapper::registerJni(JNIEnv *env) {
    if (sInited) {
        return JNI_ASPLAYER_OK;
    }

    openJniASPlayerSo();

    if (sSoHandle == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    initSymbols();

    jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    if (ASPlayer_registerJni != nullptr) {
        ret = ASPlayer_registerJni(env);
    }

    sInited = true;

    return ret;
}

void DynamicJniASPlayerWrapper::openJniASPlayerSo() {
    for (int i = 0; i < NUMEL(JNI_ASPLAYER_LIB_PATHS); i++) {
        const char *libpath = JNI_ASPLAYER_LIB_PATHS[i];
        void *handle = dlopen(libpath, RTLD_LAZY | RTLD_LOCAL);

        char *errstr = NULL;
        if (handle == nullptr) {
            ALOGE("%s[%d] dlopen(\"%s\") failed", __func__, __LINE__, libpath);

            errstr = dlerror();
            if (errstr != nullptr) {
                ALOGE("%s[%d] dlerror: %s", __func__, __LINE__, errstr);
            }
            continue;
        } else {
            ALOGI("%s[%d] dlopen(\"%s\") success", __func__, __LINE__, libpath);
            sSoHandle = handle;
            break;
        }
    }
}

void DynamicJniASPlayerWrapper::initSymbols() {
    void *handle = sSoHandle;
    if (handle == nullptr) {
        return;
    }

    ASPlayer_registerJni = (JniASPlayer_registerJNI_FUNC)(dlsym(handle, "JniASPlayer_registerJNI"));
    CHECK_SYMBOL(ASPlayer_registerJni, "JniASPlayer_registerJNI");
    ASPlayer_create = (JniASPlayer_create_FUNC)(dlsym(handle, "JniASPlayer_create"));
    CHECK_SYMBOL(ASPlayer_create, "JniASPlayer_create");
    ASPlayer_getVersion = (JniASPlayer_getVersion_FUNC)(dlsym(handle, "JniASPlayer_getVersion"));
    CHECK_SYMBOL(ASPlayer_getVersion, "JniASPlayer_getVersion");
    ASPlayer_getInstanceNo = (JniASPlayer_getInstanceNo_FUNC)(dlsym(handle, "JniASPlayer_getInstansNo"));
    CHECK_SYMBOL(ASPlayer_getInstanceNo, "JniASPlayer_getInstansNo");
    ASPlayer_getSyncInstanceNo = (JniASPlayer_getSyncInstanceNo_FUNC)(dlsym(handle, "JniASPlayer_getSyncInstansNo"));
    CHECK_SYMBOL(ASPlayer_getSyncInstanceNo, "JniASPlayer_getSyncInstansNo");
    ASPlayer_getJavaASPlayer = (JniASPlayer_getJavaASPlayer_FUNC)(dlsym(handle, "JniASPlayer_getJavaASPlayer"));
    CHECK_SYMBOL(ASPlayer_getJavaASPlayer, "JniASPlayer_getJavaASPlayer");
    ASPlayer_prepare = (JniASPlayer_prepare_FUNC)(dlsym(handle, "JniASPlayer_prepare"));
    CHECK_SYMBOL(ASPlayer_prepare, "JniASPlayer_prepare");
    ASPlayer_registerCb = (JniASPlayer_registerCb_FUNC)(dlsym(handle, "JniASPlayer_registerCb"));
    CHECK_SYMBOL(ASPlayer_registerCb, "JniASPlayer_registerCb");
    ASPlayer_getCb = (JniASPlayer_getCb_FUNC)(dlsym(handle, "JniASPlayer_getCb"));
    CHECK_SYMBOL(ASPlayer_getCb, "JniASPlayer_getCb");
    ASPlayer_release = (JniASPlayer_release_FUNC)(dlsym(handle, "JniASPlayer_release"));
    CHECK_SYMBOL(ASPlayer_release, "JniASPlayer_release");
    ASPlayer_writeFrameData = (JniASPlayer_writeFrameData_FUNC)(dlsym(handle, "JniASPlayer_writeFrameData"));
    CHECK_SYMBOL(ASPlayer_writeFrameData, "JniASPlayer_writeFrameData");
    ASPlayer_writeData = (JniASPlayer_writeData_FUNC)(dlsym(handle, "JniASPlayer_writeData"));
    CHECK_SYMBOL(ASPlayer_writeData, "JniASPlayer_writeData");
    ASPlayer_setWorkMode = (JniASPlayer_setWorkMode_FUNC)(dlsym(handle, "JniASPlayer_setWorkMode"));
    CHECK_SYMBOL(ASPlayer_setWorkMode, "JniASPlayer_setWorkMode");
    ASPlayer_resetWorkMode = (JniASPlayer_resetWorkMode_FUNC)(dlsym(handle, "JniASPlayer_resetWorkMode"));
    CHECK_SYMBOL(ASPlayer_resetWorkMode, "JniASPlayer_resetWorkMode");
    ASPlayer_setPIPMode = (JniASPlayer_setPIPMode_FUNC)(dlsym(handle, "JniASPlayer_setPIPMode"));
    CHECK_SYMBOL(ASPlayer_setPIPMode, "JniASPlayer_setPIPMode");
    ASPlayer_getCurrentTime = (JniASPlayer_getCurrentTime_FUNC)(dlsym(handle, "JniASPlayer_getCurrentTime"));
    CHECK_SYMBOL(ASPlayer_getCurrentTime, "JniASPlayer_getCurrentTime");
    ASPlayer_getSyncMode = (JniASPlayer_getSyncMode_FUNC)(dlsym(handle, "JniASPlayer_getSyncMode"));
    CHECK_SYMBOL(ASPlayer_getSyncMode, "JniASPlayer_getSyncMode");
    ASPlayer_setPcrPid = (JniASPlayer_setPcrPid_FUNC)(dlsym(handle, "JniASPlayer_setPcrPid"));
    CHECK_SYMBOL(ASPlayer_setPcrPid, "JniASPlayer_setPcrPid");
    ASPlayer_startFast = (JniASPlayer_startFast_FUNC)(dlsym(handle, "JniASPlayer_startFast"));
    CHECK_SYMBOL(ASPlayer_startFast, "JniASPlayer_startFast");
    ASPlayer_stopFast = (JniASPlayer_stopFast_FUNC)(dlsym(handle, "JniASPlayer_stopFast"));
    CHECK_SYMBOL(ASPlayer_stopFast, "JniASPlayer_stopFast");
    ASPlayer_setTrickMode = (JniASPlayer_setTrickMode_FUNC)(dlsym(handle, "JniASPlayer_setTrickMode"));
    CHECK_SYMBOL(ASPlayer_setTrickMode, "JniASPlayer_setTrickMode");
    ASPlayer_setSurface = (JniASPlayer_setSurface_FUNC)(dlsym(handle, "JniASPlayer_setSurface"));
    CHECK_SYMBOL(ASPlayer_setSurface, "JniASPlayer_setSurface");
    ASPlayer_flush = (JniASPlayer_flush_FUNC)(dlsym(handle, "JniASPlayer_flush"));
    CHECK_SYMBOL(ASPlayer_flush, "ASPlayer_flush");
    ASPlayer_flushDvr = (JniASPlayer_flush_FUNC)(dlsym(handle, "JniASPlayer_flushDvr"));
    CHECK_SYMBOL(ASPlayer_flushDvr, "ASPlayer_flushDvr");
    ASPlayer_setVideoParams = (JniASPlayer_setVideoParams_FUNC)(dlsym(handle, "JniASPlayer_setVideoParams"));
    CHECK_SYMBOL(ASPlayer_setVideoParams, "JniASPlayer_setVideoParams");
    ASPlayer_setTransitionModeBefore = (JniASPlayer_setTransitionModeBefore_FUNC)(dlsym(handle, "JniASPlayer_setTransitionModeBefore"));
    CHECK_SYMBOL(ASPlayer_setTransitionModeBefore, "JniASPlayer_setTransitionModeBefore");
    ASPlayer_getVideoInfo = (JniASPlayer_getVideoInfo_FUNC)(dlsym(handle, "JniASPlayer_getVideoInfo"));
    CHECK_SYMBOL(ASPlayer_getVideoInfo, "JniASPlayer_getVideoInfo");
    ASPlayer_startVideoDecoding = (JniASPlayer_startVideoDecoding_FUNC)(dlsym(handle, "JniASPlayer_startVideoDecoding"));
    CHECK_SYMBOL(ASPlayer_startVideoDecoding, "JniASPlayer_startVideoDecoding");
    ASPlayer_pauseVideoDecoding = (JniASPlayer_pauseVideoDecoding_FUNC)(dlsym(handle, "JniASPlayer_pauseVideoDecoding"));
    CHECK_SYMBOL(ASPlayer_pauseVideoDecoding, "JniASPlayer_pauseVideoDecoding");
    ASPlayer_resumeVideoDecoding = (JniASPlayer_resumeVideoDecoding_FUNC)(dlsym(handle, "JniASPlayer_resumeVideoDecoding"));
    CHECK_SYMBOL(ASPlayer_resumeVideoDecoding, "JniASPlayer_resumeVideoDecoding");
    ASPlayer_stopVideoDecoding = (JniASPlayer_stopVideoDecoding_FUNC)(dlsym(handle, "JniASPlayer_stopVideoDecoding"));
    CHECK_SYMBOL(ASPlayer_stopVideoDecoding, "JniASPlayer_stopVideoDecoding");
    ASPlayer_setAudioVolume = (JniASPlayer_setAudioVolume_FUNC)(dlsym(handle, "JniASPlayer_setAudioVolume"));
    CHECK_SYMBOL(ASPlayer_setAudioVolume, "JniASPlayer_setAudioVolume");
    ASPlayer_getAudioVolume = (JniASPlayer_getAudioVolume_FUNC)(dlsym(handle, "JniASPlayer_getAudioVolume"));
    CHECK_SYMBOL(ASPlayer_getAudioVolume, "JniASPlayer_getAudioVolume");
    ASPlayer_setAudioStereoMode = (JniASPlayer_setAudioStereoMode_FUNC)(dlsym(handle, "JniASPlayer_setAudioStereoMode"));
    CHECK_SYMBOL(ASPlayer_setAudioStereoMode, "JniASPlayer_setAudioStereoMode");
    ASPlayer_getAudioStereoMode = (JniASPlayer_getAudioStereoMode_FUNC)(dlsym(handle, "JniASPlayer_getAudioStereoMode"));
    CHECK_SYMBOL(ASPlayer_getAudioStereoMode, "JniASPlayer_getAudioStereoMode");
    ASPlayer_setAudioMute = (JniASPlayer_setAudioMute_FUNC)(dlsym(handle, "JniASPlayer_setAudioMute"));
    CHECK_SYMBOL(ASPlayer_setAudioMute, "JniASPlayer_setAudioMute");
    ASPlayer_getAudioMute = (JniASPlayer_getAudioMute_FUNC)(dlsym(handle, "JniASPlayer_getAudioMute"));
    CHECK_SYMBOL(ASPlayer_getAudioMute, "JniASPlayer_getAudioMute");
    ASPlayer_setAudioParams = (JniASPlayer_setAudioParams_FUNC)(dlsym(handle, "JniASPlayer_setAudioParams"));
    CHECK_SYMBOL(ASPlayer_setAudioParams, "JniASPlayer_setAudioParams");
    ASPlayer_switchAudioTrack = (JniASPlayer_switchAudioTrack_FUNC)(dlsym(handle, "JniASPlayer_switchAudioTrack"));
    CHECK_SYMBOL(ASPlayer_switchAudioTrack, "JniASPlayer_switchAudioTrack");
    ASPlayer_getAudioInfo = (JniASPlayer_getAudioInfo_FUNC)(dlsym(handle, "JniASPlayer_getAudioInfo"));
    CHECK_SYMBOL(ASPlayer_getAudioInfo, "JniASPlayer_getAudioInfo");
    ASPlayer_startAudioDecoding = (JniASPlayer_startAudioDecoding_FUNC)(dlsym(handle, "JniASPlayer_startAudioDecoding"));
    CHECK_SYMBOL(ASPlayer_startAudioDecoding, "JniASPlayer_startAudioDecoding");
    ASPlayer_pauseAudioDecoding = (JniASPlayer_pauseAudioDecoding_FUNC)(dlsym(handle, "JniASPlayer_pauseAudioDecoding"));
    CHECK_SYMBOL(ASPlayer_pauseAudioDecoding, "JniASPlayer_pauseAudioDecoding");
    ASPlayer_resumeAudioDecoding = (JniASPlayer_resumeAudioDecoding_FUNC)(dlsym(handle, "JniASPlayer_resumeAudioDecoding"));
    CHECK_SYMBOL(ASPlayer_resumeAudioDecoding, "JniASPlayer_resumeAudioDecoding");
    ASPlayer_stopAudioDecoding = (JniASPlayer_stopAudioDecoding_FUNC)(dlsym(handle, "JniASPlayer_stopAudioDecoding"));
    CHECK_SYMBOL(ASPlayer_stopAudioDecoding, "JniASPlayer_stopAudioDecoding");
    ASPlayer_getADInfo = (JniASPlayer_getADInfo_FUNC)(dlsym(handle, "JniASPlayer_getADInfo"));
    CHECK_SYMBOL(ASPlayer_getADInfo, "JniASPlayer_getADInfo");
    ASPlayer_setSubPid = (JniASPlayer_setSubPid_FUNC)(dlsym(handle, "JniASPlayer_setSubPid"));
    CHECK_SYMBOL(ASPlayer_setSubPid, "JniASPlayer_setSubPid");
    ASPlayer_getParams = (JniASPlayer_getParams_FUNC)(dlsym(handle, "JniASPlayer_getParams"));
    CHECK_SYMBOL(ASPlayer_getParams, "JniASPlayer_getParams");
    ASPlayer_setParams = (JniASPlayer_setParams_FUNC)(dlsym(handle, "JniASPlayer_setParams"));
    CHECK_SYMBOL(ASPlayer_setParams, "JniASPlayer_setParams");
    ASPlayer_getState = (JniASPlayer_getState_FUNC)(dlsym(handle, "JniASPlayer_getState"));
    CHECK_SYMBOL(ASPlayer_getState, "JniASPlayer_getState");
    ASPlayer_startSub = (JniASPlayer_startSub_FUNC)(dlsym(handle, "JniASPlayer_startSub"));
    CHECK_SYMBOL(ASPlayer_startSub, "JniASPlayer_startSub");
    ASPlayer_stopSub = (JniASPlayer_stopSub_FUNC)(dlsym(handle, "JniASPlayer_stopSub"));
    CHECK_SYMBOL(ASPlayer_stopSub, "JniASPlayer_stopSub");
    ASPlayer_getFirstPts = (JniASPlayer_getFirstPts_FUNC)(dlsym(handle, "JniASPlayer_getFirstPts"));
    CHECK_SYMBOL(ASPlayer_getFirstPts, "JniASPlayer_getFirstPts");
    ASPlayer_setADParams = (JniASPlayer_setADParams_FUNC)(dlsym(handle, "JniASPlayer_setADParams"));
    CHECK_SYMBOL(ASPlayer_setADParams, "JniASPlayer_setADParams");
    ASPlayer_enableADMix = (JniASPlayer_enableADMix_FUNC)(dlsym(handle, "JniASPlayer_enableADMix"));
    CHECK_SYMBOL(ASPlayer_enableADMix, "JniASPlayer_enableADMix");
    ASPlayer_disableADMix = (JniASPlayer_disableADMix_FUNC)(dlsym(handle, "JniASPlayer_disableADMix"));
    CHECK_SYMBOL(ASPlayer_disableADMix, "JniASPlayer_disableADMix");
    ASPlayer_setADVolumeDB = (JniASPlayer_setADVolumeDB_FUNC)(dlsym(handle, "JniASPlayer_setADVolumeDB"));
    CHECK_SYMBOL(ASPlayer_setADVolumeDB, "JniASPlayer_setADVolumeDB");
    ASPlayer_getADVolumeDB = (JniASPlayer_getADVolumeDB_FUNC)(dlsym(handle, "JniASPlayer_getADVolumeDB"));
    CHECK_SYMBOL(ASPlayer_getADVolumeDB, "JniASPlayer_getADVolumeDB");
}

void DynamicJniASPlayerWrapper::closeJniASPlayerSo() {
    if (sSoHandle != nullptr) {
        dlclose(sSoHandle);
        sSoHandle = nullptr;
    }
}

void DynamicJniASPlayerWrapper::setHandle(jni_asplayer_handle handle) {
    std::lock_guard<std::mutex> lock(mMutex);
    mHandle = handle;
}

jni_asplayer_handle DynamicJniASPlayerWrapper::getHandle() {
    std::lock_guard<std::mutex> lock(mMutex);
    return mHandle;
}

jni_asplayer_result DynamicJniASPlayerWrapper::create(jni_asplayer_init_params &params, void *jTuner, jni_asplayer_handle *handle) {
    LOG_FUNCTION_ENTER();
    if (ASPlayer_create == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_create(params, jTuner, handle);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::getJavaASPlayer(jobject *pPlayer) {
    LOG_FUNCTION_ENTER();
    std::lock_guard<std::mutex> lock(mMutex);
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_getJavaASPlayer == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_getJavaASPlayer(handle, pPlayer);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
        return ret;
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::prepare() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_registerCb == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    } else if (ASPlayer_prepare == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    // register callback
    jni_asplayer_result ret = ASPlayer_registerCb(handle, asplayer_callback, mpCallbackUserData);
    if (ret != JNI_ASPLAYER_OK) {
        ALOGE("%s[%d] prepare asplayer error, failed to register callback, error: %d", __func__, __LINE__, ret);
        return ret;
    }

    ret = ASPlayer_prepare(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
        return ret;
    }

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::addPlaybackListener(JNIEnv *env, jobject listener) {
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

jni_asplayer_result DynamicJniASPlayerWrapper::removePlaybackListener(JNIEnv *env, jobject listener) {
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

jni_asplayer_result DynamicJniASPlayerWrapper::startVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_startVideoDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_startVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::stopVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_stopVideoDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_stopVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::pauseVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_pauseVideoDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_pauseVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::resumeVideoDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_resumeVideoDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_resumeVideoDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::startAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_startAudioDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_startAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::stopAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_stopAudioDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_stopAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::pauseAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_pauseAudioDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_pauseAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::resumeAudioDecoding() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_resumeAudioDecoding == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_resumeAudioDecoding(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setVideoParams(jni_asplayer_video_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setVideoParams == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setVideoParams(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setAudioParams(jni_asplayer_audio_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setAudioParams == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setAudioParams(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::switchAudioTrack(jni_asplayer_audio_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_switchAudioTrack == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_switchAudioTrack(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::flush() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_flush == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_flush(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::flushDvr() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_flushDvr == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_flushDvr(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::writeData(jni_asplayer_input_buffer *buf, uint64_t timeout_ms) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_writeData == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_writeData(handle, buf, timeout_ms);
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setSurface(void *surface) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setSurface == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setSurface(handle, surface);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setAudioMute(bool analogMute, bool digitMute) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setAudioMute == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    bool_t aMute = analogMute ? 1 : 0;
    bool_t dMute = digitMute ? 1 : 0;
    jni_asplayer_result ret = ASPlayer_setAudioMute(handle, aMute, dMute);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setAudioVolume(int volume) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setAudioVolume == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setAudioVolume(handle, (int32_t) volume);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::getAudioVolume(int *volume) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_getAudioVolume == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_getAudioVolume(handle, volume);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::startFast(float scale) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_startFast == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_startFast(handle, scale);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::stopFast() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_stopFast == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_stopFast(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setTrickMode(jni_asplayer_video_trick_mode trickMode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setTrickMode == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setTrickMode(handle, trickMode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result
DynamicJniASPlayerWrapper::setTransitionModeBefore(jni_asplayer_transition_mode_before mode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setTransitionModeBefore == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setTransitionModeBefore(handle, mode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setWorkMode(jni_asplayer_work_mode mode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setWorkMode == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setWorkMode(handle, mode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::resetWorkMode() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_resetWorkMode == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_resetWorkMode(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setPIPMode(jni_asplayer_pip_mode mode) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setPIPMode == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setPIPMode(handle, mode);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setADParams(jni_asplayer_audio_params *params) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setADParams == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setADParams(handle, params);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::enableADMix() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_enableADMix == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_enableADMix(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::disableADMix() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_disableADMix == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_disableADMix(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::setADVolumeDB(float volumeDb) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_setADVolumeDB == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_setADVolumeDB(handle, volumeDb);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::getADVolumeDB(float *volumeDb) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_getADVolumeDB == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_getADVolumeDB(handle, volumeDb);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::getVideoInfo(jni_asplayer_video_info *videoInfo) {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_getVideoInfo == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_getVideoInfo(handle, videoInfo);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }
    LOG_FUNCTION_INT_END(ret);
    return ret;
}

jni_asplayer_result DynamicJniASPlayerWrapper::release() {
    LOG_FUNCTION_ENTER();
    jni_asplayer_handle handle = mHandle;
    if (handle == 0) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    if (ASPlayer_release == nullptr) {
        jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        LOG_FUNCTION_INT_END(ret);
        return ret;
    }

    jni_asplayer_result ret = ASPlayer_release(handle);
    if (ret != JNI_ASPLAYER_OK) {
        LOG_PLAYER_OP_FAILED(ret);
    }

    mHandle = 0;

    LOG_FUNCTION_INT_END(ret);
    return ret;
}

void DynamicJniASPlayerWrapper::notifyPlaybackListeners(jni_asplayer_event *event) {
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
