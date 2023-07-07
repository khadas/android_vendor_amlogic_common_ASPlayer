/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "JniASPlayerJNI.h"
#include <jni.h>
#include <assert.h>
#include <unistd.h>
#include <memory>
#include "log.h"
#include "NativeHelper.h"
#include "JniMediaFormat.h"
#include "JniPlaybackListener.h"

#define JASPLAYER_JNIENV_NAME "asplayer"

#define NELEM(arr) (sizeof(arr) / sizeof(arr[0]))

static const char *JNI_ASPLAYER_CLASSPATH_NAME = "com/amlogic/jniasplayer/JniASPlayer";

/**
 * JNI common
 */
static jobject gClassLoader;
static jmethodID gFindClassMethod;

/**
 * ASPlayer interface
 */
struct asplayer_t {
    jfieldID context;

    jmethodID constructorMID;
    jmethodID prepareMID;
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
    jmethodID releaseMID;
    jmethodID addPlaybackListenerMID;
    jmethodID removePlaybackListenerMID;
    jmethodID setPIPModeMID;
};

// InitParams
struct init_param_t {
    jmethodID constructorMID;
    jfieldID playbackMode;
    jfieldID inputSourceType;
    jfieldID inputBufferType;
    jfieldID dmxDevId;
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
    jfieldID mediaFormat;
};

// InputBuffer
struct input_buffer_t {
    jmethodID constructorMID;
    jfieldID inputBufferType;
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

static inline jclass FindClassOrDie(JNIEnv* env, const char* class_name) {
    jclass clazz = env->FindClass(class_name);
    LOG_ALWAYS_FATAL_IF(clazz == NULL, "Unable to find class %s", class_name);
    return clazz;
}

static inline jfieldID GetFieldIDOrDie(JNIEnv* env, jclass clazz, const char* field_name,
                                       const char* field_signature) {
    jfieldID res = env->GetFieldID(clazz, field_name, field_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to find static field %s with signature %s", field_name,
                        field_signature);
    return res;
}

static inline jmethodID GetMethodIDOrDie(JNIEnv* env, jclass clazz, const char* method_name,
                                         const char* method_signature) {
    jmethodID res = env->GetMethodID(clazz, method_name, method_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to find method %s with signature %s", method_name,
                        method_signature);
    return res;
}

static inline jfieldID GetStaticFieldIDOrDie(JNIEnv* env, jclass clazz, const char* field_name,
                                             const char* field_signature) {
    jfieldID res = env->GetStaticFieldID(clazz, field_name, field_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to find static field %s with signature %s", field_name,
                        field_signature);
    return res;
}

static inline jmethodID GetStaticMethodIDOrDie(JNIEnv* env, jclass clazz, const char* method_name,
                                               const char* method_signature) {
    jmethodID res = env->GetStaticMethodID(clazz, method_name, method_signature);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to find static method %s with signature %s",
                        method_name, method_signature);
    return res;
}

template <typename T>
static inline T MakeGlobalRefOrDie(JNIEnv* env, T in) {
    jobject res = env->NewGlobalRef(in);
    LOG_ALWAYS_FATAL_IF(res == NULL, "Unable to create global reference.");
    return static_cast<T>(res);
}

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

bool JniASPlayerJNI::createJniASPlayer(JNIEnv *env, jni_asplayer_init_params params, void *tuner, jobject *outJniASPlayer) {
    assert(env != nullptr);
    ALOGD("%s[%d] start", __func__, __LINE__);
    jobject jInitParam;
    if (!createInitParams(env, params, &jInitParam)) {
        ALOGW("%s[%d] createInitParams failed", __func__, __LINE__);
        return false;
    }

    ALOGD("%s[%d] createInitParams end", __func__, __LINE__);
    jobject obj = env->NewObject(gASPlayerCls, gASPlayerCtx.constructorMID, jInitParam, (jobject)tuner, nullptr);
    env->DeleteLocalRef(jInitParam);

    ALOGD("%s[%d] create java InitParams end", __func__, __LINE__);
    *outJniASPlayer = obj;
    return true;
}

