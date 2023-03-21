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

    public static final String EXTRA_TS_PATH = "ts.path";
    public static final String EXTRA_PROGRAM_BUNDLE = "program.bundle";
    public static final String EXTRA_VIDEO_PID = "video.pid";
    public static final String EXTRA_VIDEO_MIMETYPE = "video.mimetype";
    public static final String EXTRA_AUDIO_PID = "audio.pid";
    public static final String EXTRA_AUDIO_MIMETYPE = "audio.mimetype";

    public static final String EXTRA_FRONTEND_BUNDLE = "frontend.bundle";
    public static final String EXTRA_FRONTEND_FREQUENCY = "frequency";

    public static final int DEFAULT_VIDEO_PID = 0x0258;
    public static final int DEFAULT_AUDIO_PID = 0x0259;
    public static final String DEFAULT_VIDEO_MIMETYPE = "video/mpeg2";
    public static final String DEFAULT_AUDIO_MIMETYPE = "audio/mpeg";
    public static final String DEFAULT_TS_PATH = "/data/video/BBC_MUX_UH_DVBT.ts";
    public static final int DEFAULT_DVBT_FREQUENCY = 490;
}
