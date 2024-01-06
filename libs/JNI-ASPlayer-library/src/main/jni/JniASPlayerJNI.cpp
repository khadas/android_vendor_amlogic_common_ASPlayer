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

#include <jni.h>
#include <assert.h>
#include <unistd.h>
#include <memory>
#include "log.h"
#include "NativeHelper.h"
#include "JniMediaFormat.h"
#include "JniPlaybackListener.h"
#include "JniASPlayerJNI.h"

#define JASPLAYER_JNIENV_NAME "asplayer"

#define NELEM(arr) (sizeof(arr) / sizeof(arr[0]))

#define LOG_GET_JNIENV_FAILED(msg) AP_LOGE("%s failed, failed to get JNIEnv", msg)

static const char *JNI_ASPLAYER_CLASSPATH_NAME = "com/amlogic/jniasplayer/JniASPlayer";

/**
 * ASPlayer interface
 */
struct asplayer_t {
    jfieldID context;

    jmethodID constructorMID;
    jmethodID prepareMID;
    jmethodID getInstanceNoMID;
    jmethodID getSyncInstanceNoMID;
    jmethodID startVideoDecodingMID;
    jmethodID pauseVideoDecodingMID;
    jmethodID resumeVideoDecodingMID;
    jmethodID stopVideoDecodingMID;
    jmethodID startAudioDecodingMID;
    jmethodID stopAudioDecodingMID;
    jmethodID pauseAudioDecodingMID;
    jmethodID resumeAudioDecodingMID;
    jmethodID setVideoParamsMID;
    jmethodID setAudioParamsMID;
    jmethodID switchAudioTrackMID;
    jmethodID flushMID;
    jmethodID flushDvrMID;
//    jmethodID writeByteBufferMID;
    jmethodID writeDataMID;
    jmethodID setSurfaceMID;
    jmethodID setAudioMuteMID;
    jmethodID setAudioVolumeMID;
    jmethodID getAudioVolumeMID;
    jmethodID startFastMID;
    jmethodID stopFastMID;
    jmethodID setTrickModeMID;
    jmethodID setTransitionModeBeforeMID;
    jmethodID setTransitionModeAfterMID;
    jmethodID setTransitionPrerollRateMID;
    jmethodID setTransitionPrerollAvToleranceMID;
    jmethodID setVideoMuteMID;
    jmethodID setScreenColorMID;
    jmethodID releaseMID;
    jmethodID addPlaybackListenerMID;
    jmethodID removePlaybackListenerMID;
    jmethodID setWorkModeMID;
    jmethodID resetWorkModeMID;
    jmethodID setPIPModeMID;
    jmethodID setADParamsMID;
    jmethodID enableADMixMID;
    jmethodID disableADMixMID;
    jmethodID setADVolumeDBMID;
    jmethodID getADVolumeDBMID;
    jmethodID setADMixLevelMID;
    jmethodID getADMixLevelMID;
    jmethodID setAudioDualMonoModeMID;
    jmethodID getAudioDualMonoModeMID;
    jmethodID setParametersMID;
    jmethodID getVideoInfoMID;
};

// InitParams
struct init_param_t {
    jmethodID constructorMID;
    jfieldID playbackMode;
    jfieldID inputSourceType;
    jfieldID eventMask;
};

// VideoParams
struct video_param_t {
    jmethodID constructorMID;
    jfieldID mimeType;
    jfieldID width;
    jfieldID height;
    jfieldID pid;
    jfieldID trackFilterId;
    jfieldID avSyncHwId;
    jfieldID scrambled;
    jfieldID hasVideo;
    jfieldID mediaFormat;
};

// AudioParams
struct audio_param_t {
    jmethodID constructorMID;
    jfieldID mimeType;
    jfieldID sampleRate;
    jfieldID channelCount;
    jfieldID pid;
    jfieldID trackFilterId;
    jfieldID avSyncHwId;
    jfieldID secLevel;
    jfieldID scrambled;
    jfieldID mediaFormat;
    jfieldID extraInfoJson;
};

// InputBuffer
struct input_buffer_t {
    jmethodID constructorMID;
    jfieldID buffer;
    jfieldID offset;
    jfieldID bufferSize;
};

struct exceptions_t {
    jclass nullPointerExceptionCls;
    jclass illegalArgumentExceptionCls;
    jclass illegalStateExceptionCls;
};

static jclass gASPlayerCls;
static asplayer_t gASPlayerCtx;
static jclass gInitParamsCls;
static init_param_t gInitParamsCtx;
static jclass gVideoParamsCls;
static video_param_t gVideoParamsCtx;
static jclass gAudioParamsCls;
static audio_param_t gAudioParamsCtx;
static jclass gInputBufferCls;
static input_buffer_t gInputBufferCtx;

static exceptions_t gExceptionsCtx;

static volatile bool gJniInit = false;

/* static */JavaVM* JniASPlayerJNI::mJavaVM = nullptr;

void JniASPlayerJNI::setJavaVM(JavaVM* javaVM) {
    mJavaVM = javaVM;
}

/** return a pointer to the JNIEnv for this thread */
JNIEnv* JniASPlayerJNI::getJNIEnv() {
    assert(mJavaVM != nullptr);
    JNIEnv* env;
    if (mJavaVM->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        return nullptr;
    }
    return env;
}

/** create a JNIEnv* for this thread or assert if one already exists */
JNIEnv* JniASPlayerJNI::attachJNIEnv(const char *envName) {
    assert(getJNIEnv() == nullptr);
    JNIEnv* env = nullptr;
    JavaVMAttachArgs args = { JNI_VERSION_1_4, envName, NULL };
    int result = mJavaVM->AttachCurrentThread(&env, (void*) &args);
    if (result != JNI_OK) {
        ALOGE("%d thread attach failed: %#x", gettid(), result);
    }
    return env;
}

/** detach the current thread from the JavaVM */
void JniASPlayerJNI::detachJNIEnv() {
    assert(mJavaVM != nullptr);
    ALOGV("%d detachJNIEnv", gettid());
    mJavaVM->DetachCurrentThread();
}

