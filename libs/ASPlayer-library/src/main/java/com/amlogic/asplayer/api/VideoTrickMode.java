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
     * Smooth trick mode
     */
    public static final int TRICK_MODE_SMOOTH = 1;

    /**
     * Trick mode by seek
     */
    public static final int TRICK_MODE_BY_SEEK = 2;

    /**
     * Decoding and Out I frame only
     */
    public static final int TRICK_MODE_IONLY = 3;
}