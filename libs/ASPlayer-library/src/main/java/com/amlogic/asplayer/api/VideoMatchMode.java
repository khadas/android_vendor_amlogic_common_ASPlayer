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
 * Video display match mode
 */
public class VideoMatchMode {

    /**
     * Keep original
     */
    public static final int NONE = 0;

    /**
     * Stretch the video to the full window
     */
    public static final int FULLSCREEN = 1;

    /**
     * Letter box match mode
     */
    public static final int LETTER_BOX = 2;

    /**
     * Pan scan match mode
     */
    public static final int PAN_SCAN = 3;

    /**
     * Combined pan scan and letter box
     */
    public static final int COMBINED = 4;

    /**
     * Stretch the video width to the full window
     */
    public static final int WIDTHFULL = 5;

    /**
     * Stretch the video height to the full window
     */
    public static final int HEIGHTFULL = 6;

    public static final int WIDEOPTION_4_3_LETTER_BOX = 7;

    public static final int WIDEOPTION_4_3_PAN_SCAN = 8;

    public static final int WIDEOPTION_4_3_COMBINED = 9;

    public static final int WIDEOPTION_16_9_IGNORE = 10;

    public static final int WIDEOPTION_16_9_LETTER_BOX = 11;

    public static final int WIDEOPTION_16_9_PAN_SCAN = 12;

    public static final int WIDEOPTION_16_9_COMBINED = 13;

    public static final int WIDEOPTION_CUSTOM = 14;
}