JNIEnv *JniASPlayerJNI::getOrAttachJNIEnvironment() {
    if (mJavaVM == nullptr) {
        return nullptr;
    }

    JNIEnv *env = getJNIEnv();
    if (!env) {
        if (mJavaVM == nullptr) {
            return nullptr;
        }

        ALOGV("%d attach current thread to jvm", gettid());
        int result = mJavaVM->AttachCurrentThread(&env, nullptr);
        if (result != JNI_OK) {
            ALOGE("thread attach failed");
        }
        struct VmDetacher {
            VmDetacher(JavaVM *vm) : mVm(vm) {}
            ~VmDetacher() { ALOGV("%d detach current thread to jvm", gettid()); mVm->DetachCurrentThread(); }

        private:
            JavaVM *const mVm;
        };
        static thread_local VmDetacher detacher(mJavaVM);
    }
    return env;
}

bool JniASPlayerJNI::createJniASPlayer(JNIEnv *env, jni_asplayer_init_params *params, void *tuner, jobject *outJniASPlayer) {
    LOG_FUNCTION_ENTER();

    if (env == nullptr) {
        AP_LOGE("create JniASPlayer failed, env == null");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject jInitParam;
    if (!createInitParams(env, params, &jInitParam)) {
        AP_LOGE("createInitParams failed");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    AP_LOGI("createInitParams end");
    jobject obj = env->NewObject(gASPlayerCls, gASPlayerCtx.constructorMID, jInitParam, (jobject)tuner, nullptr);
    if (env->IsSameObject(obj, nullptr)) {
        AP_LOGE("create java ASPlayer failed");
        CHECK_JNI_EXCEPTION(env);
        DELETE_LOCAL_REF(env, jInitParam);
        return false;
    }

    DELETE_LOCAL_REF(env, jInitParam);

    AP_LOGI("create java ASPlayer end");
    *outJniASPlayer = obj;
    return true;
}

bool JniASPlayerJNI::createInitParams(JNIEnv *env, jni_asplayer_init_params *params, jobject *outJInitParams) {
    LOG_FUNCTION_ENTER();

    if (env == nullptr) {
        AP_LOGE("create InitParams failed, env is null");
        return false;
    } else if (params == nullptr) {
        AP_LOGE("create InitParams failed, invalid init_param");
        return false;
    } else if (outJInitParams == nullptr) {
        AP_LOGE("create InitParams failed, invalid out param");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject initParams = env->NewObject(gInitParamsCls, gInitParamsCtx.constructorMID);
    if (env->IsSameObject(initParams, nullptr)) {
        AP_LOGE("create java InitParams failed");
        CHECK_JNI_EXCEPTION(env);
        return false;
    }

    env->SetIntField(initParams, gInitParamsCtx.playbackMode, (jint)(params->playback_mode));
    env->SetIntField(initParams, gInitParamsCtx.inputSourceType, (jint)(params->source));
    env->SetLongField(initParams, gInitParamsCtx.eventMask, (jlong)(params->event_mask));
    *outJInitParams = initParams;

    LOG_FUNCTION_END();
    return true;
}

bool JniASPlayerJNI::createVideoParams(JNIEnv *env, jni_asplayer_video_params *params, jobject *outJVideoParams) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        AP_LOGE("create VideoParams failed, env is null");
        return false;
    } else if (params == nullptr) {
        AP_LOGE("create VideoParams failed, invalid video_param");
        return false;
    } else if (outJVideoParams == nullptr) {
        AP_LOGE("create VideoParams failed, invalid out param");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject videoParams = env->NewObject(gVideoParamsCls, gVideoParamsCtx.constructorMID);
    if (env->IsSameObject(videoParams, nullptr)) {
        AP_LOGE("create VideoParams failed");
        CHECK_JNI_EXCEPTION(env);
        return false;
    }

    if (params->mimeType) {
        jstring jMimeType = env->NewStringUTF(params->mimeType);
        env->SetObjectField(videoParams, gVideoParamsCtx.mimeType, jMimeType);
    }
    env->SetIntField(videoParams, gVideoParamsCtx.width, (jint) params->width);
    env->SetIntField(videoParams, gVideoParamsCtx.height, (jint) params->height);
    env->SetIntField(videoParams, gVideoParamsCtx.pid, (jint) params->pid);
    env->SetIntField(videoParams, gVideoParamsCtx.trackFilterId, (jint) params->filterId);
    env->SetIntField(videoParams, gVideoParamsCtx.avSyncHwId, (jint) params->avSyncHwId);
    env->SetBooleanField(videoParams, gVideoParamsCtx.scrambled, (jboolean) params->scrambled);
    env->SetObjectField(videoParams, gVideoParamsCtx.mediaFormat, params->mediaFormat);

    bool hasVideo = params->hasVideo;
    if (!hasVideo) {
        if ((params->pid > 0 && params->pid != 0x1fff)
            || (params->mimeType && (strcmp(params->mimeType, "video/unknown") != 0))) {
            // has video pid or mimeType
            hasVideo = true;
        }
    }

    env->SetBooleanField(videoParams, gVideoParamsCtx.hasVideo, (jboolean) hasVideo);

    *outJVideoParams = videoParams;

    LOG_FUNCTION_END();
    return true;
}

bool JniASPlayerJNI::createAudioParams(JNIEnv *env, jni_asplayer_audio_params *params, jobject *outJAudioParams) {
    LOG_FUNCTION_ENTER();
    if (env == nullptr) {
        AP_LOGE("create AudioParams failed, env is null");
        return false;
    } else if (params == nullptr) {
        AP_LOGE("create AudioParams failed, invalid audio_param");
        return false;
    } else if (outJAudioParams == nullptr) {
        AP_LOGE("create AudioParams failed, invalid out param");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject audioParams = env->NewObject(gAudioParamsCls, gAudioParamsCtx.constructorMID);
    if (env->IsSameObject(audioParams, nullptr)) {
        AP_LOGE("create AudioParams failed");
        CHECK_JNI_EXCEPTION(env);
        return false;
    }

    if (params->mimeType) {
        jstring jMimeType = env->NewStringUTF(params->mimeType);
        env->SetObjectField(audioParams, gAudioParamsCtx.mimeType, jMimeType);
    }
    env->SetIntField(audioParams, gAudioParamsCtx.sampleRate, (jint) params->sampleRate);
    env->SetIntField(audioParams, gAudioParamsCtx.channelCount, (jint) params->channelCount);
    env->SetIntField(audioParams, gAudioParamsCtx.pid, (jint) params->pid);
    env->SetIntField(audioParams, gAudioParamsCtx.trackFilterId, (jint)params->filterId);
    env->SetIntField(audioParams, gAudioParamsCtx.avSyncHwId, (jint)params->avSyncHwId);
    env->SetIntField(audioParams, gAudioParamsCtx.secLevel, (jint) params->seclevel);
    env->SetBooleanField(audioParams, gAudioParamsCtx.scrambled, (jboolean) params->scrambled);
    env->SetObjectField(audioParams, gAudioParamsCtx.mediaFormat, params->mediaFormat);
    if (params->extraInfoJson) {
        jstring jExtraInfoJson = env->NewStringUTF(params->extraInfoJson);
        env->SetObjectField(audioParams, gAudioParamsCtx.extraInfoJson, jExtraInfoJson);
    }

    *outJAudioParams = audioParams;

    LOG_FUNCTION_END();
    return true;
}

bool JniASPlayerJNI::createInputBuffer(JNIEnv *env, jni_asplayer_input_buffer *inputBuffer, jobject *outJInputBuffer) {
    if (env == nullptr) {
        AP_LOGE("create InputBuffer failed, env is null");
        return false;
    } else if (inputBuffer == nullptr) {
        AP_LOGE("create InputBuffer failed, invalid in input_buffer");
        return false;
    } else if (outJInputBuffer == nullptr) {
        AP_LOGE("create InputBuffer failed, invalid out param");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject buffer = env->NewObject(gInputBufferCls, gInputBufferCtx.constructorMID);
    if (env->IsSameObject(buffer, nullptr)) {
        AP_LOGE("create InputBuffer failed");
        CHECK_JNI_EXCEPTION(env);
        return false;
    }

    int32_t offset = inputBuffer->offset;
    int32_t bufferSize = inputBuffer->buf_size;
    env->SetIntField(buffer, gInputBufferCtx.offset, (jlong) offset);
    env->SetIntField(buffer, gInputBufferCtx.bufferSize, (jlong) bufferSize);

    jbyte *byteBuffer = static_cast<jbyte *>(inputBuffer->buf_data);
    if (byteBuffer != nullptr && bufferSize > 0) {
        jbyteArray buf = env->NewByteArray(bufferSize);
        jbyte *tempBuffer = new jbyte[bufferSize];
        memcpy(tempBuffer, ((jbyte*)inputBuffer->buf_data) + offset, bufferSize);
        env->SetByteArrayRegion(buf, 0, bufferSize, tempBuffer);
        delete [] tempBuffer;
        // offset start from 0 in tempBuffer
        env->SetIntField(buffer, gInputBufferCtx.offset, 0);
        env->SetObjectField(buffer, gInputBufferCtx.buffer, buf);
    } else {
        env->SetObjectField(buffer, gInputBufferCtx.buffer, nullptr);
        AP_LOGE("set InputBuffer buffer failed, buffer is null");
    }

    *outJInputBuffer = buffer;

    return true;
}

bool JniASPlayerJNI::initASPlayerJNI(JNIEnv *jniEnv) {
    JNIEnv *env = jniEnv;
    if (env == nullptr) {
        env = JniASPlayerJNI::getJNIEnv();
    }

    if (env == nullptr) {
        ALOGE("failed to get JNIEnv initASPlayerJNI failed");
        return false;
    }

    bool error = false;
    jclass asplayerCls = nullptr;
    jclass initParamCls = nullptr;
    jclass videoParamCls = nullptr;
    jclass audioParamCls = nullptr;
    jclass inputBufferCls = nullptr;
    jclass nullPointerExceptionCls = nullptr;
    jclass illegalArgumentExceptionCls = nullptr;
    jclass illegalStateExceptionCls = nullptr;

    do {
        // ASPlayer
        asplayerCls = NativeHelper::FindClass(env, JNI_ASPLAYER_CLASSPATH_NAME);
        if (asplayerCls == nullptr) {
            error = true;
            break;
        }

        gASPlayerCls = NativeHelper::MakeGlobalRef(env, asplayerCls, "class<ASPlayer>");
        if (gASPlayerCls == nullptr) {
            error = true;
            break;
        }

        DELETE_LOCAL_REF(env, asplayerCls);

        gASPlayerCtx.context = NativeHelper::GetFieldID(
                env, gASPlayerCls, "mNativeContext", "J");
        gASPlayerCtx.constructorMID = NativeHelper::GetMethodID(
                env,gASPlayerCls,"<init>",
                "(Lcom/amlogic/asplayer/api/InitParams;Landroid/media/tv/tuner/Tuner;Landroid/os/Looper;)V");
        gASPlayerCtx.prepareMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "prepare", "()I");
        gASPlayerCtx.getInstanceNoMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "getInstanceNo", "()I");
        gASPlayerCtx.getSyncInstanceNoMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "getSyncInstanceNo", "()I");
        gASPlayerCtx.startVideoDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "startVideoDecoding", "()I");
        gASPlayerCtx.stopVideoDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "stopVideoDecoding", "()I");
        gASPlayerCtx.pauseVideoDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "pauseVideoDecoding", "()I");
        gASPlayerCtx.resumeVideoDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "resumeVideoDecoding", "()I");
        gASPlayerCtx.startAudioDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "startAudioDecoding", "()I");
        gASPlayerCtx.stopAudioDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "stopAudioDecoding", "()I");
        gASPlayerCtx.pauseAudioDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "pauseAudioDecoding", "()I");
        gASPlayerCtx.resumeAudioDecodingMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "resumeAudioDecoding", "()I");
        gASPlayerCtx.setVideoParamsMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setVideoParams",
                "(Lcom/amlogic/asplayer/api/VideoParams;)V");
        gASPlayerCtx.setAudioParamsMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setAudioParams",
                "(Lcom/amlogic/asplayer/api/AudioParams;)V");
        gASPlayerCtx.switchAudioTrackMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "switchAudioTrack",
                "(Lcom/amlogic/asplayer/api/AudioParams;)I");
        gASPlayerCtx.flushMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "flush", "()I");
        gASPlayerCtx.flushDvrMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "flushDvr", "()I");
