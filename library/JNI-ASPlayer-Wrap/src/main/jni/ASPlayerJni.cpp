/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include <memory>
#include "ASPlayerJni.h"
#include "common/utils/Log.h"

struct init_param_t {
    jfieldID playbackMode;
    jfieldID inputSourceType;
    jfieldID inputBufferType;
    jfieldID dmxDevId;
    jfieldID eventMask;
};

struct video_param_t {
    jfieldID mimeType;
    jfieldID width;
    jfieldID height;
    jfieldID pid;
    jfieldID trackFilterId;
    jfieldID avSyncHwId;
    jfieldID mediaFormat;
};

struct audio_param_t {
    jfieldID mimeType;
    jfieldID sampleRate;
    jfieldID channelCount;
    jfieldID pid;
    jfieldID trackFilterId;
    jfieldID avSyncHwId;
    jfieldID secLevel;
    jfieldID mediaFormat;
};

struct input_buffer_t {
    jfieldID inputBufferType;
    jfieldID buffer;
    jfieldID offset;
    jfieldID bufferSize;
};

struct media_format_t {
    jmethodID constructor;
    jmethodID setInteger;
};

struct video_format_change_event_t {
    jmethodID constructor;
    jfieldID mediaFormat;
};

struct playback_listener_t {
    jmethodID onPlaybackEvent;
};

static jclass gInitParamsCls;
static init_param_t gInitParamsCtx;
static jclass gVideoParamsCls;
static video_param_t gVideoParamsCtx;
static jclass gAudioParamsCls;
static audio_param_t gAudioParamsCtx;
static jclass gInputBufferCls;
static input_buffer_t gInputBufferCtx;

static jclass gMediaFormatCls;
static media_format_t gMediaFormatCtx;
static jclass gVideoFormatChangeEventCls;
static video_format_change_event_t gVideoFormatChangeEventCtx;
static jclass gPlaybackListenerCls;
static playback_listener_t gPlaybackListenerCtx;

static bool g_inited = false;

ASPlayerJni::ASPlayerJni() {

}

ASPlayerJni::~ASPlayerJni() {

}