bool JniASPlayerJNI::createInitParams(JNIEnv *env, jni_asplayer_init_params params, jobject *outJInitParams) {
    ALOGD("%s[%d] start", __func__, __LINE__);
    jobject initParams = env->NewObject(gInitParamsCls, gInitParamsCtx.constructorMID);
    if (env->ExceptionOccurred()) {
        ALOGE("%s[%d] create java InitParams failed", __func__, __LINE__);
        env->ExceptionDescribe();
        return false;
    }
    env->SetIntField(initParams, gInitParamsCtx.playbackMode, (jint)params.playback_mode);
    env->SetIntField(initParams, gInitParamsCtx.inputSourceType, (jint)params.source);
    env->SetIntField(initParams, gInitParamsCtx.inputBufferType, (jint)params.drmmode);
    env->SetIntField(initParams, gInitParamsCtx.dmxDevId, (jint)params.dmx_dev_id);
    env->SetLongField(initParams, gInitParamsCtx.eventMask, (jlong)params.event_mask);
    *outJInitParams = initParams;
    ALOGD("%s[%d] end", __func__, __LINE__);
    return true;
}

bool JniASPlayerJNI::createVideoParams(JNIEnv *env, jni_asplayer_video_params *params, jobject *outJVideoParams) {
    LOG_FUNCTION_ENTER();
    if (!env || !params || !outJVideoParams) {
        ALOGE("%s[%d] create VideoParams failed, invalid parameter", __func__, __LINE__);
        return false;
    }

    jobject videoParams = env->NewObject(gVideoParamsCls, gVideoParamsCtx.constructorMID);
    if (env->ExceptionOccurred()) {
        ALOGE("%s[%d] create VideoParams failed", __func__, __LINE__);
        env->ExceptionDescribe();
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
    env->SetObjectField(videoParams, gVideoParamsCtx.mediaFormat, params->mediaFormat);

    *outJVideoParams = videoParams;

    LOG_FUNCTION_END();
    return true;
}

bool JniASPlayerJNI::createAudioParams(JNIEnv *env, jni_asplayer_audio_params *params, jobject *outJAudioParams) {
    LOG_FUNCTION_ENTER();
    if (!env || !params || !outJAudioParams) {
        ALOGE("%s[%d] create AudioParams failed, invalid parameter", __func__, __LINE__);
        return false;
    }

    jobject audioParams = env->NewObject(gAudioParamsCls, gAudioParamsCtx.constructorMID);
    if (env->ExceptionOccurred()) {
        ALOGE("%s[%d] create AudioParams failed", __func__, __LINE__);
        env->ExceptionDescribe();
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
    env->SetObjectField(audioParams, gAudioParamsCtx.mediaFormat, params->mediaFormat);

    *outJAudioParams = audioParams;

    LOG_FUNCTION_END();
    return true;
}

bool JniASPlayerJNI::createInputBuffer(JNIEnv *env, jni_asplayer_input_buffer *inputBuffer, jobject *outJInputBuffer) {
    if (!env || !inputBuffer || !outJInputBuffer) {
        ALOGE("%s[%d] create InputBuffer failed, invalid parameter", __func__, __LINE__);
        return false;
    }

    jobject buffer = env->NewObject(gInputBufferCls, gInputBufferCtx.constructorMID);
    if (env->ExceptionOccurred()) {
        ALOGE("%s[%d] create InputBuffer failed", __func__, __LINE__);
        env->ExceptionDescribe();
        return false;
    }

    env->SetIntField(buffer, gInputBufferCtx.inputBufferType, (jint) inputBuffer->buf_type);

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
        // 这里数据是从下标 0 开始的, 需要修改 offset 为 0
        env->SetIntField(buffer, gInputBufferCtx.offset, 0);
        env->SetObjectField(buffer, gInputBufferCtx.buffer, buf);
//        ALOGD("[%s/%d] set InputBuffer buffer success, buffer length: %d", __func__, __LINE__, bufferSize);
    } else {
        env->SetObjectField(buffer, gInputBufferCtx.buffer, nullptr);
        ALOGD("[%s/%d] set InputBuffer buffer failed, buffer is null", __func__, __LINE__);
    }

    *outJInputBuffer = buffer;

    return true;
}

bool JniASPlayerJNI::initASPlayerJNI(JNIEnv *jniEnv) {
    JNIEnv *env = jniEnv;
    if (env == nullptr) {
        env = JniASPlayerJNI::getJNIEnv();
    }
    if (env != nullptr) {
        initJNIEnv(env);
    }
    /*
    bool needAttach = (env == nullptr);
    if (needAttach) {
        ALOGI("need attach thread to jvm");
        env = JniASPlayerJNI::attachJNIEnv(JASPLAYER_JNIENV_NAME);
        if (env == nullptr) {
            ALOGE("failed to attach to thread to init ts player");
            return false;
        }
    }
    */
//    JNIEnv *env = getOrAttachJNIEnvironment();
    if (env == nullptr) {
        ALOGE("failed to get jnienv, attach thread failed");
        return false;
    }

    // ASPlayer
    jclass asplayerCls = env->FindClass(JNI_ASPLAYER_CLASSPATH_NAME);
    gASPlayerCls = static_cast<jclass>(env->NewGlobalRef(asplayerCls));
    env->DeleteLocalRef(asplayerCls);
    gASPlayerCtx.context = GetFieldIDOrDie(env, gASPlayerCls, "mNativeContext", "J");
    gASPlayerCtx.constructorMID = GetMethodIDOrDie(env, gASPlayerCls, "<init>", "(Lcom/amlogic/asplayer/api/InitParams;Landroid/media/tv/tuner/Tuner;Landroid/os/Looper;)V");
    gASPlayerCtx.prepareMID = GetMethodIDOrDie(env, gASPlayerCls, "prepare", "()I");
    gASPlayerCtx.startVideoDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "startVideoDecoding", "()I");
    gASPlayerCtx.stopVideoDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "stopVideoDecoding", "()I");
    gASPlayerCtx.pauseVideoDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "pauseVideoDecoding", "()I");
    gASPlayerCtx.resumeVideoDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "resumeVideoDecoding", "()I");
    gASPlayerCtx.startAudioDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "startAudioDecoding", "()I");
    gASPlayerCtx.stopAudioDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "stopAudioDecoding", "()I");
    gASPlayerCtx.pauseAudioDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "pauseAudioDecoding", "()I");
    gASPlayerCtx.resumeAudioDecodingMID = GetMethodIDOrDie(env, gASPlayerCls, "resumeAudioDecoding", "()I");
    gASPlayerCtx.setVideoParamsMID = GetMethodIDOrDie(env, gASPlayerCls, "setVideoParams", "(Lcom/amlogic/asplayer/api/VideoParams;)V");
    gASPlayerCtx.setAudioParamsMID = GetMethodIDOrDie(env, gASPlayerCls, "setAudioParams", "(Lcom/amlogic/asplayer/api/AudioParams;)V");
    gASPlayerCtx.switchAudioTrackMID = GetMethodIDOrDie(env, gASPlayerCls, "switchAudioTrack", "(Lcom/amlogic/asplayer/api/AudioParams;)I");
    gASPlayerCtx.flushMID = GetMethodIDOrDie(env, gASPlayerCls, "flush", "()I");
    gASPlayerCtx.flushDvrMID = GetMethodIDOrDie(env, gASPlayerCls, "flushDvr", "()I");