//    gASPlayerCtx.writeByteBufferMID = NativeHelper::GetMethodID(
//              env, gASPlayerCls, "writeData", "(I[BJJJ)I");
        gASPlayerCtx.writeDataMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "writeData", "(Lcom/amlogic/asplayer/api/InputBuffer;J)I");
        gASPlayerCtx.setSurfaceMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setSurface", "(Landroid/view/Surface;)I");
        gASPlayerCtx.setAudioMuteMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setAudioMute", "(ZZ)I");
        gASPlayerCtx.setAudioVolumeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setAudioVolume", "(I)I");
        gASPlayerCtx.getAudioVolumeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "getAudioVolume", "()I");
        gASPlayerCtx.startFastMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "startFast", "(F)I");
        gASPlayerCtx.stopFastMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "stopFast", "()I");
        gASPlayerCtx.setTrickModeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setTrickMode", "(I)I");
        gASPlayerCtx.setTransitionModeBeforeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setTransitionModeBefore", "(I)I");
        gASPlayerCtx.setTransitionModeAfterMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setTransitionModeAfter", "(I)I");
        gASPlayerCtx.setTransitionPrerollRateMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setTransitionPreRollRate", "(F)I");
        gASPlayerCtx.setTransitionPrerollAvToleranceMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setTransitionPreRollAVTolerance", "(I)I");
        gASPlayerCtx.setVideoMuteMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setVideoMute", "(I)I");
        gASPlayerCtx.setScreenColorMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setScreenColor", "(II)I");
        gASPlayerCtx.addPlaybackListenerMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "addPlaybackListener",
                "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V");
        gASPlayerCtx.removePlaybackListenerMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "removePlaybackListener",
                "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V");
        gASPlayerCtx.releaseMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "release", "()V");
        gASPlayerCtx.setWorkModeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setWorkMode", "(I)I");
        gASPlayerCtx.resetWorkModeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "resetWorkMode", "()I");
        gASPlayerCtx.setPIPModeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setPIPMode", "(I)I");
        gASPlayerCtx.setADParamsMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "setADParams", "(Lcom/amlogic/asplayer/api/AudioParams;)I");
        gASPlayerCtx.enableADMixMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "enableADMix", "()I");
        gASPlayerCtx.disableADMixMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "disableADMix", "()I");
        gASPlayerCtx.setADVolumeDBMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setADVolumeDB", "(F)I");
        gASPlayerCtx.getADVolumeDBMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "getADVolumeDB", "()F");
        gASPlayerCtx.setADMixLevelMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setADMixLevel", "(I)I");
        gASPlayerCtx.getADMixLevelMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "getADMixLevel", "()I");
        gASPlayerCtx.setAudioDualMonoModeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setAudioDualMonoMode", "(I)I");
        gASPlayerCtx.getAudioDualMonoModeMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "getAudioDualMonoMode", "()I");
        gASPlayerCtx.getVideoInfoMID = NativeHelper::GetMethodID(
                env, gASPlayerCls,
                "getVideoInfo", "()Landroid/media/MediaFormat;");
        gASPlayerCtx.setParametersMID = NativeHelper::GetMethodID(
                env, gASPlayerCls, "setParameters", "(Landroid/os/Bundle;)I");

        // InitParams
        initParamCls = NativeHelper::FindClass(env, "com/amlogic/asplayer/api/InitParams");
        if (initParamCls == nullptr) {
            error = true;
            break;
        }

        gInitParamsCls = NativeHelper::MakeGlobalRef(env, initParamCls, "class<InitParams>");
        if (gInitParamsCls == nullptr) {
            error = true;
            break;
        }

        DELETE_LOCAL_REF(env, initParamCls);

        gInitParamsCtx.constructorMID = NativeHelper::GetMethodID(
                env, gInitParamsCls, "<init>", "()V");
        gInitParamsCtx.playbackMode = NativeHelper::GetFieldID(
                env, gInitParamsCls, "mPlaybackMode", "I");
        gInitParamsCtx.inputSourceType = NativeHelper::GetFieldID(
                env, gInitParamsCls, "mInputSourceType", "I");
        gInitParamsCtx.eventMask = NativeHelper::GetFieldID(
                env, gInitParamsCls, "mEventMask", "J");

        // VideoParams
        videoParamCls = NativeHelper::FindClass(env, "com/amlogic/asplayer/api/VideoParams");
        if (videoParamCls == nullptr) {
            error = true;
            break;
        }

        gVideoParamsCls = NativeHelper::MakeGlobalRef(env, videoParamCls, "class<VideoParams>");
        if (gVideoParamsCls == nullptr) {
            error = true;
            break;
        }

        DELETE_LOCAL_REF(env, videoParamCls);

        gVideoParamsCtx.constructorMID = NativeHelper::GetMethodID(
                env, gVideoParamsCls, "<init>", "()V");
        gVideoParamsCtx.mimeType = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mMimeType", "Ljava/lang/String;");
        gVideoParamsCtx.width = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mWidth", "I");
        gVideoParamsCtx.height = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mHeight", "I");
        gVideoParamsCtx.pid = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mPid", "I");
        gVideoParamsCtx.trackFilterId = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mTrackFilterId", "I");
        gVideoParamsCtx.avSyncHwId = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mAvSyncHwId", "I");
        gVideoParamsCtx.scrambled = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mScrambled", "Z");
        gVideoParamsCtx.hasVideo = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mHasVideo", "Z");
        gVideoParamsCtx.mediaFormat = NativeHelper::GetFieldID(
                env, gVideoParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");

        // AudioParams
        audioParamCls = NativeHelper::FindClass(env, "com/amlogic/asplayer/api/AudioParams");
        if (audioParamCls == nullptr) {
            error = true;
            break;
        }

        gAudioParamsCls = NativeHelper::MakeGlobalRef(env, audioParamCls, "class<AudioParams>");
        if (gAudioParamsCls == nullptr) {
            error = true;
            break;
        }

        DELETE_LOCAL_REF(env, audioParamCls);

        gAudioParamsCtx.constructorMID = NativeHelper::GetMethodID(
                env, gAudioParamsCls, "<init>", "()V");
        gAudioParamsCtx.mimeType = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mMimeType", "Ljava/lang/String;");
        gAudioParamsCtx.sampleRate = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mSampleRate", "I");
        gAudioParamsCtx.channelCount = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mChannelCount", "I");
        gAudioParamsCtx.pid = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mPid", "I");
        gAudioParamsCtx.trackFilterId = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mTrackFilterId", "I");
        gAudioParamsCtx.avSyncHwId = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mAvSyncHwId", "I");
        gAudioParamsCtx.secLevel = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mSecLevel", "I");
        gAudioParamsCtx.scrambled = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mScrambled", "Z");
        gAudioParamsCtx.mediaFormat = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");
        gAudioParamsCtx.extraInfoJson = NativeHelper::GetFieldID(
                env, gAudioParamsCls, "mExtraInfoJson", "Ljava/lang/String;");

        // MediaFormat
        JniMediaFormat::initJni(env);

        // Bundle
        JniBundle::initJni(env);

        // InputBuffer
        inputBufferCls = NativeHelper::FindClass(env, "com/amlogic/asplayer/api/InputBuffer");
        if (inputBufferCls == nullptr) {
            error = true;
            break;
        }

        gInputBufferCls = NativeHelper::MakeGlobalRef(env, inputBufferCls, "class<InputBuffer>");
        if (gInputBufferCls == nullptr) {
            error = true;
            break;
        }

        DELETE_LOCAL_REF(env, inputBufferCls);

        gInputBufferCtx.constructorMID = NativeHelper::GetMethodID(
                env, gInputBufferCls, "<init>", "()V");
        gInputBufferCtx.buffer = NativeHelper::GetFieldID(
                env, gInputBufferCls, "mBuffer", "[B");
        gInputBufferCtx.offset = NativeHelper::GetFieldID(
                env, gInputBufferCls, "mOffset", "I");
        gInputBufferCtx.bufferSize = NativeHelper::GetFieldID(
                env, gInputBufferCls, "mBufferSize", "I");

        initASPlayerNotify(env);

        nullPointerExceptionCls = NativeHelper::FindClass(
                env, "java/lang/NullPointerException");
        gExceptionsCtx.nullPointerExceptionCls = NativeHelper::MakeGlobalRef(
                env, nullPointerExceptionCls, "class<NullPointerException>");
        DELETE_LOCAL_REF(env, nullPointerExceptionCls);

        illegalArgumentExceptionCls = NativeHelper::FindClass(
                env, "java/lang/IllegalArgumentException");
        gExceptionsCtx.illegalArgumentExceptionCls = NativeHelper::MakeGlobalRef(
                env, illegalArgumentExceptionCls, "class<IllegalArgumentException>");
        DELETE_LOCAL_REF(env, illegalArgumentExceptionCls);

        illegalStateExceptionCls = NativeHelper::FindClass(
                env, "java/lang/IllegalStateException");
        gExceptionsCtx.illegalStateExceptionCls = NativeHelper::MakeGlobalRef(
                env, illegalStateExceptionCls, "class<IllegalStateException>");
        DELETE_LOCAL_REF(env, illegalStateExceptionCls);
    } while (0);

    DELETE_LOCAL_REF(env, asplayerCls);
    DELETE_LOCAL_REF(env, initParamCls);
    DELETE_LOCAL_REF(env, videoParamCls);
    DELETE_LOCAL_REF(env, audioParamCls);
    DELETE_LOCAL_REF(env, inputBufferCls);
    DELETE_LOCAL_REF(env, nullPointerExceptionCls);
    DELETE_LOCAL_REF(env, illegalArgumentExceptionCls);
    DELETE_LOCAL_REF(env, illegalStateExceptionCls);

    gJniInit = error ? false : true;

    AP_LOGI("init JNIASPlayer interface success");
    return gJniInit;
}

