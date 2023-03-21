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
 * Avsync mode
 */
public class AVSyncMode {

    /**
     * Video Master
     */
    public static final int VMASTER = 0;

    /**
     * Audio Master
     */
    public static final int AMASTER = 1;

    /**
     * PCR Master
     */
    public static final int PCRMASTER = 2;

    /**
     * Free run
     */
    public static final int NOSYNC = 3;

//    /**
//     * Video Master
//     */
//    VMASTER(0),
//
//    /**
//     * Audio Master
//     */
//    AMASTER(1),
//
//    /**
//     * PCR Master
//     */
//    PCRMASTER(2),
//
//    /**
//     * Free run
//     */
//    NOSYNC(3);
//
//    private int value;
//
//    AVSyncMode(int value) {
//        this.value = value;
//    }
}