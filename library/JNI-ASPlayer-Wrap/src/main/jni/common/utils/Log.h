/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */

#ifndef JNI_ASPLAYER_WRAPPER_LOG_H
#define JNI_ASPLAYER_WRAPPER_LOG_H

#include <android/log.h>

#if (USE_SYSTEM_SO == 1)
#define LOG_TAG "DJniASPlayerWrapper"
#else
#define LOG_TAG "JniASPlayerWrapper"
#endif

#define ALOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define ALOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define ALOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define ALOGV(...) ((void)__android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__))

#define LOG_FUNCTION_ENTER() ALOGV("%s[%d] start", __func__, __LINE__)
#define LOG_FUNCTION_END() ALOGV("%s[%d] ok", __func__, __LINE__)
#define LOG_FUNCTION_INT_END(ret) ALOGV("%s[%d] ok, " #ret ": %d", __func__, __LINE__, ret)

#endif //JNI_ASPLAYER_WRAPPER_LOG_H