int JniASPlayerJNI::initASPlayerNotify(JNIEnv *env) {
    if (!JniPlaybackListener::init(env)) {
        AP_LOGE("failed to init JniPlaybackListener");
        return -1;
    }

    return 0;
}

JniASPlayer::JniASPlayer() : mJavaPlayer(nullptr), mPlaybackListener(nullptr),
    mEventCallback(nullptr), mEventUserData(nullptr) {
#ifdef HAVE_VERSION_INFO
    ALOGI("\n--------------------------------\n"
            "ARCH:                 %s\n"
            "branch name:          %s\n"
            "%s\n"
            "ID:                   %s\n"
            "last changed:         %s\n"
            "build-time:           %s\n"
            "build-name:           %s\n"
            "uncommitted-file-num: %s\n"
            "version:              %s\n"
            "--------------------------------\n",
#if defined(__aarch64__)
            "arm64",
#else
            "arm",
#endif
            BRANCH_NAME,
            COMMIT_CHANGEID,
            COMMIT_PD,
            LAST_CHANGED,
            BUILD_TIME,
            BUILD_NAME,
            GIT_UNCOMMIT_FILE_NUM,
            ASPLAYER_VERSION
    );
#endif
}

JniASPlayer::~JniASPlayer() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (mPlaybackListener != nullptr) {
        mPlaybackListener->release(env);
        delete mPlaybackListener;
        mPlaybackListener = nullptr;
    }

    if (mJavaPlayer != nullptr) {
        if (env != nullptr) {
            env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.releaseMID);
            env->DeleteGlobalRef(mJavaPlayer);
        }
        mJavaPlayer = nullptr;
    }
}