//    gASPlayerCtx.writeByteBufferMID = GetMethodIDOrDie(env, gASPlayerCls, "writeData", "(I[BJJJ)I");
    gASPlayerCtx.writeDataMID = GetMethodIDOrDie(env, gASPlayerCls, "writeData", "(Lcom/amlogic/asplayer/api/InputBuffer;J)I");
    gASPlayerCtx.setSurfaceMID = GetMethodIDOrDie(env, gASPlayerCls, "setSurface", "(Landroid/view/Surface;)I");
    gASPlayerCtx.setAudioMuteMID = GetMethodIDOrDie(env, gASPlayerCls, "setAudioMute", "(ZZ)I");
    gASPlayerCtx.setAudioVolumeMID = GetMethodIDOrDie(env, gASPlayerCls, "setAudioVolume", "(I)I");
    gASPlayerCtx.getAudioVolumeMID = GetMethodIDOrDie(env, gASPlayerCls, "getAudioVolume", "()I");
    gASPlayerCtx.startFastMID = GetMethodIDOrDie(env, gASPlayerCls, "startFast", "(F)I");
    gASPlayerCtx.stopFastMID = GetMethodIDOrDie(env, gASPlayerCls, "stopFast", "()I");
    gASPlayerCtx.setTrickModeMID = GetMethodIDOrDie(env, gASPlayerCls, "setTrickMode", "(I)I");
    gASPlayerCtx.setTransitionModeBeforeMID = GetMethodIDOrDie(env, gASPlayerCls, "setTransitionModeBefore", "(I)I");
    gASPlayerCtx.addPlaybackListenerMID = GetMethodIDOrDie(env, gASPlayerCls, "addPlaybackListener", "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V");
    gASPlayerCtx.removePlaybackListenerMID = GetMethodIDOrDie(env, gASPlayerCls, "removePlaybackListener", "(Lcom/amlogic/asplayer/api/TsPlaybackListener;)V");
    gASPlayerCtx.releaseMID = GetMethodIDOrDie(env, gASPlayerCls, "release", "()V");
    gASPlayerCtx.setPIPModeMID = GetMethodIDOrDie(env, gASPlayerCls, "setPIPMode", "(I)I");

    // InitParams
    jclass initParamCls = env->FindClass("com/amlogic/asplayer/api/InitParams");
    gInitParamsCls = static_cast<jclass>(env->NewGlobalRef(initParamCls));
    env->DeleteLocalRef(initParamCls);
    gInitParamsCtx.constructorMID = env->GetMethodID(gInitParamsCls, "<init>", "()V");
    gInitParamsCtx.playbackMode = env->GetFieldID(gInitParamsCls, "mPlaybackMode", "I");
    gInitParamsCtx.inputSourceType = env->GetFieldID(gInitParamsCls, "mInputSourceType", "I");
    gInitParamsCtx.inputBufferType = env->GetFieldID(gInitParamsCls, "mInputBufferType", "I");
    gInitParamsCtx.dmxDevId = env->GetFieldID(gInitParamsCls, "mDmxDevId", "I");
    gInitParamsCtx.eventMask = env->GetFieldID(gInitParamsCls, "mEventMask", "J");

    // VideoParams
    jclass videoParamCls = env->FindClass("com/amlogic/asplayer/api/VideoParams");
    gVideoParamsCls = static_cast<jclass>(env->NewGlobalRef(videoParamCls));
    env->DeleteLocalRef(videoParamCls);
    gVideoParamsCtx.constructorMID = env->GetMethodID(gVideoParamsCls, "<init>", "()V");
    gVideoParamsCtx.mimeType = env->GetFieldID(gVideoParamsCls, "mMimeType", "Ljava/lang/String;");
    gVideoParamsCtx.width = env->GetFieldID(gVideoParamsCls, "mWidth", "I");
    gVideoParamsCtx.height = env->GetFieldID(gVideoParamsCls, "mHeight", "I");
    gVideoParamsCtx.pid = env->GetFieldID(gVideoParamsCls, "mPid", "I");
    gVideoParamsCtx.trackFilterId = env->GetFieldID(gVideoParamsCls, "mTrackFilterId", "I");
    gVideoParamsCtx.avSyncHwId = env->GetFieldID(gVideoParamsCls, "mAvSyncHwId", "I");
    gVideoParamsCtx.mediaFormat = env->GetFieldID(gVideoParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");

    // AudioParams
    jclass audioParamCls = env->FindClass("com/amlogic/asplayer/api/AudioParams");
    gAudioParamsCls = static_cast<jclass>(env->NewGlobalRef(audioParamCls));
    env->DeleteLocalRef(audioParamCls);
    gAudioParamsCtx.constructorMID = env->GetMethodID(gAudioParamsCls, "<init>", "()V");
    gAudioParamsCtx.mimeType = env->GetFieldID(gAudioParamsCls, "mMimeType", "Ljava/lang/String;");
    gAudioParamsCtx.sampleRate = env->GetFieldID(gAudioParamsCls, "mSampleRate", "I");
    gAudioParamsCtx.channelCount = env->GetFieldID(gAudioParamsCls, "mChannelCount", "I");
    gAudioParamsCtx.pid = env->GetFieldID(gAudioParamsCls, "mPid", "I");
    gAudioParamsCtx.trackFilterId = env->GetFieldID(gAudioParamsCls, "mTrackFilterId", "I");
    gAudioParamsCtx.avSyncHwId = env->GetFieldID(gAudioParamsCls, "mAvSyncHwId", "I");
    gAudioParamsCtx.secLevel = env->GetFieldID(gAudioParamsCls, "mSecLevel", "I");
    gAudioParamsCtx.mediaFormat = env->GetFieldID(gAudioParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");

    // MediaFormat
    JniMediaFormat::initJni(env);

    // InputBuffer
    jclass inputBufferCls = env->FindClass("com/amlogic/asplayer/api/InputBuffer");
    gInputBufferCls = static_cast<jclass>(env->NewGlobalRef(inputBufferCls));
    env->DeleteLocalRef(inputBufferCls);
    gInputBufferCtx.constructorMID = env->GetMethodID(gInputBufferCls, "<init>", "()V");
    gInputBufferCtx.inputBufferType = env->GetFieldID(gInputBufferCls, "mInputBufferType", "I");
    gInputBufferCtx.buffer = env->GetFieldID(gInputBufferCls, "mBuffer", "[B");
    gInputBufferCtx.offset = env->GetFieldID(gInputBufferCls, "mOffset", "I");
    gInputBufferCtx.bufferSize = env->GetFieldID(gInputBufferCls, "mBufferSize", "I");

    initASPlayerNotify(env);

    jclass nullPointerExceptionCls = env->FindClass("java/lang/NullPointerException");
    gExceptionsCtx.nullPointerExceptionCls = static_cast<jclass>(env->NewGlobalRef(nullPointerExceptionCls));
    jclass illegalArgumentExceptionCls = env->FindClass("java/lang/IllegalArgumentException");
    gExceptionsCtx.illegalArgumentExceptionCls = static_cast<jclass>(env->NewGlobalRef(illegalArgumentExceptionCls));
    jclass illegalStateExceptionCls = env->FindClass("java/lang/IllegalStateException");
    gExceptionsCtx.illegalStateExceptionCls = static_cast<jclass>(env->NewGlobalRef(illegalStateExceptionCls));

    gJniInit = true;

    ALOGV("init JNIASPlayer interface success");
    return gJniInit;
}

int JniASPlayerJNI::initASPlayerNotify(JNIEnv *env) {
    if (!JniPlaybackListener::init(env)) {
        ALOGE("%s[%d] failed to init JniPlaybackListener", __func__, __LINE__);
        return -1;
    }

    return 0;
}

int JniASPlayerJNI::initJNIEnv(JNIEnv *env) {
    jclass asPlayerClass = env->FindClass(JNI_ASPLAYER_CLASSPATH_NAME);
    jclass classClass = env->GetObjectClass(asPlayerClass);
    auto classLoaderClass = env->FindClass("java/lang/ClassLoader");
    auto getClassLoaderMethod = env->GetMethodID(classClass, "getClassLoader",
                                                 "()Ljava/lang/ClassLoader;");
    jobject classLoader = env->CallObjectMethod(asPlayerClass, getClassLoaderMethod);
    gClassLoader = env->NewGlobalRef(classLoader);
    gFindClassMethod = env->GetMethodID(classLoaderClass, "findClass",
                                        "(Ljava/lang/String;)Ljava/lang/Class;");
    ALOGV("init jni env, success to find JNIASPlayer class");
    return 0;
}

jclass JniASPlayerJNI::findClass(const char *name) {
    JNIEnv *env = getOrAttachJNIEnvironment();
    return static_cast<jclass>(env->CallObjectMethod(gClassLoader, gFindClassMethod, env->NewStringUTF(name)));
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

bool JniASPlayer::create(jni_asplayer_init_params params, void *tuner) {
    ALOGD("%s[%d] start", __func__, __LINE__);
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        ALOGE("create JniASPlayer failed, failed to get JNIenv");
        return false;
    }

    ALOGD("[%s/%d] call createJniASPlayer", __func__, __LINE__);
    jobject javaPlayer;
    if (!JniASPlayerJNI::createJniASPlayer(env, params, tuner, &javaPlayer)) {
        ALOGE("[%s/%d] createJniASPlayer failed", __func__, __LINE__);
        return false;
    }

    ALOGD("[%s/%d] call createJniASPlayer end", __func__, __LINE__);
    mJavaPlayer = MakeGlobalRefOrDie(env, javaPlayer);

    setJavaASPlayerHandle(env, mJavaPlayer);

    // register playback listener
    mPlaybackListener = new JniPlaybackListener(mEventCallback, mEventUserData);
    if (!mPlaybackListener->createPlaybackListener(env)) {
        ALOGE("[%s/%d] prepare failed, failed to create playback listener", __func__, __LINE__);
        return false;
    }

    jobject playbackListener = mPlaybackListener->getJavaPlaybackListener();
    if (playbackListener == nullptr) {
        ALOGE("[%s/%d] prepare failed, failed to get playback listener", __func__, __LINE__);
        return false;
    }

    // register playback listener
    int result = addPlaybackListener(playbackListener);
    if (result != 0) {
        ALOGE("[%s/%d] prepare failed, failed to add playback listener", __func__, __LINE__);
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
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.prepareMID);
    LOG_FUNCTION_INT_END(result);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::addPlaybackListener(jobject listener) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.addPlaybackListenerMID, listener);
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::removePlaybackListener(jobject listener) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.removePlaybackListenerMID, listener);
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::startVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.startVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::stopVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.stopVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::pauseVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.pauseVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::resumeVideoDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.resumeVideoDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::startAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.startAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::stopAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.stopAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::pauseAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.pauseAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::resumeAudioDecoding() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.resumeAudioDecodingMID);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setVideoParams(jni_asplayer_video_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jobject videoParam;
    if (!JniASPlayerJNI::createVideoParams(env, params, &videoParam)) {
        ALOGE("[%s/%d] failed to convert VideoParams", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->ExceptionClear();

    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.setVideoParamsMID, videoParam);

    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    env->DeleteLocalRef(videoParam);

    jni_asplayer_result result = JNI_ASPLAYER_OK;
    if (exception != nullptr) {
        if (env->IsInstanceOf(exception, gExceptionsCtx.nullPointerExceptionCls)
            || env->IsInstanceOf(exception, gExceptionsCtx.illegalArgumentExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
            ALOGE("[%s/%d] failed, NullPointerException or IllegalArgumentException", __FUNCTION__, __LINE__);
        } else if (env->IsInstanceOf(exception, gExceptionsCtx.illegalStateExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
            ALOGE("[%s/%d] failed, PointerException or IllegalStateException", __FUNCTION__, __LINE__);
        } else {
            result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        }
    }

    if (result != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed, result: %d", __FUNCTION__, __LINE__, result);
    }

    return result;
}

jni_asplayer_result JniASPlayer::setAudioParams(jni_asplayer_audio_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jobject audioParam;
    if (!JniASPlayerJNI::createAudioParams(env, params, &audioParam)) {
        ALOGE("[%s/%d] failed to convert AudioParams", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    env->ExceptionClear();

    env->CallVoidMethod(mJavaPlayer, gASPlayerCtx.setAudioParamsMID, audioParam);

    jthrowable exception = env->ExceptionOccurred();
    env->ExceptionClear();

    env->DeleteLocalRef(audioParam);

    jni_asplayer_result result = JNI_ASPLAYER_OK;
    if (exception != nullptr) {
        if (env->IsInstanceOf(exception, gExceptionsCtx.nullPointerExceptionCls)
            || env->IsInstanceOf(exception, gExceptionsCtx.illegalArgumentExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_PARAMS;
            ALOGE("[%s/%d] failed, NullPointerException or IllegalArgumentException", __FUNCTION__, __LINE__);
        } else if (env->IsInstanceOf(exception, gExceptionsCtx.illegalStateExceptionCls)) {
            result = JNI_ASPLAYER_ERROR_INVALID_OPERATION;
            ALOGE("[%s/%d] failed, PointerException or IllegalStateException", __FUNCTION__, __LINE__);
        } else {
            result = JNI_ASPLAYER_ERROR_INVALID_OBJECT;
        }
    }

    if (result != JNI_ASPLAYER_OK) {
        ALOGE("[%s/%d] failed, result: %d", __FUNCTION__, __LINE__, result);
    }

    LOG_FUNCTION_INT_END(result);
    return result;
}

jni_asplayer_result JniASPlayer::switchAudioTrack(jni_asplayer_audio_params *params) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jobject audioParam;
    if (!JniASPlayerJNI::createAudioParams(env, params, &audioParam)) {
        ALOGE("[%s/%d] failed to convert AudioParams", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.switchAudioTrackMID, audioParam);
    env->DeleteLocalRef(audioParam);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::flush() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.flushMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::flushDvr() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.flushDvrMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::writeData(jni_asplayer_input_buffer *buffer, uint64_t timeout_ms) {
    if (buffer == nullptr) {
        LOG_FUNCTION_INT_FAILED(JNI_ASPLAYER_ERROR_INVALID_PARAMS);
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jobject inputBuffer = nullptr;
    if (!JniASPlayerJNI::createInputBuffer(env, buffer, &inputBuffer)) {
        ALOGE("[%s/%d] failed to convert InputBuffer", __FUNCTION__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_OPERATION;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.writeDataMID, inputBuffer, timeout_ms);
    env->DeleteLocalRef(inputBuffer);

    if (result <= 0 && result != JNI_ASPLAYER_ERROR_RETRY && result != JNI_ASPLAYER_ERROR_BUSY) {
        ALOGD("[%s/%d] writeData error: %d", __FUNCTION__, __LINE__, result);
    }
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setSurface(void *surface) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setSurfaceMID, (jobject)surface);
    LOG_FUNCTION_INT_END(result);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setAudioMute(bool analogMute, bool digitMute) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int result = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setAudioMuteMID, (jboolean)analogMute, (jboolean)digitMute);
    LOG_FUNCTION_INT_END(result);
    return static_cast<jni_asplayer_result>(result);
}

jni_asplayer_result JniASPlayer::setAudioVolume(int32_t volume) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setAudioVolumeMID, (jint) volume);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::getAudioVolume(int *volume) {
    if (volume == nullptr) {
        ALOGE("[%s/%d] error, failed to get audio volume, invalid parameter", __func__, __LINE__);
        return JNI_ASPLAYER_ERROR_INVALID_PARAMS;
    }

    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    jint vol = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.getAudioVolumeMID);
    *volume = vol;
    return JNI_ASPLAYER_OK;
}

jni_asplayer_result JniASPlayer::startFast(float scale) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.startFastMID, scale);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::stopFast() {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.stopFastMID);
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTrickMode(jni_asplayer_video_trick_mode trickMode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTrickModeMID, (jint)(trickMode));
    return static_cast<jni_asplayer_result>(ret);
}

jni_asplayer_result JniASPlayer::setTransitionModeBefore(jni_asplayer_transition_mode_before mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setTransitionModeBeforeMID, (jint)(mode));
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
        ALOGE("%s[%d] error, failed to get jni env", __func__, __LINE__);
        return;
    }

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

jni_asplayer_result JniASPlayer::setPIPMode(jni_asplayer_pip_mode mode) {
    JNIEnv *env = JniASPlayerJNI::getOrAttachJNIEnvironment();
    if (env == nullptr) {
        LOG_GET_JNIENV_FAILED();
        return JNI_ASPLAYER_ERROR_INVALID_OBJECT;
    }

    int pipMode = static_cast<int>(mode);
    int ret = env->CallIntMethod(mJavaPlayer, gASPlayerCtx.setPIPModeMID, pipMode);
    return static_cast<jni_asplayer_result>(ret);
}
