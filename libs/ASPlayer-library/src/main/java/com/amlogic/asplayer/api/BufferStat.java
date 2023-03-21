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
 * ASPlayer stream buffer status
 */
// Event
public class BufferStat {

    /**
     * Buffer size
     */
    public int size;

    /**
     * The len of data in buffer
     */
    public int dataLength;

    /**
     * The len of free in buffer
     */
    public int freeLength;
}