bool JniASPlayer::create(jni_asplayer_init_params *params, void *tuner) {
    LOG_FUNCTION_ENTER();
    if (params == nullptr) {
        AP_LOGE("create JniASPlayer failed, init params is null");
        return false;
    }

    AP_LOGI("JniASPlayer create playback mode: %d, input source type: %d, "
            "event mask: %" PRId64 ", has tuner: %s",
            params->playback_mode,
            params->source,
            params->event_mask,
            (tuner != nullptr) ? "true" : "false");

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        AP_LOGE("create JniASPlayer failed, failed to get JNIEnv");
        return false;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject javaPlayer;
    if (!JniASPlayerJNI::createJniASPlayer(env, params, tuner, &javaPlayer)) {
        AP_LOGE("createJniASPlayer failed");
        return false;
    }

    AP_LOGI("createJniASPlayer success");

    mJavaPlayer = env->NewGlobalRef(javaPlayer);
    if (env->IsSameObject(mJavaPlayer, nullptr)) {
        AP_LOGE("failed to create asplayer global ref");
        CHECK_JNI_EXCEPTION(env);
        return false;
    }

    setJavaASPlayerHandle(env, mJavaPlayer);

    // register playback listener
    mPlaybackListener = new JniPlaybackListener(mEventCallback, mEventUserData);
    if (!mPlaybackListener->createPlaybackListener(env)) {
        AP_LOGE("prepare failed, failed to create playback listener");
        return false;
    }

    jobject playbackListener = mPlaybackListener->getJavaPlaybackListener();
    if (playbackListener == nullptr) {
        AP_LOGE("prepare failed, failed to get playback listener");
        return false;
    }

    // register playback listener
    int result = addPlaybackListener(playbackListener);
    if (result != 0) {
        AP_LOGE("prepare failed, failed to add playback listener");
        return false;
    }

    return true;
}

