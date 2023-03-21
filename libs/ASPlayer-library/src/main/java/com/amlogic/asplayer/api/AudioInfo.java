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
 * Audio basic information
 */
public class AudioInfo {

    /**
     * Audio sample rate
     */
    public int sampleRate;

    /**
     * Audio channels
     */
    public int channels;

    /**
     * Audio channel mask
     */
    public int channelMask;

    /**
     * Audio bit rate
     */
    public int bitrate;
}