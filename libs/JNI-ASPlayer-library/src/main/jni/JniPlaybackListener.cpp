/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#include "JniMediaFormat.h"
#include "NativeHelper.h"
#include "JniPlaybackListener.h"

#define NELEM(arr) (sizeof(arr) / sizeof(arr[0]))

static const char *JNI_PLAYBACK_LISTENER_CLASS_NAME = "com/amlogic/jniasplayer/JniPlaybackListener";

// PlaybackEvent
struct playback_event_t {
    jfieldID eventType;
};

// VideoFormatChangeEvent
struct video_format_change_event_t {
    jfieldID videoFormat;
};

// AudioFormatChangeEvent
struct audio_format_change_event_t {
    jfieldID audioFormat;
};

// FirstFrameEvent
struct first_frame_event_t {
    jfieldID pts;
};

// PtsEvent
struct pts_event_t {
    jfieldID streamType;
    jfieldID pts;
    jfieldID renderTime;
};

// PlaybackListener
struct jni_tsplayback_listener_t {
    jmethodID constructorMID;
    jfieldID context;
};

static jclass gJniTsPlaybackListenerCls;
static jni_tsplayback_listener_t gJniTsPlaybackListenerCtx;

static jclass gPlaybackEventCls;
static playback_event_t gPlaybackEventCtx;

static jclass gVideoFormatChangeEventCls;
static video_format_change_event_t gVideoFormatChangeEventCtx;

static jclass gAudioFormatChangeEventCls;
static audio_format_change_event_t gAudioFormatChangeEventCtx;

static jclass gFirstFrameEventCls;
static first_frame_event_t gFirstFrameEventCtx;
static jclass gVideoFirstFrameEventCls;
static jclass gAudioFirstFrameEventCls;
static jclass gDecodeFirstVideoFrameEventCls;
static jclass gDecodeFirstAudioFrameEventCls;

static jclass gPtsEventCls;
static pts_event_t gPtsEventCtx;

static jclass gPlaybackInfoEventCls;

bool JniPlaybackListener::gInited = false;

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

JniPlaybackListener::JniPlaybackListener(event_callback callback, void *userdata)
    : mJavaListener(nullptr), mCallback(callback), mUserData(userdata) {

}

JniPlaybackListener::~JniPlaybackListener() {

}

static jint native_notifyPlaybackEvent(JNIEnv *env, jobject jListener, jobject jEvent) {
    JniPlaybackListener *listener = JniPlaybackListener::getNativeListener(env, jListener);
    if (listener == nullptr) {
        ALOGE("%s[%d] notifyPlaybackEvent failed, listener is null", __func__, __LINE__);
        return 0;
    }

    listener->notifyPlaybackEvent(env, jEvent);

    return 0;
}

static const JNINativeMethod gJniPlaybackListenerMethods[] = {
        { "native_notifyPlaybackEvent", "(Lcom/amlogic/asplayer/api/TsPlaybackListener$PlaybackEvent;)V", (void*)native_notifyPlaybackEvent}
};