bool JniASPlayer::getJavaASPlayer(jobject **pPlayer) {
    if (mJavaPlayer != nullptr) {
        *pPlayer = &mJavaPlayer;
        return true;
    }

    return false;
}

int JniASPlayer::setJavaASPlayerHandle(JNIEnv *env, jobject javaPlayer) {
    if (javaPlayer == nullptr) {
        return -1;
    }

    std::lock_guard<std::mutex> lock(mMutex);
    env->SetLongField(javaPlayer, gASPlayerCtx.context, (jlong)this);
    return 0;
}

jni_asplayer_result JniASPlayer::prepare() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.prepareMID);
    LOG_FUNCTION_INT_END(result);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::getInstanceNo(int32_t *numb) {
    if (!numb) {
        AP_LOGE("failed to getInstanceNo, invalid parameter");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jint instanceNo = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.getInstanceNoMID);
    *numb = instanceNo;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::getSyncInstanceNo(int32_t *numb) {
    if (!numb) {
        AP_LOGE("error, failed to getSyncInstanceNo, invalid parameter");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jint syncInstanceNo = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.getSyncInstanceNoMID);
    *numb = syncInstanceNo;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::addPlaybackListener(jobject listener) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.addPlaybackListenerMID, listener);
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::removePlaybackListener(jobject listener) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.removePlaybackListenerMID, listener);
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::startVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.startVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::stopVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.stopVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::pauseVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.pauseVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::resumeVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.resumeVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::startAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.startAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::stopAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.stopAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::pauseAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.pauseAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::resumeAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.resumeAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setVideoParams(jni_asplayer_video_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (params == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    AP_LOGI("setVideoParams mimeType: %s, size: %d x %d, pid: %d, filterId: %d, 0x%x, "
            "avSyncHwId: %d, 0x%x, hasVideo: %s, scrambled: %s",
        params->mimeType ? params->mimeType : "null",
        params->width,
        params->height,
        params->pid,
        params->filterId,
        params->filterId,
        params->avSyncHwId,
        params->avSyncHwId,
        params->hasVideo ? "true" : "false",
        params->scrambled ? "true" : "false");

    CHECK_JNI_EXCEPTION(env);

    jobject videoParam;
    if (!JniASPlayerJNI::createVideoParams(env, params, &videoParam)) {
        AP_LOGE("failed to convert VideoParams");
        CHECK_JNI_EXCEPTION(env);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->ExceptionClear();

    // this method call may throw exception
    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.setVideoParamsMID, videoParam);

    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    DELETE_LOCAL_REF(env, videoParam);

    jni_asplayer_result result = JNI_ASPLAYER_OK;
    if (exception != nullptr) {
        if (env->IsInstanceOf(exception, gExceptionsCtx.nullPointerExceptionCls)
            || env->IsInstanceOf(exception, gExceptionsCtx.illegalArgumentExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
            AP_LOGE("failed, NullPointerException or IllegalArgumentException");
        } else if (env->IsInstanceOf(exception, gExceptionsCtx.illegalStateExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
            AP_LOGE("failed, PointerException or IllegalStateException");
        } else {
            result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        }
    }

    if (result != JNI_ASPLAYER_OK) {
        AP_LOGE("failed, result: %d", result);
    }

    return result;
}

jni_asplayer_result JniASPlayer::setAudioParams(jni_asplayer_audio_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (params == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    AP_LOGI("setAudioParams mimeType: %s, sampleRate: %d, channelCount: %d, pid: %d, "
            "filterId: %d, 0x%x, avSyncHwId: %d, 0x%x, seclevel: %d, scrambled: %s, extraInfo: %s",
            params->mimeType ? params->mimeType : "null",
            params->sampleRate,
            params->channelCount,
            params->pid,
            params->filterId,
            params->filterId,
            params->avSyncHwId,
            params->avSyncHwId,
            params->seclevel,
            params->scrambled ? "true" : "false",
            params->extraInfoJson ? params->extraInfoJson : "null");

    CHECK_JNI_EXCEPTION(env);

    jobject audioParam;
    if (!JniASPlayerJNI::createAudioParams(env, params, &audioParam)) {
        AP_LOGE("failed to convert AudioParams");
        CHECK_JNI_EXCEPTION(env);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->ExceptionClear();

    // this method call may throw exception
    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.setAudioParamsMID, audioParam);

    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    DELETE_LOCAL_REF(env, audioParam);

    jni_asplayer_result result = JNI_ASPLAYER_OK;
    if (exception != nullptr) {
        if (env->IsInstanceOf(exception, gExceptionsCtx.nullPointerExceptionCls)
            || env->IsInstanceOf(exception, gExceptionsCtx.illegalArgumentExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
            AP_LOGE("failed, NullPointerException or IllegalArgumentException");
        } else if (env->IsInstanceOf(exception, gExceptionsCtx.illegalStateExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
            AP_LOGE("failed, PointerException or IllegalStateException");
        } else {
            result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        }
    }

    if (result != JNI_ASPLAYER_OK) {
        AP_LOGE("failed, result: %d", result);
    }

    return result;
}

jni_asplayer_result JniASPlayer::switchAudioTrack(jni_asplayer_audio_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (params == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    AP_LOGI("switchAudioTrack mimeType: %s, sampleRate: %d, channelCount: %d, pid: %d, "
            "filterId: %d, 0x%x, avSyncHwId: %d, 0x%x, seclevel: %d, scrambled: %s, extraInfo: %s",
            params->mimeType ? params->mimeType : "null",
            params->sampleRate,
            params->channelCount,
            params->pid,
            params->filterId,
            params->filterId,
            params->avSyncHwId,
            params->avSyncHwId,
            params->seclevel,
            params->scrambled ? "true" : "false",
            params->extraInfoJson ? params->extraInfoJson : "null");

    CHECK_JNI_EXCEPTION(env);

    jobject audioParam;
    if (!JniASPlayerJNI::createAudioParams(env, params, &audioParam)) {
        AP_LOGE("failed to convert AudioParams");
        CHECK_JNI_EXCEPTION(env);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.switchAudioTrackMID, audioParam);
    DELETE_LOCAL_REF(env, audioParam);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::flush() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.flushMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::flushDvr() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.flushDvrMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::writeData(jni_asplayer_input_buffer *buffer, uint64_t timeout_ms) {
    if (buffer == nullptr) {
        AP_LOGE("writeData failed, invalid param, buffer is null");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jobject inputBuffer = nullptr;
    if (!JniASPlayerJNI::createInputBuffer(env, buffer, &inputBuffer)) {
        AP_LOGE("failed to convert InputBuffer");
        return JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.writeDataMID, inputBuffer, timeout_ms);
    DELETE_LOCAL_REF(env, inputBuffer);

    if (result <= 0 && result != JNI_ASPLAYER_ERROR_RETRY && result != JNI_ASPLAYER_ERROR_BUSY) {
        AP_LOGE("writeData error: %d", result);
    }
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setSurface(void *surface) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setSurfaceMID, (jobject)surface);
    LOG_FUNCTION_INT_END(result);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setAudioMute(bool analogMute, bool digitMute) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setAudioMute analogMute: %s, digitMute: %s",
            analogMute ? "true" : "false",
            digitMute ? "true" : "false");

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setAudioMuteMID, (jboolean)analogMute, (jboolean)digitMute);
    LOG_FUNCTION_INT_END(result);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setAudioVolume(int32_t volume) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setAudioVolume volume: %d", volume);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setAudioVolumeMID, (jint) volume);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::getAudioVolume(int *volume) {
    if (volume == nullptr) {
        AP_LOGE("error, failed to get audio volume, invalid parameter");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jint vol = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.getAudioVolumeMID);
    *volume = vol;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::startFast(float scale) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("startFast scale: %.3f", scale);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.startFastMID, scale);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::stopFast() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.stopFastMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTrickMode(jni_asplayer_video_trick_mode trickMode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setTrickMode trickMode: %d", trickMode);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTrickModeMID, (jint)(trickMode));
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTransitionModeBefore(jni_asplayer_transition_mode_before mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setTransitionModeBefore mode: %d", mode);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTransitionModeBeforeMID, (jint)(mode));
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTransitionModeAfter(jni_asplayer_transition_mode_after mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setTransitionModeAfter mode: %d", mode);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTransitionModeAfterMID, (jint)mode);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTransitionPrerollRate(float rate) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setTransitionPrerollRate rate: %.3f", rate);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTransitionPrerollRateMID, (jfloat)rate);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTransitionPrerollAvTolerance(int32_t milliSecond) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setTransitionPrerollAvTolerance milliSecond: %d", milliSecond);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTransitionPrerollAvToleranceMID, (jint)milliSecond);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setVideoMute(jni_asplayer_video_mute mute) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setVideoMute mute: %d", mute);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setVideoMuteMID, (jint)mute);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setScreenColor(jni_asplayer_screen_color_mode mode,
                                                jni_asplayer_screen_color color) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setScreenColor mode: %d, color: %d", mode, color);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setScreenColorMID, (jint)mode, (jint)color);
    return static_cast<jni_asplayer_result>(ret);
}

void JniASPlayer::setEventCallback(event_callback callback, void *eventUserData) {
    std::lock_guard<std::mutex> lock(mEventMutex);
    mEventCallback = callback;
    mEventUserData = eventUserData;
    if (mPlaybackListener != nullptr) {
        mPlaybackListener->setEventCallback(mEventCallback, mEventUserData);
    }
}

bool JniASPlayer::getEventCallback(event_callback *callback, void **userdata) {
    if (callback == nullptr) {
        return false;
    }

    *callback = mEventCallback;
    if (userdata != nullptr) {
        *userdata = mEventUserData;
    }
    return true;
}

void JniASPlayer::release() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return;
    }

    AP_LOGI("release start");

    if (mPlaybackListener != nullptr) {
        jobject jPlaybackListener = mPlaybackListener->getJavaPlaybackListener();
        if (jPlaybackListener != nullptr) {
            removePlaybackListener(jPlaybackListener);
        }
        mPlaybackListener->release(env);
        delete mPlaybackListener;
        mPlaybackListener = nullptr;
    }

    if (mJavaPlayer) {
        env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.releaseMID);
        env->DeleteGlobalRef(mJavaPlayer);
    }

    mJavaPlayer = nullptr;
}

jni_asplayer_result JniASPlayer::setWorkMode(jni_asplayer_work_mode mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setWorkMode mode: %d", mode);

    int workMode = static_cast<int>(mode);
    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setWorkModeMID, workMode);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::resetWorkMode() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.resetWorkModeMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setPIPMode(jni_asplayer_pip_mode mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setPIPMode mode: %d", mode);

    int pipMode = static_cast<int>(mode);
    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setPIPModeMID, pipMode);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setADParams(jni_asplayer_audio_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (params == nullptr) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    AP_LOGI("setADParams mimeType: %s, sampleRate: %d, channelCount: %d, pid: %d, "
            "filterId: %d, 0x%x, avSyncHwId: %d, 0x%x, seclevel: %d, scrambled: %s, extraInfo: %s",
            params->mimeType ? params->mimeType : "null",
            params->sampleRate,
            params->channelCount,
            params->pid,
            params->filterId,
            params->filterId,
            params->avSyncHwId,
            params->avSyncHwId,
            params->seclevel,
            params->scrambled ? "true" : "false",
            params->extraInfoJson ? params->extraInfoJson : "null");

    CHECK_JNI_EXCEPTION(env);

    jobject audioParam;
    if (!JniASPlayerJNI::createAudioParams(env, params, &audioParam)) {
        AP_LOGE("failed to convert ADParams");
        CHECK_JNI_EXCEPTION(env);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setADParamsMID, audioParam);
    DELETE_LOCAL_REF(env, audioParam);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::enableADMix() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.enableADMixMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::disableADMix() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.disableADMixMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setADVolumeDB(float volumeDB) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setADVolumeDB db: %.3f", volumeDB);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setADVolumeDBMID, volumeDB);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::getADVolumeDB(float *volumeDB) {
    if (volumeDB == nullptr) {
        AP_LOGE("error, failed to get ad volume in DB, invalid parameter");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jfloat volume = env->CallFloatMethod(mJavaPlayer, gASPlayerCtx.getADVolumeDBMID);
    *volumeDB = volume;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::setADMixLevel(int32_t mixLevel) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setADMixLevel mixLevel: %d", mixLevel);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setADMixLevelMID, (jint)mixLevel);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::getADMixLevel(int32_t *mixLevel) {
    if (mixLevel == nullptr) {
        AP_LOGE("error, failed to get ad mix level, invalid parameter");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jint level = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.getADMixLevelMID);
    *mixLevel = (int32_t)level;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::setAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    AP_LOGI("setAudioDualMonoMode mode: %d", mode);

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setAudioDualMonoModeMID, (jint)mode);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::getAudioDualMonoMode(jni_asplayer_audio_dual_mono_mode *mode) {
    if (mode == nullptr) {
        AP_LOGE("error, failed to get audio dual mono mode, invalid parameter");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jint monoMode = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.getAudioDualMonoModeMID);
    if (monoMode < 0) {
        return static_cast<jni_asplayer_result>(monoMode);
    }

    if (monoMode == JNI_ASPLAYER_DUAL_MONO_OFF
        || monoMode == JNI_ASPLAYER_DUAL_MONO_LR
        || monoMode == JNI_ASPLAYER_DUAL_MONO_LL
        || monoMode == JNI_ASPLAYER_DUAL_MONO_RR) {
        *mode = static_cast<jni_asplayer_audio_dual_mono_mode>(monoMode);
        return JNI_ASPLAYER_OK;
    } else {
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }
}

jni_asplayer_result JniASPlayer::getVideoInfo(jni_asplayer_video_info *videoInfo) {
    if (!videoInfo) {
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED(__FUNCTION__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    CHECK_JNI_EXCEPTION(env);

    jobject jVideoMediaFormat = env->CallObjectMethod(mJavaPlayer, gASPlayerCtx.getVideoInfoMID);
    if (env->IsSameObject(jVideoMediaFormat, nullptr)) {
        AP_LOGE("get videoInfo from java ASPlayer failed");
        CHECK_JNI_EXCEPTION(env);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int32_t width = JniMediaFormat::getInteger(env, jVideoMediaFormat, "width", 0);
    int32_t height = JniMediaFormat::getInteger(env, jVideoMediaFormat, "height", 0);
    int32_t frameRate = JniMediaFormat::getInteger(env, jVideoMediaFormat, "frame-rate", 0);
    int32_t aspectRatio = JniMediaFormat::getInteger(env, jVideoMediaFormat, "aspect-ratio", 0);
    int32_t vfType = JniMediaFormat::getInteger(env, jVideoMediaFormat, "vf-type", 0);

    videoInfo->width = (uint32_t)width;
    videoInfo->height = (uint32_t)height;
    videoInfo->framerate = (uint32_t)frameRate;
    videoInfo->aspectRatio = (uint32_t)aspectRatio;
    videoInfo->vfType = (uint32_t)vfType;

    DELETE_LOCAL_REF(env, jVideoMediaFormat);

    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::setParameter(jni_asplayer_parameter type, void *arg) {
    jni_asplayer_result ret = JNI_ASPLAYER_ERROR_INVALID_PARAMS;

    switch (type) {
        default:
            ret = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
            break;
    }

    return ret;
}

jni_asplayer_result JniASPlayer::setBundleParameters(JNIEnv *env, JniBundle *bundle) {
    if (!bundle) {
        AP_LOGE("setBundleParameters failed, bundle is null");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    return setParameters(env, bundle->getJavaBundleObject());
}

jni_asplayer_result JniASPlayer::setParameters(JNIEnv *env, jobject bundleObj) {
    if (!env) {
        AP_LOGE("setParameters failed, JNIEnv is null");
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    } else if (!bundleObj) {
        AP_LOGE("setParameters failed, bundleObj is null");
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setParametersMID, bundleObj);
    return static_cast<jni_asplayer_result>(ret);
}
