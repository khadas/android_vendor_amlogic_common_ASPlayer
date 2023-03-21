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
 * Player working mode
 */
public class WorkMode {

    /**
     * Normal mode
     */
    public static final int NORMAL = 0;

    /**
     * Only caching data, do not decode. Used in FCC
     */
    public static final int CACHING_ONLY = 1;

    /**
     * Decode data but do not output
     */
    public static final int DECODE_ONLY = 2;
}