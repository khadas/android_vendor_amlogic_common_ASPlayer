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
 * Call back event mask
 */
public class EventMask {

    public static final int EVENT_TYPE_PTS_MASK = 1 << EventType.EVENT_TYPE_PTS;

    public static final int EVENT_TYPE_DTV_SUBTITLE_MASK = 1 << EventType.EVENT_TYPE_DTV_SUBTITLE;

    public static final int EVENT_TYPE_USERDATA_AFD_MASK = 1 << EventType.EVENT_TYPE_USERDATA_AFD;

    public static final int EVENT_TYPE_USERDATA_CC_MASK = 1 << EventType.EVENT_TYPE_USERDATA_CC;

    public static final int EVENT_TYPE_VIDEO_CHANGED_MASK = 1 << EventType.EVENT_TYPE_VIDEO_CHANGED;

    public static final int EVENT_TYPE_AUDIO_CHANGED_MASK = 1 << EventType.EVENT_TYPE_AUDIO_CHANGED;

    public static final int EVENT_TYPE_DATA_LOSS_MASK = 1 << EventType.EVENT_TYPE_DATA_LOSS;

    public static final int EVENT_TYPE_DATA_RESUME_MASK = 1 << EventType.EVENT_TYPE_DATA_RESUME;

    public static final int EVENT_TYPE_SCRAMBLING_MASK = 1 << EventType.EVENT_TYPE_SCRAMBLING;

    public static final int EVENT_TYPE_FIRST_FRAME_MASK = 1 << EventType.EVENT_TYPE_FIRST_FRAME;
}