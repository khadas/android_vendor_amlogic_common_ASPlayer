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
 * Video basic information
 */
public class VideoInfo {

    /**
     * Video frame width
     */
    public int width;

    /**
     * Video frame height
     */
    public int height;

    /**
     * Video frame rate
     */
    public int framerate;

    /**
     * Video bit rate
     */
    public int bitrate;

    /**
     * Video aspect ratio
     */
    public long ratio64;
}