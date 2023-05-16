/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo.utils;

import android.media.tv.tuner.filter.AvSettings;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TunerHelper {

    public static int getVideoStreamType(String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return -1;
        }

        String name = null;

        switch (mimeType) {
            case "video/mpeg2":
                name = "VIDEO_STREAM_TYPE_MPEG2";
                break;
            case "video/avc":
                name = "VIDEO_STREAM_TYPE_AVC";
                break;
            case "video/hevc":
                name = "VIDEO_STREAM_TYPE_HEVC";
                break;
        }

        if (!TextUtils.isEmpty(name)) {
            return getVideoStreamTypeByReflect(name);
        } else {
            return -1;
        }
    }

    public static int getVideoStreamTypeByReflect(String fieldName) {
        return getConstantIntFiledByReflect(AvSettings.class, fieldName);
    }

    public static int getConstantIntFiledByReflect(Class<?> cls, String name) {
        try {
            Field field = cls.getField(name);
            return field.getInt(null);
        } catch (IllegalAccessException e) {
            TvLog.e("getIntField failed IllegalAccessException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            TvLog.e("getIntField failed NoSuchFieldException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        }
        return -1;
    }

    public static void setVideoStreamType(AvSettings.Builder builder, String mimeType) {
        TvLog.i("setVideoStreamType called, mimeType: %s", mimeType);
        int videoStreamType = getVideoStreamType(mimeType);
        if (videoStreamType != -1) {
            setVideoStreamType(builder, videoStreamType);
        }
    }

    public static void setVideoStreamType(AvSettings.Builder builder, int videoStreamType) {
        try {
            Class<?> cls = builder.getClass();
            Method setVideoStreamTypeMethod = cls.getMethod("setVideoStreamType", int.class);
            setVideoStreamTypeMethod.invoke(builder, videoStreamType);
        } catch (NoSuchMethodException e) {
            TvLog.e("setVideoStreamType failed NoSuchMethodException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            TvLog.e("setVideoStreamType failed IllegalAccessException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            TvLog.e("setVideoStreamType failed InvocationTargetException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }

    public static int getAudioStreamType(String mimeType) {
        if (TextUtils.isEmpty(mimeType)) {
            return -1;
        }

        String name = null;

        switch (mimeType) {
            case "audio/mpeg":
                name = "AUDIO_STREAM_TYPE_MPEG1";
                break;
            case "audio/mp4a-latm":
                name = "AUDIO_STREAM_TYPE_AAC_LATM";
                break;
        }

        if (!TextUtils.isEmpty(name)) {
            return getAudioStreamTypeByReflect(name);
        } else {
            return -1;
        }
    }

    public static int getAudioStreamTypeByReflect(String fieldName) {
        return getConstantIntFiledByReflect(AvSettings.class, fieldName);
    }

    public static void setAudioStreamType(AvSettings.Builder builder, String mimeType) {
        TvLog.i("setAudioStreamType called, mimeType: %s", mimeType);
        int audioStreamType = getAudioStreamType(mimeType);
        if (audioStreamType != -1) {
            setAudioStreamType(builder, audioStreamType);
        }
    }

    public static void setAudioStreamType(AvSettings.Builder builder, int audioStreamType) {
        try {
            Class<?> cls = builder.getClass();
            Method setAudioStreamTypeMethod = cls.getMethod("setAudioStreamType", int.class);
            setAudioStreamTypeMethod.invoke(builder, audioStreamType);
        } catch (NoSuchMethodException e) {
            TvLog.e("setAudioStreamType failed NoSuchMethodException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            TvLog.e("setAudioStreamType failed IllegalAccessException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            TvLog.e("setAudioStreamType failed InvocationTargetException %s, %s", e.getMessage(), Log.getStackTraceString(e));
            e.printStackTrace();
        }
    }
}
