/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.api;

/**
 * Call back event type
 */
public class EventType {

    /**
     * pts in for some stream
     */
    public static final int EVENT_TYPE_PTS = 0;

    /**
     * external subtitle of dtv
     */
    public static final int EVENT_TYPE_DTV_SUBTITLE = EVENT_TYPE_PTS + 1;

    /**
     * user data (afd)
     */
    public static final int EVENT_TYPE_USERDATA_AFD = EVENT_TYPE_DTV_SUBTITLE + 1;

    /**
     * user data (cc)
     */
    public static final int EVENT_TYPE_USERDATA_CC = EVENT_TYPE_USERDATA_AFD + 1;

    /**
     * video format changed
     */
    public static final int EVENT_TYPE_VIDEO_CHANGED = EVENT_TYPE_USERDATA_CC + 1;

    /**
     * audio format changed
     */
    public static final int EVENT_TYPE_AUDIO_CHANGED = EVENT_TYPE_VIDEO_CHANGED + 1;

    /**
     * demod data loss
     */
    public static final int EVENT_TYPE_DATA_LOSS = EVENT_TYPE_AUDIO_CHANGED + 1;

    /**
     * demod data resume
     */
    public static final int EVENT_TYPE_DATA_RESUME = EVENT_TYPE_DATA_LOSS + 1;

    /**
     * scrambling status changed
     */
    public static final int EVENT_TYPE_SCRAMBLING = EVENT_TYPE_DATA_RESUME + 1;

    /**
     * first video frame showed
     */
    public static final int EVENT_TYPE_FIRST_FRAME = EVENT_TYPE_SCRAMBLING + 1;

    /**
     * endof stream mode
     */
    public static final int EVENT_TYPE_STREAM_MODE_EOF = EVENT_TYPE_FIRST_FRAME + 1;

    /**
     * The video decoder outputs the first frame.
     */
    public static final int EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO = EVENT_TYPE_STREAM_MODE_EOF + 1;

    /**
     * The audio decoder outputs the first frame.
     */
    public static final int EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO = EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO + 1;

    /**
     * av sync done
     */
    public static final int EVENT_TYPE_AV_SYNC_DONE = EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO + 1;

    /**
     * input video buffer done
     */
    public static final int EVENT_TYPE_INPUT_VIDEO_BUFFER_DONE = EVENT_TYPE_AV_SYNC_DONE + 1;

    /**
     * input audio buffer done
     */
    public static final int EVENT_TYPE_INPUT_AUDIO_BUFFER_DONE = EVENT_TYPE_INPUT_VIDEO_BUFFER_DONE + 1;

    /**
     * The video decoder frame error count
     */
    public static final int EVENT_TYPE_DECODE_FRAME_ERROR_COUNT = EVENT_TYPE_INPUT_AUDIO_BUFFER_DONE + 1;

    /**
     * Video amstream buffer overflow
     */
    public static final int EVENT_TYPE_VIDEO_OVERFLOW = EVENT_TYPE_DECODE_FRAME_ERROR_COUNT + 1;

    /**
     * Video amstream buffer underflow
     */
    public static final int EVENT_TYPE_VIDEO_UNDERFLOW = EVENT_TYPE_VIDEO_OVERFLOW + 1;

    /**
     * Audio amstream buffer overflow
     */
    public static final int EVENT_TYPE_AUDIO_OVERFLOW = EVENT_TYPE_VIDEO_UNDERFLOW + 1;

    /**
     * Audio amstream buffer underflow
     */
    public static final int EVENT_TYPE_AUDIO_UNDERFLOW = EVENT_TYPE_AUDIO_OVERFLOW + 1;

    /**
     * Video invalid timestamp
     */
    public static final int EVENT_TYPE_VIDEO_INVALID_TIMESTAMP = EVENT_TYPE_AUDIO_UNDERFLOW + 1;

    /**
     * Video invalid data
     */
    public static final int EVENT_TYPE_VIDEO_INVALID_DATA = EVENT_TYPE_VIDEO_INVALID_TIMESTAMP + 1;

    /**
     * Audio invalid timestamp
     */
    public static final int EVENT_TYPE_AUDIO_INVALID_TIMESTAMP = EVENT_TYPE_VIDEO_INVALID_DATA + 1;

    /**
     * Audio invalid data
     */
    public static final int EVENT_TYPE_AUDIO_INVALID_DATA = EVENT_TYPE_AUDIO_INVALID_TIMESTAMP + 1;

    /**
     * Video is not supported.
     */
    public static final int EVENT_TYPE_DECODE_VIDEO_UNSUPPORT = EVENT_TYPE_AUDIO_INVALID_DATA + 1;

    /**
     * Instance was preempted, apk need release this instance
     */
    public static final int EVENT_TYPE_PREEMPTED = EVENT_TYPE_DECODE_VIDEO_UNSUPPORT + 1;
}