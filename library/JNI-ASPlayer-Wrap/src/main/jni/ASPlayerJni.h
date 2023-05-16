/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef ASPLAYER_WRAPPER_ASPLAYER_JNI_H
#define ASPLAYER_WRAPPER_ASPLAYER_JNI_H

#include <jni.h>
#include "JNIASPlayer.h"

class ASPlayerJni {

public:
    bool initJni(JNIEnv *env);
    bool convertInitParams(JNIEnv *env, jobject jInitParam, jni_asplayer_init_params* outParams);
    bool convertVideoParams(JNIEnv *env, jobject jVideoParam, jni_asplayer_video_params *outParams);
    bool convertAudioParams(JNIEnv *env, jobject jAudioParam, jni_asplayer_audio_params *outParams);
    bool convertInputBuffer(JNIEnv *env, jobject jInputBuffer, jni_asplayer_input_buffer *outInputBuffer);

    static bool createVideoFormatChangeEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent);
    static bool createAudioFormatChangeEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent);
    static bool createVideoFirstFrameEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent);
    static bool createAudioFirstFrameEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent);
    static bool createDecodeFirstVideoFrameEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent);
    static bool createDecodeFirstAudioFrameEvent(JNIEnv *env, jni_asplayer_event *event, jobject *jEvent);

    static bool notifyPlaybackEvent(JNIEnv *env, jobject jPlaybackListener, jobject playbackEvent);

public:
    ASPlayerJni();
    virtual ~ASPlayerJni();
};


#endif //ASPLAYER_WRAPPER_ASPLAYER_JNI_H
