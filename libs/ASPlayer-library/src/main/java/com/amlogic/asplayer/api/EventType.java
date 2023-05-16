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
     * user data (afd)
     */
    public static final int EVENT_TYPE_USERDATA_AFD = EVENT_TYPE_PTS + 1;

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
     * The video decoder outputs the first frame.
     */
    public static final int EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO = EVENT_TYPE_DATA_RESUME + 1;

    /**
     * The audio decoder outputs the first frame.
     */
    public static final int EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO = EVENT_TYPE_DECODE_FIRST_FRAME_VIDEO + 1;

    /**
     * The video decoder render the first frame
     */
    public static final int EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO = EVENT_TYPE_DECODE_FIRST_FRAME_AUDIO + 1;

    /**
     * The audio decoder render the first frame
     */
    public static final int EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO = EVENT_TYPE_RENDER_FIRST_FRAME_VIDEO + 1;

    /**
     * av sync done
     */
    public static final int EVENT_TYPE_AV_SYNC_DONE = EVENT_TYPE_RENDER_FIRST_FRAME_AUDIO + 1;
}