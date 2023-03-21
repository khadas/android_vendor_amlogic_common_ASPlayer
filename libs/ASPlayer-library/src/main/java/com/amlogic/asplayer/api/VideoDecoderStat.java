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
 * Video decoder real time information
 */
public class VideoDecoderStat {

    public VideoQos qos;

    /** us */
    public int decodeTimeCost;

    public int frameWidth;

    public int frameHeight;

    public int frameRate;

    /**
     * original bit_rate
     */
    public  int bitDepthLuma;

    public int frameDur;

    /**
     * original frame_data
     */
    public int bitDepthChroma;

    public int errorCount;

    public int status;

    public int frameCount;

    public int errorFrameCount;

    public int dropFrameCount;

    public long totalData;

    /**
     * original samp_cnt
     */
    public int doubleWriteMode;

    public int offset;

    public int ratioControl;

    public int vfType;

    public int signalType;

    public int pts;

    public long ptsUs64;
}