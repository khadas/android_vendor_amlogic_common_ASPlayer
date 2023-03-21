/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_JNIPLAYBACKLISTENER_H
#define JNI_ASPLAYER_JNIPLAYBACKLISTENER_H

#include <mutex>
#include <jni.h>
#include <JNIASPlayer.h>

class JniPlaybackListener {

public:
    static bool init(JNIEnv *env);

    bool createPlaybackListener(JNIEnv *env);

    jobject getJavaPlaybackListener();

    void setEventCallback(event_callback callback, void *userdata);

    void notifyPlaybackEvent(JNIEnv *env, jobject jEvent);

    void release(JNIEnv *env);

    static JniPlaybackListener* getNativeListener(JNIEnv *env, jobject jListener);

public:
    JniPlaybackListener(event_callback callback, void *userdata);
    virtual ~JniPlaybackListener();

private:
    static void setNativeHandle(JNIEnv *env, jobject jListener, JniPlaybackListener *listener);

    void handleVideoFormatChangeEvent(JNIEnv *env, jobject jEvent);

    void notifyCallbackEvent(jni_asplayer_event *event);

private:
    jobject mJavaListener;
    event_callback mCallback;
    void *mUserData;
    std::mutex mMutex;
    static bool gInited;
};


#endif //JNI_ASPLAYER_JNIPLAYBACKLISTENER_H
