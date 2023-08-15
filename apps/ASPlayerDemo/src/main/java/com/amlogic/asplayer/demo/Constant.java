/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.demo;

public class Constant {

    public static final String LOG_TAG = "ASPlayerDemo";

    public static final boolean USE_JNI_AS_PLAYER = true;

    public static final String EXTRA_TS_PATH = "ts.path";
    public static final String EXTRA_PROGRAM_BUNDLE = "program.bundle";
    public static final String EXTRA_VIDEO_PID = "video.pid";
    public static final String EXTRA_VIDEO_MIME_TYPE = "video.mime.type";
    public static final String EXTRA_VIDEO_STREAM_TYPE = "video.stream.type";
    public static final String EXTRA_AUDIO_PID = "audio.pid";
    public static final String EXTRA_AUDIO_MIME_TYPE = "audio.mime.type";
    public static final String EXTRA_AUDIO_STREAM_TYPE = "audio.stream.type";

    public static final String EXTRA_FRONTEND_BUNDLE = "frontend.bundle";
    public static final String EXTRA_FRONTEND_FREQUENCY = "frequency";

    public static final String EXTRA_TUNER_HAL_VERSION = "tunerhal.version";

    public static final String EXTRA_PIP_PROGRAM_BUNDLE = "pip.program.bundle";

    public static final int TUNER_HAL_VERSION_1_0 = 1;
    public static final int TUNER_HAL_VERSION_1_1 = 2;

    public static final int DEFAULT_VIDEO_PID = 0x0258;
    public static final int DEFAULT_AUDIO_PID = 0x0259;
    public static final String DEFAULT_VIDEO_MIMETYPE = "video/mpeg2";
    public static final String DEFAULT_AUDIO_MIMETYPE = "audio/mpeg";
    public static final String DEFAULT_VIDEO_STREAM_TYPE = "VIDEO_STREAM_TYPE_MPEG2";
    public static final String DEFAULT_AUDIO_STREAM_TYPE = "AUDIO_STREAM_TYPE_MPEG1";
    public static final String DEFAULT_TS_PATH = "/data/video/BBC_MUX_UH_DVBT.ts";
    public static final int DEFAULT_DVBT_FREQUENCY = 490;
}
