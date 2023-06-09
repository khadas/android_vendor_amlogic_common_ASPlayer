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
 * Video qos information
 */
public class VideoQos {

    public int num;

    public int type;

    public int size;

    public int pts;

    public int maxQp;

    public int avgQp;

    public int minQp;

    public int maxSkip;

    public int avgSkip;

    public int minSkip;

    public int maxMv;

    public int minMv;

    public int avgMv;

    public int decodeBuffer;
}