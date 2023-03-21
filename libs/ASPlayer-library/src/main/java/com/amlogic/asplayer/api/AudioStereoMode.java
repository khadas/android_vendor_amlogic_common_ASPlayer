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
 * Audio stereo output mode
 */
public class AudioStereoMode {

    /**
     * Stereo mode
     */
    public static final int STEREO = 0;

    /**
     * Output left channel
     */
    public static final int LEFT = 1;

    /**
     * Output right channel
     */
    public static final int RIGHT = 2;

    /**
     * Swap left and right channel
     */
    public static final int SWAP = 3;

    /**
     * mix left and right channel
     */
    public static final int LRMIX = 4;
}