bool JniPlaybackListener::init(JNIEnv *env) {
    if (gInited) {
        return true;
    }

    // JniPlaybackListener
    if (makeClassGlobalRef(&gJniTsPlaybackListenerCls, env,
                           JNI_PLAYBACK_LISTENER_CLASS_NAME)) {
        gJniTsPlaybackListenerCtx.constructorMID = env->GetMethodID(
                gJniTsPlaybackListenerCls, "<init>", "()V");
        gJniTsPlaybackListenerCtx.context = env->GetFieldID(
                gJniTsPlaybackListenerCls, "mNativeContext", "J");
    }

    // PlaybackEvent
    if (makeClassGlobalRef(&gPlaybackEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$PlaybackEvent")) {
        gPlaybackEventCtx.eventType = env->GetFieldID(
                gPlaybackEventCls, "mEventType", "I");
    }

    // VideoFormatChangeEvent
    if (makeClassGlobalRef(&gVideoFormatChangeEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$VideoFormatChangeEvent")) {
        gVideoFormatChangeEventCtx.videoFormat = env->GetFieldID(
                gVideoFormatChangeEventCls, "mVideoFormat","Landroid/media/MediaFormat;");
    }

    // AudioFormatChangeEvent
    if (makeClassGlobalRef(&gAudioFormatChangeEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$AudioFormatChangeEvent")) {
        gAudioFormatChangeEventCtx.audioFormat = env->GetFieldID(
                gAudioFormatChangeEventCls, "mAudioFormat", "Landroid/media/MediaFormat;");
    }

    // FirstFrameEvent
    if (makeClassGlobalRef(&gFirstFrameEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$FirstFrameEvent")) {
        gFirstFrameEventCtx.pts = env->GetFieldID(
                gFirstFrameEventCls, "mPositionMs", "J");
    }

    // VideoFirstFrameEvent
    makeClassGlobalRef(&gVideoFirstFrameEventCls, env,
                       "com/amlogic/asplayer/api/TsPlaybackListener$VideoFirstFrameEvent");

    // AudioFirstFrameEvent
    makeClassGlobalRef(&gAudioFirstFrameEventCls, env,
                       "com/amlogic/asplayer/api/TsPlaybackListener$AudioFirstFrameEvent");

    // DecodeFirstVideoFrameEvent
    makeClassGlobalRef(&gDecodeFirstVideoFrameEventCls, env,
                       "com/amlogic/asplayer/api/TsPlaybackListener$DecodeFirstVideoFrameEvent");

    // DecodeFirstAudioFrameEvent
    makeClassGlobalRef(&gDecodeFirstAudioFrameEventCls, env,
                       "com/amlogic/asplayer/api/TsPlaybackListener$DecodeFirstAudioFrameEvent");

    // PtsEvent
    if (makeClassGlobalRef(&gPtsEventCls, env,
                           "com/amlogic/asplayer/api/TsPlaybackListener$PtsEvent")) {
        gPtsEventCtx.streamType = env->GetFieldID(gPtsEventCls, "mStreamType", "I");
        gPtsEventCtx.pts = env->GetFieldID(gPtsEventCls, "mPts", "J");
        gPtsEventCtx.renderTime = env->GetFieldID(gPtsEventCls, "mRenderTime", "J");
    }

    // PlaybackInfoEvent
    makeClassGlobalRef(&gPlaybackInfoEventCls, env,
                       "com/amlogic/asplayer/api/TsPlaybackListener$PlaybackInfoEvent");

    int ret = NativeHelper::registerNativeMethods(env, JNI_PLAYBACK_LISTENER_CLASS_NAME, gJniPlaybackListenerMethods, NELEM(gJniPlaybackListenerMethods));
    if (ret != JNI_TRUE) {
        ALOGE("%s[%d] failed to register JniPlaybackListener native methods", __func__, __LINE__);
        return -1;
    }
    gInited = true;

    return true;
}

bool JniPlaybackListener::createPlaybackListener(JNIEnv *env) {
    if (env == nullptr) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mMutex);

    jobject playbackListener = env->NewObject(gJniTsPlaybackListenerCls, gJniTsPlaybackListenerCtx.constructorMID);
    setNativeHandle(env, playbackListener, this);

    mJavaListener = env->NewGlobalRef(playbackListener);
    env->DeleteLocalRef(playbackListener);

    return true;
}

jobject JniPlaybackListener::getJavaPlaybackListener() {
    std::lock_guard<std::mutex> lock(mMutex);
    return mJavaListener;
}

void JniPlaybackListener::setEventCallback(event_callback callback, void *userdata) {
    std::lock_guard<std::mutex> lock(mMutex);
    mCallback = callback;
    mUserData = userdata;
}

void JniPlaybackListener::setNativeHandle(JNIEnv *env, jobject jListener, JniPlaybackListener *listener) {
    if (env == nullptr || jListener == nullptr || listener == nullptr) {
        return;
    }

    env->SetLongField(jListener, gJniTsPlaybackListenerCtx.context, (jlong)listener);
}

JniPlaybackListener* JniPlaybackListener::getNativeListener(JNIEnv *env, jobject jListener) {
    if (env == nullptr || jListener == nullptr) {
        return nullptr;
    }

    jlong handle = env->GetLongField(jListener, gJniTsPlaybackListenerCtx.context);
    return reinterpret_cast<JniPlaybackListener*>(handle);
}

void JniPlaybackListener::notifyPlaybackEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        ALOGI("[%s/%d] env is null or event is null", __FUNCTION__, __LINE__);
        return;
    }

    if (env->IsInstanceOf(jEvent, gVideoFormatChangeEventCls)) {
        // VideoFormatChangeEvent
        ALOGI("[%s/%d] VideoFormatChangeEvent", __FUNCTION__, __LINE__);
        handleVideoFormatChangeEvent(env, jEvent);
        return;
    } else if (env->IsInstanceOf(jEvent, gAudioFormatChangeEventCls)) {
        // AudioFormatChangeEvent
        ALOGI("[%s/%d] AudioFormatChangeEvent", __FUNCTION__, __LINE__);
        handleAudioFormatChangeEvent(env, jEvent);
    } else if (env->IsInstanceOf(jEvent, gVideoFirstFrameEventCls)) {
        // VideoFirstFrameEvent
        ALOGI("[%s/%d] VideoFirstFrameEvent", __FUNCTION__, __LINE__);
        handleVideoFirstFrameEvent(env, jEvent);
    } else if (env->IsInstanceOf(jEvent, gAudioFirstFrameEventCls)) {
        // AudioFirstFrameEvent
        ALOGI("[%s/%d] AudioFirstFrameEvent", __FUNCTION__, __LINE__);
        handleAudioFirstFrameEvent(env, jEvent);
    } else if (env->IsInstanceOf(jEvent, gDecodeFirstVideoFrameEventCls)) {
        // DecodeFirstVideoFrameEvent
        ALOGI("[%s/%d] DecodeFirstVideoFrameEvent", __FUNCTION__, __LINE__);
        handleDecodeFirstVideoFrameEvent(env, jEvent);
    } else if (env->IsInstanceOf(jEvent, gDecodeFirstAudioFrameEventCls)) {
        // DecodeFirstAudioFrameEvent
        ALOGI("[%s/%d] DecodeFirstAudioFrameEvent", __FUNCTION__, __LINE__);
        handleDecodeFirstAudioFrameEvent(env, jEvent);
    } else if (env->IsInstanceOf(jEvent, gPtsEventCls)) {
        // PtsEvent
        handlePtsEvent(env, jEvent);
    } else if (env->IsInstanceOf(jEvent, gPlaybackInfoEventCls)) {
        // PlaybackInfoEvent
        handlePlaybackInfoEvent(env, jEvent);
    } else {
        ALOGE("[%s/%d] notifyPlaybackEvent unknown event", __FUNCTION__, __LINE__);
    }

    // warning: Attempt to remove non-JNI local reference, dumping thread
    // env->DeleteLocalRef(jEvent);
}

void JniPlaybackListener::handleVideoFormatChangeEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jobject jVideoFormat = env->GetObjectField(jEvent, gVideoFormatChangeEventCtx.videoFormat);
    if (jVideoFormat == nullptr) {
        ALOGE("%s[%d] video format is null", __func__, __LINE__);
        return;
    }

    int32_t width = 0;
    int32_t height = 0;
    int32_t frameRate = 0;
    int32_t aspectratio = 0;

    if (!JniMediaFormat::getInteger(env, jVideoFormat, "width", &width)) {
        ALOGE("[%s/%d] failed to get video width from MediaFormat", __FUNCTION__, __LINE__);
        env->DeleteLocalRef(jVideoFormat);
        return;
    }
    if (!JniMediaFormat::getInteger(env, jVideoFormat, "height", &height)) {
        ALOGE("[%s/%d] failed to get video height from MediaFormat", __FUNCTION__, __LINE__);
        env->DeleteLocalRef(jVideoFormat);
        return;
    }
    frameRate = JniMediaFormat::getInteger(env, jVideoFormat, "frame-rate", frameRate);
    aspectratio = JniMediaFormat::getInteger(env, jVideoFormat, "aspect-ratio", aspectratio);

    jni_asplayer_video_format_t videoFormat = {
        .frame_width = (uint32_t) width,
        .frame_height = (uint32_t) height,
        .frame_rate = (uint32_t) frameRate,
        .frame_aspectratio = (uint32_t) aspectratio,
    };

    jni_asplayer_event event = {
        .event = { .video_format = videoFormat },
        .type = JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED
    };

    env->DeleteLocalRef(jVideoFormat);

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handleAudioFormatChangeEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jobject jAudioFormat = env->GetObjectField(jEvent, gAudioFormatChangeEventCtx.audioFormat);
    if (jAudioFormat == nullptr) {
        ALOGE("%s[%d] video format is null", __func__, __LINE__);
        return;
    }

    int32_t sampleRate = 0;
    int32_t channels = 0;
    int32_t channelMask = 0;

    if (!JniMediaFormat::getInteger(env, jAudioFormat, "sample-rate", &sampleRate)) {
        ALOGE("%s[%d] failed to get sampleRate from MediaFormat", __func__, __LINE__);
        env->DeleteLocalRef(jAudioFormat);
        return;
    }
    if (!JniMediaFormat::getInteger(env, jAudioFormat, "channel-count", &channels)) {
        ALOGE("%s[%d] failed to get channelCount from MediaFormat", __func__, __LINE__);
        env->DeleteLocalRef(jAudioFormat);
        return;
    }
    if (!JniMediaFormat::getInteger(env, jAudioFormat, "channel-mask", &channelMask)) {
        ALOGE("%s[%d] failed to get channelMask from MediaFormat", __func__, __LINE__);
        env->DeleteLocalRef(jAudioFormat);
        return;
    }

    jni_asplayer_audio_format_t audioFormat = {
        .sample_rate = (uint32_t) sampleRate,
        .channels = (uint32_t) channels,
        .channel_mask = (uint32_t) channelMask
    };

    jni_asplayer_event event = {
        .event = { .audio_format = audioFormat },
        .type = JNI_ASPLAYER_EVENT_TYPE_AUDIO_CHANGED
    };

    env->DeleteLocalRef(jAudioFormat);

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handleVideoFirstFrameEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jlong jPts = env->GetLongField(jEvent, gFirstFrameEventCtx.pts);

    jni_asplayer_pts_t pts = {
        .stream_type = JNI_ASPLAYER_TS_STREAM_VIDEO,
        .pts = static_cast<uint64_t>(jPts),
        .renderTime = static_cast<uint64_t>(jPts)
    };

    jni_asplayer_event event = {
        .event = { .pts = pts, },
        .type = JNI_ASPLAYER_EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO,
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handleAudioFirstFrameEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jlong jPts = env->GetLongField(jEvent, gFirstFrameEventCtx.pts);

    jni_asplayer_pts_t pts = {
        .stream_type = JNI_ASPLAYER_TS_STREAM_AUDIO,
        .pts = static_cast<uint64_t>(jPts),
        .renderTime = static_cast<uint64_t>(jPts)
    };

    jni_asplayer_event event = {
        .event = { .pts = pts, },
        .type = JNI_ASPLAYER_EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO,
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handleDecodeFirstVideoFrameEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jlong jPts = env->GetLongField(jEvent, gFirstFrameEventCtx.pts);

    jni_asplayer_pts_t pts = {
        .stream_type = JNI_ASPLAYER_TS_STREAM_VIDEO,
        .pts = static_cast<uint64_t>(jPts),
        .renderTime = static_cast<uint64_t>(jPts)
    };

    jni_asplayer_event event = {
        .event = { .pts = pts, },
        .type = JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO,
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handleDecodeFirstAudioFrameEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jlong jPts = env->GetLongField(jEvent, gFirstFrameEventCtx.pts);

    jni_asplayer_pts_t pts = {
        .stream_type = JNI_ASPLAYER_TS_STREAM_AUDIO,
        .pts = static_cast<uint64_t>(jPts),
        .renderTime = static_cast<uint64_t>(jPts)
    };

    jni_asplayer_event event = {
        .event = { .pts = pts, },
        .type = JNI_ASPLAYER_EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO,
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handlePtsEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jint jStreamType = env->GetIntField(jEvent, gPtsEventCtx.streamType);
    jlong jPts = env->GetLongField(jEvent, gPtsEventCtx.pts);
    jlong jRenderTime = env->GetLongField(jEvent, gPtsEventCtx.renderTime);

    jni_asplayer_pts_t pts = {
        .stream_type = static_cast<jni_asplayer_stream_type>(jStreamType),
        .pts = static_cast<uint64_t>(jPts),
        .renderTime = static_cast<uint64_t>(jRenderTime)
    };

    jni_asplayer_event event = {
        .type = JNI_ASPLAYER_EVENT_TYPE_PTS,
        .event = { .pts = pts, },
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::handlePlaybackInfoEvent(JNIEnv *env, jobject jEvent) {
    if (env == nullptr || jEvent == nullptr) {
        return;
    }

    jint jEventType = env->GetIntField(jEvent, gPlaybackEventCtx.eventType);

    ALOGI("[%s/%d] handlePlaybackInfoEvent, eventType: %d", __func__, __LINE__, jEventType);

    jni_asplayer_event event = {
            .type = static_cast<jni_asplayer_event_type>(jEventType),
            .event = { .bufptr = nullptr, },
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::notifyCallbackEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    } else if (mCallback == nullptr) {
        return;
    }

    ALOGD("[%s/%d] notifyCallbackEvent event: %d", __FUNCTION__, __LINE__, event->type);

    if (mCallback != nullptr) {
        mCallback(mUserData, event);
    }
}

void JniPlaybackListener::release(JNIEnv *env) {
    if (mJavaListener != nullptr) {
        env->DeleteGlobalRef(mJavaListener);
        mJavaListener = nullptr;
    }

    mCallback = nullptr;
    mUserData = nullptr;
}