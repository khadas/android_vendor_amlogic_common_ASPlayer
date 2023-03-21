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
 * Video decoder trick mode
 */
public class VideoTrickMode {

    /**
     * Disable trick mode
     */
    public static final int NONE = 0;

    /**
     * Pause the video decoder
     */
    public static final int PAUSE = 1;

    /**
     * Pause the video decoder when a new frame displayed
     */
    public static final int PAUSE_NEXT = 2;

    /**
     * Decoding and Out I frame only
     */
    public static final int IONLY = 3;
}