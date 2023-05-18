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
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class TunerHelper {

    private static final Map<String, String> sVideoStreamTypeMap = new HashMap<>();
    private static final Map<String, String> sAudioStreamTypeMap = new HashMap<>();

    static {
        // VideoStreamType
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_MPEG1", "video/mpeg");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_MPEG2", "video/mpeg2");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_MPEG4P2", "video/mp4v-es");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_AVC", "video/avc");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_HEVC", "video/hevc");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_VC1", "video/vc1");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_VP8", "video/x-vnd.on2.vp8");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_VP9", "video/x-vnd.on2.vp9");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_AV1", "video/av01");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_AVS", "video/avs");
        sVideoStreamTypeMap.put("VIDEO_STREAM_TYPE_AVS2", "video/avs2");

        // AudioStreamType
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_MP3", "audio/mpeg");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_MPEG1", "audio/mpeg");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_AAC", "audio/mp4a-latm");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_AC3", "audio/ac3");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_EAC3", "audio/eac3");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_AC4", "audio/ac4");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_DTS", "audio/vnd.dts");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_DTS_HD", "audio/vnd.dts.hd");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_OPUS", "audio/opus");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_VORBIS", "audio/vorbis");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_DRA", "audio/vnd.dra");
        sAudioStreamTypeMap.put("AUDIO_STREAM_TYPE_AAC_LATM", "audio/mp4a-latm");
    }

    public static int getVideoStreamTypeByReflect(String videoStreamTypeName) {
        return getConstantIntFiledByReflect(AvSettings.class, videoStreamTypeName);
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

    public static void setVideoStreamType(AvSettings.Builder builder, String videoStreamTypeName) {
        TvLog.i("setVideoStreamType called, videoStreamType: %s", videoStreamTypeName);
        int videoStreamType = getVideoStreamTypeByReflect(videoStreamTypeName);
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

    public static String getMimeTypeFromVideoStreamType(String videoStreamTypeName) {
        return sVideoStreamTypeMap.get(videoStreamTypeName);
    }

    public static String getMimeTypeFromAudioStreamType(String audioStreamTypeName) {
        return sAudioStreamTypeMap.get(audioStreamTypeName);
    }

    public static int getAudioStreamTypeByReflect(String fieldName) {
        return getConstantIntFiledByReflect(AvSettings.class, fieldName);
    }

    public static void setAudioStreamType(AvSettings.Builder builder, String audioStreamTypeName) {
        TvLog.i("setAudioStreamType called, audioStreamType: %s", audioStreamTypeName);
        int audioStreamType = getAudioStreamTypeByReflect(audioStreamTypeName);
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

    public static class TunerVersionChecker {
        public static int TUNER_VERSION_UNKNOWN;
        public static int TUNER_VERSION_1_0;
        public static int TUNER_VERSION_1_1;


        static Method sIsHigherOrEqualVersionTo;
        static Method sGetTunerVersion;
        static {
            try {
                Class<?> TunerVersionChecker =
                        Class.forName("android.media.tv.tuner.TunerVersionChecker");
                sIsHigherOrEqualVersionTo = TunerVersionChecker
                        .getDeclaredMethod("isHigherOrEqualVersionTo", int.class);
                sGetTunerVersion = TunerVersionChecker
                        .getDeclaredMethod("getTunerVersion");
                TUNER_VERSION_UNKNOWN = TunerVersionChecker
                        .getDeclaredField("TUNER_VERSION_UNKNOWN").getInt(null);
                TUNER_VERSION_1_0 = TunerVersionChecker
                        .getDeclaredField("TUNER_VERSION_1_0").getInt(null);
                TUNER_VERSION_1_1 = TunerVersionChecker
                        .getDeclaredField("TUNER_VERSION_1_1").getInt(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static boolean isHigherOrEqualVersionTo(int version) {
            try {
                Boolean result = (Boolean) sIsHigherOrEqualVersionTo.invoke(null, version);
                return result != null? result: false;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        public static int getTunerVersion() {
            try {
                Integer result = (Integer) sGetTunerVersion.invoke(null);
                return result != null? result: TUNER_VERSION_UNKNOWN;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return TUNER_VERSION_UNKNOWN;
        }
    }
}
