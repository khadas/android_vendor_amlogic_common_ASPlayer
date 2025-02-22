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
    jfieldID eventMask;
};

struct video_param_t {
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

struct audio_lang_t {
    jmethodID getFirstLanguage;
    jmethodID getSecondLanguage;
};

struct audio_param_t {
    jfieldID mimeType;
    jfieldID sampleRate;
    jfieldID channelCount;
    jfieldID pid;
    jfieldID trackFilterId;
    jfieldID avSyncHwId;
    jfieldID secLevel;
    jfieldID scrambled;
    jfieldID audioPresentation;
    jfieldID audioLanguage;
    jfieldID mediaFormat;
    jfieldID extraInfoJson;
};

struct input_buffer_t {
    jfieldID buffer;
    jfieldID offset;
    jfieldID bufferSize;
};

struct media_format_t {
    jmethodID constructor;
    jmethodID setInteger;
    jmethodID setFloat;
    jmethodID setLong;
};

struct bundle_t {
    jmethodID constructor;
    jmethodID putInt;
};

struct video_format_change_event_t {
    jmethodID constructor;
    jfieldID mediaFormat;
};

struct audio_format_change_event_t {
    jmethodID constructor;
    jfieldID mediaFormat;
};

struct first_frame_event_t {
    jfieldID streamType;
    jfieldID pts;
    jfieldID renderTime;
};

struct video_first_frame_event_t {
    first_frame_event_t base;
    jmethodID constructor;
};

struct audio_first_frame_event_t {
    first_frame_event_t base;
    jmethodID constructor;
};

struct decode_first_video_frame_event_t {
    first_frame_event_t base;
    jmethodID constructor;
};

struct decode_first_audio_frame_event_t {
    first_frame_event_t base;
    jmethodID constructor;
};

struct pts_event_t {
    jmethodID constructor;
    jfieldID streamType;
    jfieldID pts;
    jfieldID renderTime;
};

struct playback_info_event_t {
    jmethodID constructor;
};

struct playback_listener_t {
    jmethodID onPlaybackEvent;
};

static jclass gInitParamsCls;
static init_param_t gInitParamsCtx;
static jclass gVideoParamsCls;
static video_param_t gVideoParamsCtx;
static jclass gAudioLangCls;
static audio_lang_t gAudioLanguageCtx;
static jclass gAudioParamsCls;
static audio_param_t gAudioParamsCtx;
static jclass gInputBufferCls;
static input_buffer_t gInputBufferCtx;

static jclass gMediaFormatCls;
static media_format_t gMediaFormatCtx;

static jclass gBundleCls;
static bundle_t gBundleCtx;

static jclass gVideoFormatChangeEventCls;
static video_format_change_event_t gVideoFormatChangeEventCtx;
static jclass gAudioFormatChangeEventCls;
static audio_format_change_event_t gAudioFormatChangeEventCtx;
static jclass gFirstFrameEventCls;
static first_frame_event_t gFirstFrameEventCtx;
static jclass gVideoFirstFrameEventCls;
static video_first_frame_event_t gVideoFirstFrameEventCtx;
static jclass gAudioFirstFrameEventCls;
static audio_first_frame_event_t gAudioFirstFrameEventCtx;
static jclass gDecodeFirstVideoFrameEventCls;
static decode_first_video_frame_event_t gDecodeFirstVideoFrameEventCtx;
static jclass gDecodeFirstAudioFrameEventCls;
static decode_first_audio_frame_event_t gDecodeFirstAudioFrameEventCtx;
static jclass gPtsEventCls;
static pts_event_t gPtsEventCtx;
static jclass gPlaybackInfoEventCls;
static playback_info_event_t gPlaybackInfoEventCtx;

static jclass gPlaybackListenerCls;
static playback_listener_t gPlaybackListenerCtx;

static bool g_inited = false;

static inline bool makeClassGlobalRef(jclass *globalRef, JNIEnv *env, const char *className) {
    if (globalRef == nullptr || env == nullptr || className == nullptr) {
        return false;
    }

    jclass localCls = env->FindClass(className);
    if (localCls) {
        *globalRef = static_cast<jclass>(env->NewGlobalRef(localCls));
        env->DeleteLocalRef(localCls);
        return true;
    } else {
        ALOGI("[%s/%d] failed to find class \"%s\"", __FUNCTION__, __LINE__, className);
        *globalRef = nullptr;
        return false;
    }
}

ASPlayerJni::ASPlayerJni() {

}

ASPlayerJni::~ASPlayerJni() {

}

bool ASPlayerJni::initJni(JNIEnv *env) {
    if (g_inited) {
        return true;
    }

    // init InitParams
    if (makeClassGlobalRef(&gInitParamsCls, env,
                           "com/amlogic/asplayer/api/InitParams")) {
        gInitParamsCtx.playbackMode = env->GetFieldID(
                gInitParamsCls, "mPlaybackMode", "I");
        gInitParamsCtx.inputSourceType = env->GetFieldID(
                gInitParamsCls, "mInputSourceType", "I");
        gInitParamsCtx.eventMask = env->GetFieldID(
                gInitParamsCls, "mEventMask", "J");
    }

    // init VideoParams
    if (makeClassGlobalRef(&gVideoParamsCls, env,
                           "com/amlogic/asplayer/api/VideoParams")) {
        gVideoParamsCtx.mimeType = env->GetFieldID(
                gVideoParamsCls, "mMimeType", "Ljava/lang/String;");
        gVideoParamsCtx.width = env->GetFieldID(gVideoParamsCls, "mWidth", "I");
        gVideoParamsCtx.height = env->GetFieldID(gVideoParamsCls, "mHeight", "I");
        gVideoParamsCtx.pid = env->GetFieldID(gVideoParamsCls, "mPid", "I");
        gVideoParamsCtx.trackFilterId = env->GetFieldID(
                gVideoParamsCls, "mTrackFilterId", "I");
        gVideoParamsCtx.avSyncHwId = env->GetFieldID(
                gVideoParamsCls, "mAvSyncHwId", "I");
        gVideoParamsCtx.scrambled = env->GetFieldID(
                gVideoParamsCls, "mScrambled", "Z");
        gVideoParamsCtx.hasVideo = env->GetFieldID(
                gVideoParamsCls, "mHasVideo", "Z");
        gVideoParamsCtx.mediaFormat = env->GetFieldID(
                gVideoParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");
    }

    // init AudioLang
    if (makeClassGlobalRef(&gAudioLangCls, env, "com/amlogic/asplayer/api/AudioLang")) {
        gAudioLanguageCtx.getFirstLanguage = env->GetMethodID(gAudioLangCls, "getFirstLanguage", "()I");
        gAudioLanguageCtx.getSecondLanguage = env->GetMethodID(gAudioLangCls, "getSecondLanguage", "()I");
    }

    // init AudioParams
    if (makeClassGlobalRef(&gAudioParamsCls, env,
                           "com/amlogic/asplayer/api/AudioParams")) {
        gAudioParamsCtx.mimeType = env->GetFieldID(
                gAudioParamsCls, "mMimeType", "Ljava/lang/String;");
        gAudioParamsCtx.sampleRate = env->GetFieldID(
                gAudioParamsCls, "mSampleRate", "I");
        gAudioParamsCtx.channelCount = env->GetFieldID(
                gAudioParamsCls, "mChannelCount", "I");
        gAudioParamsCtx.pid = env->GetFieldID(
                gAudioParamsCls, "mPid", "I");
        gAudioParamsCtx.trackFilterId = env->GetFieldID(
                gAudioParamsCls, "mTrackFilterId", "I");
        gAudioParamsCtx.avSyncHwId = env->GetFieldID(
                gAudioParamsCls, "mAvSyncHwId", "I");
        gAudioParamsCtx.secLevel = env->GetFieldID(
                gAudioParamsCls, "mSecLevel", "I");
        gAudioParamsCtx.scrambled = env->GetFieldID(
                gAudioParamsCls, "mScrambled", "Z");
        gAudioParamsCtx.audioPresentation = env->GetFieldID(
                gAudioParamsCls, "mAudioPresentation", "Landroid/media/AudioPresentation;");
        gAudioParamsCtx.audioLanguage = env->GetFieldID(
                gAudioParamsCls, "mAudioLanguage", "Lcom/amlogic/asplayer/api/AudioLang;");
        gAudioParamsCtx.mediaFormat = env->GetFieldID(
                gAudioParamsCls, "mMediaFormat", "Landroid/media/MediaFormat;");
        gAudioParamsCtx.extraInfoJson = env->GetFieldID(
                gAudioParamsCls, "mExtraInfoJson", "Ljava/lang/String;");
    }

    // init InputBuffer
    if (makeClassGlobalRef(&gInputBufferCls, env,
                           "com/amlogic/asplayer/api/InputBuffer")) {
        gInputBufferCtx.buffer = env->GetFieldID(gInputBufferCls, "mBuffer", "[B");
        gInputBufferCtx.offset = env->GetFieldID(gInputBufferCls, "mOffset", "I");
        gInputBufferCtx.bufferSize = env->GetFieldID(
                gInputBufferCls, "mBufferSize", "I");
    }

    // init MediaFormat
    if (makeClassGlobalRef(&gMediaFormatCls, env, "android/media/MediaFormat")) {
        gMediaFormatCtx.constructor = env->GetMethodID(gMediaFormatCls, "<init>", "()V");
        gMediaFormatCtx.setInteger = env->GetMethodID(
                gMediaFormatCls, "setInteger", "(Ljava/lang/String;I)V");
        gMediaFormatCtx.setFloat = env->GetMethodID(
                gMediaFormatCls, "setFloat", "(Ljava/lang/String;F)V");
        gMediaFormatCtx.setLong = env->GetMethodID(
                gMediaFormatCls, "setLong", "(Ljava/lang/String;J)V");
    }

    // init VideoFormatChangeEvent
    if (makeClassGlobalRef(&gVideoFormatChangeEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$VideoFormatChangeEvent")) {
        gVideoFormatChangeEventCtx.constructor = env->GetMethodID(
                gVideoFormatChangeEventCls, "<init>", "(Landroid/media/MediaFormat;)V");
        gVideoFormatChangeEventCtx.mediaFormat = env->GetFieldID(
                gVideoFormatChangeEventCls, "mVideoFormat","Landroid/media/MediaFormat;");
    }

    // AudioFormatChangeEvent
    if (makeClassGlobalRef(&gAudioFormatChangeEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$AudioFormatChangeEvent")) {
        gAudioFormatChangeEventCtx.constructor = env->GetMethodID(
                gAudioFormatChangeEventCls, "<init>", "(Landroid/media/MediaFormat;)V");
        gAudioFormatChangeEventCtx.mediaFormat = env->GetFieldID(
                gAudioFormatChangeEventCls, "mAudioFormat", "Landroid/media/MediaFormat;");
    }

    // FirstFrameEvent
    if (makeClassGlobalRef(&gFirstFrameEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$FirstFrameEvent")) {
        gFirstFrameEventCtx.streamType = env->GetFieldID(
                gFirstFrameEventCls, "mStreamType", "I");
        gFirstFrameEventCtx.pts = env->GetFieldID(
                gFirstFrameEventCls, "mPts", "J");
        gFirstFrameEventCtx.renderTime = env->GetFieldID(
                gFirstFrameEventCls, "mRenderTime", "J");
    }

    // VideoFirstFrameEvent
    if (makeClassGlobalRef(&gVideoFirstFrameEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$VideoFirstFrameEvent")) {
        gVideoFirstFrameEventCtx.constructor = env->GetMethodID(
                gVideoFirstFrameEventCls, "<init>", "(JJ)V");
        gVideoFirstFrameEventCtx.base = gFirstFrameEventCtx;
    }

    // AudioFirstFrameEvent
    if (makeClassGlobalRef(&gAudioFirstFrameEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$AudioFirstFrameEvent")) {
        gAudioFirstFrameEventCtx.constructor = env->GetMethodID(
                gAudioFirstFrameEventCls, "<init>", "(JJ)V");
        gAudioFirstFrameEventCtx.base = gFirstFrameEventCtx;
    }

    // DecodeFirstVideoFrameEvent
    if (makeClassGlobalRef(&gDecodeFirstVideoFrameEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$DecodeFirstVideoFrameEvent")) {
        gDecodeFirstVideoFrameEventCtx.constructor = env->GetMethodID(
                gDecodeFirstVideoFrameEventCls, "<init>", "(J)V");
        gDecodeFirstVideoFrameEventCtx.base = gFirstFrameEventCtx;
    }

    // DecodeFirstAudioFrameEvent
    if (makeClassGlobalRef(&gDecodeFirstAudioFrameEventCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener$DecodeFirstAudioFrameEvent")) {
        gDecodeFirstAudioFrameEventCtx.constructor = env->GetMethodID(
                gDecodeFirstAudioFrameEventCls, "<init>", "(J)V");
        gDecodeFirstAudioFrameEventCtx.base = gFirstFrameEventCtx;
    }

    // PtsEvent
    if (makeClassGlobalRef(&gPtsEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$PtsEvent")) {
        gPtsEventCtx.constructor = env->GetMethodID(gPtsEventCls, "<init>", "(IJJ)V");
        gPtsEventCtx.streamType = env->GetFieldID(gPtsEventCls, "mStreamType", "I");
        gPtsEventCtx.pts = env->GetFieldID(gPtsEventCls, "mPts", "J");
        gPtsEventCtx.renderTime = env->GetFieldID(gPtsEventCls, "mRenderTime", "J");
    }

    // PlaybackInfoEvent
    if (makeClassGlobalRef(&gPlaybackInfoEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$PlaybackInfoEvent")) {
        gPlaybackInfoEventCtx.constructor = env->GetMethodID(
                gPlaybackInfoEventCls, "<init>", "(II)V");
    }

    // init PlaybackListener
    if (makeClassGlobalRef(&gPlaybackListenerCls, env,
            "com/amlogic/asplayer/api/TsPlaybackListener")) {
        gPlaybackListenerCtx.onPlaybackEvent = env->GetMethodID(
                gPlaybackListenerCls, "onPlaybackEvent",
                "(Lcom/amlogic/asplayer/api/TsPlaybackListener$PlaybackEvent;)V");
    }

    // init Bundle
    if (makeClassGlobalRef(&gBundleCls, env, "android/os/Bundle")) {
        gBundleCtx.constructor = env->GetMethodID(gBundleCls, "<init>", "()V");
        gBundleCtx.putInt = env->GetMethodID(gBundleCls, "putInt", "(Ljava/lang/String;I)V");
    }

    g_inited = true;
    return true;
}

bool ASPlayerJni::convertInitParams(
        JNIEnv *env, jobject jInitParam, jni_asplayer_init_params* outParams) {
    if (env == nullptr || jInitParam == nullptr || outParams == nullptr) {
        return false;
    }

    LOG_FUNCTION_ENTER();
    jni_asplayer_playback_mode playbackMode = static_cast<jni_asplayer_playback_mode>(env->GetIntField(
            jInitParam, gInitParamsCtx.playbackMode));
    jni_asplayer_input_source_type tsType = static_cast<jni_asplayer_input_source_type>(env->GetIntField(
            jInitParam, gInitParamsCtx.inputSourceType));
    int64_t eventMask = env->GetLongField(jInitParam, gInitParamsCtx.eventMask);

    outParams->playback_mode = playbackMode;
    outParams->source = tsType;
    outParams->event_mask = eventMask;

    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::convertVideoParams(
        JNIEnv *env, jobject jVideoParam, jni_asplayer_video_params *outParams) {
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
    jboolean scrambled = env->GetBooleanField(jVideoParam, gVideoParamsCtx.scrambled);
    jboolean hasVideo = env->GetBooleanField(jVideoParam, gVideoParamsCtx.hasVideo);
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
    outParams->scrambled = scrambled;
    outParams->hasVideo = hasVideo;
    outParams->mediaFormat = mediaFormat;

    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::convertAudioPresentation(JNIEnv *env, jobject jAudioPresentation,
                                           jni_asplayer_audio_presentation *outPresentation) {
    if (env == nullptr || jAudioPresentation == nullptr || outPresentation == nullptr) {
        return false;
    }

    jclass audioPresentationCls = env->FindClass("android/media/AudioPresentation");
    if (audioPresentationCls == nullptr) {
        return false;
    }

    jmethodID getPresentationMID = env->GetMethodID(audioPresentationCls, "getPresentationId", "()I");
    jmethodID getProgramMID = env->GetMethodID(audioPresentationCls, "getProgramId", "()I");

    outPresentation->presentation_id = static_cast<int32_t>(
            env->CallIntMethod(jAudioPresentation, getPresentationMID));
    outPresentation->program_id = static_cast<int32_t>(
            env->CallIntMethod(jAudioPresentation, getProgramMID));

    env->DeleteLocalRef(audioPresentationCls);
    return true;
}

bool ASPlayerJni::convertAudioLanguage(JNIEnv *env, jobject jAudioLanguage,
                                       jni_asplayer_audio_lang *outLanguage) {
    if (env == nullptr || jAudioLanguage == nullptr || outLanguage == nullptr) {
        ALOGE("convertAudioLanguage failed, invalid params");
        return false;
    }

    if (gAudioLangCls == nullptr) {
        ALOGE("convertAudioLanguage failed, failed to find class com/amlogic/asplayer/api/AudioLang");
        return false;
    }

    outLanguage->first_lang = static_cast<int32_t>(
            env->CallIntMethod(jAudioLanguage, gAudioLanguageCtx.getFirstLanguage));
    outLanguage->second_lang = static_cast<int32_t>(
            env->CallIntMethod(jAudioLanguage, gAudioLanguageCtx.getSecondLanguage));

    return true;
}

bool ASPlayerJni::convertAudioParams(
        JNIEnv *env, jobject jAudioParam, jni_asplayer_audio_params *outParams) {
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
    jboolean scrambled = env->GetBooleanField(jAudioParam, gAudioParamsCtx.scrambled);
    jobject audioPresentation = env->GetObjectField(jAudioParam, gAudioParamsCtx.audioPresentation);
    jobject audioLanguage = env->GetObjectField(jAudioParam, gAudioParamsCtx.audioLanguage);
    jobject mediaFormat = env->GetObjectField(jAudioParam, gAudioParamsCtx.mediaFormat);
    jstring jExtraInfoJson = static_cast<jstring>(env->GetObjectField(jAudioParam, gAudioParamsCtx.extraInfoJson));

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
    outParams->scrambled = scrambled;
    outParams->mediaFormat = mediaFormat;

    if (audioPresentation != nullptr) {
        if (!convertAudioPresentation(env, audioPresentation, &(outParams->presentation))) {
            ALOGE("convertAudioPresentation failed");
        }
    }

    ALOGI("audioPresentationId: %d, programId: %d",
          outParams->presentation.presentation_id,
          outParams->presentation.program_id);

    if (audioLanguage != nullptr) {
        if (!convertAudioLanguage(env, audioLanguage, &(outParams->language))) {
            ALOGE("convertAudioLanguage failed");
        }
    }

    ALOGI("audioLanguage, firstLanguage: %d, 0x%x, secondLanguage: %d, 0x%x",
          outParams->language.first_lang, outParams->language.first_lang,
          outParams->language.second_lang, outParams->language.second_lang);

    if (jExtraInfoJson != nullptr) {
        jsize extraInfoLen = env->GetStringUTFLength(jExtraInfoJson);
        if (extraInfoLen > 0) {
            char *extraInfo = new char[extraInfoLen + 1];
            memset(extraInfo, 0, extraInfoLen + 1);
            env->GetStringUTFRegion(jExtraInfoJson, 0, extraInfoLen, extraInfo);
            outParams->extraInfoJson = extraInfo;
        }
    }

    LOG_FUNCTION_END();
    return true;
}

bool ASPlayerJni::convertInputBuffer(
        JNIEnv *env, jobject jInputBuffer, jni_asplayer_input_buffer *outInputBuffer) {
    if (env == nullptr || jInputBuffer == nullptr || outInputBuffer == nullptr) {
        return false;
    }

    jint offset = env->GetIntField(jInputBuffer, gInputBufferCtx.offset);
    jint bufferSize = env->GetIntField(jInputBuffer, gInputBufferCtx.bufferSize);
    jbyteArray buffer = static_cast<jbyteArray>(env->GetObjectField(jInputBuffer, gInputBufferCtx.buffer));

    outInputBuffer->offset = (int32_t) offset;
    outInputBuffer->buf_size = (int32_t) bufferSize;

    jbyte *bufferData = nullptr;
    if (buffer != nullptr && bufferSize > 0) {
        jsize bufferLength = env->GetArrayLength(buffer);
        bufferData = new jbyte[bufferLength];
        env->GetByteArrayRegion(buffer, 0, bufferLength, bufferData);
//        ALOGD("[%s/%d] alloc buffer %d success, offset: %d, bufferSize: %d", __func__, __LINE__, bufferLength, offset, bufferSize);
    }
    outInputBuffer->buf_data = bufferData;

    return true;
}

bool ASPlayerJni::createPtsEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jint streamType = event->event.pts.stream_type;
    jlong pts = event->event.pts.pts;
    jlong renderTime = event->event.pts.renderTime;

    jobject ptsEvent = env->NewObject(gPtsEventCls, gPtsEventCtx.constructor,
                                      streamType, pts, renderTime);
    *jEvent = ptsEvent;

    return true;
}

bool ASPlayerJni::createVideoFormatChangeEvent(
        JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jobject mediaFormat = env->NewObject(gMediaFormatCls, gMediaFormatCtx.constructor);

    jstring width = env->NewStringUTF("width");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, width, event->event.video_format.frame_width);
    env->DeleteLocalRef(width);

    jstring height = env->NewStringUTF("height");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, height, event->event.video_format.frame_height);
    env->DeleteLocalRef(height);

    jstring frameRateKey = env->NewStringUTF("frame-rate");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, frameRateKey, event->event.video_format.frame_rate);
    env->DeleteLocalRef(frameRateKey);

    jstring aspectRatioKey = env->NewStringUTF("aspect-ratio");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, aspectRatioKey, event->event.video_format.frame_aspectratio);
    env->DeleteLocalRef(aspectRatioKey);

    jobject videoFormatChangeEvent = env->NewObject(gVideoFormatChangeEventCls,
            gVideoFormatChangeEventCtx.constructor, mediaFormat);
    *jEvent = videoFormatChangeEvent;

    return true;
}

bool ASPlayerJni::createAudioFormatChangeEvent(
        JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jobject mediaFormat = env->NewObject(gMediaFormatCls, gMediaFormatCtx.constructor);

    jstring sampleRate = env->NewStringUTF("sample-rate");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, sampleRate, event->event.audio_format.sample_rate);
    env->DeleteLocalRef(sampleRate);

    jstring channelCount = env->NewStringUTF("channel-count");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, channelCount, event->event.audio_format.channels);
    env->DeleteLocalRef(channelCount);

    jstring channelMask = env->NewStringUTF("channel-mask");
    env->CallVoidMethod(mediaFormat,
            gMediaFormatCtx.setInteger, channelMask, event->event.audio_format.channel_mask);
    env->DeleteLocalRef(channelMask);

    jobject audioFormatChangeEvent = env->NewObject(gAudioFormatChangeEventCls,
            gAudioFormatChangeEventCtx.constructor, mediaFormat);
    *jEvent = audioFormatChangeEvent;

    return true;
}

bool ASPlayerJni::createVideoFirstFrameEvent(
        JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jlong pts = event->event.pts.pts;
    jlong renderTime = event->event.pts.renderTime;

    jobject videoFirstFrameEvent = env->NewObject(gVideoFirstFrameEventCls,
            gVideoFirstFrameEventCtx.constructor, pts, renderTime);
    *jEvent = videoFirstFrameEvent;
    return true;
}

bool ASPlayerJni::createAudioFirstFrameEvent(
        JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jlong pts = event->event.pts.pts;
    jlong renderTime = event->event.pts.renderTime;

    jobject audioFirstFrameEvent = env->NewObject(gAudioFirstFrameEventCls,
            gAudioFirstFrameEventCtx.constructor, pts, renderTime);
    *jEvent = audioFirstFrameEvent;
    return true;
}

bool ASPlayerJni::createDecodeFirstVideoFrameEvent(
        JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jlong pts = event->event.pts.pts;

    jobject decodeFirstVideoFrameEvent = env->NewObject(gDecodeFirstVideoFrameEventCls,
            gDecodeFirstVideoFrameEventCtx.constructor, pts);
    *jEvent = decodeFirstVideoFrameEvent;
    return true;
}

bool ASPlayerJni::createDecodeFirstAudioFrameEvent(
        JNIEnv *env, jni_asplayer_event *event, jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jlong pts = event->event.pts.pts;

    jobject decodeFirstAudioFrameEvent = env->NewObject(gDecodeFirstAudioFrameEventCls,
            gDecodeFirstAudioFrameEventCtx.constructor, pts);
    *jEvent = decodeFirstAudioFrameEvent;
    return true;
}

bool ASPlayerJni::createMediaFormat(JNIEnv *env, jni_asplayer_video_info *videoInfo, jobject *jMediaFormat) {
    if (env == nullptr) {
        return false;
    } else if (videoInfo == nullptr || jMediaFormat == nullptr) {
        return false;
    }

    jobject mediaFormat = env->NewObject(gMediaFormatCls, gMediaFormatCtx.constructor);

    jstring width = env->NewStringUTF("width");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, width,videoInfo->width);
    env->DeleteLocalRef(width);

    jstring height = env->NewStringUTF("height");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, height,videoInfo->height);
    env->DeleteLocalRef(height);

    jstring frameRate = env->NewStringUTF("frame-rate");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, frameRate,videoInfo->framerate);
    env->DeleteLocalRef(frameRate);

    jstring aspectRadio = env->NewStringUTF("aspect-ratio");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, aspectRadio,videoInfo->aspectRatio);
    env->DeleteLocalRef(aspectRadio);

    jstring vfType = env->NewStringUTF("vf-type");
    env->CallVoidMethod(mediaFormat, gMediaFormatCtx.setInteger, vfType, videoInfo->vfType);
    env->DeleteLocalRef(vfType);

    *jMediaFormat = mediaFormat;
    return true;
}

bool ASPlayerJni::createBundleObject(JNIEnv *env, jobject *jBundleObj) {
    if (env == nullptr || jBundleObj == nullptr) {
        return false;
    }

    jobject bundle = env->NewObject(gBundleCls, gBundleCtx.constructor);
    if (env->IsSameObject(bundle, nullptr)) {
        return false;
    }

    *jBundleObj = bundle;
    return true;
}

bool ASPlayerJni::putIntToBundle(JNIEnv *env, jobject bundleObj, const char *key, int32_t value) {
    if (env == nullptr || key == nullptr) {
        return false;
    }

    jstring keyStr = env->NewStringUTF(key);
    env->CallVoidMethod(bundleObj, gBundleCtx.putInt, keyStr, (jint)value);
    env->DeleteLocalRef(keyStr);

    return true;
}

bool ASPlayerJni::createPlaybackInfoEvent(JNIEnv *env, jni_asplayer_event *event,
                                                       jobject *jEvent) {
    if (env == nullptr || event == nullptr || jEvent == nullptr) {
        return false;
    }

    jobject playbackInfoEvent = env->NewObject(gPlaybackInfoEventCls,
                                               gPlaybackInfoEventCtx.constructor,
                                               event->type,
                                               event->event.stream_type);
    *jEvent = playbackInfoEvent;

    return true;
}

bool ASPlayerJni::notifyPlaybackEvent(
        JNIEnv *env, jobject jPlaybackListener, jobject playbackEvent) {
    if (env == nullptr || jPlaybackListener == nullptr || playbackEvent == nullptr) {
        return false;
    }

    env->CallVoidMethod(jPlaybackListener, gPlaybackListenerCtx.onPlaybackEvent, playbackEvent);
    return true;
}