bool ASPlayerJni::initJni(JNIEnv *env) {
    if (g_inited) {
        return true;
    }

    // init InitParams
    jclass initParamCls = env->FindClass("com/amlogic/asplayer/api/InitParams");
    gInitParamsCls = static_cast<jclass>(env->NewGlobalRef(initParamCls));
    env->DeleteLocalRef(initParamCls);
    gInitParamsCtx.playbackMode = env->GetFieldID(gInitParamsCls, "mPlaybackMode", "I");
    gInitParamsCtx.inputSourceType = env->GetFieldID(gInitParamsCls, "mInputSourceType", "I");
    gInitParamsCtx.inputBufferType = env->GetFieldID(gInitParamsCls, "mInputBufferType", "I");
    gInitParamsCtx.dmxDevId = env->GetFieldID(gInitParamsCls, "mDmxDevId", "I");
    gInitParamsCtx.eventMask = env->GetFieldID(gInitParamsCls, "mEventMask", "I");

    // init VideoParams
    jclass videoParamCls = env->FindClass("com/amlogic/asplayer/api/VideoParams");
    gVideoParamsCls = static_cast<jclass>(env->NewGlobalRef(videoParamCls));
    env->DeleteLocalRef(videoParamCls);
    gVideoParamsCtx.mimeType = env->GetFieldID(gVideoParamsCls, "mMimeType", "Ljava/lang/String;");
    gVideoParamsCtx.width = env->GetFieldID(gVideoParamsCls, "mWidth", "I");
    gVideoParamsCtx.height = env->GetFieldID(gVideoParamsCls, "mHeight", "I");
    gVideoParamsCtx.pid = env->GetFieldID(gVideoParamsCls, "mPid", "I");
    gVideoParamsCtx.trackFilterId = env->GetFieldID(gVideoParamsCls, "mTrackFilterId", "I");
    gVideoParamsCtx.avSyncHwId = env->GetFieldID(gVideoParamsCls, "mAvSyncHwId", "I");
    gVideoParamsCtx.mediaFormat = env->GetFieldID(gVideoParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");

    // init AudioParams
    jclass audioParamCls = env->FindClass("com/amlogic/asplayer/api/AudioParams");
    gAudioParamsCls = static_cast<jclass>(env->NewGlobalRef(audioParamCls));
    env->DeleteLocalRef(audioParamCls);
    gAudioParamsCtx.mimeType = env->GetFieldID(gAudioParamsCls, "mMimeType", "Ljava/lang/String;");
    gAudioParamsCtx.sampleRate = env->GetFieldID(gAudioParamsCls, "mSampleRate", "I");
    gAudioParamsCtx.channelCount = env->GetFieldID(gAudioParamsCls, "mChannelCount", "I");
    gAudioParamsCtx.pid = env->GetFieldID(gAudioParamsCls, "mPid", "I");
    gAudioParamsCtx.trackFilterId = env->GetFieldID(gAudioParamsCls, "mTrackFilterId", "I");
    gAudioParamsCtx.avSyncHwId = env->GetFieldID(gAudioParamsCls, "mAvSyncHwId", "I");
    gAudioParamsCtx.secLevel = env->GetFieldID(gAudioParamsCls, "mSecLevel", "I");
    gAudioParamsCtx.mediaFormat = env->GetFieldID(gAudioParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");

    // init InputBuffer
    jclass inputBufferCls = env->FindClass("com/amlogic/asplayer/api/InputBuffer");
    gInputBufferCls = static_cast<jclass>(env->NewGlobalRef(inputBufferCls));
    env->DeleteLocalRef(inputBufferCls);
    gInputBufferCtx.inputBufferType = env->GetFieldID(gInputBufferCls, "mInputBufferType", "I");
    gInputBufferCtx.buffer = env->GetFieldID(gInputBufferCls, "mBuffer", "[B");
    gInputBufferCtx.offset = env->GetFieldID(gInputBufferCls, "mOffset", "I");
    gInputBufferCtx.bufferSize = env->GetFieldID(gInputBufferCls, "mBufferSize", "I");

    // init MediaFormat
    jclass mediaFormatCls = env->FindClass("android/media/MediaFormat");
    gMediaFormatCls = static_cast<jclass>(env->NewGlobalRef(mediaFormatCls));
    env->DeleteLocalRef(mediaFormatCls);
    gMediaFormatCtx.constructor = env->GetMethodID(gMediaFormatCls, "<init>", "()V");
    gMediaFormatCtx.setInteger = env->GetMethodID(gMediaFormatCls, "setInteger", "(Ljava/lang/String;I)V");

    // init VideoFormatChangeEvent
    jclass videoFormatChangeEventCls = env->FindClass("com/amlogic/asplayer/api/TsPlaybackListener$VideoFormatChangeEvent");
    gVideoFormatChangeEventCls = static_cast<jclass>(env->NewGlobalRef(videoFormatChangeEventCls));
    env->DeleteLocalRef(videoFormatChangeEventCls);
    gVideoFormatChangeEventCtx.constructor = env->GetMethodID(gVideoFormatChangeEventCls, "<init>", "(Landroid/media/MediaFormat;)V");
    gVideoFormatChangeEventCtx.mediaFormat = env->GetFieldID(gVideoFormatChangeEventCls, "mVideoFormat","Landroid/media/MediaFormat;");

    // init PlaybackListener
    jclass playbackListenerCls = env->FindClass("com/amlogic/asplayer/api/TsPlaybackListener");
    gPlaybackListenerCls = static_cast<jclass>(env->NewGlobalRef(playbackListenerCls));
    env->DeleteLocalRef(playbackListenerCls);
    gPlaybackListenerCtx.onPlaybackEvent = env->GetMethodID(gPlaybackListenerCls, "onPlaybackEvent",
                                                            "(Lcom/amlogic/asplayer/api/TsPlaybackListener$PlaybackEvent;)V");

    g_inited = true;
    return true;
}

bool ASPlayerJni::convertInitParams(JNIEnv *env, jobject jInitParam, jni_asplayer_init_params* outParams) {
    if (env == nullptr || jInitParam == nullptr || outParams == nullptr) {
        return false;
    }

    LOG_FUNCTION_ENTER();
    jni_asplayer_playback_mode playbackMode = static_cast<jni_asplayer_playback_mode>(env->GetIntField(
            jInitParam, gInitParamsCtx.playbackMode));
    jni_asplayer_input_source_type tsType = static_cast<jni_asplayer_input_source_type>(env->GetIntField(
            jInitParam, gInitParamsCtx.inputSourceType));
    jni_asplayer_input_buffer_type drmmode = static_cast<jni_asplayer_input_buffer_type>(env->GetIntField(
            jInitParam, gInitParamsCtx.inputBufferType));
    int32_t dmxDevId = env->GetIntField(jInitParam, gInitParamsCtx.dmxDevId);
    int32_t eventMask = env->GetIntField(jInitParam, gInitParamsCtx.eventMask);

    outParams->playback_mode = playbackMode;
    outParams->source = tsType;
    outParams->drmmode = drmmode;
    outParams->dmx_dev_id = dmxDevId;
    outParams->event_mask = eventMask;

    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::convertVideoParams(JNIEnv *env, jobject jVideoParam, jni_asplayer_video_params *outParams) {
    if (env == nullptr || jVideoParam == nullptr || outParams == nullptr) {
        return false;
    }

    LOG_FUNCTION_ENTER();
    jstring jMimeType = static_cast<jstring>(env->GetObjectField(jVideoParam, gVideoParamsCtx.mimeType));
    jint width = env->GetIntField(jVideoParam, gVideoParamsCtx.width);
    jint height = env->GetIntField(jVideoParam, gVideoParamsCtx.height);
    jint pid = env->GetIntField(jVideoParam, gVideoParamsCtx.pid);
    jint filterId = env->GetIntField(jVideoParam, gVideoParamsCtx.trackFilterId);
    jint avSyncHwId = env->GetIntField(jVideoParam, gVideoParamsCtx.avSyncHwId);
    jobject mediaFormat = env->GetObjectField(jVideoParam, gVideoParamsCtx.mediaFormat);

    memset(outParams, 0, sizeof(jni_asplayer_video_params));

    if (jMimeType) {
        jsize mimeTypeLen = env->GetStringUTFLength(jMimeType);
        if (mimeTypeLen > 0) {
            char *mimeType = new char[mimeTypeLen + 1];
            memset(mimeType, 0, mimeTypeLen + 1);
            env->GetStringUTFRegion(jMimeType, 0, mimeTypeLen, mimeType);
            outParams->mimeType = mimeType;
        }
    }

    outParams->width = width;
    outParams->height = height;
    outParams->pid = pid;
    outParams->filterId = filterId;
    outParams->avSyncHwId = avSyncHwId;
    outParams->mediaFormat = mediaFormat;

    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::convertAudioParams(JNIEnv *env, jobject jAudioParam, jni_asplayer_audio_params *outParams) {
    if (env == nullptr || jAudioParam == nullptr || outParams == nullptr) {
        return false;
    }

    LOG_FUNCTION_ENTER();
    jstring jMimeType = static_cast<jstring>(env->GetObjectField(jAudioParam, gAudioParamsCtx.mimeType));
    jint sampleRate = env->GetIntField(jAudioParam, gAudioParamsCtx.sampleRate);
    jint channelCount = env->GetIntField(jAudioParam, gAudioParamsCtx.channelCount);
    jint pid = env->GetIntField(jAudioParam, gAudioParamsCtx.pid);
    jint filterId = env->GetIntField(jAudioParam, gAudioParamsCtx.trackFilterId);
    jint avSyncHwId = env->GetIntField(jAudioParam, gAudioParamsCtx.avSyncHwId);
    jint secLevel = env->GetIntField(jAudioParam, gAudioParamsCtx.secLevel);
    jobject mediaFormat = env->GetObjectField(jAudioParam, gAudioParamsCtx.mediaFormat);

    memset(outParams, 0, sizeof(jni_asplayer_audio_params));

    if (jMimeType != nullptr) {
        jsize mimeTypeLen = env->GetStringUTFLength(jMimeType);
        if (mimeTypeLen > 0) {
            char *mimeType = new char[mimeTypeLen + 1];
            memset(mimeType, 0, mimeTypeLen + 1);
            env->GetStringUTFRegion(jMimeType, 0, mimeTypeLen, mimeType);
            outParams->mimeType = mimeType;
        }
    }
    outParams->sampleRate = sampleRate;
    outParams->channelCount = channelCount;
    outParams->pid = pid;
    outParams->filterId = filterId;
    outParams->avSyncHwId = avSyncHwId;
    outParams->seclevel = secLevel;
    outParams->mediaFormat = mediaFormat;

    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::convertInputBuffer(JNIEnv *env, jobject jInputBuffer, jni_asplayer_input_buffer *outInputBuffer) {
    if (env == nullptr || jInputBuffer == nullptr || outInputBuffer == nullptr) {
        return false;
    }

//    LOG_FUNCTION_ENTER();

    jint inputBufferType = env->GetIntField(jInputBuffer, gInputBufferCtx.inputBufferType);
    jint offset = env->GetIntField(jInputBuffer, gInputBufferCtx.offset);
    jint bufferSize = env->GetIntField(jInputBuffer, gInputBufferCtx.bufferSize);
    jbyteArray buffer = static_cast<jbyteArray>(env->GetObjectField(jInputBuffer, gInputBufferCtx.buffer));

    outInputBuffer->buf_type = static_cast<jni_asplayer_input_buffer_type>(inputBufferType);
    outInputBuffer->offset = (int32_t) offset;
    outInputBuffer->buf_size = (int32_t) bufferSize;

    jbyte *bufferData = nullptr;
    if (buffer != nullptr && bufferSize > 0) {
        jsize bufferLength = env->GetArrayLength(buffer);
        bufferData = new jbyte[bufferLength];
        env->GetByteArrayRegion(buffer, 0, bufferLength, bufferData);
        ALOGD("%s[%d] alloc buffer %d success, offset: %d, bufferSize: %d", __func__, __LINE__, bufferLength, offset, bufferSize);
    }
    outInputBuffer->buf_data = bufferData;

//    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::createVideoFormatChangeEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr) {
        return false;
    } else if (event == nullptr) {
        return false;
    } else if (jEvent == nullptr) {
        return false;
    }

    jobject mediaFormat = env->NewObject(gMediaFormatCls, gMediaFormatCtx.constructor);

    jstring width = env->NewStringUTF("width");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, width, event->event.video_format.frame_width);
    env->DeleteLocalRef(width);

    jstring height = env->NewStringUTF("height");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, height, event->event.video_format.frame_height);
    env->DeleteLocalRef(height);

    jobject videoFormatChangeEvent = env->NewObject(gVideoFormatChangeEventCls, gVideoFormatChangeEventCtx.constructor, mediaFormat);
    *jEvent = videoFormatChangeEvent;

    return true;
}

bool ASPlayerJni::notifyVideoFormatChangeEvent(JNIEnv *env, jobject jPlaybackListener, jobject videoFormatChangeEvent) {
    if (env == nullptr) {
        return false;
    } else if (jPlaybackListener == nullptr || videoFormatChangeEvent == nullptr) {
        return false;
    }

    env->CallVoidMethod(jPlaybackListener, gPlaybackListenerCtx.onPlaybackEvent, videoFormatChangeEvent);
    return true;
}