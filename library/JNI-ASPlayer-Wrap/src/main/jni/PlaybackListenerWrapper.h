/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_PLAYBACKLISTENER_WRAPPER_H
#define JNI_ASPLAYER_WRAPPER_PLAYBACKLISTENER_WRAPPER_H

#include <jni.h>
#include <JNIASPlayer.h>
#include "common/utils/AutoEnv.h"

class PlaybackListenerWrapper {

public:
    jobject getJavaListener();

    void notifyPlaybackEvent(jni_asplayer_event *event);

    void release();

public:
    PlaybackListenerWrapper(JNIEnv *env, jobject jListener);
    virtual ~PlaybackListenerWrapper();

private:
    void notifyVideoFormatChangeEvent(jni_asplayer_event *event);
    void notifyAudioFormatChangeEvent(jni_asplayer_event *event);
    void notifyVideoFirstFrameEvent(jni_asplayer_event *event);
    void notifyAudioFirstFrameEvent(jni_asplayer_event *event);
    void notifyDecodeFirstVideoFrameEvent(jni_asplayer_event *event);
    void notifyDecodeFirstAudioFrameEvent(jni_asplayer_event *event);

private:
    jobject mJavaListener;
};


#endif //JNI_ASPLAYER_WRAPPER_PLAYBACKLISTENER_WRAPPER_H
