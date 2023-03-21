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

// VideoFormatChangeEvent
struct video_format_change_event_t {
    jmethodID constructorMID;
    jfieldID videoFormat;
};

// PlaybackListener
struct jni_tsplayback_listener_t {
    jmethodID constructorMID;
    jfieldID context;
};

static jclass gJniTsPlaybackListenerCls;
static jni_tsplayback_listener_t gJniTsPlaybackListenerCtx;

static jclass gVideoFormatChangeEventCls;
static video_format_change_event_t gVideoFormatChangeEventCtx;

bool JniPlaybackListener::gInited = false;

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
    jclass jniPlaybackListenerCls = env->FindClass(JNI_PLAYBACK_LISTENER_CLASS_NAME);
    gJniTsPlaybackListenerCls = static_cast<jclass>(env->NewGlobalRef(jniPlaybackListenerCls));
    env->DeleteLocalRef(jniPlaybackListenerCls);
    gJniTsPlaybackListenerCtx.constructorMID = env->GetMethodID(gJniTsPlaybackListenerCls, "<init>", "()V");
    gJniTsPlaybackListenerCtx.context = env->GetFieldID(gJniTsPlaybackListenerCls, "mNativeContext", "J");

    // VideoFormatChangeEvent
    jclass videoFormatChangeEventCls = env->FindClass("com/amlogic/asplayer/api/TsPlaybackListener$VideoFormatChangeEvent");
    gVideoFormatChangeEventCls = static_cast<jclass>(env->NewGlobalRef(videoFormatChangeEventCls));
    env->DeleteLocalRef(videoFormatChangeEventCls);
    gVideoFormatChangeEventCtx.constructorMID = env->GetMethodID(gVideoFormatChangeEventCls, "<init>", "(Landroid/media/MediaFormat;)V");
    gVideoFormatChangeEventCtx.videoFormat = env->GetFieldID(gVideoFormatChangeEventCls, "mVideoFormat","Landroid/media/MediaFormat;");

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
        return;
    }

    if (env->IsInstanceOf(jEvent, gVideoFormatChangeEventCls)) {
        // VideoFormatChangeEvent
        ALOGI("%s[%d] VideoFormatChangeEvent", __func__, __LINE__);
        handleVideoFormatChangeEvent(env, jEvent);
        return;
    }
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
        ALOGE("%s[%d] failed to get video width from MediaFormat", __func__, __LINE__);
        env->DeleteLocalRef(jVideoFormat);
        return;
    }
    if (!JniMediaFormat::getInteger(env, jVideoFormat, "height", &height)) {
        ALOGE("%s[%d] failed to get video height from MediaFormat", __func__, __LINE__);
        env->DeleteLocalRef(jVideoFormat);
        return;
    }

    jni_asplayer_video_format_t videoFormat {
        .frame_width = (uint32_t) width,
        .frame_height = (uint32_t) height,
        .frame_rate = (uint32_t) frameRate,
        .frame_aspectratio = (uint32_t) aspectratio
    };

    jni_asplayer_event event {
        .event = {videoFormat},
        .type = JNI_ASPLAYER_EVENT_TYPE_VIDEO_CHANGED
    };

    notifyCallbackEvent(&event);
}

void JniPlaybackListener::notifyCallbackEvent(jni_asplayer_event *event) {
    if (event == nullptr) {
        return;
    } else if (mCallback == nullptr) {
        return;
    }

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